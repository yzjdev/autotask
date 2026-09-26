package com.example.composedemo.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 任务引擎 v4:协程顺序流,替代 v3 的 postDelayed 回调链。
 *
 * 执行模型(全部在主线程协程上,绝不阻塞):
 *  1. onActivityChanged → 过滤 enabled + 匹配任务 → 触发
 *  2. 步骤按序执行:Click/Swipe/Back 立即,Sleep delay,Wait 轮询至超时
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
                    if (trigger.once &&
                        !executed.add("$packageName|$activityName|${task.id}")
                    ) continue  // 本次进入已执行过
                    launchTask(task, dedupKey = "$packageName|$activityName|${task.id}")
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
        scope.launch {
            val job = coroutineContext[Job]!!
            jobs[task.id] = job
            try {
                when (val trigger = task.trigger) {
                    is Task.Trigger.OnPage ->
                        withRetry(task, dedupKey) { runRound(task) }
                    is Task.Trigger.Loop -> {
                        var round = 0
                        while (isActiveCheck(job) && task.enabled && inForeground(task)) {
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
                if (jobs[task.id] === job) jobs.remove(task.id)
            }
        }
    }

    private fun isActiveCheck(job: Job): Boolean = job.isActive

    /** 失败策略包装:整组重试或单次执行 */
    private suspend fun withRetry(
        task: Task,
        dedupKey: String?,
        round: suspend () -> Boolean,
    ) {
        val policy = task.onFailure
        if (policy is Task.OnFailure.Retry) {
            for (attempt in 0..policy.times) {
                if (round()) {
                    val note = if (attempt > 0) "(第${attempt + 1}次尝试)" else ""
                    LogStore.log("✓ 成功「${task.stepsSummary()}」$note")
                    return
                }
                if (attempt < policy.times) delay(policy.intervalMs)
            }
            LogStore.log("✕ 失败「${task.stepsSummary()}」,重试${policy.times}次后放弃")
            dedupKey?.let { executed.remove(it) }  // 放弃后释放去重,下次进入可再试
        } else {
            if (!round()) LogStore.log("✕ 失败「${task.stepsSummary()}」")
        }
    }

    /**
     * 单轮步骤顺序执行核心。返回 true = 全部成功;false = 某步失败(交给重试策略)。
     * 每步开始前检查前台,用户离开即取消整个执行。
     */
    private suspend fun runRound(task: Task): Boolean {
        task.steps.forEachIndexed { index, step ->
            if (!task.enabled || !inForeground(task)) {
                throw kotlinx.coroutines.CancellationException("left foreground")
            }
            if (!executeStep(task, index, step)) return false
        }
        return true
    }

    /** 执行单步;false = 失败 */
    private suspend fun executeStep(task: Task, index: Int, step: Step): Boolean = when (step) {
        is Step.Click -> {
            val root = service.rootInActiveWindow
            val node = root?.let { findNode(it, step.locator) }
            if (node == null) {
                LogStore.log("  ↳ 第${index + 1}步 未找到节点 ${step.locator.summary()}")
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
                        LogStore.log("  ↳ 第${index + 1}步 点击 ${step.locator.summary()}(坐标)")
                        true
                    }
                } else {
                    LogStore.log("  ↳ 第${index + 1}步 点击 ${step.locator.summary()}")
                    true
                }
            }
        }

        is Step.Wait -> {
            // 轮询等待;超时 = 本轮失败,用户取消 = 静默退出
            val found = try {
                withTimeout(step.timeoutMs) {
                    while (service.rootInActiveWindow?.let { findNode(it, step.locator) } == null) {
                        delay(WAIT_POLL_MS)
                    }
                    true
                }
            } catch (e: TimeoutCancellationException) {
                false
            }
            LogStore.log("  ↳ 第${index + 1}步 ${if (found) "等待命中" else "等待超时"}")
            found
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

    /** Wait 轮询间隔 */
    private companion object {
        const val WAIT_POLL_MS = 200L
        const val TAG = "TaskRunner"
    }

    /** 单定位器查找:TEXT 精确值走系统查询预筛加速,否则 DFS */
    private fun findNode(
        root: AccessibilityNodeInfo,
        locator: NodeLocator,
    ): AccessibilityNodeInfo? {
        val textCond = locator.takeIf {
            it.field == NodeLocator.Field.TEXT && it.mode == NodeLocator.MatchMode.EQUALS
        }
        val candidates = if (textCond != null) {
            root.findAccessibilityNodeInfosByText(textCond.value).asSequence()
                .filter { it.text?.toString() == textCond.value }
        } else {
            sequenceOf(root).plus(dfs(root))
        }
        return candidates.firstOrNull { locator.matches(it) }
            ?: dfs(root).firstOrNull { locator.matches(it) }
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
