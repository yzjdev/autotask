package com.example.composedemo.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 规则引擎 v5:GKD 订阅对齐的协程顺序流。
 *
 * 执行模型(全部在主线程协程上,绝不阻塞):
 *  1. onActivityChanged → 过滤 enabled + 包名/Activity/触发选择器命中 → 触发
 *  2. 动作按序执行:Click/LongClick/WaitNode/Swipe/Back 立即,Sleep delay
 *  3. 动作失败 → 按 [GkdTask.OnFailure] 整组重试;超过上限放弃并释放去重
 *  4. 全部动作成功 → 成功日志;循环模式按间隔排下一轮,
 *     直到离开目标应用或达到 maxRounds
 *
 * 取消 = Job.cancel():离开前台/离开包名/整体替换规则时逐个 cancel。
 */
class TaskRunner(private val service: DramaAccessibilityService) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = HashMap<String, Job>()

    /** 单次触发去重:pkg|activity|ruleId(仅 OnPage(once=true)) */
    private val executed = HashSet<String>()

    private var tasks: List<GkdTask> = emptyList()

    /** 整体替换规则表并停掉全部执行(保存规则后调用) */
    fun setTasks(next: List<GkdTask>) {
        tasks = next
        cancelAll()
    }

    /** Activity 切换入口:由服务的 TYPE_WINDOW_STATE_CHANGED 调用 */
    fun onActivityChanged(packageName: String, activityName: String) {
        for (task in tasks) {
            if (!task.enabled || !matches(task, packageName, activityName)) continue
            // 触发选择器:声明了就必须命中才触发(GKD `matches` 语义)
            if (task.matches != null) {
                val root = service.rootInActiveWindow ?: continue
                if (findNode(root, task.matches) == null) continue
            }
            // OnPage(once=true) 去重;其余直接触发
            val key = "$packageName|$activityName|${task.id}"
            if (!executed.add(key)) continue
            launchTask(task, dedupKey = key)
        }
    }

    /** 离开目标包名时清空去重并停掉该包的规则(下次进入重新执行) */
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

    /** 手动执行一次规则(测试页用):不走触发匹配,不校验前台包名 */
    fun runManually(task: GkdTask) {
        LogStore.log("▶ 手动执行「${task.actionsSummary()}」")
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

    /** 触发匹配:包名必须相等;activityIds 空 = 任意,否则精确名或前缀命中 */
    private fun matches(task: GkdTask, pkg: String, activity: String): Boolean {
        if (task.packageName != pkg) return false
        if (task.activityIds.isEmpty()) return true
        return task.activityIds.any { activity == it || activity.startsWith(it) }
    }

    /** 当前前台是否仍在目标应用(用户可能已手动离开) */
    private fun inForeground(task: GkdTask): Boolean =
        service.rootInActiveWindow?.packageName == task.packageName

    /** 启动一个规则的执行协程;每轮之间按 500ms 间隔轮转(循环语义) */
    private fun launchTask(task: GkdTask, dedupKey: String?) {
        LogStore.log("▶ 触发「${task.actionsSummary()}」@ ${task.packageName}/${task.activityIds.joinToString("|").ifEmpty { "*" }}")
        // 同步注册 job:同一回调里下一个窗口事件查 containsKey 不会漏掉在途启动
        val job = scope.launch {
            val self = coroutineContext[Job]!!
            try {
                withRetry(task, dedupKey) { runRound(task) }
                // 触发选择器命中时每轮重跑,直到离开前台(循环语义)
                if (task.matches != null) {
                    while (self.isActive && inForeground(task)) {
                        delay(500)
                        if (!inForeground(task)) break
                        withRetry(task, dedupKey) { runRound(task) }
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

    /** 失败策略包装:整组重试(无限 = times<0)或单次执行 */
    private suspend fun withRetry(
        task: GkdTask,
        dedupKey: String?,
        round: suspend () -> Boolean,
    ) {
        val policy = task.onFailure
        if (policy is GkdTask.OnFailure.Retry) {
            val times = policy.times.coerceAtLeast(-1)
            var attempt = 0
            while (true) {
                if (round()) {
                    val note = if (attempt > 0) "(第${attempt + 1}次尝试)" else ""
                    LogStore.log("✓ 成功「${task.actionsSummary()}」$note")
                    return
                }
                if (times >= 0) {
                    if (attempt >= times) {
                        LogStore.log("✕ 失败「${task.actionsSummary()}」,重试${times}次后放弃")
                        dedupKey?.let { executed.remove(it) }
                        return
                    }
                    delay(policy.intervalMs)
                    attempt++
                } else {
                    LogStore.log("↻ 失败「${task.actionsSummary()}」,无限重试中…")
                    delay(policy.intervalMs)
                }
            }
        } else {
            if (!round()) LogStore.log("✕ 失败「${task.actionsSummary()}」")
        }
    }

    /**
     * 单轮动作顺序执行核心。返回 true = 全部成功;false = 某步失败(交给重试策略)。
     * 每步开始前检查前台,用户离开即取消整个执行。
     * 条件不满足 = 跳过(不算失败);条件满足 = 按 repeat 次数重复执行动作。
     */
    private suspend fun runRound(task: GkdTask, checkForeground: Boolean = true): Boolean {
        task.actions.forEachIndexed { index, action ->
            if (!task.enabled || (checkForeground && !inForeground(task))) {
                throw kotlinx.coroutines.CancellationException("left foreground")
            }
            val cond = action.condition
            if (cond != null) {
                val root = service.rootInActiveWindow
                val present = root?.let { findNode(it, cond) } != null
                if (!present) {
                    LogStore.log("  ↳ 第${index + 1}步 条件不满足(${cond.summary()}),跳过")
                    return@forEachIndexed
                }
            }
            repeat(action.repeat.coerceAtLeast(1)) { n ->
                if (action.repeat > 1) LogStore.log("  ↳ 第${index + 1}步 第${n + 1}/${action.repeat}次")
                if (!executeAction(index, action)) return false
            }
        }
        return true
    }

    /** 执行单个动作;false = 失败 */
    private suspend fun executeAction(index: Int, action: Action): Boolean = when (action) {
        is Action.Click -> {
            val node = findNode(action.matches) ?: run {
                LogStore.log("  ↳ 第${index + 1}步 未找到节点 ${action.matches.summary()}")
                return false
            }
            val ok = node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!ok) {
                // 退化:中心点坐标点击(点击事件常挂在不可点击的父容器上)
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                if (r.isEmpty) {
                    LogStore.log("  ↳ 第${index + 1}步 节点不可点击")
                    false
                } else {
                    service.clickAt(r.exactCenterX(), r.exactCenterY())
                    LogStore.log("  ↳ 第${index + 1}步 点击 ${action.matches.summary()}(坐标)")
                    true
                }
            } else {
                LogStore.log("  ↳ 第${index + 1}步 点击 ${action.matches.summary()}")
                true
            }
        }

        is Action.LongClick -> {
            val node = findNode(action.matches) ?: run {
                LogStore.log("  ↳ 第${index + 1}步 未找到节点 ${action.matches.summary()}")
                return false
            }
            val ok = node.isLongClickable &&
                node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (!ok) {
                // 退化:中心点长按手势
                val r = android.graphics.Rect()
                node.getBoundsInScreen(r)
                if (r.isEmpty) {
                    LogStore.log("  ↳ 第${index + 1}步 节点不可长按")
                    false
                } else {
                    service.longClickAt(r.exactCenterX(), r.exactCenterY())
                    LogStore.log("  ↳ 第${index + 1}步 长按 ${action.matches.summary()}(手势)")
                    true
                }
            } else {
                LogStore.log("  ↳ 第${index + 1}步 长按 ${action.matches.summary()}")
                true
            }
        }

        is Action.WaitNode -> {
            val deadline = System.currentTimeMillis() + action.timeoutMs
            var found = findNode(action.matches)
            while (found == null && System.currentTimeMillis() < deadline) {
                delay(200)
                found = findNode(action.matches)
            }
            if (found == null) {
                LogStore.log("  ↳ 第${index + 1}步 等待超时 ${action.matches.summary()}(${action.timeoutMs}ms)")
                false
            } else {
                LogStore.log("  ↳ 第${index + 1}步 节点已出现 ${action.matches.summary()}")
                true
            }
        }

        is Action.Sleep -> {
            delay(action.ms)
            LogStore.log("  ↳ 第${index + 1}步 延时${action.ms}ms")
            true
        }

        is Action.Swipe -> {
            if (action.up) service.swipeUp() else service.swipeDown()
            LogStore.log("  ↳ 第${index + 1}步 ${if (action.up) "上滑" else "下滑"}")
            true
        }

        is Action.Back -> {
            service.goBack()
            LogStore.log("  ↳ 第${index + 1}步 返回键")
            true
        }
    }

    /** 在当前活动窗口查找选择器命中的节点 */
    private fun findNode(selector: GkdSelector): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return findNode(root, selector)
    }

    /** 选择器查找:按 text= 值走系统查询预筛加速,否则 DFS */
    private fun findNode(
        root: AccessibilityNodeInfo,
        selector: GkdSelector,
    ): AccessibilityNodeInfo? {
        val textCond = selector.props.firstOrNull { it.key == "text" && it.op == GkdSelector.Prop.Op.EQ }
        val candidates = if (textCond != null) {
            root.findAccessibilityNodeInfosByText(textCond.value).asSequence()
                .filter { it.text?.toString() == textCond.value }
        } else {
            dfs(root)
        }
        return candidates.firstOrNull { selector.matches(it) }
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
