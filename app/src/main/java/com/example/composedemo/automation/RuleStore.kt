package com.example.composedemo.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 规则持久化 v3:SharedPreferences + JSON。
 *
 *  - v3 格式:conditions/steps/loop 全量序列化(见 [ruleFromJson])
 *  - 旧格式兼容:读到 targetText(单文本点击)自动迁移为
 *    单步骤 [TEXT = 文本 → 点击] 规则,enabled/once/重试参数原样保留
 *  - 规则由「应用 tab → 任务页」编辑器创建/修改,保存即写入;
 *    服务连接时 [loadAll] 进 [RuleEngine] 立即生效
 */
object RuleStore {

    private const val PREFS = "automation_rules"
    private const val KEY_RULES = "rules"

    /** 读取全部规则(按保存顺序,含旧格式迁移) */
    fun loadAll(context: Context): List<AutomationRule> {
        val json = prefs(context).getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ruleFromJson(o)
            }
        }.getOrDefault(emptyList())
    }

    /** 全量替换保存(任务页删除/新建/编辑后整体写回) */
    fun saveAll(context: Context, rules: List<AutomationRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(toJson(it)) }
        prefs(context).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 查询已安装的用户应用(排除系统应用),按应用名排序。
     * 用于应用列表与新建任务时选择目标包名。
     */
    fun loadInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }  // 有入口的才可启动
            .filter { !it.packageName.startsWith("com.android.") && !it.packageName.startsWith("android") }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** 应用条目:包名 + 显示名 */
    data class AppInfo(val packageName: String, val label: String)

    // ---- JSON 序列化(v3)----

    private fun toJson(r: AutomationRule) = JSONObject().apply {
        put("id", r.id)
        put("packageName", r.packageName)
        put("activityPattern", r.activityPattern ?: "")
        put("enabled", r.enabled)
        put("once", r.once)
        put("loopIntervalMs", r.loopIntervalMs)
        put("loopMaxRounds", r.loopMaxRounds)
        put("maxRetries", r.maxRetries)
        put("retryIntervalMs", r.retryIntervalMs)
        val steps = JSONArray()
        r.steps.forEach { s ->
            steps.put(JSONObject().apply {
                val conds = JSONArray()
                s.conditions.forEach { c ->
                    conds.put(JSONObject().apply {
                        put("field", c.field.name)
                        put("mode", c.mode.name)
                        put("value", c.value)
                    })
                }
                put("conditions", conds)
                put("action", s.action::class.simpleName ?: "Click")
                put("timeoutMs", s.timeoutMs)
                put("delayMs", s.delayMs)
                put("swipeUp", s.swipeUp)
            })
        }
        put("steps", steps)
    }

    private fun ruleFromJson(o: JSONObject) = runCatching {
        val pkg = o.getString("packageName")
        if (pkg.isBlank()) return@runCatching null

        // v3:steps 数组
        val stepsJson = o.optJSONArray("steps")
        val steps = if (stepsJson != null && stepsJson.length() > 0) {
            (0 until stepsJson.length()).mapNotNull { i ->
                stepFromJson(stepsJson.optJSONObject(i) ?: return@mapNotNull null)
            }
        } else {
            // 旧格式迁移:targetText → 单步骤 [TEXT=文本 → 点击]
            val legacyText = o.optString("targetText")
            if (legacyText.isBlank()) return@runCatching null
            listOf(
                RuleStep(
                    conditions = listOf(
                        NodeCondition(NodeCondition.Field.TEXT, NodeCondition.MatchMode.EQUALS, legacyText)
                    ),
                    action = RuleStep.Action.Click,
                )
            )
        }

        AutomationRule(
            id = o.getString("id"),
            packageName = pkg,
            activityPattern = o.optString("activityPattern").takeIf { it.isNotBlank() },
            enabled = o.optBoolean("enabled", true),
            once = o.optBoolean("once", true),
            steps = steps,
            loopIntervalMs = o.optLong("loopIntervalMs", 0L),
            loopMaxRounds = o.optInt("loopMaxRounds", 0),
            maxRetries = o.optInt("maxRetries", 5),
            retryIntervalMs = o.optLong("retryIntervalMs", 500L),
        )
    }.getOrNull()

    private fun stepFromJson(s: JSONObject): RuleStep? = runCatching {
        val condsJson = s.optJSONArray("conditions")
        val conds = (0 until (condsJson?.length() ?: 0)).mapNotNull { i ->
            val c = condsJson!!.optJSONObject(i) ?: return@mapNotNull null
            NodeCondition(
                field = NodeCondition.Field.valueOf(c.optString("field", "TEXT")),
                mode = NodeCondition.MatchMode.valueOf(c.optString("mode", "EQUALS")),
                value = c.optString("value"),
            )
        }
        RuleStep(
            conditions = conds,
            action = when (s.optString("action", "Click")) {
                "WaitForNode" -> RuleStep.Action.WaitForNode
                "Delay" -> RuleStep.Action.Delay
                "PressBack" -> RuleStep.Action.PressBack
                "Swipe" -> RuleStep.Action.Swipe
                else -> RuleStep.Action.Click
            },
            timeoutMs = s.optLong("timeoutMs", 5000L),
            delayMs = s.optLong("delayMs", 1000L),
            swipeUp = s.optBoolean("swipeUp", true),
        )
    }.getOrNull()
}
