package com.yzjdev.autotask.automation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * GKD 订阅 JSON5 ↔ 规则模型 —— 逐函数照抄 gkd-li/gkd 仓库
 * gkd-app/src/main/kotlin/li/gkd/app/data/RawSubscription.kt 的解析语义:
 *
 *  - 规则元素三形态(JsonObject 原样;JsonPrimitive/JsonArray 包装为 {"matches": …})
 *  - 组元素三形态(JsonObject 原样;JsonPrimitive/JsonObject 包装为 {"rules": …})
 *  - rules 字段:缺省 = 空组(保留,不跳过);单个对象/裸值包装为单元素
 *  - 旧别名:actionCd→cd、actionDelay→delay
 *  - versionCode/versionName 新旧两种形态(matcher 对象 / versionCodes 数组兼容)
 *  - key 去重:distinctNotNullBy(无 key 规则全保留);组按 key 去重 distinctByIfAny
 *  - 全局组:key/name 必填,matchAnyApp/matchSystemApp/matchLauncher/apps/scopeKeys/
 *    disableIfAppGroupMatch 全量读取;全局规则同样支持 apps 约束
 *  - Position 六字段表达式字符串(left/top/right/bottom/x/y),SwipeArg{start,end,duration}
 *
 * 空规则组与无效选择器规则照 GKD 保留(标记 invalid 而非丢弃),选择器编译失败仅在执行期记日志。
 */
object GkdSubscription {

    private val json = Json { ignoreUnknownKeys = true }

    /** 导入结果:规则组 + 跳过条目原因(仅结构错误,如缺 key/name) */
    data class ImportResult(val tasks: List<GkdTask>, val skipped: List<String>)

    /** 解析 GKD 订阅 JSON/JSON5(照抄 RawSubscription.parse) */
    fun parse(text: String): ImportResult {
        val root = runCatching { json.parseToJsonElement(text) }
            .getOrElse { json.parseToJsonElement(stripJson5(text)) }
            .jsonObject
        val tasks = ArrayList<GkdTask>()
        val skipped = ArrayList<String>()

        // apps → RawApp(RawSubscription.jsonToSubscriptionRaw;空组应用照 GKD filterIfNotAll 过滤)
        val apps = (root["apps"] as? JsonArray)
            ?.mapIndexed { index, el -> runCatching { jsonToAppRaw(el, index) } }
            ?: emptyList()
        val keptApps = apps.filterIfNotAll { r -> r.getOrNull()?.groups?.isNotEmpty() == true }
        keptApps.forEach { r ->
            r.getOrElse { e ->
                skipped += "apps: ${e.message}"
                return@forEach
            }.let { app ->
                app.groups.forEach { g -> tasks += appGroupToTask(g, app.id) }
            }
        }

        // globalGroups → RawGlobalGroup
        (root["globalGroups"] as? JsonArray)?.mapIndexed { index, el ->
            index to runCatching { jsonToGlobalGroup(el, index) }
        }?.distinctByIfAny { (index, r) -> r.getOrNull()?.key?.let { "$index" } }
            ?.forEach { (_, r) ->
                r.getOrElse { e ->
                    skipped += "globalGroups: ${e.message}"
                    return@forEach
                }.let { g -> tasks += globalGroupToTask(g) }
            }

        if (apps.isEmpty() && root["globalGroups"] == null) skipped += "订阅中没有 apps/globalGroups"
        return ImportResult(tasks, skipped)
    }

    // ==================== 组 → GkdTask ====================

    private fun appGroupToTask(g: RawAppGroup, appId: String): GkdTask = GkdTask(
        id = "gkd_${appId}_${g.key}",
        name = g.name,
        key = g.key.toLong(),
        packageName = appId,
        // 导入的规则组默认关闭:由用户逐组审阅后手动开启(避免订阅静默接管点击)
        enabled = g.enable ?: false,
        desc = g.desc,
        activityIds = g.activityIds ?: emptyList(),
        excludeActivityIds = g.excludeActivityIds ?: emptyList(),
        rules = g.rules.map { ruleFromRaw(it) },
        actionCd = g.actionCd ?: 1000L,
        actionDelay = g.actionDelay ?: 0L,
        fastQuery = g.fastQuery ?: false,
        matchRoot = g.matchRoot ?: false,
        matchDelay = g.matchDelay ?: 0L,
        matchTime = g.matchTime ?: 0L,
        actionMaximum = g.actionMaximum?.toLong() ?: 0L,
        resetMatch = parseResetMatch(g.resetMatch) ?: GkdTask.ResetMatch.Activity,
        actionCdKey = g.actionCdKey?.toLong() ?: 0L,
        actionMaximumKey = g.actionMaximumKey?.toLong() ?: 0L,
        priorityTime = g.priorityTime ?: 0L,
        priorityActionMaximum = g.priorityActionMaximum?.toLong() ?: 1L,
        order = g.order?.toLong() ?: 0L,
        forcedTime = g.forcedTime ?: 0L,
        versionCode = g.versionCode,
        versionName = g.versionName,
        ignoreGlobalGroupMatch = g.ignoreGlobalGroupMatch,
    )

