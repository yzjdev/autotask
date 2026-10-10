package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.HashSet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 无障碍服务:提供界面节点读取、控件定位、模拟点击与手势。
 *
 * 典型用法:
 * ```kotlin
 * val service = DramaAccessibilityService.instance ?: return
 * service.clickByText("播放")
 * service.findNodeByText("下一集")?.let { service.clickNode(it) }
 * service.swipeUp()
 * ```
 *
 * 服务需在系统「无障碍」中开启;拥有「写入安全设置」权限时也可由自动化层
 * 直接写入 [android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] 完成开关。
 */
open class DramaAccessibilityService : AccessibilityService() {

    companion object {
        private val MAIN = Handler(Looper.getMainLooper())

        @Volatile
        private var sInstance: DramaAccessibilityService? = null

        /** 当前服务实例;未开启时为 null */
        val instance: DramaAccessibilityService?
            get() = sInstance

        /** 无障碍服务是否处于已连接状态(用于门禁判断) */
        val isRunning: Boolean
            get() = sInstance != null

        private val stateListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

        fun addStateListener(listener: (Boolean) -> Unit) {
            stateListeners += listener
        }

        fun removeStateListener(listener: (Boolean) -> Unit) {
            stateListeners -= listener
        }

        private fun notifyRunning(running: Boolean) {
            MAIN.post { stateListeners.toList().forEach { it(running) } }
        }

        // 调试悬浮窗显隐监听:长按悬浮球关闭等非主页入口的显隐变化同步主页开关 UI
        private val overlayListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

        fun addOverlayListener(listener: (Boolean) -> Unit) {
            overlayListeners += listener
        }

        fun removeOverlayListener(listener: (Boolean) -> Unit) {
            overlayListeners -= listener
        }

        fun notifyOverlayShowing(showing: Boolean) {
            MAIN.post { overlayListeners.toList().forEach { it(showing) } }
        }

        // 窗口列表悬浮窗显隐监听:卡片自身 ✕ 关闭等非主页入口的变化同步主页开关 UI
        private val windowListListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

        fun addWindowListListener(listener: (Boolean) -> Unit) {
            windowListListeners += listener
        }

        fun removeWindowListListener(listener: (Boolean) -> Unit) {
            windowListListeners -= listener
        }

        fun notifyWindowListShowing(showing: Boolean) {
            MAIN.post { windowListListeners.toList().forEach { it(showing) } }
        }
    }

    /** 单次遍历内已处理节点,避免重复处理 */
    private val processedNodes = HashSet<AccessibilityNodeInfo>()
    private val processedLock = Any()

    // 无障碍调试悬浮窗:由主页开关控制显隐,连接后不自动显示
    private val debugOverlay = OverlayDebugWindow(this)

    // 窗口列表悬浮窗:枚举所有可见窗口并按应用分组,同样由主页开关控制
    private val windowListOverlay = OverlayWindowListWindow(this)

    // 一像素完全透明悬浮窗:触摸穿透,不遮挡不影响任何操作
    private var pixelView: View? = null

    /** 显示一像素透明悬浮窗(触摸穿透;TYPE_ACCESSIBILITY_OVERLAY 无需悬浮窗权限) */
    private fun showPixelOverlay() {
        if (pixelView != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        val view = View(this)
        view.setBackgroundColor(0x00000000)
        try {
            wm.addView(view, lp)
            pixelView = view
        } catch (_: Exception) {
        }
    }

    private fun hidePixelOverlay() {
        pixelView?.let { v ->
            try {
                getSystemService(WindowManager::class.java)?.removeView(v)
            } catch (_: Exception) {
            }
        }
        pixelView = null
    }

    /** 任务引擎 v4:窗口切换时匹配任务并执行(协程顺序流) */
    val taskRunner = TaskRunner(this)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sInstance = this
        clearProcessed()
        notifyRunning(true)
        // 服务连接时加载持久化任务,立即生效
        val loaded = TaskStore.loadAll(this)
        taskRunner.setTasks(loaded)
        LogStore.log("🔌 无障碍服务已连接,加载任务 ${loaded.size} 个")
        showPixelOverlay()
    }

    /** 调试悬浮球当前是否显示 */
    val isDebugOverlayShowing: Boolean
        get() = debugOverlay.isShowing

    /** 窗口列表悬浮窗当前是否显示 */
    val isWindowListShowing: Boolean
        get() = windowListOverlay.isShowing

    /** 显示/隐藏调试悬浮球(由主页开关调用);服务未连接时返回 false */
    fun showDebugOverlay(show: Boolean): Boolean {
        val overlay = debugOverlay
        if (show) overlay.show() else overlay.hide()
        notifyOverlayShowing(show && sInstance != null)
        return sInstance != null
    }

