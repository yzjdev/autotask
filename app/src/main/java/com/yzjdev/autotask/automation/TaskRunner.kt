package com.yzjdev.autotask.automation

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 协程执行器 —— 完整复刻 GKD 调度语义(gkd.li/api RawCommonProps):
 *
 *  - 匹配顺序:priorityTime 窗内的优先级规则先于普通规则,内部按 order 排序
 *  - preKeys:要求指定 key 的规则「刚刚执行过」才触发(组内顺序链)
 *  - actionCd / actionCdKey:执行冷却;CdKey 使多条规则共享同一冷却
 *  - actionMaximum / actionMaximumKey:最大执行次数,达到即休眠;MaximumKey 共享次数
 *  - matchTime:规则参与匹配的时间窗,超时休眠
 *  - actionDelay:查到节点 → 等待 → 再次查到才执行
 *  - resetMatch:app(离开应用清零)/ activity(Activity 刷新清零)/ match(失配→匹配清零)
 */
class TaskRunner(private val service: DramaAccessibilityService) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = HashMap<String, Job>()

    /** 场景流(多步骤编排)在途任务:task.id → Job,流在途时同组不重复触发 */
    private val sceneJobs = HashMap<String, Job>()

    /** 单条规则执行状态 */
    private class ExecState {
        var count = 0L            // actionMaximum 计数
        var lastAt = 0L           // 上次执行时刻(actionCd / preKeys 依据)
        var matchSince = 0L       // 本轮匹配起点(matchTime 窗口起点)
        var priorityLeft = 0L     // 优先级剩余次数(priorityActionMaximum)
        var wasMatching = false   // resetMatch=match 的失配→匹配检测
    }

    private val execStates = HashMap<String, ExecState>()
    private var tasks: List<GkdTask> = emptyList()
    private var bootAt = 0L

    /** 整体替换规则表并停掉全部执行(保存规则后调用) */
    fun setTasks(next: List<GkdTask>) {
        tasks = next
        cancelAll()
        bootAt = System.currentTimeMillis()
        startForcedPolling()
    }

    /**
     * forcedTime 主动轮询:flutter/webview 应用界面变化不触发 onAccessibilityEvent,
     * 对声明了 forcedTime 的规则在窗口期内每 250ms 主动查屏匹配一次。
     */
    private var pollJob: Job? = null

    private fun startForcedPolling() {
        pollJob?.cancel()
        val hasForced = tasks.any { t ->
            t.enabled && (t.forcedTime > 0 || t.rules.any { it.forcedTime > 0 } || (t.steps?.isNotEmpty() == true && t.forcedTime > 0))
        }
        if (!hasForced) return
        pollJob = scope.launch {
            while (coroutineContext[Job]!!.isActive) {
                delay(250)
                val now = System.currentTimeMillis()
                val root = service.rootInActiveWindow ?: continue
                val pkg = root.packageName?.toString() ?: continue
                for (task in tasks) {
                    if (!task.enabled) continue
                    // packageName 空 = 全局规则(GKD globalGroups),匹配任意前台应用
                    if (task.packageName.isNotEmpty() && task.packageName != pkg) continue
                    // disableIfAppGroupMatch 同款抑制(与 onActivityChanged 一致)
                    if (task.packageName.isEmpty() && task.disableIfAppGroupMatch.isNotEmpty() &&
                        tasks.any { it.packageName == pkg && it.name.contains(task.disableIfAppGroupMatch) }
                    ) continue
                    // 多步骤编排组:forcedTime 窗口内轮询触发场景流
                    val steps = task.steps
                    if (steps != null && steps.isNotEmpty()) {
                        if (task.forcedTime > 0 && now - bootAt <= task.forcedTime) {
                            tryLaunchScene(task, steps, root, now)
                        }
                        continue
                    }
                    for (rule in task.rules) {
                        val ft = groupOr(task.forcedTime, rule.forcedTime)
                        if (ft <= 0 || now - bootAt > ft) continue
                        val s = state(task, rule)
                        if (!schedulable(task, rule, s, now)) continue
                        if (!preKeysOk(task, rule, s, now)) continue
                        if (ruleMatches(root, rule)) launchRule(task, rule, s, now)
                    }
                }
            }
        }
    }

    /** Activity 切换入口:由服务的 TYPE_WINDOW_STATE_CHANGED 调用 */
    fun onActivityChanged(packageName: String, activityName: String) {
        val now = System.currentTimeMillis()
        val root = service.rootInActiveWindow ?: return
        for (task in tasks.sortedBy { it.order }) {
            if (!task.enabled) continue
            // packageName 空 = 全局规则(GKD globalGroups),匹配任意前台应用
            if (task.packageName.isNotEmpty() && task.packageName != packageName) continue
            // disableIfAppGroupMatch:目标应用已有名称匹配此模式的规则组时,本全局规则在该应用禁用
            if (task.packageName.isEmpty() && task.disableIfAppGroupMatch.isNotEmpty() &&
                tasks.any { it.packageName == packageName && it.name.contains(task.disableIfAppGroupMatch) }
            ) continue
            if (!activityMatch(task.activityIds, activityName)) continue
            // 多步骤编排组:规则不独立调度,由第一步匹配启动场景流
            val steps = task.steps
            if (steps != null && steps.isNotEmpty()) {
                tryLaunchScene(task, steps, root, now)
                continue
            }
            // 规则排序:优先级(窗内)在前,其余按 order;普通规则不被优先级规则中断(简化:一次遍历内先执行优先级)
            val ranked = task.rules
                .map { it to state(task, it) }
                .filter { (r, s) -> schedulable(task, r, s, now) }
                .sortedWith(compareBy({ (r, s) -> if (priorityActive(task, r, s, now)) 0 else 1 }, { (r, _) -> groupOr(task.order, r.order) }))
            for ((rule, state) in ranked) {
                if (preKeysOk(task, rule, state, now) && ruleMatches(root, rule)) {
                    launchRule(task, rule, state, now)
                }
            }
        }
    }

    /** Activity 刷新(resetMatch=activity 的重置时机):记录当前 activity 供比对 */
    private fun activityMatch(ids: List<String>, activity: String): Boolean =
        ids.isEmpty() || ids.any { activity == it || activity.startsWith(it) }

    private fun state(task: GkdTask, rule: GkdTask.Rule): ExecState =
        execStates.getOrPut("${task.id}|${rule.key}") { ExecState() }

    /** 取规则级覆盖值,0 回退组级 */
    private fun groupOr(taskV: Long, ruleV: Long, default: Long = 0L): Long =
        if (ruleV != 0L) ruleV else if (taskV != 0L) taskV else default

    private fun groupOrBool(taskV: Boolean, ruleV: Boolean): Boolean = ruleV || taskV

    /** 调度准入:休眠(次数/时间窗)+ 冷却 + 优先级时间窗 */
    private fun schedulable(task: GkdTask, rule: GkdTask.Rule, s: ExecState, now: Long): Boolean {
        val max = groupOr(task.actionMaximum, rule.actionMaximum)
        if (max > 0 && s.count >= max) return false
        val mt = groupOr(task.matchTime, rule.matchTime)
        if (mt > 0) {
            if (s.matchSince == 0L) s.matchSince = now
            else if (now - s.matchSince > mt) return false // 匹配窗已过,休眠
        }
        val cd = groupOr(task.actionCd, rule.actionCd, 1000L)
        if (s.lastAt > 0 && now - s.lastAt < cd) return false
        return true
    }

    /** 优先级状态:priorityTime 窗内且优先次数未用完 */
    private fun priorityActive(task: GkdTask, rule: GkdTask.Rule, s: ExecState, now: Long): Boolean {
        val pt = groupOr(task.priorityTime, rule.priorityTime)
        if (pt <= 0) return false
        val pMax = groupOr(task.priorityActionMaximum, rule.priorityActionMaximum, 1L)
        return bootAt > 0 && now - bootAt <= pt && s.priorityLeft != -1L && s.count < pMax
    }

    /** preKeys:要求的 key 刚刚执行过(lastAt 晚于本规则上次执行) */
    private fun preKeysOk(task: GkdTask, rule: GkdTask.Rule, s: ExecState, now: Long): Boolean {
        if (rule.preKeys.isEmpty()) return true
        return rule.preKeys.all { pre ->
            val preState = execStates["${task.id}|$pre"]
            preState != null && preState.lastAt > 0 &&
                (s.lastAt == 0L || preState.lastAt > s.lastAt) && now - preState.lastAt < 10_000
        }
    }

    /** 规则选择器匹配:matches 全命中 + exclude 排除(anyMatches 与 matches 独立判定) */
    private fun ruleMatches(root: AccessibilityNodeInfo, rule: GkdTask.Rule): Boolean {
        if (rule.excludeMatches.isNotEmpty() && rule.excludeMatches.any { it.find(root).isNotEmpty() }) return false
        if (rule.excludeAllMatches.isNotEmpty() && rule.excludeAllMatches.all { it.find(root).isNotEmpty() }) return false
        if (rule.matches.isNotEmpty()) return rule.matches.all { it.find(root).isNotEmpty() }
        if (rule.anyMatches.isNotEmpty()) return rule.anyMatches.any { it.find(root).isNotEmpty() }
        return false
    }

    // ---- 场景流(多步骤编排) ----

    /** 步骤匹配:与规则同语义(matches 全命中 + anyMatches 任一 + exclude 排除;全空 = 无条件步) */
    private fun stepMatches(root: AccessibilityNodeInfo, step: GkdTask.Step): Boolean {
        if (step.excludeMatches.isNotEmpty() && step.excludeMatches.any { it.find(root).isNotEmpty() }) return false
        if (step.matches.isNotEmpty()) return step.matches.all { it.find(root).isNotEmpty() }
        if (step.anyMatches.isNotEmpty()) return step.anyMatches.any { it.find(root).isNotEmpty() }
        return true
    }

    /** 场景流启动:第一步命中且不在途、过了组级冷却才启动(状态键独立于 rules,步骤组 rules 可为空) */
    private fun tryLaunchScene(task: GkdTask, steps: List<GkdTask.Step>, root: AccessibilityNodeInfo, now: Long) {
        if (sceneJobs.containsKey(task.id)) return
        val s = execStates.getOrPut("${task.id}|scene") { ExecState() }
        val cd = groupOr(task.actionCd, 0L, 1000L)
        if (s.lastAt > 0 && now - s.lastAt < cd) return
        val max = task.actionMaximum
        if (max > 0 && s.count >= max) return
        val first = steps.first()
        if (!stepMatches(root, first)) return
        launchScene(task, steps, s)
    }

    private fun launchScene(task: GkdTask, steps: List<GkdTask.Step>, s: ExecState) {
        LogStore.log("▶ 场景流「${task.name}」启动(${steps.size} 步)")
        val job = scope.launch {
            val self = coroutineContext[Job]!!
            try {
                runScene(task, steps)
                s.count++
                s.lastAt = System.currentTimeMillis()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                if (sceneJobs[task.id] === self) sceneJobs.remove(task.id)
            }
        }
        sceneJobs[task.id] = job
    }

    /** 按序执行步骤:每步等目标出现(waitTimeout)→ 稳定 → 执行动作;超时按策略跳过或终止 */
    private suspend fun runScene(task: GkdTask, steps: List<GkdTask.Step>) {
        for ((i, step) in steps.withIndex()) {
            val label = step.name.ifEmpty { "步骤${i + 1}" }
            val target = awaitStepTarget(step)
            if (target == null) {
                if (step.onTimeout == GkdTask.Step.TimeoutPolicy.Continue) {
                    LogStore.log("  ↳ 「$label」等待超时,跳过")
                    continue
                }
                LogStore.log("  ⏹ 「$label」等待超时,流终止")
                return
            }
            delay(step.settleTime)
            delay(groupOr(task.actionDelay, 0L))
            executeStep(task, step, target, label)
        }
        LogStore.log("✔ 场景流「${task.name}」完成")
    }

    /** 轮询等待步骤目标出现;waitTimeout=0 时只查当帧 */
    private suspend fun awaitStepTarget(step: GkdTask.Step): AccessibilityNodeInfo? {
        val deadline = if (step.waitTimeout <= 0L) 0L else System.currentTimeMillis() + step.waitTimeout
        val interval = if (step.waitInterval > 0L) step.waitInterval else 250L
        while (true) {
            val root = service.rootInActiveWindow
            if (root != null && stepMatches(root, step)) {
                val sel = step.matches.lastOrNull() ?: step.anyMatches.firstOrNull()
                val node = sel?.find(root)?.firstOrNull()
                if (node != null || (step.matches.isEmpty() && step.anyMatches.isEmpty())) return node
            }
            if (deadline == 0L || System.currentTimeMillis() >= deadline) return null
            delay(interval)
        }
    }

    /** 步骤动作执行:与规则共用动作语义(position/swipeArg 分支照抄 executeRule) */
    private fun executeStep(task: GkdTask, step: GkdTask.Step, target: AccessibilityNodeInfo, label: String) {
        val action = step.action ?: Action.Click
        val r = android.graphics.Rect()
        target.getBoundsInScreen(r)
        when (action) {
            Action.Click, Action.ClickNode, Action.ClickCenter -> {
                when {
                    action == Action.ClickCenter || (action == Action.Click && step.position != null) ->
                        clickAtRect(step.position, r)
                    action == Action.ClickNode -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> if (!target.performAction(ACTION_CLICK)) clickAtRect(step.position, r)
                }
                LogStore.log("  ↳ 「$label」点击")
            }
            Action.LongClick, Action.LongClickNode, Action.LongClickCenter -> {
                when {
                    action == Action.LongClickCenter || (action == Action.LongClick && step.position != null) ->
                        longClickAtRect(step.position, r)
                    action == Action.LongClickNode -> target.performAction(ACTION_LONG_CLICK)
                    else -> if (!target.performAction(ACTION_LONG_CLICK)) longClickAtRect(step.position, r)
                }
                LogStore.log("  ↳ 「$label」长按")
            }
            Action.Back -> {
                service.goBack()
                LogStore.log("  ↳ 「$label」返回键")
            }
            Action.Swipe -> {
                val arg = step.swipeArg
                when {
                    arg != null -> {
                        val startP = arg.start.calc(r, service.screenWidth(), service.screenHeight())
                        val endP = arg.end?.calc(r, service.screenWidth(), service.screenHeight()) ?: startP
                        if (startP != null && endP != null) {
                            service.swipe(startP.first, startP.second, endP.first, endP.second, arg.duration)
                        }
                    }
                    else -> service.swipeUp()
                }
                LogStore.log("  ↳ 「$label」滑动")
            }
            Action.InputText -> {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                LogStore.log("  ↳ 「$label」输入文本(聚焦)")
            }
            Action.LaunchApp -> LogStore.log("  ↳ 「$label」启动应用(待实现)")
            Action.Check, Action.Uncheck -> {
                val ok = target.performAction(
                    if (action == Action.Check) AccessibilityNodeInfo.ACTION_SELECT
                    else AccessibilityNodeInfo.ACTION_CLEAR_SELECTION,
                )
                LogStore.log("  ↳ 「$label」${if (action == Action.Check) "勾选" else "取消勾选"}${if (ok) "" else "(失败)"}")
            }
            Action.None -> LogStore.log("  ↳ 「$label」匹配标记(无动作)")
        }
    }

    /** 离开目标包名:清执行记录、按 resetMatch=app 重置计数、停掉在途执行 */
    fun onLeftPackage(packageName: String) {
        sceneJobs.entries.removeIf { (k, job) ->
            val task = tasks.firstOrNull { it.id == k } ?: return@removeIf false
            val hit = task.packageName == packageName || task.packageName.isEmpty()
            if (hit) {
                LogStore.log("⏹ 场景流「${task.name}」因离开应用终止")
                job.cancel()
            }
            hit
        }
        execStates.entries.removeIf { (k, _) ->
            val task = tasks.firstOrNull { it.id == k.substringBefore('|') } ?: return@removeIf false
            // 应用规则:离开即清;全局规则(packageName 空)按 resetMatch=app 在离开任意应用时重置
            task.packageName == packageName ||
                (task.packageName.isEmpty() && task.resetMatch == GkdTask.ResetMatch.App)
        }
        jobs.entries.removeIf { entry ->
            val task = tasks.firstOrNull { it.id == entry.key.substringBefore('|') }
            val hit = task?.packageName == packageName ||
                (task != null && task.packageName.isEmpty()) // 全局规则在途执行随离开应用终止
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
        sceneJobs.values.forEach { it.cancel() }
        sceneJobs.clear()
        execStates.clear()
    }

    /** 启动单条规则执行;actionDelay 二次确认后执行 */
    private fun launchRule(task: GkdTask, rule: GkdTask.Rule, s: ExecState, now: Long) {
        val stateKey = "${task.id}|${rule.key}"
        val label = rule.name.ifEmpty { "规则${rule.key}" }
        LogStore.log("▶ 触发「${task.name}·$label」@ ${task.packageName}")
        val job = scope.launch {
            val self = coroutineContext[Job]!!
            try {
                val ad = groupOr(task.actionDelay, rule.actionDelay)
                if (ad > 0) {
                    delay(ad)
                    val root = service.rootInActiveWindow ?: return@launch
                    if (!ruleMatches(root, rule)) return@launch
                }
                executeRule(task, rule)
                s.count++
                s.lastAt = System.currentTimeMillis()
                if (priorityActive(task, rule, s, System.currentTimeMillis())) {
                    s.priorityLeft--
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                if (jobs[stateKey] === self) jobs.remove(stateKey)
            }
        }
        jobs[stateKey] = job
    }

    /**
     * 执行单条规则动作,完整 GKD action 语义:
     *  - click:可点则节点事件,否则中心坐标;clickNode 仅节点事件;clickCenter 仅坐标
     *  - position:相对节点边界的偏移(正 = 左/上,负 = 右/下)
     *  - swipeArg:绝对坐标滑动;方向编码 endY=-1 上滑 / -2 下滑
     */
    private suspend fun executeRule(task: GkdTask, rule: GkdTask.Rule) {
        val label = rule.name.ifEmpty { "规则${rule.key}" }
        val action = rule.action ?: Action.Click
        // 动作目标:matches 最后一条的查找结果(从右往左);position 存在时默认 clickCenter
        val target = findTarget(rule) ?: run {
            LogStore.log("  ↳ 「$label」未找到目标节点")
            return
        }
        val r = android.graphics.Rect()
        target.getBoundsInScreen(r)
        when (action) {
            Action.Click, Action.ClickNode, Action.ClickCenter -> {
                when {
                    action == Action.ClickCenter || (action == Action.Click && rule.position != null) -> clickAtWithPosition(rule, r)
                    action == Action.ClickNode -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> { // click 混合语义
                        if (!target.performAction(ACTION_CLICK)) clickAtWithPosition(rule, r)
                    }
                }
                LogStore.log("  ↳ 「$label」点击")
            }
            Action.LongClick, Action.LongClickNode, Action.LongClickCenter -> {
                when {
                    action == Action.LongClickCenter || (action == Action.LongClick && rule.position != null) -> longClickAtWithPosition(rule, r)
                    action == Action.LongClickNode -> target.performAction(ACTION_LONG_CLICK)
                    else -> if (!target.performAction(ACTION_LONG_CLICK)) longClickAtWithPosition(rule, r)
                }
                LogStore.log("  ↳ 「$label」长按")
            }
            Action.Back -> {
                service.goBack()
                LogStore.log("  ↳ 「$label」返回键")
            }
            Action.Swipe -> {
                // 照抄 GkdAction.Swipe:swipeArg.start 必填,end 缺省 = start;无参数回退上下滑
                val arg = rule.swipeArg
                when {
                    arg != null -> {
                        val rect = android.graphics.Rect()
                        target.getBoundsInScreen(rect)
                        val startP = arg.start.calc(rect, service.screenWidth(), service.screenHeight())
                        val endP = arg.end?.calc(rect, service.screenWidth(), service.screenHeight()) ?: startP
                        if (startP != null && endP != null) {
                            service.swipe(startP.first, startP.second, endP.first, endP.second, arg.duration)
                        }
                    }
                    rule.swipeDir == 2 -> service.swipeDown()
                    rule.swipeDir == 1 -> service.swipeUp()
                    else -> service.swipeUp()
                }
                LogStore.log("  ↳ 「$label」滑动")
            }
            Action.InputText -> {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                LogStore.log("  ↳ 「$label」输入文本(聚焦;文本参数由 position/swipeArg 之外字段待扩展)")
            }
            Action.LaunchApp -> LogStore.log("  ↳ 「$label」启动应用(待实现)")
            Action.Check, Action.Uncheck -> {
                val ok = target.performAction(
                    if (action == Action.Check) AccessibilityNodeInfo.ACTION_SELECT
                    else AccessibilityNodeInfo.ACTION_CLEAR_SELECTION,
                )
                LogStore.log("  ↳ 「$label」${if (action == Action.Check) "勾选" else "取消勾选"}${if (ok) "" else "(失败)"}")
            }
            Action.None -> LogStore.log("  ↳ 「$label」匹配标记(无动作)")
        }
    }

    /** position 六字段表达式求坐标(照抄 Position.calc);无效或缺省回退节点中心 */
    private fun clickAtWithPosition(rule: GkdTask.Rule, r: android.graphics.Rect) {
        val p = rule.position
        val point = p?.takeIf { it.isValid }?.calc(r, service.screenWidth(), service.screenHeight())
        if (point != null) {
            service.clickAt(point.first, point.second)
        } else if (!r.isEmpty) {
            service.clickAt(r.exactCenterX(), r.exactCenterY())
        }
    }

    private fun longClickAtWithPosition(rule: GkdTask.Rule, r: android.graphics.Rect) {
        val p = rule.position
        val point = p?.takeIf { it.isValid }?.calc(r, service.screenWidth(), service.screenHeight())
        if (point != null) {
            service.longClickAt(point.first, point.second)
        } else if (!r.isEmpty) {
            service.longClickAt(r.exactCenterX(), r.exactCenterY())
        }
    }

    /** 步骤 position 六字段表达式求坐标;无效或缺省回退节点中心(与规则 clickAtWithPosition 同语义) */
    private fun clickAtRect(p: GkdTask.Position?, r: android.graphics.Rect) {
        val point = p?.takeIf { it.isValid }?.calc(r, service.screenWidth(), service.screenHeight())
        if (point != null) {
            service.clickAt(point.first, point.second)
        } else if (!r.isEmpty) {
            service.clickAt(r.exactCenterX(), r.exactCenterY())
        }
    }

    private fun longClickAtRect(p: GkdTask.Position?, r: android.graphics.Rect) {
        val point = p?.takeIf { it.isValid }?.calc(r, service.screenWidth(), service.screenHeight())
        if (point != null) {
            service.longClickAt(point.first, point.second)
        } else if (!r.isEmpty) {
            service.longClickAt(r.exactCenterX(), r.exactCenterY())
        }
    }

    /** 动作目标节点:matches 最后一条(链的 @ 目标在末端)或 anyMatches 首条 */
    private fun findTarget(rule: GkdTask.Rule): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val sel = rule.matches.lastOrNull() ?: rule.anyMatches.firstOrNull() ?: return null
        return sel.find(root).firstOrNull()
    }

    private companion object {
        const val ACTION_CLICK = AccessibilityNodeInfo.ACTION_CLICK
        const val ACTION_LONG_CLICK = AccessibilityNodeInfo.ACTION_LONG_CLICK
    }
}