    private fun globalGroupToTask(g: RawGlobalGroup): GkdTask = GkdTask(
        id = "gkd_global_${g.key}",
        name = g.name,
        key = g.key.toLong(),
        packageName = "", // 空 = 全局规则
        enabled = g.enable ?: false, // 默认关闭,同应用组
        desc = g.desc,
        rules = g.rules.map { ruleFromRaw(it) },
        actionCd = g.actionCd ?: 1000L,
        actionDelay = g.actionDelay ?: 0L,
        fastQuery = g.fastQuery ?: false,
        matchRoot = g.matchRoot ?: false,
        matchDelay = g.matchDelay ?: 0L,
        matchTime = g.matchTime ?: 0L,
        actionMaximum = g.actionMaximum?.toLong() ?: 0L,
        resetMatch = parseResetMatch(g.resetMatch) ?: GkdTask.ResetMatch.Activity,
        actionCdKey = g.actionCdKey?.toLong() ?: 0L,
        actionMaximumKey = g.actionMaximumKey?.toLong() ?: 0L,
        priorityTime = g.priorityTime ?: 0L,
        priorityActionMaximum = g.priorityActionMaximum?.toLong() ?: 1L,
        order = g.order?.toLong() ?: 0L,
        forcedTime = g.forcedTime ?: 0L,
        disableIfAppGroupMatch = g.disableIfAppGroupMatch ?: "",
        scopeKeys = g.scopeKeys,
        matchAnyApp = g.matchAnyApp,
        matchSystemApp = g.matchSystemApp,
        matchLauncher = g.matchLauncher,
        globalApps = g.apps,
    )

    private fun ruleFromRaw(r: RawRule): GkdTask.Rule = GkdTask.Rule(
        key = r.key?.toLong() ?: 0L,
        name = r.name ?: "",
        activityIds = r.activityIds ?: emptyList(),
        excludeActivityIds = r.excludeActivityIds ?: emptyList(),
        matches = (r.matches ?: emptyList()).map { GkdSelector(it) },
        anyMatches = (r.anyMatches ?: emptyList()).map { GkdSelector(it) },
        excludeMatches = (r.excludeMatches ?: emptyList()).map { GkdSelector(it) },
        excludeAllMatches = (r.excludeAllMatches ?: emptyList()).map { GkdSelector(it) },
        preKeys = r.preKeys?.map { it.toLong() } ?: emptyList(),
        action = r.action?.let { s -> Action.entries.firstOrNull { it.gkd == s } },
        actionRaw = r.action?.takeIf { s -> Action.entries.none { it.gkd == s } },
        position = r.position,
        swipeArg = r.swipeArg,
        actionCd = r.actionCd ?: 0L,
        actionDelay = r.actionDelay ?: 0L,
        fastQuery = r.fastQuery ?: false,
        matchRoot = r.matchRoot ?: false,
        matchDelay = r.matchDelay ?: 0L,
        matchTime = r.matchTime ?: 0L,
        actionMaximum = r.actionMaximum?.toLong() ?: 0L,
        resetMatch = r.resetMatch?.let { parseResetMatch(it) },
        actionCdKey = r.actionCdKey?.toLong() ?: 0L,
        actionMaximumKey = r.actionMaximumKey?.toLong() ?: 0L,
        priorityTime = r.priorityTime ?: 0L,
        priorityActionMaximum = r.priorityActionMaximum?.toLong() ?: 1L,
        order = r.order?.toLong() ?: 0L,
        forcedTime = r.forcedTime ?: 0L,
        versionCode = r.versionCode,
        versionName = r.versionName,
        matchAnyApp = r.matchAnyApp,
        matchSystemApp = r.matchSystemApp,
        matchLauncher = r.matchLauncher,
        globalApps = r.apps,
    )

