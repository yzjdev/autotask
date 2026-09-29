package com.example.composedemo.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 任务引擎 v4:协程顺序流,替代 v3 的 postDelayed 回调链。
 *
 * 执行模型(全部在主线程协程上,绝不阻塞):
 *  1. onActivityChanged → 过滤 enabled + 匹配任务 → 触发
 *  2. 步骤按序执行:Click/Swipe/Back 立即,Sleep delay
 *  3. 步骤失败 → 按 [Task.OnFailure] 整组重试;超过上限放弃并释放去重
 *  4. 全部步骤成功 → 成功日志;循环模式按 intervalMs 排下一轮,
 *     直到离开目标应用或达到 maxRounds
 *
 * 取消 = Job.cancel():离开前台/离开包名/整体替换任务时逐个 cancel,
 * 不再有回调表与状态 map 的手工清理。
 */
class TaskRunner(private val service: DramaAccessibilityService) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = HashMap<String, Job>()

    /** 单次触发去重:pkg|activity|taskId(仅 Trigger.OnPage(once=true)) */
    private val executed = HashSet<String>()

    private var tasks: List<Task> = emptyList()

    /** 整体替换任务表并停掉全部执行(保存任务后调用) */
    fun setTasks(next: List<Task>) {
        tasks = next
        cancelAll()
    }

    /** Activity 切换入口:由服务的 TYPE_WINDOW_STATE_CHANGED 调用 */
    fun onActivityChanged(packageName: String, activityName: String) {
        for (task in tasks) {
            if (!task.enabled || !matches(task, packageName, activityName)) continue
            when (val trigger = task.trigger) {
                is Task.Trigger.OnPage -> {
                    // 仅 once 任务参与去重;once=false 无需占位
                    val key = if (trigger.once) "$packageName|$activityName|${task.id}" else null
                    if (key != null && !executed.add(key)) continue  // 本次进入已执行过
                    launchTask(task, dedupKey = key)
                }
                is Task.Trigger.Loop -> {
                    if (jobs.containsKey(task.id)) continue  // 循环已在跑
                    launchTask(task, dedupKey = null)
                }
            }
        }
    }

    /** 离开目标包名时清空去重并停掉该包的任务(下次进入重新执行) */
    fun onLeftPackage(packageName: String) {
        executed.removeAll { it.startsWith("$packageName|") }
        jobs.entries.removeIf { entry ->
            val task = tasks.firstOrNull { it.id == entry.key }
            val hit = task?.packageName == packageName
            if (hit) entry.value.cancel()
            hit
        }
    }

    fun cancel(taskId: String) {
        jobs.remove(taskId)?.cancel()
    }

    /** 手动执行一次任务(测试页用):不走触发匹配,不校验前台包名 */
    fun runManually(task: Task) {
        LogStore.log("▶ 手动执行「${task.stepsSummary()}」")
        scope.launch {
            try {
                withRetry(task, dedupKey = null) { runRound(task, checkForeground = false) }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 页面销毁等取消场景,静默退出
            }
        }
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        executed.clear()
    }

    /** 触发匹配:包名必须相等;Activity 支持精确名 / 前缀 / null 任意 */
    private fun matches(task: Task, pkg: String, activity: String): Boolean {
        if (task.packageName != pkg) return false
        val pattern = task.activityPattern ?: return true
        return activity == pattern || activity.startsWith(pattern)
    }

    /** 当前前台是否仍在目标应用(用户可能已手动离开) */
    private fun inForeground(task: Task): Boolean =
        service.rootInActiveWindow?.packageName == task.packageName

    /** 启动一个任务的执行协程;循环模式在协程内轮转 */
    private fun launchTask(task: Task, dedupKey: String?) {
        LogStore.log("▶ 触发「${task.stepsSummary()}」@ ${task.packageName}/${task.activityPattern ?: "*"}")
        // 同步注册 job:同一回调里下一个窗口事件查 containsKey 不会漏掉在途启动
        val job = scope.launch {
            val self = coroutineContext[Job]!!  // 协程内的真实 Job,用于身份判定与循环条件
            try {
                when (val trigger = task.trigger) {
                    is Task.Trigger.OnPage ->
                        withRetry(task, dedupKey) { runRound(task) }
                    is Task.Trigger.Loop -> {
                        var round = 0
                        while (self.isActive && inForeground(task)) {
                            withRetry(task, dedupKey) { runRound(task) }
                            round++
                            if (trigger.maxRounds in 1..round) {
                                LogStore.log("⏹ 循环结束:已达 ${trigger.maxRounds} 轮上限")
                                break
                            }
                            delay(trigger.intervalMs)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e  // 用户离开/取消:静默退出
            } finally {
                if (jobs[task.id] === self) jobs.remove(task.id)
            }
        }
        jobs[task.id] = job
    }

    /** 失败策略包装:整组重试(无限 = times<0,持续到成功或被取消)或单次执行 */
    private suspend fun withRetry(
        task: Task,
        dedupKey: String?,
        round: suspend () -> Boolean,
    ) {
        val policy = task.onFailure
        if (policy is Task.OnFailure.Retry) {
            val times = policy.times.coerceAtLeast(-1)
            var attempt = 0
            while (true) {
                if (round()) {
                    val note = if (attempt > 0) "(第${attempt + 1}次尝试)" else ""
                    LogStore.log("✓ 成功「${task.stepsSummary()}」$note")
                    return
                }
                if (times >= 0) {
                    if (attempt >= times) {
                        LogStore.log("✕ 失败「${task.stepsSummary()}」,重试${times}次后放弃")
                        dedupKey?.let { executed.remove(it) }  // 放弃后释放去重,下次进入可再试
                        return
                    }
                    delay(policy.intervalMs)
                    attempt++
                } else {
                    LogStore.log("↻ 失败「${task.stepsSummary()}」,无限重试中…")
                    delay(policy.intervalMs)
                }
            }
        } else {
            if (!round()) LogStore.log("✕ 失败「${task.stepsSummary()}」")
        }
    }

    /**
     * 单轮步骤顺序执行核心。返回 true = 全部成功;false = 某步失败(交给重试策略)。
     * 每步开始前检查前台,用户离开即取消整个执行。
     * 条件不满足 = 跳过(不算失败);条件满足 = 按 repeat 次数重复执行动作。
     */
    private suspend fun runRound(task: Task, checkForeground: Boolean = true): Boolean {
        task.steps.forEachIndexed { index, step ->
            if (!task.enabled || (checkForeground && !inForeground(task))) {
                throw kotlinx.coroutines.CancellationException("left foreground")
            }
            // 条件门槛:不满足则跳过本步,不算失败
            val cond = step.condition
            if (cond != null) {
                val present = service.rootInActiveWindow?.let { anyNode(it, cond.query) } == true
                if (present != cond.expectPresent) {
                    LogStore.log("  ↳ 第${index + 1}步 条件不满足(${cond.summary()}),跳过")
                    return@forEachIndexed
                }
            }
            repeat(step.repeat.coerceAtLeast(1)) { n ->
                if (step.repeat > 1) LogStore.log("  ↳ 第${index + 1}步 第${n + 1}/${step.repeat}次")
                if (!executeStep(task, index, step)) return false
            }
        }
        return true
    }

    /** 执行单步;false = 失败 */
    private suspend fun executeStep(task: Task, index: Int, step: Step): Boolean = when (step) {
        is Step.Click -> {
            val root = service.rootInActiveWindow
            val node = root?.let { findNode(it, step.query) }
            if (node == null) {
                LogStore.log("  ↳ 第${index + 1}步 未找到节点 ${step.query.summary()}")
                false
            } else {
                val ok = node.isClickable &&
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!ok) {
                    // 退化:中心点坐标点击(点击事件常挂在不可点击的父容器上)
                    val r = android.graphics.Rect()
                    node.getBoundsInScreen(r)
                    if (r.isEmpty) {
                        LogStore.log("  ↳ 第${index + 1}步 节点不可点击")
                        false
                    } else {
                        service.clickAt(r.exactCenterX(), r.exactCenterY())
                        LogStore.log("  ↳ 第${index + 1}步 点击 ${step.query.summary()}(坐标)")
                        true
                    }
                } else {
                    LogStore.log("  ↳ 第${index + 1}步 点击 ${step.query.summary()}")
                    true
                }
            }
        }

        is Step.Sleep -> {
            delay(step.ms)
            LogStore.log("  ↳ 第${index + 1}步 延时${step.ms}ms")
            true
        }

        is Step.Swipe -> {
            if (step.up) service.swipeUp() else service.swipeDown()
            LogStore.log("  ↳ 第${index + 1}步 ${if (step.up) "上滑" else "下滑"}")
            true
        }

        is Step.Back -> {
            service.goBack()
            LogStore.log("  ↳ 第${index + 1}步 返回键")
            true
        }
    }

    /** 表达式是否命中任一节点 */
    private fun anyNode(root: AccessibilityNodeInfo, query: NodeQuery): Boolean =
        findNode(root, query) != null

    /** 单表达式查找:按 text= 值走系统查询预筛加速,否则 DFS */
    private fun findNode(
        root: AccessibilityNodeInfo,
        query: NodeQuery,
    ): AccessibilityNodeInfo? {
        val textCond = query.groups.firstOrNull()
            ?.firstOrNull { it.field == NodeQuery.Assertion.Field.TEXT && it.op == NodeQuery.Assertion.Op.EQ }
        val candidates = if (textCond != null) {
            root.findAccessibilityNodeInfosByText(textCond.value).asSequence()
                .filter { it.text?.toString() == textCond.value }
        } else {
            sequenceOf(root).plus(dfs(root))
        }
        return candidates.firstOrNull { query.matches(it) }
            ?: dfs(root).firstOrNull { query.matches(it) }
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
