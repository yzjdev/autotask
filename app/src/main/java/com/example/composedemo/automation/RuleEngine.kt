package com.example.composedemo.automation

import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 节点匹配条件:单个属性断言(字段 + 匹配方式 + 值)。
 */
data class NodeCondition(
    val field: Field,
    val mode: MatchMode,
    val value: String,
) {
    enum class Field { VIEW_ID, TEXT, DESC, CLASS_NAME }

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
    fun summary(): String = "${field.name.lowercase()} ${if (mode == MatchMode.EQUALS) "=" else "≈"} \"$value\""
}

/**
 * 执行步骤:多条件选择器(全部满足才命中,AND)+ 动作。
 * conditions 为空表示无条件动作(延时/返回/滑动)。
 */
data class RuleStep(
    val conditions: List<NodeCondition>,
    val action: Action,
    /** WAIT 动作专用:等待超时(ms) */
    val timeoutMs: Long = 5000L,
    /** DELAY 动作专用:延时时长(ms) */
    val delayMs: Long = 1000L,
    /** SWIPE 动作专用:true=上滑 */
    val swipeUp: Boolean = true,
) {
    sealed class Action {
        /** 点击命中节点;不可点击时退化为中心点坐标点击 */
        object Click : Action()

        /** 等待条件命中的节点出现,超时算本轮失败 */
        object WaitForNode : Action()

        /** 固定延时 [RuleStep.delayMs] */
        object Delay : Action()

        /** 系统返回键 */
        object PressBack : Action()

        /** 滑动:方向见 [RuleStep.swipeUp] */
        object Swipe : Action()
    }

    /** 编辑器/日志摘要 */
    fun summary(): String = when (action) {
        is Action.Click -> "点击"
        is Action.WaitForNode -> "等待出现"
        is Action.Delay -> "延时${delayMs}ms"
        is Action.PressBack -> "返回键"
        is Action.Swipe -> if (swipeUp) "上滑" else "下滑"
    } + if (conditions.isEmpty()) "" else " ←" + conditions.joinToString("且") { it.summary() }
}

/**
 * 自动化规则 v3:触发条件(应用+页面)+ 步骤序列 + 执行控制。
 *
 * @property activityPattern Activity 类名匹配,null = 任意;支持精确名与前缀
 * @property enabled 停用的规则不参与匹配
 * @property once 同一次进入该页面只触发一次(false = 每次进入都触发)
 * @property steps 有序步骤,自上而下执行,失败整组重试
 * @property loopIntervalMs >0 = 循环模式:前台停留期间每隔该间隔重跑全部步骤
 * @property loopMaxRounds 循环最大轮数,0 = 无限(离开目标应用自动停)
 */
data class AutomationRule(
    val id: String,
    val packageName: String,
    val activityPattern: String? = null,
    val enabled: Boolean = true,
    val once: Boolean = true,
    val steps: List<RuleStep> = emptyList(),
    val loopIntervalMs: Long = 0,
    val loopMaxRounds: Int = 0,
    val maxRetries: Int = 5,
    val retryIntervalMs: Long = 500,
) {
    /** 日志/列表展示用:步骤摘要,空规则回退 id */
    fun stepsSummary(): String =
        if (steps.isEmpty()) id else steps.joinToString("→") { it.actionLabel() }

    private fun RuleStep.actionLabel(): String = when (action) {
        is RuleStep.Action.Click -> {
            val text = conditions.firstOrNull { it.field == NodeCondition.Field.TEXT }?.value
            if (text != null) "点「$text」" else "点击"
        }
        is RuleStep.Action.WaitForNode -> "等待"
        is RuleStep.Action.Delay -> "延时${delayMs}ms"
        is RuleStep.Action.PressBack -> "返回"
        is RuleStep.Action.Swipe -> if (swipeUp) "上滑" else "下滑"
    }
}

/**
 * 规则引擎 v3:挂在 [DramaAccessibilityService] 的窗口切换事件上。
 *
 * 执行模型(全部在主线程 Handler 上,绝不阻塞):
 *  1. onActivityChanged → 过滤 enabled + 匹配规则 → 触发
 *  2. 步骤按序执行:Click/Swipe/Back 立即,Delay postDelayed,Wait 轮询至超时
 *  3. 步骤失败 → 整组重试(重启到第 0 步),超过 maxRetries 放弃并释放去重
 *  4. 全部步骤成功 → 成功日志;循环模式下按 loopIntervalMs 排下一轮,
 *     直到离开目标应用或达到 loopMaxRounds
 */