    private fun parseResetMatch(s: String?): GkdTask.ResetMatch? = s?.let { v ->
        GkdTask.ResetMatch.entries.firstOrNull { it.gkd == v }
    }

    // ==================== 中间数据(RawSubscription 数据类镜像) ====================

    internal data class RawApp(val id: String, val name: String?, val groups: List<RawAppGroup>)
    internal data class RawAppGroup(
        val key: Int, val name: String, val desc: String?, val enable: Boolean?,
        val activityIds: List<String>?, val excludeActivityIds: List<String>?,
        val actionCd: Long?, val actionDelay: Long?, val fastQuery: Boolean?, val matchRoot: Boolean?,
        val actionMaximum: Int?, val matchDelay: Long?, val matchTime: Long?, val resetMatch: String?,
        val actionCdKey: Int?, val actionMaximumKey: Int?, val priorityTime: Long?,
        val priorityActionMaximum: Int?, val order: Int?, val forcedTime: Long?,
        val versionCode: IntegerMatcher?, val versionName: StringMatcher?,
        val ignoreGlobalGroupMatch: Boolean?, val rules: List<RawRule>,
    )

    internal data class RawRule(
        val key: Int?, val name: String?, val preKeys: List<Int>?, val action: String?,
        val position: GkdTask.Position?, val swipeArg: GkdTask.SwipeArg?,
        val matches: List<String>?, val anyMatches: List<String>?,
        val excludeMatches: List<String>?, val excludeAllMatches: List<String>?,
        val activityIds: List<String>?, val excludeActivityIds: List<String>?,
        val actionCd: Long?, val actionDelay: Long?, val fastQuery: Boolean?, val matchRoot: Boolean?,
        val actionMaximum: Int?, val matchDelay: Long?, val matchTime: Long?, val resetMatch: String?,
        val actionCdKey: Int?, val actionMaximumKey: Int?, val priorityTime: Long?,
        val priorityActionMaximum: Int?, val order: Int?, val forcedTime: Long?,
        val versionCode: IntegerMatcher?, val versionName: StringMatcher?,
        val matchAnyApp: Boolean?, val matchSystemApp: Boolean?, val matchLauncher: Boolean?,
        val apps: List<GlobalApp>?,
    )

    internal data class RawGlobalGroup(
        val key: Int, val name: String, val desc: String?, val enable: Boolean?,
        val actionCd: Long?, val actionDelay: Long?, val fastQuery: Boolean?, val matchRoot: Boolean?,
        val actionMaximum: Int?, val matchDelay: Long?, val matchTime: Long?, val resetMatch: String?,
        val actionCdKey: Int?, val actionMaximumKey: Int?, val priorityTime: Long?,
        val priorityActionMaximum: Int?, val order: Int?, val forcedTime: Long?,
        val matchAnyApp: Boolean?, val matchSystemApp: Boolean?, val matchLauncher: Boolean?,
        val apps: List<GlobalApp>?, val rules: List<RawRule>, val scopeKeys: List<Int>?,
        val disableIfAppGroupMatch: String?,
    )

    // ==================== JSON → Raw(照抄 companion 各 jsonToXxx) ====================

    private fun jsonToAppRaw(obj: JsonElement, appIndex: Int?): RawApp {
        val jsonObject = obj as? JsonObject ?: error("miss id")
        val id = getString(jsonObject, "id")
            ?: error(if (appIndex != null) "miss subscription.apps[$appIndex].id" else "miss id")
        val groups = when (val groupsJson = jsonObject["groups"]) {
            null, is JsonNull -> emptyList()
            is JsonPrimitive, is JsonObject -> listOf(groupsJson)
            is JsonArray -> groupsJson
        }.map { jsonToGroupRaw(it) }.distinctByIfAny { it.key }
        return RawApp(id, getString(jsonObject, "name"), groups)
    }

