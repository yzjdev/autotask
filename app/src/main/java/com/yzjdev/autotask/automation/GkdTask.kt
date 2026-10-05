package com.yzjdev.autotask.automation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 自动化任务 v7 模型 —— 逐字段照抄 gkd-li/gkd 仓库 RawSubscription(gkd-app/data/RawSubscription.kt):
 *
 * 一条 [GkdTask] = 一个规则组:
 *  - 应用组(RawAppGroup):packageName 非空,含 activityIds/excludeActivityIds/versionCode/versionName
 *  - 全局组(RawGlobalGroup):packageName 为空,含 matchAnyApp/matchSystemApp/matchLauncher/
 *    globalApps/scopeKeys/disableIfAppGroupMatch
 *  - [Rule] = RawAppRule / RawGlobalRule(matches/preKeys/action/position/swipeArg + 公共属性覆盖)
 *  - [Position] = 六字段表达式(left/top/right/bottom/x/y),运行时按节点边界求值(exp4j 语义)
 *  - [SwipeArg] = start/end(Position) + duration;[StringMatcher]/[IntegerMatcher] 照抄
 */
@Serializable
data class GkdTask(
    val id: String,
    /** 组名(RawGroupProps.name) */
    val name: String,
    /** 组 key(RawGroupProps.key;订阅缺省时本地补序号) */
    val key: Long = 0L,
    /** 目标应用(RawApp.id);空 = 全局规则 */
    val packageName: String = "",
    /** 组级 enable */
    val enabled: Boolean = true,
    val desc: String? = null,
    /** 组级 activityIds / excludeActivityIds(RawAppGroupProps) */
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    /** 组内规则;规则可为空列表(GKD 保留空组,如壳 App 提示组) */
    val rules: List<Rule> = emptyList(),
    /** 本地扩展:多步骤编排。非空时组内规则不独立调度,由场景流按序驱动;不导出订阅 */
    val steps: List<Step>? = null,

    // ---- RawCommonProps(组级默认,规则可覆盖;0/null = 用默认) ----
    val actionCd: Long = 1000L,
    val actionDelay: Long = 0L,
    val fastQuery: Boolean = false,
    val matchRoot: Boolean = false,
    val matchDelay: Long = 0L,
    val matchTime: Long = 0L,
    val actionMaximum: Long = 0L,
    val resetMatch: ResetMatch = ResetMatch.Activity,
    val actionCdKey: Long = 0L,
    val actionMaximumKey: Long = 0L,
    val priorityTime: Long = 0L,
    val priorityActionMaximum: Long = 1L,
    val order: Long = 0L,
    val forcedTime: Long = 0L,
    /** 全局组:目标应用存在名称以此开头的应用组时禁用 */
    val disableIfAppGroupMatch: String = "",
    // ---- 应用组专用 ----
    val versionCode: IntegerMatcher? = null,
    val versionName: StringMatcher? = null,
    val ignoreGlobalGroupMatch: Boolean? = null,
    // ---- 全局组专用(RawGlobalGroupProps) ----
    val scopeKeys: List<Int>? = null,
    val matchAnyApp: Boolean? = null,
    val matchSystemApp: Boolean? = null,
    val matchLauncher: Boolean? = null,
    val globalApps: List<GlobalApp>? = null,
) {
    /** 休眠重置策略(RawCommonProps.resetMatch) */
    @Serializable
    enum class ResetMatch(val gkd: String) {
        @SerialName("activity") Activity("activity"),
        @SerialName("match") Match("match"),
        @SerialName("app") App("app");
    }

    /**
     * 多步骤编排的单个步骤(本地扩展,不导出 GKD 订阅):
     * 按列表顺序执行,每步等待自己的目标出现后执行动作。
     */
    @Serializable
    data class Step(
        val name: String = "",
        /** 该步目标选择器(命中后执行动作;空 = 无条件步,仅延时) */
        val matches: List<GkdSelector> = emptyList(),
        /** 任一命中即可 */
        val anyMatches: List<GkdSelector> = emptyList(),
        /** 存在一个命中则不执行此步 */
        val excludeMatches: List<GkdSelector> = emptyList(),
        /** 动作;null = click */
        val action: Action? = null,
        val actionRaw: String? = null,
        val position: Position? = null,
        val swipeArg: SwipeArg? = null,
        /** 等待目标出现的超时;0 = 不等待(当帧没有则按 onTimeout 处理) */
        val waitTimeout: Long = 0L,
        /** 等待轮询间隔,默认 250ms */
        val waitInterval: Long = 250L,
        /** 目标出现后额外稳定时间,防动画未结束时点空 */
        val settleTime: Long = 0L,
        /** 超时未出现:continue = 跳过此步继续 / abort = 终止整个流 */
        val onTimeout: TimeoutPolicy = TimeoutPolicy.Abort,
    ) {
        @Serializable
        enum class TimeoutPolicy {
            @SerialName("continue") Continue,
            @SerialName("abort") Abort;
        }
    }

    /**
     * 组内规则(RawAppRule / RawGlobalRule)。
     * matches 可为空(GKD 允许无选择器的 preKeys 链动作规则)。
     */
    @Serializable
    data class Rule(
        /** 规则 key;0 = 订阅未写(GKD null) */
        val key: Long = 0L,
        val name: String = "",
        val activityIds: List<String> = emptyList(),
        val excludeActivityIds: List<String> = emptyList(),
        /** 触发选择器(全部命中;点击最后一条的目标节点) */
        val matches: List<GkdSelector> = emptyList(),
        /** 任一命中即可 */
        val anyMatches: List<GkdSelector> = emptyList(),
        /** 存在一个命中则停止匹配 */
        val excludeMatches: List<GkdSelector> = emptyList(),
        /** 全部命中则停止匹配 */
        val excludeAllMatches: List<GkdSelector> = emptyList(),
        /** 前置 key 链 */
        val preKeys: List<Long> = emptyList(),
        /** 动作;null = GKD 默认语义(click) */
        val action: Action? = null,
        /** 订阅写了引擎不认识的 action 字符串时原样保留(执行时报不支持) */
        val actionRaw: String? = null,
        /** 自定义位置(RawRuleProps.position,六字段表达式) */
        val position: Position? = null,
        /** 滑动参数(RawRuleProps.swipeArg) */
        val swipeArg: SwipeArg? = null,
        /** 本地扩展:1 = 上滑 2 = 下滑(编辑器/旧 scrollForward/scrollBackward),不导出 */
        val swipeDir: Int? = null,
        val actionCd: Long = 0L,
        val actionDelay: Long = 0L,
        val fastQuery: Boolean = false,
        val matchRoot: Boolean = false,
        val matchDelay: Long = 0L,
        val matchTime: Long = 0L,
        val actionMaximum: Long = 0L,
        val resetMatch: ResetMatch? = null,
        val actionCdKey: Long = 0L,
        val actionMaximumKey: Long = 0L,
        val priorityTime: Long = 0L,
        val priorityActionMaximum: Long = 1L,
        val order: Long = 0L,
        val forcedTime: Long = 0L,
        val versionCode: IntegerMatcher? = null,
        val versionName: StringMatcher? = null,
        // ---- 全局规则专用(RawGlobalRuleProps) ----
        val matchAnyApp: Boolean? = null,
        val matchSystemApp: Boolean? = null,
        val matchLauncher: Boolean? = null,
        val globalApps: List<GlobalApp>? = null,
    )

    /**
     * 自定义位置(RawSubscription.Position):字段为表达式字符串,
     * 变量 left/top/right/bottom/width/height/random/screenWidth/screenHeight。
     */
    @Serializable
    data class Position(
        val left: String? = null,
        val top: String? = null,
        val right: String? = null,
        val bottom: String? = null,
        val x: String? = null,
        val y: String? = null,
    ) {
        val isValid: Boolean
            get() = listOf(left, right, x).any { it != null } && listOf(top, bottom, y).any { it != null }

        /** 求 (x, y)(照抄 Position.calc:left→边界+x0,right→边界-x0,x→绝对) */
        fun calc(rect: android.graphics.Rect, screenWidth: Int, screenHeight: Int): Pair<Float, Float>? {
            if (!isValid) return null
            val vars = mapOf(
                "left" to rect.left.toDouble(),
                "top" to rect.top.toDouble(),
                "right" to rect.right.toDouble(),
                "bottom" to rect.bottom.toDouble(),
                "width" to rect.width().toDouble(),
                "height" to rect.height().toDouble(),
                "random" to Math.random(),
                "screenWidth" to screenWidth.toDouble(),
                "screenHeight" to screenHeight.toDouble(),
            )
            val x0 = (left ?: right ?: x)?.let { ExprEval.eval(it, vars) } ?: return null
            val y0 = (top ?: bottom ?: y)?.let { ExprEval.eval(it, vars) } ?: return null
            val x = when {
                left != null -> rect.left + x0
                right != null -> rect.right - x0
                else -> x0
            }
            val y = when {
                top != null -> rect.top + y0
                bottom != null -> rect.bottom - y0
                else -> y0
            }
            return x.toFloat() to y.toFloat()
        }
    }

    /** 滑动参数(RawSubscription.SwipeArg):start 必填,end 缺省 = start */
    @Serializable
    data class SwipeArg(
        val start: Position,
        val end: Position? = null,
        val duration: Long = 300L,
    )

    // ---- 兼容便捷视图 ----

    val actionsSummary: String by lazy {
        rules.joinToString("→") { r ->
            r.action?.let { a ->
                val text = r.matches.firstOrNull()?.firstPropValue("text")
                when (a) {
                    Action.Click, Action.ClickNode, Action.ClickCenter -> if (text != null) "点「$text」" else "点击"
                    Action.LongClick, Action.LongClickNode, Action.LongClickCenter -> if (text != null) "长按「$text」" else "长按"
                    Action.Back -> "返回"
                    Action.Swipe -> "滑动"
                    Action.InputText -> "输入"
                    Action.LaunchApp -> "启动"
                    Action.Check -> "勾选"
                    Action.Uncheck -> "取消勾选"
                    Action.None -> "标记"
                }
            } ?: r.matches.firstOrNull()?.let { m ->
                m.firstPropValue("text")?.let { "点「$it」" } ?: m.summary().ifEmpty { "匹配" }
            } ?: r.name.ifEmpty { "规则${r.key}" }
        }.ifEmpty { name }
    }
}

