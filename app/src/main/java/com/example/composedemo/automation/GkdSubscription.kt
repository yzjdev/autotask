package com.example.composedemo.automation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GKD 订阅 JSON ↔ 本规则模型 的双向映射。
 *
 * 模型差异说明(GKD 一条规则一个动作;本模型一条规则一组有序动作):
 *  - 导入:GKD group 内的 rules 按顺序合并为一条 [GkdTask](动作列表);
 *    每条 rule 贡献一个动作,第一条带 matches 的规则的选择器作为触发条件。
 *  - 导出:每条 [GkdTask] 导出为一个 group,动作逐条展开为 rules
 *    (首条规则同时携带触发选择器),GKD 内按组展示但无顺序执行语义。
 *  - 不支持的动作(inputText / launchApp / check 等)导入时跳过并记录原因。
 */
object GkdSubscription {

    private val json = Json { ignoreUnknownKeys = true }

    /** 导入结果:可用的任务 + 被跳过条目的原因说明 */
    data class ImportResult(val tasks: List<GkdTask>, val skipped: List<String>)

    /** 解析 GKD 订阅 JSON;结构不合法抛异常,由调用方提示 */
    fun parse(text: String): ImportResult {
        val root = json.parseToJsonElement(text).jsonObject
        val groups = (root["groups"] ?: return ImportResult(emptyList(), listOf("订阅中没有 groups")))
            .jsonArray
        val tasks = ArrayList<GkdTask>()
        val skipped = ArrayList<String>()
        val defaultPkg = root.string("appId") ?: root.string("id") ?: ""

        groups.forEach { gEl ->
            val g = runCatching { gEl.jsonObject }.getOrNull() ?: return@forEach
            val pkg = g.string("appId") ?: defaultPkg
            if (pkg.isBlank()) {
                skipped += "组「${g.string("name") ?: "?"}」缺少 appId,跳过"
                return@forEach
            }
            val groupName = g.string("name") ?: "未命名"
            val groupActivities = g.stringList("activityIds")
            val rules = g["rules"]?.jsonArray ?: JsonArray(emptyList())
            if (rules.isEmpty()) {
                skipped += "组「$groupName」没有 rules,跳过"
                return@forEach
            }

            val actions = ArrayList<Action>()
            var trigger: GkdSelector? = null
            rules.forEach { rEl ->
                val r = runCatching { rEl.jsonObject }.getOrNull() ?: return@forEach
                val ruleName = r.string("name") ?: groupName
                // 选择器:解析失败视为跳过该条规则
                val sel = r.stringList("matches").firstOrNull()?.let { expr ->
                    runCatching { GkdSelector.parse(expr) }.getOrElse {
                        skipped += "规则「$ruleName」选择器无法解析,跳过"
                        null
                    }
                }
                if (r.stringList("matches").isNotEmpty() && sel == null) return@forEach
                if (trigger == null && sel != null) trigger = sel

                val repeat = (r.int("actionMaximum") ?: 1).coerceAtLeast(1)
                val action: Action? = when (r.string("action") ?: "click") {
                    "click", "clickNode" -> Action.Click(sel ?: GkdSelector(), repeat = repeat)
                    "longClick" -> Action.LongClick(sel ?: GkdSelector(), repeat = repeat)
                    "back", "backToHome" -> Action.Back(repeat = repeat)
                    "scrollForward" -> Action.Swipe(up = true, repeat = repeat)
                    "scrollBackward" -> Action.Swipe(up = false, repeat = repeat)
                    else -> null
                }
                if (action == null) {
                    skipped += "规则「$ruleName」动作 ${r.string("action")} 不支持,跳过"
                } else {
                    actions += action
                }
            }

            if (actions.isEmpty()) {
                skipped += "组「$groupName」无可用动作,跳过"
                return@forEach
            }
            tasks += GkdTask(
                id = "task_${System.currentTimeMillis()}_${tasks.size}",
                name = groupName,
                packageName = pkg,
                activityIds = groupActivities,
                matches = trigger,
                actions = actions,
            )
        }
        return ImportResult(tasks, skipped)
    }

    /** 导出为 GKD 订阅 JSON(单应用页使用,只含该应用的规则) */
    fun toJson(tasks: List<GkdTask>, appId: String): String {
        val root = buildJsonObject {
            put("id", "local-$appId-${System.currentTimeMillis()}")
            put("name", "本地规则导出 $appId")
            put("version", 1)
            put("appId", appId)
            put("date", System.currentTimeMillis())
            put("groups", buildJsonArray {
                tasks.forEachIndexed { gi, task ->
                    add(buildJsonObject {
                        put("key", gi + 1)
                        put("name", task.name)
                        put("enable", task.enabled)
                        put("appId", task.packageName)
                        put("activityIds", JsonArray(task.activityIds.map { JsonPrimitive(it) }))
                        // 本模型的多动作在 GKD 中无顺序执行语义,desc 记录完整步骤供参考
                        put("desc", task.actionsSummary())
                        put("rules", buildJsonArray {
                            task.actions.forEachIndexed { ri, action ->
                                val sel = (action as? Action.Click)?.matches
                                    ?: (action as? Action.LongClick)?.matches
                                // 首条规则同时携带触发选择器(链在动作目标之前)
                                val trigger = task.matches?.summary()
                                add(buildJsonObject {
                                    put("key", ri)
                                    put("name", "${task.name}·${ri + 1}")
                                    if (task.activityIds.isNotEmpty()) {
                                        put("activityIds", JsonArray(task.activityIds.map { JsonPrimitive(it) }))
                                    }
                                    if (ri == 0 && trigger != null) {
                                        put("matches", JsonArray(listOf(JsonPrimitive(trigger))))
                                    } else if (sel != null) {
                                        put("matches", JsonArray(listOf(JsonPrimitive(sel.summary()))))
                                    }
                                    put("action", when (action) {
                                        is Action.Click -> "click"
                                        is Action.LongClick -> "longClick"
                                        is Action.Back -> "back"
                                        is Action.Swipe -> if (action.up) "scrollForward" else "scrollBackward"
                                        // GKD 无延时动作;导出为不带 matches 的 click 占位会被
                                        // GKD 当作全页匹配点击,故此处跳过(见 desc 说明)
                                        is Action.Sleep -> return@forEachIndexed
                                        is Action.WaitNode -> "clickNode"
                                    })
                                    if (action.repeat > 1) put("actionMaximum", action.repeat)
                                })
                            }
                        })
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    // ---- JsonObject 便捷取值 ----

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content.matches(Regex("-?\\d+.*")) }?.content
            ?: (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.stringList(key: String): List<String> =
        when (val v = this[key]) {
            is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(v.content)
            else -> emptyList()
        }
}