    private fun jsonToGroupRaw(groupRawJson: JsonElement): RawAppGroup {
        val jsonObject = when (groupRawJson) {
            is JsonNull -> error("group must not be null")
            is JsonObject -> groupRawJson
            is JsonPrimitive, is JsonArray -> JsonObject(mapOf("rules" to groupRawJson))
        }
        val rules = when (val rulesJson = jsonObject["rules"]) {
            null, is JsonNull -> emptyList()
            is JsonPrimitive, is JsonObject -> listOf(rulesJson)
            is JsonArray -> rulesJson
        }.map { jsonToRuleRaw(it) }.distinctNotNullBy { it.key }
        return RawAppGroup(
            key = getInt(jsonObject, "key") ?: error("miss group key"),
            name = getString(jsonObject, "name") ?: error("miss group name"),
            desc = getString(jsonObject, "desc"),
            enable = getBoolean(jsonObject, "enable"),
            activityIds = getStringIArray(jsonObject, "activityIds"),
            excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
            actionCd = getLong(jsonObject, "actionCd") ?: getLong(jsonObject, "cd"),
            actionDelay = getLong(jsonObject, "actionDelay") ?: getLong(jsonObject, "delay"),
            fastQuery = getBoolean(jsonObject, "fastQuery"),
            matchRoot = getBoolean(jsonObject, "matchRoot"),
            actionMaximum = getInt(jsonObject, "actionMaximum"),
            matchDelay = getLong(jsonObject, "matchDelay"),
            matchTime = getLong(jsonObject, "matchTime"),
            resetMatch = getString(jsonObject, "resetMatch"),
            actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
            actionCdKey = getInt(jsonObject, "actionCdKey"),
            order = getInt(jsonObject, "order"),
            forcedTime = getLong(jsonObject, "forcedTime"),
            versionCode = getCompatVersionCode(jsonObject),
            versionName = getCompatVersionName(jsonObject),
            priorityTime = getLong(jsonObject, "priorityTime"),
            priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
            ignoreGlobalGroupMatch = getBoolean(jsonObject, "ignoreGlobalGroupMatch"),
            rules = rules,
        )
    }

    private fun jsonToRuleRaw(rulesRawJson: JsonElement): RawRule {
        val jsonObject = when (rulesRawJson) {
            is JsonNull -> error("miss current rule")
            is JsonObject -> rulesRawJson
            is JsonPrimitive, is JsonArray -> JsonObject(mapOf("matches" to rulesRawJson))
        }
        return RawRule(
            key = getInt(jsonObject, "key"),
            name = getString(jsonObject, "name"),
            preKeys = getIntIArray(jsonObject, "preKeys"),
            action = getString(jsonObject, "action"),
            position = getPosition(jsonObject),
            swipeArg = getSwipeArg(jsonObject),
            matches = getStringIArray(jsonObject, "matches"),
            anyMatches = getStringIArray(jsonObject, "anyMatches"),
            excludeMatches = getStringIArray(jsonObject, "excludeMatches"),
            excludeAllMatches = getStringIArray(jsonObject, "excludeAllMatches"),
            activityIds = getStringIArray(jsonObject, "activityIds"),
            excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
            actionCd = getLong(jsonObject, "actionCd") ?: getLong(jsonObject, "cd"),
            actionDelay = getLong(jsonObject, "actionDelay") ?: getLong(jsonObject, "delay"),
            fastQuery = getBoolean(jsonObject, "fastQuery"),
            matchRoot = getBoolean(jsonObject, "matchRoot"),
            actionMaximum = getInt(jsonObject, "actionMaximum"),
            matchDelay = getLong(jsonObject, "matchDelay"),
            matchTime = getLong(jsonObject, "matchTime"),
            resetMatch = getString(jsonObject, "resetMatch"),
            actionCdKey = getInt(jsonObject, "actionCdKey"),
            actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
            order = getInt(jsonObject, "order"),
            forcedTime = getLong(jsonObject, "forcedTime"),
            priorityTime = getLong(jsonObject, "priorityTime"),
            priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
            versionCode = getCompatVersionCode(jsonObject),
            versionName = getCompatVersionName(jsonObject),
            matchAnyApp = getBoolean(jsonObject, "matchAnyApp"),
            matchSystemApp = getBoolean(jsonObject, "matchSystemApp"),
            matchLauncher = getBoolean(jsonObject, "matchLauncher"),
            apps = jsonObject["apps"]?.let { el ->
                (el as? JsonArray)?.mapIndexed { index, e ->
                    jsonToGlobalApp(e.jsonObject, index)
                }
            }?.distinctByIfAny { it.id },
        )
    }

