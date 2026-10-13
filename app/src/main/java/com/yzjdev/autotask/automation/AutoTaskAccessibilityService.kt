package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.HashSet
import java.util.concurrent.CopyOnWriteArrayList
import com.yzjdev.autotask.automation.log.AppLog
import com.yzjdev.autotask.automation.log.LogSource

/**
 * 窗口类别:在无障碍的窗口类型之上再细分 ——
 * 系统窗口里分出通知栏与导航栏,应用窗口里分出桌面,列表与头部才能一眼分辨。
 */
enum class WindowKind(val label: String) {
    Launcher("桌面"),
    StatusBar("通知栏"),
    NavigationBar("导航栏"),
    Application("应用"),
    InputMethod("输入法"),
    AccessibilityOverlay("无障碍悬浮"),
    SplitScreenDivider("分屏分割"),
    System("系统"),
    Other("其他"),
}

/**
 * 无障碍服务:提供界面节点读取、控件定位、模拟点击与手势。
 *
 * 典型用法:
 * ```kotlin
 * val service = AutoTaskAccessibilityService.instance ?: return
 * service.clickByText("播放")
 * service.findNodeByText("下一集")?.let { service.clickNode(it) }
 * service.swipeUp()
 * ```
 *
 * 服务需在系统「无障碍」中开启;拥有「写入安全设置」权限时也可由自动化层
 * 直接写入 [android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] 完成开关。
 */
open class AutoTaskAccessibilityService : AccessibilityService() {

    companion object {
        private val MAIN = Handler(Looper.getMainLooper())

        /** 节点树打印的 logcat 标签 */
        private const val TAG = "AutoTaskTree"

        @Volatile
        private var sInstance: AutoTaskAccessibilityService? = null

        /** 当前服务实例;未开启时为 null */
        val instance: AutoTaskAccessibilityService?
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
    }

    /** 单次遍历内已处理节点,避免重复处理 */
    private val processedNodes = HashSet<AccessibilityNodeInfo>()
    private val processedLock = Any()

    // 无障碍调试悬浮窗:由主页开关控制显隐,连接后不自动显示
    private val debugOverlay = OverlayDebugWindow(this)

