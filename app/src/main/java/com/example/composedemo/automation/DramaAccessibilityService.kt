package com.example.composedemo.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
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
        taskRunner.setTasks(TaskStore.loadAll(this))
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
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        taskRunner.cancelAll()
        debugOverlay.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        sInstance = null
        clearProcessed()
        notifyRunning(false)
        taskRunner.cancelAll()
        debugOverlay.hide()
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
        // 忽略自己 App 内的事件,避免触发循环
        val packageName = event.packageName?.toString()
        if (packageName == this.packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                // 过滤非 Activity 窗口(Dialog/PopupWindow/输入法等,多为 android.widget.*
                // 或 android.app.Dialog 前缀):通过前缀检查的 className 视为当前 Activity
                if (packageName != null && isActivityClassName(className)) {
                    lastActivity = ComponentName(packageName, className)
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

    /** className 前缀过滤:排除 android.widget.* / android.app.* 等系统窗口类 */
    private fun isActivityClassName(cls: String): Boolean =
        cls.isNotEmpty() &&
            !cls.startsWith("android.widget") &&
            !cls.startsWith("android.app") &&
            !cls.startsWith("android.view") &&
            cls != "android.widget.FrameLayout"

    /** 窗口切换(进入新页面 / 播放页):先驱动规则引擎,再留给子类扩展 */
    open fun onWindowChanged(packageName: String, className: String) {
        taskRunner.onActivityChanged(packageName, className)
    }

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
    fun findNodeByText(matcher: (String) -> Boolean): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        traverseNodes { node, _ ->
            val text = readableText(node)
            if (text != null && matcher(text)) {
                result = node
                true
            } else false
        }
        return result
    }

    /** 按 resourceId 查找节点 */
    fun findNodeByResourceId(resourceId: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        traverseNodes { node, _ ->
            if (node.viewIdResourceName == resourceId) {
                result = node
                true
            } else false
        }
        return result
    }

    /** 按 className 查找节点 */
    fun findNodeByClassName(className: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        traverseNodes { node, _ ->
            if (node.className.toString() == className) {
                result = node
                true
            } else false
        }
        return result
    }

    /** 收集所有可点击节点(定位按钮列表) */
    fun findAllClickableNodes(): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        traverseNodes { node, _ ->
            if (node.isClickable) result += node
            false
        }
        return result
    }

    /** 读取节点可读文本(优先 contentDescription,其次 text) */
    fun readableText(node: AccessibilityNodeInfo): String? =
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: node.text?.toString()?.takeIf { it.isNotBlank() }

    // ---- 动作 ----

    /** 点击节点 */
    fun clickNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

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

    /** 在屏幕中心模拟点击 */
    fun clickAtCenter() {
        val (width, height) = screenSize() ?: return
        clickAt(width / 2f, height / 2f)
    }

    /** 上滑(翻页 / 切下一集) */
    fun swipeUp() {
        val (width, height) = screenSize() ?: return
        gesture(
            width / 2f, height * 0.75f,
            width / 2f, height * 0.25f,
            350L,
        )
    }

    /** 下滑 */
    fun swipeDown() {
        val (width, height) = screenSize() ?: return
        gesture(
            width / 2f, height * 0.25f,
            width / 2f, height * 0.75f,
            350L,
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

    private fun isProcessed(node: AccessibilityNodeInfo): Boolean {
        synchronized(processedLock) {
            if (processedNodes.contains(node)) return true
            processedNodes.add(node)
            return false
        }
    }

    private fun recycleSafely(node: AccessibilityNodeInfo) {
        try {
            node.recycle()
        } catch (_: Throwable) {
            // 节点可能已被系统回收
        }
    }
}