    private fun jsonToGlobalGroup(groupJson: JsonElement, groupIndex: Int): RawGlobalGroup {
        val jsonObject = groupJson as? JsonObject ?: error("miss group[$groupIndex]")
        return RawGlobalGroup(
            key = getInt(jsonObject, "key") ?: error("miss group[$groupIndex].key"),
            name = getString(jsonObject, "name") ?: error("miss group[$groupIndex].name"),
            desc = getString(jsonObject, "desc"),
            enable = getBoolean(jsonObject, "enable"),
            actionCd = getLong(jsonObject, "actionCd"),
            actionDelay = getLong(jsonObject, "actionDelay"),
            fastQuery = getBoolean(jsonObject, "fastQuery"),
            matchRoot = getBoolean(jsonObject, "matchRoot"),
            actionMaximum = getInt(jsonObject, "actionMaximum"),
            matchDelay = getLong(jsonObject, "matchDelay"),
            matchTime = getLong(jsonObject, "matchTime"),
            resetMatch = getString(jsonObject, "resetMatch"),
            actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
            actionCdKey = getInt(jsonObject, "actionCdKey"),
            matchSystemApp = getBoolean(jsonObject, "matchSystemApp"),
            matchAnyApp = getBoolean(jsonObject, "matchAnyApp"),
            matchLauncher = getBoolean(jsonObject, "matchLauncher"),
            apps = jsonObject["apps"]?.let { el ->
                (el as? JsonArray)?.mapIndexed { index, e ->
                    jsonToGlobalApp(e.jsonObject, index)
                }
            }?.distinctByIfAny { it.id },
            rules = (jsonObject["rules"] as? JsonArray)?.map { jsonToRuleRaw(it) }
                ?: emptyList(),
            order = getInt(jsonObject, "order"),
            scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
            forcedTime = getLong(jsonObject, "forcedTime"),
            priorityTime = getLong(jsonObject, "priorityTime"),
            priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
            disableIfAppGroupMatch = getString(jsonObject, "disableIfAppGroupMatch"),
        )
    }

    private fun jsonToGlobalApp(jsonObject: JsonObject, index: Int): GlobalApp = GlobalApp(
        id = getString(jsonObject, "id") ?: error("miss apps[$index].id"),
        enable = getBoolean(jsonObject, "enable"),
        activityIds = getStringIArray(jsonObject, "activityIds") ?: emptyList(),
        excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds") ?: emptyList(),
        versionCode = getCompatVersionCode(jsonObject),
        versionName = getCompatVersionName(jsonObject),
    )

    // ==================== 基础取值(照抄 getString/getLong/getInt/getBoolean/…IArray) ====================

    private fun getString(jsonObject: JsonObject?, key: String): String? =
        when (val p = jsonObject?.get(key)) {
            is JsonPrimitive -> if (p.isString) p.content else null
            else -> null
        }

    private fun getLong(jsonObject: JsonObject?, key: String): Long? =
        when (val p = jsonObject?.get(key)) {
            is JsonPrimitive -> p.content.toLongOrNull() // GKD: p.long;宽松避免脏数据整组失败
            else -> null
        }

    private fun getInt(jsonObject: JsonObject?, key: String): Int? =
        when (val p = jsonObject?.get(key)) {
            is JsonPrimitive -> p.content.toIntOrNull()
            else -> null
        }

    private fun getBoolean(jsonObject: JsonObject?, key: String): Boolean? =
        when (val p = jsonObject?.get(key)) {
            is JsonPrimitive -> p.content.toBooleanStrictOrNull()
            else -> null
        }

    private fun getStringIArray(jsonObject: JsonObject?, key: String): List<String>? =
        when (val element = jsonObject?.get(key)) {
            null, is JsonNull -> null
            is JsonArray -> element.map {
                (it as? JsonPrimitive)?.content ?: error("Element $it is not a string")
            }
            is JsonPrimitive -> listOf(element.content)
            is JsonObject -> error("Element $element can not be object")
        }

    private fun getIntIArray(jsonObject: JsonObject?, key: String): List<Int>? =
        when (val element = jsonObject?.get(key)) {
            null, is JsonNull -> null
            is JsonArray -> element.map {
                (it as? JsonPrimitive)?.content?.toIntOrNull() ?: error("Element $it is not a int")
            }
            is JsonPrimitive -> listOf(element.content.toIntOrNull() ?: error("Element $element is not a int"))
            else -> error("Element $element is not a Array")
        }

    // ==================== matcher / position / swipeArg(照抄 companion) ====================

    private fun getCompatVersionCode(jsonObject: JsonObject): IntegerMatcher? {
        getIntMatcher(jsonObject, "versionCode")?.let { return it }
        val a = getIntIArray(jsonObject, "versionCodes")
        val b = getIntIArray(jsonObject, "excludeVersionCodes")
        if (a != null || b != null) return IntegerMatcher(minimum = null, maximum = null, include = a, exclude = b)
        return null
    }

