package com.example.composedemo.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** 步骤 id 默认值 */
private fun uuid(): String = UUID.randomUUID().toString()

/**
 * 自动化任务 v4 模型。
 *
 * 「找到谁」([NodeLocator]) 与「做什么」([Step]) 彻底分离;
 * 触发策略([Task.Trigger])与失败策略([Task.OnFailure])在任务级独立声明,
 * 不再混进步骤序列。
 */

/**
 * 节点定位器:单属性断言(字段 + 匹配方式 + 值)。
 */
@Serializable
data class NodeLocator(
    val field: Field,
    val mode: MatchMode,
    val value: String,
) {
    @Serializable
    enum class Field { VIEW_ID, TEXT, DESC, CLASS_NAME }

    @Serializable
    enum class MatchMode { EQUALS, CONTAINS }

    /** 对节点求值;取值失败(如无 viewId)视为不匹配 */
    fun matches(node: AccessibilityNodeInfo): Boolean {
        val actual: String = when (field) {
            Field.VIEW_ID -> node.viewIdResourceName?.substringAfter('/') ?: return false
            Field.TEXT -> node.text?.toString() ?: return false
            Field.DESC -> node.contentDescription?.toString() ?: return false
            Field.CLASS_NAME -> node.className?.toString() ?: return false
        }
        return when (mode) {
            MatchMode.EQUALS -> actual == value
            MatchMode.CONTAINS -> actual.contains(value, ignoreCase = true)
        }
    }

    /** 编辑器/日志摘要 */
    fun summary(): String =
        "${field.name.lowercase()} ${if (mode == MatchMode.EQUALS) "=" else "≈"} \"$value\""
}

/**
 * 节点查询表达式:多个断言用 & (且) / | (或) 连接,& 优先级高于 |。
 *
 * 断言语法:`字段 运算符 值`,字段: text / vid / desc / visible;
 * 运算符: =(等于) / !=(不等于)。例:
 *   text=跳过 & visible=true
 *   vid!=btn_ad | text contains 广告 → 写作 text!=广告 之类
 *
 * 由 [parse] 构造,[matches] 对节点求值。
 */
