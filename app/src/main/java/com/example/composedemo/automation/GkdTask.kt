package com.example.composedemo.automation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** 步骤 id 默认值 */
private fun uuid(): String = UUID.randomUUID().toString()

/**
 * 自动化任务 v5 模型 —— 对齐 GKD 订阅结构。
 *
 * 规则 = 触发条件([GkdTask.Rule.activityIds] + [GkdTask.Rule.matches] 选择器)
 *      + 动作列表([GkdTask.Rule.actions]) + 失败策略([GkdTask.Rule.onFailure])。
 * 定位一律用 [GkdSelector](GKD 选择器子集),不再有独立的 NodeLocator/NodeQuery。
 *
 * 旧 v4 数据不迁移:TaskStore 恢复失败时丢弃旧快照。
 */

/**
 * 执行动作:每种动作只带自己的参数。
 * 定位类动作带 [matches] 选择器;命中才执行。
 */
@Serializable
sealed interface Action {
    val id: String

    /** 执行前的条件门槛;null = 无条件;不满足则跳过本步(不算失败) */
    val condition: GkdSelector?

    /** 执行次数,>=1 */
    val repeat: Int

    /** 点击命中节点;不可点击时退化为中心点坐标点击 */
    @Serializable
    @SerialName("click")
    data class Click(
        val matches: GkdSelector,
        override val condition: GkdSelector? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Action

    /** 长按命中节点 */
    @Serializable
    @SerialName("longClick")
    data class LongClick(
        val matches: GkdSelector,
        override val condition: GkdSelector? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Action

    /** 等待选择器命中,超时算失败 */
    @Serializable
    @SerialName("waitNode")
    data class WaitNode(
        val matches: GkdSelector,
        val timeoutMs: Long = 5000L,
        override val condition: GkdSelector? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Action

    /** 固定延时 [ms] */
    @Serializable
    @SerialName("sleep")
    data class Sleep(
        val ms: Long = 1000L,
        override val condition: GkdSelector? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Action

    /** 滑动:[up]=true 上滑 */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val up: Boolean = true,
        override val condition: GkdSelector? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Action

    /** 系统返回键 */
    @Serializable
    @SerialName("back")
    data class Back(
        override val condition: GkdSelector? = null,
        override val repeat: Int = 1,
        override val id: String = uuid(),
    ) : Action
}

/**
 * 自动化任务 v5 —— GKD 订阅对齐。
 *
 * @property name 人起的名字,列表/日志展示用
 * @property packageName 目标应用(单包;批量复用靠复制规则)
 * @property activityIds Activity 类名匹配列表,空 = 任意;支持精确名与前缀
 * @property enabled 停用的规则不参与匹配
 * @property matches 触发选择器:命中即认为页面就绪(GKD 的 `matches` 语义)
 * @property actions 有序动作,自上而下执行,失败按 [onFailure] 处理
 * @property onFailure 失败策略
 */
@Serializable
data class GkdTask(
    val id: String,
    val name: String,
    val packageName: String,
    val activityIds: List<String> = emptyList(),
    val enabled: Boolean = true,
    val matches: GkdSelector? = null,
    val actions: List<Action> = emptyList(),
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
        /** 整组重试:失败后从第 0 个动作重来,超过 [times] 次放弃 */
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

/** 动作展示摘要(编辑器/日志) */
fun Action.label(): String = when (this) {
    is Action.Click -> {
        val text = matches.props.firstOrNull { it.key == "text" }?.value
        if (text != null) "点「$text」" else "点击 ${matches.summary()}"
    }
    is Action.LongClick -> {
        val text = matches.props.firstOrNull { it.key == "text" }?.value
        if (text != null) "长按「$text」" else "长按 ${matches.summary()}"
    }
    is Action.WaitNode -> "等待 ${matches.summary()}"
    is Action.Sleep -> "延时${ms}ms"
    is Action.Back -> "返回"
    is Action.Swipe -> if (up) "上滑" else "下滑"
}

/** 规则动作摘要(日志/列表展示);空动作回退规则名 */
fun GkdTask.actionsSummary(): String =
    actions.joinToString("→") { it.label() }.ifEmpty { name }