/** 全局组的 apps 约束(RawGlobalApp) */
@Serializable
data class GlobalApp(
    val id: String,
    val enable: Boolean? = null,
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    val versionCode: IntegerMatcher? = null,
    val versionName: StringMatcher? = null,
)

/** 字符串匹配器(照抄 RawSubscription.StringMatcher) */
@Serializable
data class StringMatcher(
    val pattern: String? = null,
    val include: List<String>? = null,
    val exclude: List<String>? = null,
) {
    private val patternRegex by lazy { pattern?.let { p -> runCatching { Regex(p) }.getOrNull() } }

    fun match(value: String?): Boolean {
        if (value == null) return false
        if (exclude?.contains(value) == true) return false
        if (include?.contains(value) == false) return false
        if (patternRegex?.matches(value) == false) return false
        return true
    }
}

/** 整数匹配器(照抄 RawSubscription.IntegerMatcher) */
@Serializable
data class IntegerMatcher(
    val minimum: Int? = null,
    val maximum: Int? = null,
    val include: List<Int>? = null,
    val exclude: List<Int>? = null,
) {
    fun match(value: Int?): Boolean {
        if (value == null) return false
        if (exclude?.contains(value) == true) return false
        if (include?.contains(value) == false) return false
        if (minimum != null && value < minimum) return false
        if (maximum != null && value > maximum) return false
        return true
    }
}

