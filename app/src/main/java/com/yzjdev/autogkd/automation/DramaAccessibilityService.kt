package com.yzjdev.autogkd.automation

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
    }

    /** 单次遍历内已处理节点,避免重复处理 */
    private val processedNodes = HashSet<AccessibilityNodeInfo>()
    private val processedLock = Any()

    // 无障碍调试悬浮窗:由主页开关控制显隐,连接后不自动显示
    private val debugOverlay = OverlayDebugWindow(this)

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

    /** 显示/隐藏调试悬浮球(由主页开关调用);服务未连接时返回 false */
    fun showDebugOverlay(show: Boolean): Boolean {
        val overlay = debugOverlay
        if (show) overlay.show() else overlay.hide()
        return sInstance != null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LogStore.log("🔌 无障碍服务已断开(unbind)")
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        taskRunner.cancelAll()
        debugOverlay.hide()
        hidePixelOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        taskRunner.cancelAll()
        debugOverlay.hide()
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

    /** 当前是否已连接 */
    fun isConnected(): Boolean = true

    // ---- 事件 ----

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                // 过滤非 Activity 窗口(Dialog/PopupWindow/输入法等):
                // 先用前缀粗筛,再用 PackageManager.getActivityInfo 精确反查,
                // 只有真实注册的 Activity 才视为前台切换
                if (packageName != null && isActivityClassName(className) && isRealActivity(packageName, className)) {
                    val cn = ComponentName(packageName, className)
                    lastActivity = cn
                    notifyActivityChanged(cn)
                }
                onWindowChanged(packageName ?: "", className)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                onContentChanged(packageName ?: "", event.className?.toString() ?: "")

            else -> Unit
        }
    }

    /**
     * 最近一次确认的真实 Activity(供调试悬浮窗标题等使用)
     */
    @Volatile
    var lastActivity: ComponentName? = null
        private set

    /** lastActivity 变化监听(悬浮窗标题实时跟随前台 Activity) */
    private val activityListeners = CopyOnWriteArrayList<(ComponentName) -> Unit>()

    fun addActivityListener(listener: (ComponentName) -> Unit) {
        activityListeners += listener
    }

    fun removeActivityListener(listener: (ComponentName) -> Unit) {
        activityListeners -= listener
    }

    private fun notifyActivityChanged(cn: ComponentName) {
        activityListeners.toList().forEach { it(cn) }
    }

    /** className 前缀过滤:排除 android.widget.* / android.app.* 等系统窗口类 */
    private fun isActivityClassName(cls: String): Boolean =
        cls.isNotEmpty() &&
            !cls.startsWith("android.widget") &&
            !cls.startsWith("android.app") &&
            !cls.startsWith("android.view") &&
            cls != "android.widget.FrameLayout"

    /** getActivityInfo 反查:确认 pkg/cls 确实是已注册的 Activity(非 Activity 窗口会抛异常) */
    private fun isRealActivity(pkg: String, cls: String): Boolean = runCatching {
        packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
        true // getActivityInfo 未抛异常即为已注册(返回值恒非 null)
    }.getOrDefault(false)

    /** 窗口切换(进入新页面 / 播放页):先驱动任务引擎,再留给子类扩展 */
    open fun onWindowChanged(packageName: String, className: String) {
        // 前台包名变化时通知引擎:离开的目标包清去重、停循环(下次进入重新执行)
        if (packageName != lastForegroundPkg) {
            val prev = lastForegroundPkg
            lastForegroundPkg = packageName
            if (prev != null) taskRunner.onLeftPackage(prev)
        }
        taskRunner.onActivityChanged(packageName, className)
    }

    /** 任务表变更后按当前前台页面重新评估触发(避免停留在目标页时保存的任务不响应) */
    fun refreshTaskTriggers() {
        val cn = lastActivity ?: return
        val pkg = cn.packageName ?: return
        lastForegroundPkg = pkg
        LogStore.log("🔁 任务表变更,按当前页面重新评估触发")
        taskRunner.onActivityChanged(pkg, cn.className)
    }

    /** 最近一次窗口事件的前台包名(onLeftPackage 检测用) */
    private var lastForegroundPkg: String? = null

    /** 内容变化(列表滚动、按钮状态更新) */
    open fun onContentChanged(packageName: String, className: String) {}

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
    fun findNodeByText(text: String): AccessibilityNodeInfo? = findNodeByText { it == text }

    /** 按文本查找节点(自定义匹配) */
    fun findNodeByText(matcher: (String) -> Boolean): AccessibilityNodeInfo? =
        findFirst { readableText(it)?.let(matcher) == true }

    /** 按 resourceId 查找节点 */
    fun findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? =
        findFirst { it.viewIdResourceName == resourceId }

    /** 按 className 查找节点 */
    fun findNodeByClassName(className: String): AccessibilityNodeInfo? =
        findFirst { it.className.toString() == className }

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

    /** 基础查找:遍历节点树,收集所有满足条件的节点 */
    fun findAll(predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        traverseNodes { node, _ ->
            if (predicate(node)) result += node
            false
        }
        return result
    }

    /** 通用查找节点:条件全部满足即返回第一个匹配节点 */
    fun findNode(
        text: String? = null,
        textContains: String? = null,
        resourceId: String? = null,
        className: String? = null,
        contentDesc: String? = null,
        clickable: Boolean? = null,
        extra: ((AccessibilityNodeInfo) -> Boolean)? = null,
    ): AccessibilityNodeInfo? = findFirst { node ->
        (text == null || readableText(node) == text) &&
            (textContains == null || readableText(node)?.contains(textContains) == true) &&
            (resourceId == null || node.viewIdResourceName == resourceId) &&
            (className == null || node.className.toString() == className) &&
            (contentDesc == null || node.contentDescription?.toString() == contentDesc) &&
            (clickable == null || node.isClickable == clickable) &&
            (extra == null || extra(node))
    }

    /** 收集所有可点击节点(定位按钮列表) */
    fun findAllClickableNodes(): List<AccessibilityNodeInfo> =
        findAll { it.isClickable }

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