    private fun getCompatVersionName(jsonObject: JsonObject): StringMatcher? {
        getStringMatcher(jsonObject, "versionName")?.let { return it }
        val a = getStringIArray(jsonObject, "versionNames")
        val b = getStringIArray(jsonObject, "excludeVersionNames")
        if (a != null || b != null) return StringMatcher(pattern = null, include = a, exclude = b)
        return null
    }

    private fun getIntMatcher(jsonObject: JsonObject?, key: String): IntegerMatcher? =
        when (val element = jsonObject?.get(key)) {
            is JsonObject -> IntegerMatcher(
                minimum = getInt(element, "minimum"),
                maximum = getInt(element, "maximum"),
                include = getIntIArray(element, "include"),
                exclude = getIntIArray(element, "exclude"),
            )
            else -> null
        }

    private fun getStringMatcher(jsonObject: JsonObject?, key: String): StringMatcher? =
        when (val element = jsonObject?.get(key)) {
            is JsonObject -> StringMatcher(
                pattern = getString(element, "pattern"),
                include = getStringIArray(element, "include"),
                exclude = getStringIArray(element, "exclude"),
            )
            else -> null
        }

    private fun getPosition(jsonObject: JsonObject?, useSelf: Boolean = false): GkdTask.Position? =
        when (val element = if (useSelf) jsonObject else jsonObject?.get("position")) {
            is JsonObject -> GkdTask.Position(
                left = (element["left"] as? JsonPrimitive)?.content,
                top = (element["top"] as? JsonPrimitive)?.content,
                right = (element["right"] as? JsonPrimitive)?.content,
                bottom = (element["bottom"] as? JsonPrimitive)?.content,
                x = (element["x"] as? JsonPrimitive)?.content,
                y = (element["y"] as? JsonPrimitive)?.content,
            )
            else -> null
        }

    private fun getSwipeArg(jsonObject: JsonObject?): GkdTask.SwipeArg? =
        when (val element = jsonObject?.get("swipeArg")) {
            is JsonObject -> GkdTask.SwipeArg(
                start = getPosition(element["start"]?.jsonObject, useSelf = true)
                    ?: error("swipe start position is required"),
                end = getPosition(element["end"]?.jsonObject, useSelf = true),
                duration = getLong(element, "duration") ?: error("swipe duration is required"),
            )
            else -> null
        }

    // ==================== 列表工具(照抄 distinctByIfAny/filterIfNotAll/distinctNotNullBy) ====================

    private fun <T, K> List<T>.distinctNotNullBy(selector: (T) -> K?): List<T> {
        val set = HashSet<K>()
        val list = ArrayList<T>()
        forEach { e ->
            val key = selector(e)
            if (key == null || set.add(key)) list.add(e)
        }
        return list
    }

    private fun <T, K> List<T>.distinctByIfAny(selector: (T) -> K?): List<T> =
        if (any { selector(it) != null }) {
            val seen = HashSet<K>()
            filter { val k = selector(it); k == null || seen.add(k) }
        } else this

    private fun <T> List<T>.filterIfNotAll(predicate: (T) -> Boolean): List<T> =
        if (all { predicate(it) } || none { predicate(it) }) this else filter(predicate)

    // ==================== 订阅 app 清单(订阅弹窗展示用) ====================