/**
 * Position 表达式求值(exp4j 子集):+ - * / % () 、数字与变量。
 * 照抄 RawSubscription.getExpression 的变量集与四则语义。
 */
internal object ExprEval {
    fun eval(src: String, vars: Map<String, Double>): Double? = runCatching {
        Parser(src.trim(), vars).parse()
    }.getOrNull()

    private class Parser(private val s: String, private val vars: Map<String, Double>) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            skipWs()
            if (i < s.length) error("多余字符: ${s[i]}")
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skipWs()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else return v
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skipWs()
                if (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                    val op = s[i++]
                    val r = factor()
                    v = when (op) {
                        '*' -> v * r
                        '/' -> if (r != 0.0) v / r else error("除零")
                        else -> if (r != 0.0) v % r else error("除零")
                    }
                } else return v
            }
        }

        private fun factor(): Double {
            skipWs()
            if (i < s.length && s[i] == '-') { i++; return -factor() }
            if (i < s.length && s[i] == '+') { i++; return factor() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                skipWs()
                if (i >= s.length || s[i] != ')') error("缺少 )")
                i++
                return v
            }
            if (i < s.length && (s[i].isDigit() || s[i] == '.')) return number()
            if (i < s.length && (s[i].isLetter() || s[i] == '_')) {
                val st = i
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                val name = s.substring(st, i)
                return vars[name] ?: error("未知变量 $name")
            }
            error("非法字符")
        }

        private fun number(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDoubleOrNull() ?: error("非法数字")
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}

/**
 * 动作种类(对齐 GkdAction.kt 的 ActionPerformer 全集:
 * clickNode/clickCenter/click/longClickNode/longClickCenter/longClick/back/none/swipe;
 * InputText/LaunchApp/Check/Uncheck 为本引擎保留扩展)。
 */
@Serializable
enum class Action(val gkd: String) {
    @SerialName("click") Click("click"),
    @SerialName("clickNode") ClickNode("clickNode"),
    @SerialName("clickCenter") ClickCenter("clickCenter"),
    @SerialName("longClick") LongClick("longClick"),
    @SerialName("longClickNode") LongClickNode("longClickNode"),
    @SerialName("longClickCenter") LongClickCenter("longClickCenter"),
    @SerialName("back") Back("back"),
    @SerialName("swipe") Swipe("swipe"),
    @SerialName("inputText") InputText("inputText"),
    @SerialName("launchApp") LaunchApp("launchApp"),
    @SerialName("check") Check("check"),
    @SerialName("uncheck") Uncheck("uncheck"),
    @SerialName("none") None("none"),
}