    /** 显示/隐藏窗口列表悬浮窗(由主页开关调用);服务未连接时返回 false */
    fun showWindowListOverlay(show: Boolean): Boolean {
        val overlay = windowListOverlay
        if (show) overlay.show() else overlay.hide()
        notifyWindowListShowing(show && sInstance != null)
        return sInstance != null
    }

    /** 窗口列表点选某窗口:由节点悬浮窗绘制该窗口的节点边界(需主线程调用) */
    fun showWindowBounds(window: AccessibilityWindowInfo): Boolean =
        debugOverlay.showBoundsFor(window)

    /** 窗口列表恢复/关闭:关掉它触发的绘制层 */
    fun hideWindowBounds() = debugOverlay.hideBounds()

    override fun onUnbind(intent: Intent?): Boolean {
        LogStore.log("🔌 无障碍服务已断开(unbind)")
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        taskRunner.cancelAll()
        debugOverlay.hide()
        windowListOverlay.hide()
        hidePixelOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        taskRunner.cancelAll()
        debugOverlay.hide()
        windowListOverlay.hide()
        hidePixelOverlay()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 系统中断(手势冲突等),无需处理
    }

    private fun clearProcessed() {
        synchronized(processedLock) {
            processedNodes.clear()
        }
    }

    // ---- 事件 ----

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                // 窗口列表悬浮窗按前台切换自动重排
                notifyWindowChanged()
                // 对齐 GKD A11yState.updateTopActivity:真实 Activity 才记录并回填,
                // 非真实 Activity(Dialog/输入法等)沿用该应用最近一次有效 Activity 名,
                // 保证规则 activityIds 匹配不因窗口类名失效
                if (packageName != null && isActivityClassName(className) && isRealActivity(packageName, className)) {
                    val cn = ComponentName(packageName, className)
                    lastActivity = cn
                    recordValidActivity(cn)
                    onWindowChanged(packageName, className)
                } else {
                    // 非 Activity 窗口(Dialog/输入法):只驱动评估,不触发 resetMatch 重置
                    taskRunner.onEvent(packageName ?: "", activityFor(packageName ?: ""), "windowStateChanged")
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                onContentChanged(packageName ?: "", event.className?.toString() ?: "")

            // 其余事件类型(viewFocused/viewClicked/viewLongClicked/viewTextChanged)按需转发,
            // 供订阅了对应 eventTypes 的规则评估;未订阅的规则会被 runner 过滤掉。
            // Activity 一律按「焦点激活窗口」解析,不用 lastActivity 缓存(可能来自后台应用/旧页面)
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                taskRunner.onEvent(packageName ?: "", activityFor(packageName ?: ""), "viewFocused")
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ->
                taskRunner.onEvent(packageName ?: "", activityFor(packageName ?: ""), "viewTextChanged")
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                taskRunner.onEvent(packageName ?: "", activityFor(packageName ?: ""), "viewClicked")
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                taskRunner.onEvent(packageName ?: "", activityFor(packageName ?: ""), "viewLongClicked")

            else -> Unit
        }
    }

    /**
     * 最近一次确认的真实 Activity(供调试悬浮窗标题等使用)
     */
    @Volatile
    var lastActivity: ComponentName? = null
        private set

    /** 各应用最近一次确认的真实 Activity(非 Activity 窗口事件时按包名回填,对齐 GKD lastValidActivity) */
    private val lastValidActivityByApp = HashMap<String, ComponentName>()

    private fun recordValidActivity(cn: ComponentName) {
        synchronized(lastValidActivityByApp) {
            lastValidActivityByApp[cn.packageName ?: ""] = cn
        }
    }

    fun getValidActivity(pkg: String): ComponentName? = synchronized(lastValidActivityByApp) {
        lastValidActivityByApp[pkg]
    }

    /** 焦点且激活的窗口(优先应用窗口):规则执行、节点快照与悬浮窗标题只认这个来源 */
    internal fun focusedWindow(): AccessibilityWindowInfo? {
        val list = runCatching { windows }.getOrNull().orEmpty()
        return list.firstOrNull {
            it.isFocused && it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } ?: list.firstOrNull { it.isFocused && it.isActive }
    }

    /**
     * 当前 Activity:以「焦点且激活」的窗口为准。
     *  - 窗口 title 即 Activity 的 flattenToShortString("pkg/.MainActivity"),且必须是已注册 Activity
     *  - title 不是组件形式(对话框/输入法等)时,退回该包已确认的 Activity
     *  - 窗口列表不可用(部分 ROM)时,才退回最近一次事件记录的 Activity
     * 规则 activityIds 匹配与悬浮窗标题统一走这里,避免用后台应用或旧页面的缓存值。
     */
    fun currentActivity(): ComponentName? {
        val w = focusedWindow()
        if (w != null) {
            val pkg = runCatching { w.root?.packageName?.toString() }.getOrNull()
            if (pkg != null) {
                val title = runCatching { w.title?.toString() }.getOrNull().orEmpty()
                if (title.contains('/')) {
                    val cn = ComponentName.unflattenFromString(title)
                    if (cn != null && cn.packageName == pkg &&
                        isRealActivity(cn.packageName, cn.className)) {
                        return cn
                    }
                }
                getValidActivity(pkg)?.let { return it }
            }
        }
        return lastActivity
    }

    /** 事件所属应用的当前 Activity:同包名的焦点激活窗口优先,否则用该包已确认的 Activity */
    private fun activityFor(pkg: String): String {
        val cur = currentActivity()
        if (cur != null && cur.packageName == pkg) return cur.className
        return getValidActivity(pkg)?.className
            ?: lastActivity?.takeIf { it.packageName == pkg }?.className
            ?: ""
    }

    /** 窗口状态变化监听(节点悬浮窗标题跟随 + 窗口列表重排;监听方自行切主线程) */
    private val windowListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addWindowListener(listener: () -> Unit) {
        windowListeners += listener
    }

    fun removeWindowListener(listener: () -> Unit) {
        windowListeners -= listener
    }

    private fun notifyWindowChanged() {
        windowListeners.toList().forEach { it() }
    }

    /** className 前缀过滤:排除 android.widget.* / android.app.* 等系统窗口类 */
    private fun isActivityClassName(cls: String): Boolean =
        cls.isNotEmpty() &&
            !cls.startsWith("android.widget") &&
            !cls.startsWith("android.app") &&
            !cls.startsWith("android.view") &&
            cls != "android.widget.FrameLayout"

    /** getActivityInfo 反查结果 LRU 缓存(对齐 GKD ActivityCache):避免高频事件下重复 binder 反查 */
    private val activityCache = android.util.LruCache<Pair<String, String>, Boolean>(256)

    /** getActivityInfo 反查:确认 pkg/cls 确实是已注册的 Activity(非 Activity 窗口会抛异常) */
    private fun isRealActivity(pkg: String, cls: String): Boolean = activityCache.get(pkg to cls)
        ?: runCatching {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true // getActivityInfo 未抛异常即为已注册(返回值恒非 null)
        }.getOrDefault(false).also { activityCache.put(pkg to cls, it) }

    /** 窗口切换(进入新页面 / 播放页):先驱动任务引擎,再留给子类扩展 */
    open fun onWindowChanged(packageName: String, className: String) {
        // 前台包名变化时通知引擎:离开的目标包清去重、停循环(下次进入重新执行)
        if (packageName != lastForegroundPkg) {
            val prev = lastForegroundPkg
            lastForegroundPkg = packageName
            if (prev != null) taskRunner.onLeftPackage(prev)
        }
        // GKD updateTopActivity:同 Activity 的 STATE_CHANGED 也去抖后走 resetMatch 重置
        taskRunner.onActivityChanged(packageName, className)
    }

    /** 任务表变更后按当前前台页面重新评估触发(避免停留在目标页时保存的任务不响应) */
    fun refreshTaskTriggers() {
        val cn = currentActivity() ?: return
        val pkg = cn.packageName ?: return
        lastForegroundPkg = pkg
        LogStore.log("🔁 任务表变更,按当前页面重新评估触发")
        taskRunner.onActivityChanged(pkg, cn.className)
    }

    /** 最近一次窗口事件的前台包名(onLeftPackage 检测用) */
    private var lastForegroundPkg: String? = null

    /** 内容变化去抖:按焦点激活窗口的 Activity 重新评估订阅了 windowContentChanged 事件的规则 */
    private val contentDebounce = Runnable {
        // 以焦点激活窗口为准(切回后台 App 未发 STATE_CHANGED 时也能拿到真实前台 Activity),
        // 窗口列表不可用时退回活动根节点所属应用
        val cur = currentActivity()
        val pkg = cur?.packageName ?: rootInActiveWindow?.packageName?.toString() ?: return@Runnable
        lastForegroundPkg = pkg
        taskRunner.onContentChanged(pkg, cur?.className ?: "")
    }

    /** 内容变化:很多界面切换(对话框/弹窗/网页内跳转)只发 CONTENT 事件不发 STATE_CHANGED,
     * 去抖 300ms 后按当前 Activity 重新评估订阅了 windowContentChanged 事件的规则 */
    open fun onContentChanged(packageName: String, className: String) {
        MAIN.removeCallbacks(contentDebounce)
        MAIN.postDelayed(contentDebounce, 300)
    }

    // ---- 节点遍历 ----

    /** 遍历当前窗口所有节点;onNode 返回 true 表示找到目标并停止遍历 */
    fun traverseNodes(onNode: (node: AccessibilityNodeInfo, depth: Int) -> Boolean) {
        val root = rootInActiveWindow ?: return
        traverseNode(root, 0, onNode)
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        onNode: (AccessibilityNodeInfo, Int) -> Boolean,
    ): Boolean {
        if (isProcessed(node)) return false
        if (onNode(node, depth)) {
            recycleSafely(node)
            return true
        }
        val childCount = node.childCount
        var found = false
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            if (traverseNode(child, depth + 1, onNode)) {
                found = true
                break
            }
        }
        if (!found) recycleSafely(node)
        return false
    }

    /** 按文本精确查找节点 */
    fun findNodeByText(text: String): AccessibilityNodeInfo? =
        findFirst { readableText(it) == text }

    // ---- 查找基础方法 ----

    /** 基础查找:遍历节点树,返回第一个满足条件的节点 */
    fun findFirst(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        traverseNodes { node, _ ->
            if (predicate(node)) {
                result = node
                true
            } else false
        }
        return result
    }

    /** 读取节点可读文本(优先 contentDescription,其次 text) */
    fun readableText(node: AccessibilityNodeInfo): String? =
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: node.text?.toString()?.takeIf { it.isNotBlank() }

    // ---- 动作 ----

    /** 点击节点;节点本身不可点击时向上找最近的可点击祖先(文本子节点直接 click 无效但返回 true) */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        target = target ?: node
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** 点击文本为 [text] 的节点 */
    fun clickByText(text: String): Boolean {
        val node = findNodeByText(text) ?: return false
        return clickNode(node)
    }

    /** 长按节点 */
    fun longClickNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

    /** 聚焦节点 */
    fun focusNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

    /** 选中节点 */
    fun selectNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_SELECT)

    /** 在指定坐标模拟点击 */
    fun clickAt(x: Float, y: Float) = gesture(x, y, x, y, 80L)

    /** 中心点长按:500ms 按压手势 */
    fun longClickAt(x: Float, y: Float) = gesture(x, y, x, y, 500L)

    /** 在屏幕中心模拟点击 */
    fun clickAtCenter() {
        val (width, height) = screenSize() ?: return
        clickAt(width / 2f, height / 2f)
    }

    /** 通用滑动:指定起终点坐标与时长,基于 gesture 实现 */
    fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 350L,
    ) = gesture(startX, startY, endX, endY, durationMs)

    /** 上滑(翻页 / 切下一集) */
    fun swipeUp() {
        val (width, height) = screenSize() ?: return
        swipe(
            width / 2f, height * 0.75f,
            width / 2f, height * 0.25f,
        )
    }

    /** 下滑 */
    fun swipeDown() {
        val (width, height) = screenSize() ?: return
        swipe(
            width / 2f, height * 0.25f,
            width / 2f, height * 0.75f,
        )
    }

    /** 通用手势:从 (x,y) 滑动到 (endX,endY);终点与起点相同即为点击 */
    fun gesture(x: Float, y: Float, endX: Float, endY: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val description = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(description, null, MAIN)
    }

    // ---- 屏幕旋转保护 ----

    /** 读取当前屏幕旋转设置(0=竖屏锁定);读取失败返回 null */
    fun userRotation(): Int? = runCatching {
        android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.USER_ROTATION)
    }.getOrNull()

    /** 恢复屏幕旋转设置;写入需要 WRITE_SECURE_SETTINGS 权限,失败返回 false */
    fun restoreUserRotation(value: Int): Boolean = runCatching {
        android.provider.Settings.System.putInt(contentResolver, android.provider.Settings.System.USER_ROTATION, value)
        true
    }.getOrDefault(false)

    // ---- 全局动作 ----

    fun openNotificationPanel(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goToHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecentApps(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    // ---- 工具 ----

    /** 屏幕宽高(px);取不到返回 null */
    private fun screenSize(): Pair<Int, Int>? {
        val metrics = resources.displayMetrics
        return if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) null
        else metrics.widthPixels to metrics.heightPixels
    }

    fun screenWidth(): Int = screenSize()?.first ?: 0
    fun screenHeight(): Int = screenSize()?.second ?: 0

    private fun isProcessed(node: AccessibilityNodeInfo): Boolean {
        synchronized(processedLock) {
            if (processedNodes.contains(node)) return true
            processedNodes.add(node)
            return false
        }
    }

    @Suppress("DEPRECATION") // API 33 起 recycle 由系统自动处理,但旧版本仍需手动释放;运行时按版本判断
    private fun recycleSafely(node: AccessibilityNodeInfo) {
        try {
            node.recycle()
        } catch (_: Throwable) {
            // 节点可能已被系统回收
        }
    }
}