    /** 订阅内 app 清单(id to name);原文非法返回空表 */
    fun appsOf(raw: String): List<Pair<String, String>> = runCatching {
        val root = json.parseToJsonElement(stripJson5(raw)).jsonObject
        val apps = root["apps"] as? JsonArray ?: return@runCatching emptyList()
        apps.mapNotNull { el ->
            val a = el as? JsonObject ?: return@mapNotNull null
            val id = (a["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val name = (a["name"] as? JsonPrimitive)?.content ?: id
            id to name
        }
    }.getOrDefault(emptyList())

    // ==================== 导出(本地规则 → GKD 订阅 JSON) ====================

    /** 导出为 GKD 订阅 JSON(单应用页使用,只含该应用的规则);字段完整回写 */
    fun toJson(tasks: List<GkdTask>, appId: String): String {
        val root = buildJsonObject {
            put("id", "local-$appId-${System.currentTimeMillis()}")
            put("name", "本地规则导出 $appId")
            put("version", 1)
            put("date", System.currentTimeMillis())
            put("categories", buildJsonArray { })
            put("globalGroups", buildJsonArray { })
            put("apps", buildJsonArray {
                tasks.groupBy { it.packageName.ifEmpty { appId } }.forEach { (pkg, group) ->
                    add(buildJsonObject {
                        put("id", pkg)
                        put("name", pkg)
                        put("groups", buildJsonArray {
                            group.forEach { task -> add(exportGroup(task)) }
                        })
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun exportPosition(p: GkdTask.Position): JsonObject = buildJsonObject {
        p.left?.let { put("left", it) }
        p.top?.let { put("top", it) }
        p.right?.let { put("right", it) }
        p.bottom?.let { put("bottom", it) }
        p.x?.let { put("x", it) }
        p.y?.let { put("y", it) }
    }

    private fun exportGroup(task: GkdTask): JsonObject = buildJsonObject {
        put("key", task.key.toInt().takeIf { it != 0 } ?: (task.id.hashCode().toLong() and 0x7FFFFFFFL).toInt() % 100000)
        put("name", task.name)
        put("enable", task.enabled)
        task.desc?.let { put("desc", it) }
        if (task.activityIds.isNotEmpty()) {
            put("activityIds", buildJsonArray { task.activityIds.forEach { add(JsonPrimitive(it)) } })
        }
        if (task.excludeActivityIds.isNotEmpty()) {
            put("excludeActivityIds", buildJsonArray { task.excludeActivityIds.forEach { add(JsonPrimitive(it)) } })
        }
        if (task.actionCd != 1000L) put("actionCd", task.actionCd)
        if (task.actionDelay != 0L) put("actionDelay", task.actionDelay)
        if (task.fastQuery) put("fastQuery", true)
        if (task.matchRoot) put("matchRoot", true)
        if (task.matchDelay != 0L) put("matchDelay", task.matchDelay)
        if (task.matchTime != 0L) put("matchTime", task.matchTime)
        if (task.actionMaximum != 0L) put("actionMaximum", task.actionMaximum)
        if (task.resetMatch != GkdTask.ResetMatch.Activity) put("resetMatch", task.resetMatch.gkd)
        if (task.actionCdKey != 0L) put("actionCdKey", task.actionCdKey)
        if (task.actionMaximumKey != 0L) put("actionMaximumKey", task.actionMaximumKey)
        if (task.priorityTime != 0L) put("priorityTime", task.priorityTime)
        if (task.priorityActionMaximum != 1L) put("priorityActionMaximum", task.priorityActionMaximum)
        if (task.order != 0L) put("order", task.order)
        if (task.forcedTime != 0L) put("forcedTime", task.forcedTime)
        put("rules", buildJsonArray {
            task.rules.forEach { r -> add(exportRule(r)) }
        })
    }

    private fun exportRule(r: GkdTask.Rule): JsonObject = buildJsonObject {
        if (r.key != 0L) put("key", r.key)
        if (r.name.isNotEmpty()) put("name", r.name)
        if (r.activityIds.isNotEmpty()) {
            put("activityIds", buildJsonArray { r.activityIds.forEach { add(JsonPrimitive(it)) } })
        }
        if (r.matches.isNotEmpty()) put("matches", buildJsonArray { r.matches.forEach { add(JsonPrimitive(it.expr)) } })
        if (r.anyMatches.isNotEmpty()) put("anyMatches", buildJsonArray { r.anyMatches.forEach { add(JsonPrimitive(it.expr)) } })
        if (r.excludeMatches.isNotEmpty()) put("excludeMatches", buildJsonArray { r.excludeMatches.forEach { add(JsonPrimitive(it.expr)) } })
        if (r.excludeAllMatches.isNotEmpty()) put("excludeAllMatches", buildJsonArray { r.excludeAllMatches.forEach { add(JsonPrimitive(it.expr)) } })
        if (r.preKeys.isNotEmpty()) put("preKeys", buildJsonArray { r.preKeys.forEach { add(JsonPrimitive(it)) } })
        r.action?.let { put("action", it.gkd) }
        r.position?.let { put("position", exportPosition(it)) }
        r.swipeArg?.let { s ->
            put("swipeArg", buildJsonObject {
                put("start", exportPosition(s.start))
                s.end?.let { put("end", exportPosition(it)) }
                put("duration", s.duration)
            })
        }
        if (r.actionCd != 0L) put("actionCd", r.actionCd)
        if (r.actionDelay != 0L) put("actionDelay", r.actionDelay)
        if (r.fastQuery) put("fastQuery", true)
        if (r.matchRoot) put("matchRoot", true)
        if (r.matchDelay != 0L) put("matchDelay", r.matchDelay)
        if (r.matchTime != 0L) put("matchTime", r.matchTime)
        if (r.actionMaximum != 0L) put("actionMaximum", r.actionMaximum)
        r.resetMatch?.let { put("resetMatch", it.gkd) }
        if (r.actionCdKey != 0L) put("actionCdKey", r.actionCdKey)
        if (r.actionMaximumKey != 0L) put("actionMaximumKey", r.actionMaximumKey)
        if (r.priorityTime != 0L) put("priorityTime", r.priorityTime)
        if (r.priorityActionMaximum != 1L) put("priorityActionMaximum", r.priorityActionMaximum)
        if (r.order != 0L) put("order", r.order)
        if (r.forcedTime != 0L) put("forcedTime", r.forcedTime)
    }

    // ==================== JSON5 → JSON 清洗(字符串感知单遍扫描) ====================

    /** 去注释/尾逗号/裸键引号(字符串感知单遍扫描,不碰字符串内容) */
    private fun stripJson5(src: String): String {
        val out = StringBuilder(src.length + 64)
        var i = 0
        var lastSig = ' ' // 上一个有效字符(空白/注释不更新;键判定用)

        /** 把 src[i] 处的 JSON5 字符串('…'/"…"/`…`)写成 JSON 双引号字符串 */
        fun writeString() {
            val quote = src[i]; i++
            out.append('"')
            while (i < src.length && src[i] != quote) {
                val c = src[i]
                when {
                    c == '\\' && i + 1 < src.length -> when (val e = src[i + 1]) {
                        '\'' -> { out.append('\''); i += 2 } // \' 在 " 串内无需转义
                        'x' -> { // \xHH → \u00HH
                            if (i + 4 <= src.length) {
                                out.append("\\u00").append(src.substring(i + 2, i + 4)); i += 4
                            } else i = src.length
                        }
                        '\n', '\r' -> i += 2 // 续行符:丢弃
                        else -> { out.append(c).append(e); i += 2 }
                    }
                    c == '"' -> { out.append("\\\""); i++ } // 内嵌双引号转义(选择器常见)
                    c == '\n' -> { out.append("\\n"); i++ }
                    c == '\r' -> { out.append("\\r"); i++ }
                    c == '\t' -> { out.append("\\t"); i++ }
                    else -> { out.append(c); i++ }
                }
            }
            i++ // 闭合引号
            out.append('"')
            lastSig = '"'
        }

        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> { out.append(c); i++ }
                c == '/' && i + 1 < src.length && src[i + 1] == '/' ->
                    while (i < src.length && src[i] != '\n') i++
                c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < src.length && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(src.length)
                }
                c == '\'' || c == '"' || c == '`' -> writeString()
                c == ',' -> {
                    // 尾逗号:向后跳过空白/注释,遇 } 或 ] 则丢弃
                    var j = i + 1
                    while (j < src.length) {
                        val d = src[j]
                        if (d.isWhitespace()) j++
                        else if (d == '/' && j + 1 < src.length && src[j + 1] == '/') {
                            while (j < src.length && src[j] != '\n') j++
                        } else if (d == '/' && j + 1 < src.length && src[j + 1] == '*') {
                            j += 2
                            while (j + 1 < src.length && !(src[j] == '*' && src[j + 1] == '/')) j++
                            j += 2
                        } else break
                    }
                    if (j < src.length && (src[j] == '}' || src[j] == ']')) i++
                    else { out.append(','); i++; lastSig = ',' }
                }
                c == '_' || c.isLetter() -> {
                    var j = i
                    while (j < src.length && (src[j].isLetterOrDigit() || src[j] == '_' || src[j] == '$')) j++
                    val word = src.substring(i, j)
                    var k = j
                    while (k < src.length && src[k].isWhitespace()) k++
                    val isKey = (lastSig == '{' || lastSig == ',') && k < src.length && src[k] == ':'
                    when {
                        word == "true" || word == "false" || word == "null" -> out.append(word)
                        // { , 之后的标识+冒号 = 裸键;其余裸标识(非法值)也兜底转字符串
                        else -> out.append('"').append(word).append('"')
                    }
                    i = j
                    lastSig = if (isKey) '"' else 'x'
                }
                else -> { out.append(c); i++; lastSig = c }
            }
        }
        return out.toString()
    }
}