@Serializable
data class NodeQuery(
    /** 顶层 OR 组;组内 AND 连接 */
    val groups: List<List<Assertion>>,
) {
    @Serializable
    data class Assertion(
        val field: Field,
        val op: Op,
        val value: String,
    ) {
        @Serializable
        enum class Field { TEXT, VID, DESC, VISIBLE }

        @Serializable
        enum class Op { EQ, NEQ }

        fun matches(node: AccessibilityNodeInfo): Boolean {
            val actual: String = when (field) {
                Field.TEXT -> node.text?.toString() ?: ""
                Field.VID -> node.viewIdResourceName?.substringAfter('/') ?: ""
                Field.DESC -> node.contentDescription?.toString() ?: ""
                Field.VISIBLE -> if (node.isVisibleToUser) "true" else "false"
            }
            return when (op) {
                Op.EQ -> actual.equals(value, ignoreCase = field == Field.VISIBLE)
                Op.NEQ -> !actual.equals(value, ignoreCase = field == Field.VISIBLE)
            }
        }

        /** 编辑器/日志摘要 */
        fun summary(): String = "${field.key} ${if (op == Op.EQ) "=" else "!="} \"$value\""
    }

    /** 任一 AND 组全部成立即命中 */
    fun matches(node: AccessibilityNodeInfo): Boolean =
        groups.any { group -> group.isNotEmpty() && group.all { it.matches(node) } }

    /** 编辑器/日志摘要 */
    fun summary(): String =
        groups.joinToString(" | ") { group -> group.joinToString(" & ") { it.summary() } }

    companion object {
        /** 字段中文别名 → 枚举 */
        private val FIELD_ALIASES = mapOf(
            "text" to Assertion.Field.TEXT, "文本" to Assertion.Field.TEXT,
            "vid" to Assertion.Field.VID, "id" to Assertion.Field.VID,
            "desc" to Assertion.Field.DESC, "描述" to Assertion.Field.DESC,
            "visible" to Assertion.Field.VISIBLE, "可见" to Assertion.Field.VISIBLE,
        )

        /**
         * 解析表达式文本;空文本返回 null(无条件)。
         * 语法错误抛 [IllegalArgumentException],由调用方提示。
         */
        fun parse(expr: String): NodeQuery? {
            val src = expr.trim()
            if (src.isEmpty()) return null
            val groups = src.split('|').map { orPart ->
                orPart.split('&').map { andPart ->
                    val m = Regex("""^\s*(\S+)\s*(==|!=|=)\s*(.+?)\s*$""").find(andPart)
                        ?: throw IllegalArgumentException("无法解析: $andPart")
                    val field = FIELD_ALIASES[m.groupValues[1].lowercase()]
                        ?: throw IllegalArgumentException("未知字段: ${m.groupValues[1]}")
                    val op = if (m.groupValues[2] == "!=") Assertion.Op.NEQ else Assertion.Op.EQ
                    var v = m.groupValues[3].trim()
                    // 去掉可选引号
                    if (v.length >= 2 && (v.startsWith('"') && v.endsWith('"') || v.startsWith('\'') && v.endsWith('\''))) {
                        v = v.substring(1, v.length - 1)
                    }
                    Assertion(field, op, v)
                }
            }
            if (groups.any { it.isEmpty() }) throw IllegalArgumentException("空的 & 组")
            return NodeQuery(groups)
        }

        /** 宽松解析:失败返回 null,用于恢复旧数据 */
        fun parseOrNull(expr: String): NodeQuery? = try {
            parse(expr)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private val NodeQuery.Assertion.Field.key: String
    get() = when (this) {
        NodeQuery.Assertion.Field.TEXT -> "text"
        NodeQuery.Assertion.Field.VID -> "vid"
        NodeQuery.Assertion.Field.DESC -> "desc"
        NodeQuery.Assertion.Field.VISIBLE -> "visible"
    }

/**
 * 步骤条件:动作执行前的门槛检查,不满足则跳过本步(不算失败)。
 * expectPresent = true 要求表达式命中,false 要求不命中。
 */
@Serializable
data class StepCondition(
    val query: NodeQuery,
    val expectPresent: Boolean = true,
) {
    /** 编辑器/日志摘要 */
    fun summary(): String =
        (if (expectPresent) "有" else "无") + " " + query.summary()
}

/**
 * 执行步骤:密封接口,每种动作只带自己的参数。
 *
 * 每一步 = 条件([condition],可选门槛,不满足跳过) + 动作 + 次数([repeat])。
 */
@Serializable
sealed interface Step {
    val id: String

    /** 动作执行前的条件门槛;null = 无条件 */
    val condition: StepCondition?

    /** 执行次数,>=1 */
    val repeat: Int

    /** 点击命中节点;不可点击时退化为中心点坐标点击 */
    @Serializable
    @SerialName("click")
    data class Click(
        /** 点击目标:查询命中该节点才执行 */
        val query: NodeQuery,
        override val condition: StepCondition? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Step

    /** 固定延时 [ms] */
    @Serializable
    @SerialName("sleep")
    data class Sleep(
        val ms: Long = 1000L,
        override val condition: StepCondition? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Step

    /** 滑动:[up]=true 上滑 */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val up: Boolean = true,
        override val condition: StepCondition? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Step

    /** 系统返回键 */
    @Serializable
    @SerialName("back")
    data class Back(
        override val condition: StepCondition? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Step
}

/**
 * 自动化任务 v4。
 *
 * @property name 人起的名字,列表/日志展示用
 * @property activityPattern Activity 类名匹配,null = 任意;支持精确名与前缀
 * @property enabled 停用的任务不参与匹配
 * @property trigger 触发策略:进入页面一次 / 前台循环
 * @property steps 有序步骤,自上而下执行,失败按 [onFailure] 处理
 */
@Serializable
data class Task(
    val id: String,
    val name: String,
    val packageName: String,
    val activityPattern: String? = null,
    val enabled: Boolean = true,
    val trigger: Trigger,
    val steps: List<Step> = emptyList(),
    val onFailure: OnFailure = OnFailure.Retry(),
) {
    /**
     * 触发策略。
     */
    @Serializable
    sealed interface Trigger {
        /** 进入页面时触发;once = 同一次进入该页面只触发一次 */
        @Serializable
        @SerialName("onPage")
        data class OnPage(val once: Boolean = true) : Trigger

        /** 前台停留期间循环执行;maxRounds = 0 表示无限(离开目标应用自动停) */
        @Serializable
        @SerialName("loop")
        data class Loop(
            val intervalMs: Long,
            val maxRounds: Int = 0,
        ) : Trigger
    }

    /**
     * 失败策略。
     */
    @Serializable
    sealed interface OnFailure {
        /** 整组重试:失败后从第 0 步重来,超过 [times] 次放弃 */
        @Serializable
        @SerialName("retry")
        data class Retry(
            val times: Int = 3,
            val intervalMs: Long = 500L,
        ) : OnFailure

        /** 失败即放弃 */
        @Serializable
        @SerialName("stop")
        data object Stop : OnFailure
    }
}

/** 步骤展示摘要(编辑器/日志) */
fun Step.label(): String = when (this) {
    is Step.Click -> {
        val text = query.groups.firstOrNull()
            ?.firstOrNull { it.field == NodeQuery.Assertion.Field.TEXT }?.value
        if (text != null) "点「$text」" else "点击 ${query.summary()}"
    }
    is Step.Sleep -> "延时${ms}ms"
    is Step.Back -> "返回"
    is Step.Swipe -> if (up) "上滑" else "下滑"
}

/** 任务步骤摘要(日志/列表展示);空步骤回退任务名 */
fun Task.stepsSummary(): String =
    steps.joinToString("→") { it.label() }.ifEmpty { name }
