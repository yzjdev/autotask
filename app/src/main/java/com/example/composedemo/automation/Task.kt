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
 * 执行步骤:密封接口,每种动作只带自己的参数。
 */
@Serializable
sealed interface Step {
    val id: String

    /** 点击命中节点;不可点击时退化为中心点坐标点击 */
    @Serializable
    @SerialName("click")
    data class Click(
        val locator: NodeLocator,
        override val id: String = uuid(),
    ) : Step

    /** 等待定位器命中的节点出现,超时算本轮失败 */
    @Serializable
    @SerialName("wait")
    data class Wait(
        val locator: NodeLocator,
        val timeoutMs: Long = 5000L,
        override val id: String = uuid(),
    ) : Step

    /** 固定延时 [ms] */
    @Serializable
    @SerialName("sleep")
    data class Sleep(
        val ms: Long = 1000L,
        override val id: String = uuid(),
    ) : Step

    /** 滑动:[up]=true 上滑 */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val up: Boolean = true,
        override val id: String = uuid(),
    ) : Step

    /** 系统返回键 */
    @Serializable
    @SerialName("back")
    data object Back : Step {
        override val id: String = "back"
    }
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
        val text = locator.takeIf { it.field == NodeLocator.Field.TEXT }?.value
        if (text != null) "点「$text」" else "点击"
    }
    is Step.Wait -> "等待"
    is Step.Sleep -> "延时${ms}ms"
    is Step.Back -> "返回"
    is Step.Swipe -> if (up) "上滑" else "下滑"
}

/** 任务步骤摘要(日志/列表展示);空步骤回退任务名 */
fun Task.stepsSummary(): String =
    steps.joinToString("→") { it.label() }.ifEmpty { name }
