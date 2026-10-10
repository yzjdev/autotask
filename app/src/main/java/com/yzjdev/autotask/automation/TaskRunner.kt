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
        var matchSince = 0L       // 最近一次重置时刻(GKD matchChangedTime:matchTime/forcedTime/priorityTime 窗口起点)
        var priorityLeft = 0L     // 优先级剩余次数(priorityActionMaximum)
    }

    private val execStates = HashMap<String, ExecState>()

    /** 各规则目标节点当前是否在场(同页面内容变化时检测"从无到有",触发计数重置) */
    private val targetPresentStates = HashMap<String, Boolean>()

    /** GKD updateTopActivity 同 Activity 去抖:≥1s 才再走一次跃迁重置 */
    private var lastResetActivityKey: String? = null
    private var lastResetActivityAt = 0L

    /** 上一真实 Activity(pkg/cls):resetMatch=match 计算 previouslyMatched 用 */
    private var prevResetPkg: String? = null
    private var prevResetActivity: String? = null
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
                        // GKD checkForced:窗口起点 = matchChangedTime(最近一次重置,无记录则用 bootAt)
                        val since = execStates["${task.id}|scene"]?.matchSince?.takeIf { it > 0 } ?: bootAt
                        if (task.forcedTime > 0 && now - since <= task.forcedTime) {
                            tryLaunchScene(task, steps, root, now)
                        }
                        continue
                    }
                    for (rule in task.rules) {
                        val ft = groupOr(task.forcedTime, rule.forcedTime)
                        val s = state(task, rule)
                        // GKD checkForced:now < matchChangedTime + matchDelay + forcedTime
                        val since = s.matchSince.takeIf { it > 0 } ?: bootAt
                        if (ft <= 0 || now - since > ft) continue
                        if (!schedulable(task, rule, s, now)) continue
                        if (!preKeysOk(task, rule, s, now)) continue
                        if (ruleMatches(root, rule)) launchRule(task, rule, s, now, "forcedTime")
                    }
                }
            }
        }
    }

    /** Activity 变化入口:由服务的 TYPE_WINDOW_STATE_CHANGED 确认真实 Activity 后调用 */
    fun onActivityChanged(packageName: String, activityName: String) {
        val now = System.currentTimeMillis()
        // GKD updateTopActivity:isSame && t - lastActivityUpdateTime < 1000 直接 return(同 Activity 去抖 1s)
        val key = "$packageName/$activityName"
        val isSameActivity = key == lastResetActivityKey
        synchronized(this) {
            if (isSameActivity && now - lastResetActivityAt < 1000) {
                evaluateRules(packageName, activityName, eventType = "windowStateChanged")
                return
            }
            lastResetActivityKey = key
            lastResetActivityAt = now
        }
        // 跃迁重置,对齐 GKD onActivityTransition:
        //  - activity 策略:无条件清
        //  - match 策略:previouslyMatched = 本规则 activityIds 命中「上一 Activity」→ 未命中才清
        //  - app 策略:换应用才清(onLeftPackage)
        val prevPkg = prevResetPkg
        val prevAct = prevResetActivity
        synchronized(this) {
            prevResetPkg = packageName
            prevResetActivity = activityName
        }
        for (task in tasks) {
            if (!task.enabled) continue
            if (task.packageName.isNotEmpty() && task.packageName != packageName) continue
            val hasMatchType = task.resetMatch == GkdTask.ResetMatch.Match ||
                task.rules.any { it.resetMatch == GkdTask.ResetMatch.Match }
            if (task.resetMatch == GkdTask.ResetMatch.Activity && !hasMatchType) {
                resetExecCounters(task, now)
                continue
            }
            if (hasMatchType) {
                // previouslyMatched:任一 match 策略规则的 activityIds 命中上一 Activity(空 = 全部命中)
                val matched = task.rules.filter { resetMatchOf(task, it) == GkdTask.ResetMatch.Match }
                    .any { rule -> activityMatch(rule.activityIds.ifEmpty { task.activityIds }, prevAct ?: "") && prevPkg == packageName }
                if (!matched) resetExecCounters(task, now)
            }
        }
        evaluateRules(packageName, activityName, eventType = "windowStateChanged")
    }

    /** 清空一个任务下全部规则/场景的执行计数并刷新匹配窗起点(不动 lastAt,对齐 GKD resetState) */
    private fun resetExecCounters(task: GkdTask, now: Long) {
        val prefix = "${task.id}|"
        for ((k, s) in execStates) {
            if (k.startsWith(prefix)) {
                s.count = 0
                s.matchSince = now
            }
        }
    }

    /** 窗口内容变化入口:由服务的 TYPE_WINDOW_CONTENT_CHANGED 去抖后调用 */
    fun onContentChanged(packageName: String, activityName: String) {
        evaluateRules(packageName, activityName, eventType = "windowContentChanged")
    }

    /** 通用事件入口:viewFocused/viewClicked/viewLongClicked/viewTextChanged 等由服务转发 */
    fun onEvent(packageName: String, activityName: String, eventType: String) {
        evaluateRules(packageName, activityName, eventType)
    }

    /** 事件类型名(AccessibilityEvent type):供规则 eventTypes 匹配 */
    val knownEventTypes: List<String> = listOf(
        "windowStateChanged", "windowContentChanged", "viewFocused",
        "viewTextChanged", "viewClicked", "viewLongClicked",
    )

    /**
     * 规则评估:仅评估 eventTypes 包含当前事件类型的规则(空列表 = 默认 windowStateChanged)。
     */
    private fun evaluateRules(packageName: String, activityName: String, eventType: String) {
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
            // 多步骤编排组:规则不独立调度,由第一步匹配启动场景流(仅状态变化启动)
            val steps = task.steps
            if (steps != null && steps.isNotEmpty()) {
                if (eventType == "windowStateChanged") tryLaunchScene(task, steps, root, now)
                continue
            }
            // 同页面目标从无到有:内容变化事件重评时,目标节点重新出现视为新的一轮,
            // 重置计数/匹配窗(需在 schedulable 过滤前做——已休眠的规则也要有机会恢复)
            if (eventType == "windowContentChanged") {
                for (rule in task.rules) {
                    if (rule.triggerOnAbsent) continue
                    val stateKey = "${task.id}|${rule.key}"
                    val selector = rule.matches.lastOrNull() ?: rule.anyMatches.firstOrNull() ?: continue
                    val present = selector.find(root).isNotEmpty()
                    synchronized(this) {
                        if (targetPresentStates[stateKey] == false && present) {
                            resetExecCounters(task, now)
                        }
                        targetPresentStates[stateKey] = present
                    }
                }
            }
            // 事件类型过滤:规则未订阅当前事件类型则跳过(空列表 = 默认状态+内容变化双订阅)
            val ranked = task.rules
                .filter { rule ->
                    val types = rule.eventTypes.ifEmpty {
                        listOf("windowStateChanged", "windowContentChanged")
                    }
                    eventType in types
                }
                .map { it to state(task, it) }
                .filter { (r, s) -> schedulable(task, r, s, now) }
                .sortedWith(compareBy({ (r, s) -> if (priorityActive(task, r, s, now)) 0 else 1 }, { (r, _) -> groupOr(task.order, r.order) }))
            for ((rule, state) in ranked) {
                if (preKeysOk(task, rule, state, now) && ruleMatches(root, rule)) {
                    launchRule(task, rule, state, now, eventType)
                }
            }
        }
    }

    /** 规则是否在途:同 key 未执行完前不再重复触发(双事件/去抖重评都会走到这里) */
    private fun inFlight(task: GkdTask, rule: GkdTask.Rule): Boolean =
        jobs["${task.id}|${rule.key}"]?.isActive == true

    /** Activity 刷新(resetMatch=activity 的重置时机):记录当前 activity 供比对 */
    private fun activityMatch(ids: List<String>, activity: String): Boolean =
        ids.isEmpty() || ids.any { activity == it || activity.startsWith(it) }

    private fun state(task: GkdTask, rule: GkdTask.Rule): ExecState =
        execStates.getOrPut("${task.id}|${rule.key}") { ExecState() }

    /** 取规则级覆盖值,0 回退组级 */
    private fun groupOr(taskV: Long, ruleV: Long, default: Long = 0L): Long =
        if (ruleV != 0L) ruleV else if (taskV != 0L) taskV else default

    /** GKD resetMatch 语义:规则级覆盖组级(rule.resetMatch ?: group.resetMatch,默认 activity) */
    private fun resetMatchOf(task: GkdTask, rule: GkdTask.Rule): GkdTask.ResetMatch =
        rule.resetMatch ?: task.resetMatch

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

    /** 优先级状态:priorityTime 窗内(起点 = matchSince,对齐 GKD isPriority 的 matchChangedTime)且优先次数未用完 */
    private fun priorityActive(task: GkdTask, rule: GkdTask.Rule, s: ExecState, now: Long): Boolean {
        val pt = groupOr(task.priorityTime, rule.priorityTime)
        if (pt <= 0) return false
        val pMax = groupOr(task.priorityActionMaximum, rule.priorityActionMaximum, 1L)
        val since = s.matchSince.takeIf { it > 0 } ?: bootAt
        return since > 0 && now - since <= pt && s.priorityLeft != -1L && s.count < pMax
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
        if (rule.triggerOnAbsent) {
            // 反向触发:所有触发选择器均未命中才算命中(排除条件照常生效)
            if (rule.matches.any { it.find(root).isNotEmpty() }) return false
            if (rule.anyMatches.any { it.find(root).isNotEmpty() }) return false
            return rule.matches.isNotEmpty() || rule.anyMatches.isNotEmpty()
        }
        if (rule.matches.isNotEmpty()) return rule.matches.all { it.find(root).isNotEmpty() }
        if (rule.anyMatches.isNotEmpty()) return rule.anyMatches.any { it.find(root).isNotEmpty() }
        return false
    }

    // ---- 场景流(多步骤编排) ----

    /** 步骤匹配:与规则同语义(matches 全命中 + anyMatches 任一 + exclude 排除;全空 = 无条件步) */
    private fun stepMatches(root: AccessibilityNodeInfo, step: GkdTask.Step): Boolean {
        if (step.excludeMatches.isNotEmpty() && step.excludeMatches.any { it.find(root).isNotEmpty() }) return false
        if (step.triggerOnAbsent) {
            // 反向触发:所有触发选择器均未命中才算命中(排除条件照常生效)
            if (step.matches.any { it.find(root).isNotEmpty() }) return false
            if (step.anyMatches.any { it.find(root).isNotEmpty() }) return false
            return step.matches.isNotEmpty() || step.anyMatches.isNotEmpty()
        }
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
            if (step.triggerOnAbsent) {
                // 反向步骤:等待目标节点消失后执行(动作无节点目标,仅返回/滑动/坐标类)
                if (!awaitStepAbsent(step)) {
                    if (step.onTimeout == GkdTask.Step.TimeoutPolicy.Continue) {
                        LogStore.log("  ↳ $label 等待消失超时,跳过")
                        continue
                    }
                    LogStore.log("  ⏹ $label 等待消失超时,流终止")
                    return
                }
                delay(step.settleTime)
                delay(groupOr(task.actionDelay, 0L))
                executeStep(task, step, null, label)
                continue
            }
            val target = awaitStepTarget(step)
            if (target == null) {
                if (step.onTimeout == GkdTask.Step.TimeoutPolicy.Continue) {
                    LogStore.log("  ↳ $label 等待超时,跳过")
                    continue
                }
                LogStore.log("  ⏹ $label 等待超时,流终止")
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

    /** 轮询等待反向步骤(triggerOnAbsent)的目标消失;waitTimeout=0 时只查当帧 */
    private suspend fun awaitStepAbsent(step: GkdTask.Step): Boolean {
        val deadline = if (step.waitTimeout <= 0L) 0L else System.currentTimeMillis() + step.waitTimeout
        val interval = if (step.waitInterval > 0L) step.waitInterval else 250L
        while (true) {
            val root = service.rootInActiveWindow
            if (root != null && stepMatches(root, step)) return true
            if (deadline == 0L || System.currentTimeMillis() >= deadline) return false
            delay(interval)
        }
    }

    /** 步骤动作执行:与规则共用动作语义(position/swipeArg 分支照抄 executeRule);反向步骤 target 为 null */
    private fun executeStep(task: GkdTask, step: GkdTask.Step, target: AccessibilityNodeInfo?, label: String) {
        val action = step.action ?: Action.Click
        if (target == null && action in listOf(
                Action.ClickNode, Action.LongClickNode, Action.InputText, Action.Check, Action.Uncheck,
            )
        ) {
            // 无目标节点时节点类动作无法执行(反向触发步骤常态)
            return
        }
        val r = android.graphics.Rect()
        target?.getBoundsInScreen(r)
        when (action) {
            Action.Click, Action.ClickNode, Action.ClickCenter -> {
                when {
                    action == Action.ClickCenter || (action == Action.Click && step.position != null) -> clickAtRect(step.position, r)
                    action == Action.ClickNode -> target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> if (target?.performAction(ACTION_CLICK) != true) clickAtRect(step.position, r)
                }
            }
            Action.LongClick, Action.LongClickNode, Action.LongClickCenter -> {
                when {
                    action == Action.LongClickCenter || (action == Action.LongClick && step.position != null) -> longClickAtRect(step.position, r)
                    action == Action.LongClickNode -> target?.performAction(ACTION_LONG_CLICK)
                    else -> if (target?.performAction(ACTION_LONG_CLICK) != true) longClickAtRect(step.position, r)
                }
            }
            Action.Back -> {
                service.goBack()
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
            }
            Action.InputText -> {
                target?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            Action.Check, Action.Uncheck -> {
                target?.performAction(
                    if (action == Action.Check) AccessibilityNodeInfo.ACTION_SELECT
                    else AccessibilityNodeInfo.ACTION_CLEAR_SELECTION,
                )
            }
            Action.LaunchApp, Action.None -> {}
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
        targetPresentStates.entries.removeIf { (k, _) ->
            val task = tasks.firstOrNull { it.id == k.substringBefore('|') }
            task?.packageName == packageName ||
                (task != null && task.packageName.isEmpty() && task.resetMatch == GkdTask.ResetMatch.App)
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
        targetPresentStates.clear()
    }

    /** 启动单条规则执行;actionDelay 二次确认后执行 */
    private fun launchRule(task: GkdTask, rule: GkdTask.Rule, s: ExecState, now: Long, eventType: String) {
        val stateKey = "${task.id}|${rule.key}"
        // 在途防重:同规则未执行完前不重复触发/记日志(状态+内容双事件、去抖重评的并发来源)
        if (inFlight(task, rule)) return
        // 动作目标:matches 最后一条的查找结果(从右往左);反向触发规则允许无目标
        val action = rule.action ?: Action.Click
        val target = findTarget(rule)
        if (target == null && !rule.triggerOnAbsent && action in NODE_ACTIONS) return
        val what = nodeInfo(target)
        LogStore.log("▶ ${appName(task.packageName)} 「${task.name}」${rule.summary}$what · $eventType")
        val job = scope.launch {
            val self = coroutineContext[Job]!!
            try {
                val ad = groupOr(task.actionDelay, rule.actionDelay)
                if (ad > 0) {
                    delay(ad)
                    val root = service.rootInActiveWindow ?: return@launch
                    if (!ruleMatches(root, rule)) {
                        return@launch
                    }
                }
                executeRule(task, rule, target)
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
    private suspend fun executeRule(task: GkdTask, rule: GkdTask.Rule, found: AccessibilityNodeInfo?) {
        val action = rule.action ?: Action.Click
        // 目标由 launchRule 预查传入;actionDelay 二次确认后需重查,故此处仍按需查找
        val target = found ?: if (rule.triggerOnAbsent) null else findTarget(rule) ?: return
        if (target == null && action in listOf(
                Action.ClickNode, Action.LongClickNode, Action.InputText, Action.Check, Action.Uncheck,
            )
        ) {
            // 无目标节点时节点类动作无法执行(反向触发规则常态)
            return
        }
        val r = android.graphics.Rect()
        target?.getBoundsInScreen(r)
        when (action) {
            Action.Click, Action.ClickNode, Action.ClickCenter -> {
                when {
                    action == Action.ClickCenter || (action == Action.Click && rule.position != null) -> clickAtWithPosition(rule, r)
                    action == Action.ClickNode -> target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    else -> if (target?.performAction(ACTION_CLICK) != true) clickAtWithPosition(rule, r)
                }
            }
            Action.LongClick, Action.LongClickNode, Action.LongClickCenter -> {
                when {
                    action == Action.LongClickCenter || (action == Action.LongClick && rule.position != null) -> longClickAtWithPosition(rule, r)
                    action == Action.LongClickNode -> target?.performAction(ACTION_LONG_CLICK)
                    else -> if (target?.performAction(ACTION_LONG_CLICK) != true) longClickAtWithPosition(rule, r)
                }
            }
            Action.Back -> {
                service.goBack()
            }
            Action.Swipe -> {
                // 照抄 GkdAction.Swipe:swipeArg.start 必填,end 缺省 = start;无参数回退上下滑
                val arg = rule.swipeArg
                when {
                    arg != null -> {
                        val rect = android.graphics.Rect()
                        target?.getBoundsInScreen(rect)
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
            }
            Action.InputText -> {
                target?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            Action.Check, Action.Uncheck -> {
                target?.performAction(
                    if (action == Action.Check) AccessibilityNodeInfo.ACTION_SELECT
                    else AccessibilityNodeInfo.ACTION_CLEAR_SELECTION,
                )
            }
            Action.LaunchApp, Action.None -> {}
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

    /** 包名 → 应用显示名(查不到回退包名);空包名 = 全局规则 */
    private fun appName(pkg: String): String {
        if (pkg.isEmpty()) return "全局"
        return runCatching {
            service.packageManager.getApplicationLabel(
                service.packageManager.getApplicationInfo(pkg, 0),
            ).toString()
        }.getOrDefault(pkg)
    }

    /** 命中节点详细信息:类名/文本/desc/id/边界,供触发日志 */
    private fun nodeInfo(n: AccessibilityNodeInfo?): String {
        n ?: return ""
        val parts = ArrayList<String>()
        n.className?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add("text=${it.take(30)}") }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add("desc=${it.take(30)}") }
        n.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { parts.add("id=${it.substringAfterLast('/')}") }
        if (parts.isEmpty()) return ""
        val r = android.graphics.Rect()
        n.getBoundsInScreen(r)
        if (!r.isEmpty) parts.add("bounds=(${r.left},${r.top},${r.width()}x${r.height()})")
        return " 「${parts.joinToString(" ")}」"
    }

    private companion object {
        const val ACTION_CLICK = AccessibilityNodeInfo.ACTION_CLICK
        const val ACTION_LONG_CLICK = AccessibilityNodeInfo.ACTION_LONG_CLICK
        /** 需要目标节点的动作(无目标时放弃触发) */
        val NODE_ACTIONS = listOf(
            Action.ClickNode, Action.LongClickNode, Action.InputText, Action.Check, Action.Uncheck,
        )
    }
}