    // 窗口列表悬浮窗:由悬浮球点击唤起,枚举当前所有窗口并支持点选绘制
    private val windowListOverlay = OverlayWindowListWindow(this) { debugOverlay.closeWindowList() }

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        sInstance = this
        clearProcessed()
        notifyRunning(true)
        showPixelOverlay()
        AppLog.success(LogSource.Service, "无障碍服务已连接")
    }

    /** 调试悬浮球当前是否显示 */
    val isDebugOverlayShowing: Boolean
        get() = debugOverlay.isShowing

    /** 显示/隐藏调试悬浮球(由主页开关调用);服务未连接时返回 false */
    fun showDebugOverlay(show: Boolean): Boolean {
        val overlay = debugOverlay
        if (show) {
            overlay.show()
        } else {
            overlay.hide()
            // 悬浮球已隐藏,窗口列表失去入口:一并隐藏(窗口保留,重开时只切显隐)
            windowListOverlay.hide()
        }
        AppLog.info(LogSource.Overlay, if (show) "节点悬浮窗已开启" else "节点悬浮窗已关闭")
        notifyOverlayShowing(show && sInstance != null)
        return sInstance != null
    }

    /** 预加入窗口列表悬浮窗(隐藏态):由调试悬浮窗在信息卡之后、悬浮球之前调用,保证层级 */
    fun prepareWindowListOverlay() {
        windowListOverlay.prepare()
    }

    /**
     * 窗口列表显隐:由调试悬浮窗的面板状态机统一驱动(唯一入口)。
     * 显隐的真正决策在状态机里,这里只负责把结果落到窗口上。
     */
    fun setWindowListVisible(visible: Boolean) {
        if (visible) {
            AppLog.info(LogSource.Overlay, "窗口列表已打开")
            windowListOverlay.show()
        } else {
            windowListOverlay.hide()
        }
    }

    /**
     * 窗口列表点选某窗口:取最新窗口实例再绘制(需主线程调用)。
     * 枚举列表时的窗口引用可能已失效 —— 输入法窗口每次弹出都可能被系统重建(id 变化),
     * 过期引用的 root 会返回 null,表现为"无法获取输入法节点信息"。
     * 匹配顺序:同 id → 同类型+同包名 → 同类型(输入法等系统窗口同类型唯一)→ 原引用。
     */
    fun showWindowBounds(window: AccessibilityWindowInfo): Boolean {
        val fresh = freshWindowById(window.id) ?: freshWindowLike(window) ?: window
        return debugOverlay.showBoundsFor(fresh)
    }

    /** 按窗口 id 在全部显示器中找最新的窗口实例;找不到返回 null */
    private fun freshWindowById(id: Int): AccessibilityWindowInfo? =
        allWindows().firstOrNull { it.id == id }

    /**
     * id 失配(窗口已被系统重建)时按 类型+包名 找最新窗口实例。
     * 输入法同屏只有一个,按类型即可命中;应用窗口再用包名二次确认防误匹配。
     */
    private fun freshWindowLike(window: AccessibilityWindowInfo): AccessibilityWindowInfo? {
        val all = allWindows()
        val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
        return all.firstOrNull {
            it.type == window.type &&
                (pkg == null || runCatching { it.root?.packageName?.toString() }.getOrNull() == pkg)
        } ?: all.firstOrNull { it.type == window.type }
    }

    /** 全部显示器上的窗口(merged);不可用时退回默认显示器 */
    private fun allWindows(): List<AccessibilityWindowInfo> {
        runCatching { windowsOnAllDisplays }.getOrNull()?.let { perDisplay ->
            val out = ArrayList<AccessibilityWindowInfo>()
            for (i in 0 until perDisplay.size()) out += perDisplay.valueAt(i).orEmpty()
            if (out.isNotEmpty()) return out
        }
        return runCatching { windows }.getOrNull().orEmpty()
    }

    /** 指定窗口对应的 Activity:title 是组件形式直接采用,否则回退该包最近确认过的 Activity */
    fun activityOfWindow(window: AccessibilityWindowInfo): ComponentName? {
        val title = runCatching { window.title?.toString()?.trim() }.getOrNull().orEmpty()
        if (title.contains('/')) {
            ComponentName.unflattenFromString(title)?.let { return it }
        }
        val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull().orEmpty()
        if (pkg.isEmpty()) return null
        return getValidActivity(pkg)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        debugOverlay.hide()
        windowListOverlay.release()
        hidePixelOverlay()
        AppLog.warn(LogSource.Service, "无障碍服务已断开")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        debugOverlay.hide()
        windowListOverlay.release()
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

    private val accessibilityEventListeners = CopyOnWriteArrayList<(Int, String?) -> Unit>()

    /**
     * 订阅全部无障碍事件。
     *
     * 只把事件类型与来源包名传出去 —— [AccessibilityEvent] 用完会被系统回收,
     * 交到调用方手里就是无效对象。
     */
    fun addAccessibilityEventListener(listener: (eventType: Int, packageName: String?) -> Unit) {
        accessibilityEventListeners.add(listener)
    }

    fun removeAccessibilityEventListener(listener: (eventType: Int, packageName: String?) -> Unit) {
        accessibilityEventListeners.remove(listener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                // 真实 Activity 才记录并回填:非真实 Activity(Dialog/输入法等)沿用
                // 该应用最近一次有效 Activity 名,保证悬浮窗标题不因窗口类名失效
                if (packageName != null && isActivityClassName(className) && isRealActivity(packageName, className)) {
                    val cn = ComponentName(packageName, className)
                    lastActivity = cn
                    recordValidActivity(cn)
                }
            }

            else -> Unit
        }

        // 订阅方(规则引擎等)只拿事件类型与包名
        if (accessibilityEventListeners.isNotEmpty()) {
            val type = event.eventType
            for (listener in accessibilityEventListeners) {
                runCatching { listener(type, packageName) }
            }
        }
    }

    /**
     * 最近一次确认的真实 Activity(供调试悬浮窗标题等使用)
     */
    @Volatile
    var lastActivity: ComponentName? = null
        private set

    /** 各应用最近一次确认的真实 Activity(非 Activity 窗口事件时按包名回填) */
    private val lastValidActivityByApp = HashMap<String, ComponentName>()

    private fun recordValidActivity(cn: ComponentName) {
        synchronized(lastValidActivityByApp) {
            lastValidActivityByApp[cn.packageName ?: ""] = cn
        }
    }

    fun getValidActivity(pkg: String): ComponentName? = synchronized(lastValidActivityByApp) {
        lastValidActivityByApp[pkg]
    }

    /** 桌面(Launcher)包名:用于把桌面从普通应用窗口里分出来;解析失败为 null */
    val homePackage: String? by lazy {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()
    }

    /**
     * 窗口类别细分:
     *  - 应用窗口按 Launcher 包名分出「桌面」;
     *  - 系统窗口先按根节点类名分出「通知栏 / 导航栏」,类名读不到时按贴边位置兜底。
     */
    fun windowKind(window: AccessibilityWindowInfo): WindowKind {
        val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull().orEmpty()
        if (pkg.isNotEmpty() && pkg == homePackage) return WindowKind.Launcher
        return when (window.type) {
            AccessibilityWindowInfo.TYPE_SYSTEM -> systemBarKind(window)
            AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.Application
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.InputMethod
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowKind.AccessibilityOverlay
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> WindowKind.SplitScreenDivider
            else -> WindowKind.Other
        }
    }

    /** 系统窗口里区分通知栏与导航栏:类名优先,读不到时按「贴顶/贴底且矮」兜底 */
    private fun systemBarKind(window: AccessibilityWindowInfo): WindowKind {
        val cls = runCatching { window.root?.className?.toString() }.getOrNull()
            .orEmpty().lowercase()
        if (cls.contains("statusbar") || cls.contains("status_bar")) return WindowKind.StatusBar
        if (cls.contains("navigationbar") || cls.contains("navigation_bar")) {
            return WindowKind.NavigationBar
        }
        val rect = android.graphics.Rect().also { runCatching { window.getBoundsInScreen(it) } }
        val screenH = resources.displayMetrics.heightPixels
        val maxBarHeight = (screenH / 8).coerceAtLeast(1)
        if (rect.height() in 1..maxBarHeight) {
            if (rect.top <= 0) return WindowKind.StatusBar
            if (rect.bottom >= screenH) return WindowKind.NavigationBar
        }
        return WindowKind.System
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

    /** 最近一次窗口状态变化时间:动作之间靠它判断页面是否已安静下来 */
    @Volatile
    var lastWindowChangeAt: Long = 0L
        private set

    private fun notifyWindowChanged() {
        lastWindowChangeAt = System.currentTimeMillis()
    }

    /** className 前缀过滤:排除 android.widget.* / android.app.* 等系统窗口类 */
    private fun isActivityClassName(cls: String): Boolean =
        cls.isNotEmpty() &&
            !cls.startsWith("android.widget") &&
            !cls.startsWith("android.app") &&
            !cls.startsWith("android.view") &&
            cls != "android.widget.FrameLayout"

    /** getActivityInfo 反查结果 LRU 缓存:避免高频事件下重复 binder 反查 */
    private val activityCache = android.util.LruCache<Pair<String, String>, Boolean>(256)

    /** getActivityInfo 反查:确认 pkg/cls 确实是已注册的 Activity(非 Activity 窗口会抛异常) */
    private fun isRealActivity(pkg: String, cls: String): Boolean = activityCache.get(pkg to cls)
        ?: runCatching {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true // getActivityInfo 未抛异常即为已注册(返回值恒非 null)
        }.getOrDefault(false).also { activityCache.put(pkg to cls, it) }

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

    // ---- 内置规则接口 ----

    /**
     * 当前窗口是否存在满足 [predicate] 的节点。
     *
     * 刻意不复用 [findFirst]/[traverseNodes]:那条路径命中后会把节点直接 recycle(),
     * 且 [processedNodes] 去重集合只在服务连接时清空 —— 规则要反复检测,复用它会第二次就查不到东西。
     * 这里独立遍历,命中节点不回收(用完即弃)。
     */
    fun hasNode(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        var found = false
        walkNodes { node ->
            if (predicate(node)) {
                found = true
                true
            } else {
                false
            }
        }
        return found
    }

    /** 干净的深度优先遍历:独立于 findFirst 的去重与回收,onNode 返回 true 停止 */
    private fun walkNodes(onNode: (AccessibilityNodeInfo) -> Boolean) {
        val root = rootInActiveWindow ?: return
        walkNode(root, onNode)
    }

    private fun walkNode(
        node: AccessibilityNodeInfo,
        onNode: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        if (onNode(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (walkNode(child, onNode)) return true
        }
        return false
    }

    /**
     * 以树形结构打印当前窗口的节点树,逐行写入 logcat([TAG]),并返回整棵树文本。
     *
     * 独立递归,刻意不复用 [traverseNodes]/[findFirst]:那条路径命中即 recycle 且带去重集合,
     * 整树输出会第二次就查不到东西。遍历目标与节点快照一致 —— 优先「焦点且激活」的窗口,
     * 退回活动窗口。缩进用 `├─`/`└─` 连接线,每行形如:
     * `类名#vid "文本" desc:"描述" [可点击] @(x,y,w×h)`。
     * 不限制递归深度,整棵子树全部展开。
     */
    fun printNodeTree(): String {
        val root = focusedWindow()?.root ?: rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        appendNodeLine(root, "", true, 0, sb)
        val text = sb.toString().trimEnd('\n')
        text.lineSequence().forEach { Log.d(TAG, it) }
        return text
    }

    private fun appendNodeLine(
        node: AccessibilityNodeInfo,
        prefix: String,
        isLast: Boolean,
        depth: Int,
        sb: StringBuilder,
    ) {
        sb.append(prefix)
        // 根节点不带连接线,避免树顶出现孤立的拐角
        if (depth > 0) sb.append(if (isLast) "└─ " else "├─ ")
        sb.append(nodeSummary(node)).append('\n')
        // 先收集有效子节点:getChild 可能返回 null,按 childCount 算「最后一个」会错位
        val children = (0 until node.childCount).mapNotNull { node.getChild(it) }
        val childPrefix = if (depth == 0) "" else prefix + if (isLast) "   " else "│  "
        children.forEachIndexed { i, child ->
            appendNodeLine(child, childPrefix, i == children.size - 1, depth + 1, sb)
        }
    }

    /** 单行摘要:类名#vid "文本" desc:"描述" [可点击] @(x,y,w×h) */
    private fun nodeSummary(node: AccessibilityNodeInfo): String {
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val vid = node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        return buildString {
            append(cls)
            if (vid != null) append("#$vid")
            if (text != null) append(" \"$text\"")
            if (desc != null) append(" desc:\"$desc\"")
            if (node.isClickable) append(" [可点击]")
            append(" @(${rect.left},${rect.top},${rect.width()}×${rect.height()})")
        }
    }

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