class RuleEngine(private val service: DramaAccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val rules = ArrayList<AutomationRule>()

    /** 单次触发去重:pkg + activity + ruleId */
    private val executed = HashSet<String>()

    /** 活跃循环:key = pkg|ruleId,防止同一规则重复启动多条循环 */
    private val activeLoops = HashSet<String>()

    /** 注册规则(可在服务连接后随时追加) */
    fun addRule(rule: AutomationRule) {
        rules += rule
    }

    fun clearRules() {
        rules.clear()
        cancelAll()
    }

    /** Activity 切换入口:由服务的 TYPE_WINDOW_STATE_CHANGED 调用 */
    fun onActivityChanged(packageName: String, activityName: String) {
        for (rule in rules) {
            if (!rule.enabled || !matches(rule, packageName, activityName)) continue
            val dedupKey = "${rule.packageName}|$activityName|${rule.id}"
            val isLoop = rule.loopIntervalMs > 0
            val loopKey = "${rule.packageName}|${rule.id}"
            if (isLoop && !activeLoops.add(loopKey)) continue  // 循环已在跑
            if (rule.once && !isLoop && !executed.add(dedupKey)) continue  // 已执行过
            LogStore.log("▶ 触发「${rule.stepsSummary()}」@ $packageName/$activityName")
            runSteps(rule, dedupKey, round = 0, stepIndex = 0, attempt = 0)
        }
    }

    /** 离开目标包名时清空去重与循环(下次进入重新执行) */
    fun onLeftPackage(packageName: String) {
        executed.removeAll { it.startsWith("$packageName|") }
        activeLoops.removeAll { it.startsWith("$packageName|") }
    }

    fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
        activeLoops.clear()
    }

    /** 规则匹配:包名必须相等;Activity 支持精确名 / 前缀 / null 任意 */
    private fun matches(rule: AutomationRule, pkg: String, activity: String): Boolean {
        if (rule.packageName != pkg) return false
        val pattern = rule.activityPattern ?: return true
        return activity == pattern || activity.startsWith(pattern)
    }

    /** 当前前台是否仍在目标应用(用户可能已手动离开) */
    private fun inForeground(rule: AutomationRule): Boolean =
        service.rootInActiveWindow?.packageName == rule.packageName

    /**
     * 步骤链式执行核心。每步完成或等待后 post 自身推进到下一步;
     * 失败走 [retryOrFail] 整组重试;全部完成走 [onRoundDone]。
     */
    private fun runSteps(rule: AutomationRule, dedupKey: String, round: Int, stepIndex: Int, attempt: Int) {
        if (!rule.enabled || !inForeground(rule)) {
            executed.remove(dedupKey)
            activeLoops.remove("${rule.packageName}|${rule.id}")
            return  // 用户已离开/规则被停用,静默中止
        }
        if (stepIndex >= rule.steps.size) {
            onRoundDone(rule, dedupKey, round, attempt)
            return
        }
        val step = rule.steps[stepIndex]
        val next = { runSteps(rule, dedupKey, round, stepIndex + 1, attempt) }
        when (step.action) {
            is RuleStep.Action.Delay -> handler.postDelayed(next, step.delayMs.coerceAtLeast(0))

            is RuleStep.Action.PressBack -> {
                service.goBack()
                LogStore.log("  ↳ 第${stepIndex + 1}步 返回键")
                next()
            }

            is RuleStep.Action.Swipe -> {
                if (step.swipeUp) service.swipeUp() else service.swipeDown()
                LogStore.log("  ↳ 第${stepIndex + 1}步 ${if (step.swipeUp) "上滑" else "下滑"}")
                next()
            }

            is RuleStep.Action.Click -> {
                val root = service.rootInActiveWindow
                val node = root?.let { findNode(it, step.conditions) }
                if (node == null) {
                    retryOrFail(rule, dedupKey, round, attempt, stepIndex, "未找到节点")
                    return
                }
                val ok = node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!ok) {
                    // 退化:中心点坐标点击(点击事件常挂在不可点击的父容器上)
                    val r = android.graphics.Rect()
                    node.getBoundsInScreen(r)
                    if (r.isEmpty) {
                        retryOrFail(rule, dedupKey, round, attempt, stepIndex, "节点不可点击")
                        return
                    }
                    service.clickAt(r.exactCenterX(), r.exactCenterY())
                }
                LogStore.log("  ↳ 第${stepIndex + 1}步 点击 ${step.conditions.joinToString("且") { it.summary() }}${if (ok) "" else "(坐标)"}")
                next()
            }

            is RuleStep.Action.WaitForNode -> {
                val root = service.rootInActiveWindow
                val found = root?.let { findNode(it, step.conditions) } != null
                val key = waitKey(dedupKey, round, stepIndex)
                if (found) {
                    waitStart.remove(key)  // 清理等待起点,下一轮重新计时
                    LogStore.log("  ↳ 第${stepIndex + 1}步 等待命中")
                    next()
                    return
                }
                val start = waitStart[key] ?: System.currentTimeMillis().also { waitStart[key] = it }
                if (System.currentTimeMillis() - start >= step.timeoutMs) {
                    waitStart.remove(key)
                    retryOrFail(rule, dedupKey, round, attempt, stepIndex, "等待超时")
                    return
                }
                // 未超时:定时原地重查(不推进 stepIndex)
                handler.postDelayed({
                    runSteps(rule, dedupKey, round, stepIndex, attempt)
                }, WAIT_POLL_MS)
            }
        }
    }

    /** Wait 轮询的起始时间表:key → 开始时刻(超时判定用) */
    private val waitStart = HashMap<String, Long>()

    private fun waitKey(dedupKey: String, round: Int, stepIndex: Int) = "$dedupKey#$round:$stepIndex"

    private companion object {
        const val WAIT_POLL_MS = 200L
    }

    /** 单步失败 → 整组重试;超过上限放弃并释放去重 */
    private fun retryOrFail(
        rule: AutomationRule,
        dedupKey: String,
        round: Int,
        attempt: Int,
        stepIndex: Int,
        reason: String,
    ) {
        if (attempt + 1 >= rule.maxRetries) {
            executed.remove(dedupKey)
            LogStore.log("✕ 失败「${rule.stepsSummary()}」第${stepIndex + 1}步 $reason,重试${rule.maxRetries}次后放弃")
            return
        }
        handler.postDelayed(
            {
                clearWaits(dedupKey)  // 重启后等待重新计时
                runSteps(rule, dedupKey, round, 0, attempt + 1)  // 重启到第 0 步
            },
            rule.retryIntervalMs,
        )
    }

    /** 清理该规则全部等待起点(多规则并发时互不影响) */
    private fun clearWaits(dedupKey: String) {
        waitStart.keys.removeAll { it.startsWith("$dedupKey#") }
    }

    /** 全部步骤完成:成功日志 + 循环模式排下一轮 */
    private fun onRoundDone(rule: AutomationRule, dedupKey: String, round: Int, attempt: Int) {
        clearWaits(dedupKey)
        val attemptNote = if (attempt > 0) "(第${attempt + 1}次尝试)" else ""
        if (rule.loopIntervalMs <= 0) {
            LogStore.log("✓ 成功「${rule.stepsSummary()}」$attemptNote")
            return
        }
        LogStore.log("✓ 第${round + 1}轮「${rule.stepsSummary()}」$attemptNote")
        val nextRound = round + 1
        if (rule.loopMaxRounds in 1..nextRound) {
            LogStore.log("⏹ 循环结束:已达 ${rule.loopMaxRounds} 轮上限")
            activeLoops.remove("${rule.packageName}|${rule.id}")
            return
        }
        handler.postDelayed(
            {
                if (inForeground(rule) && rule.enabled) {
                    runSteps(rule, dedupKey, nextRound, 0, 0)
                } else {
                    activeLoops.remove("${rule.packageName}|${rule.id}")
                }
            },
            rule.loopIntervalMs,
        )
    }

    /** 多条件 AND 匹配查找:全部条件满足的节点;优先系统文本查询加速 */
    private fun findNode(
        root: AccessibilityNodeInfo,
        conditions: List<NodeCondition>,
    ): AccessibilityNodeInfo? {
        if (conditions.isEmpty()) return null
        // 快路径:含「文本=精确值」条件时用系统查询预筛
        val textCond = conditions.firstOrNull {
            it.field == NodeCondition.Field.TEXT && it.mode == NodeCondition.MatchMode.EQUALS
        }
        val candidates = if (textCond != null) {
            root.findAccessibilityNodeInfosByText(textCond.value).asSequence()
                .filter { it.text?.toString() == textCond.value }
        } else {
            sequenceOf(root).plus(dfs(root))
        }
        return candidates.firstOrNull { node -> conditions.all { it.matches(node) } }
            ?: dfs(root).firstOrNull { node -> conditions.all { it.matches(node) } }
    }

    /** 深度优先展开子树(含根) */
    private fun dfs(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            yield(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
    }
}
