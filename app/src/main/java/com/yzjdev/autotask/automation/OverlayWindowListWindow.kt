package com.yzjdev.autotask.automation

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yzjdev.autotask.R

/**
 * 窗口列表悬浮窗:枚举无障碍服务当前可见的所有窗口,一行一个窗口按层级顺序展示。
 *
 *  - 含本应用自身的窗口(悬浮球/节点卡片/本卡片/一像素层),便于对照自身层级
 *  - 每行三行文本:序号+应用名+类型+层级+状态标记 / 包名+Activity / 窗口 id+边界+根类名
 *  - 点击某行绘制该窗口的节点边界并最小化贴边,长按复制该行完整信息
 *  - 刷新时机:显示时、点「刷新」、前台窗口切换(服务 TYPE_WINDOW_STATE_CHANGED 回调,去抖)
 *  - 整卡可拖动;子 View(关闭/刷新钮、列表滚动区)消费的触摸不参与拖动
 *  - 最小化后的贴边竖条可拖动,松手自动吸附到最近的左/右边缘
 *  - TYPE_ACCESSIBILITY_OVERLAY:无需悬浮窗权限
 */
class OverlayWindowListWindow(
    private val service: AccessibilityService,
    /** 点 ✕ 关闭时回调:交给调试悬浮窗的面板状态机统一收起 */
    private val onClose: () -> Unit = {},
) {

    companion object {
        private const val DRAG_SLOP_PX = 12        // 拖动 vs 点击的位移阈值
        private const val CARD_W_DP = 300
        private const val CARD_H_DP = 360
        private const val ACCENT_DIM = 0x4022D3EE.toInt()
        private const val ACCENT_TEXT = 0xFF9AE8F5.toInt()
    }

    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 已注册 Activity 的查询入口(服务维护各应用最近确认过的真实 Activity) */
    private val a11yService: AutoTaskAccessibilityService?
        get() = service as? AutoTaskAccessibilityService

    private var cardView: LinearLayout? = null
    private var listView: LinearLayout? = null

    private val lp by lazy {
        WindowManager.LayoutParams(
            dp(CARD_W_DP),
            dp(CARD_H_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            alpha = OVERLAY_ALPHA
        }
    }

    /**
     * 预加入:构建卡片窗口并加入(隐藏态 + 触摸穿透),同步执行(调用方保证在主线程)。
     * 只在调试悬浮窗 show() 时与其他层一起调用一次,之后显隐只切 visibility/flags。
     */
    fun prepare() {
        ensureCard()
    }

    /** 由面板状态机调用:显示列表并重排内容 */
    fun show() {
        mainHandler.post {
            ensureCard()
            applyShown()
            render()
        }
    }

    /** 由面板状态机调用:收起列表(仅切显隐,窗口保留) */
    fun hide() {
        mainHandler.post { applyHidden() }
    }

    /** 彻底移除窗口:服务解绑/销毁时调用,释放已预加入的卡片 */
    fun release() {
        mainHandler.post {
            cardView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            cardView = null
            listView = null
        }
    }

    /** 构建并加入卡片窗口(仅一次);预加入与首次显示都走这里 */
    private fun ensureCard() {
        if (cardView != null) return
        buildCard()
        applyHidden()
    }

    /** 隐藏态:内容 GONE + FLAG_NOT_TOUCHABLE(卡片窗口即使内容 GONE 仍会拦截触摸) */
    private fun applyHidden() {
        val card = cardView ?: return
        card.visibility = View.GONE
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(card, lp) } catch (_: Exception) {}
    }

    /** 显示态:与 [applyHidden] 对应,切回 VISIBLE 并去掉触摸穿透 */
    private fun applyShown() {
        val card = cardView ?: return
        card.visibility = View.VISIBLE
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { wm.updateViewLayout(card, lp) } catch (_: Exception) {}
    }

    // ---- UI 构建 ----

    private fun buildCard() {
        val title = TextView(service).apply {
            text = "窗口列表"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        // 头部:标题(占满剩余宽度) + 刷新 / 关闭
        val headerRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(iconButton(R.drawable.ic_overlay_refresh) { render() })
            addView(iconButton(R.drawable.ic_overlay_close) { onClose() })
        }

        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        listView = list

        val scroll = ScrollView(service).apply {
            isVerticalScrollBarEnabled = true
            addView(list)
        }

        val divider = View(service).apply { setBackgroundColor(0x2622D3EE) }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(0xF5081418.toInt())      // 青夜底
                setStroke(dp(1), ACCENT_DIM)
            }
            addView(headerRow)
            addView(divider, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        cardView = card
        attachDrag(card)
        wm.addView(card, lp)
    }

    /** 头部小圆钮:青色圆底 + 矢量图标(图标在 24dp 视口内几何居中,避免字体符号偏心) */
    private fun iconButton(iconRes: Int, onClick: () -> Unit): ImageView = ImageView(service).apply {
        setImageResource(iconRes)
        setColorFilter(ACCENT_TEXT)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x1F22D3EE)
        }
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    // ---- 列表渲染 ----

    /** 重新枚举窗口并按层级顺序重建列表:一行一个窗口(已过滤本应用自身的窗口) */
    private fun render() {
        val container = listView ?: return
        container.removeAllViews()

        // 过滤本应用自身的窗口(悬浮球、节点卡片、本卡片、一像素层)。
        // getWindows() 只返回默认显示器的窗口,系统小窗/自由窗口在部分 ROM 上跑在虚拟显示器,
        // 整个会漏掉;故合并全部显示器的窗口,显示器编号直接并入行内文本
        val selfPkg = service.packageName
        val windows = windowEntries().filter { (_, w) ->
            runCatching { w.root?.packageName?.toString() }.getOrNull() != selfPkg
        }
        if (windows.isEmpty()) {
            container.addView(hint("未获取到窗口\n请确认无障碍服务已连接"))
            return
        }

        windows.forEachIndexed { i, (displayId, w) ->
            container.addView(windowRow(i + 1, w, displayId))
        }
    }

    /**
     * 全部显示器上的窗口(默认显示器在前),元素为 显示器 id 到窗口。
     * getWindowsOnAllDisplays 不可用时退回 getWindows(仅默认显示器)
     */
    private fun windowEntries(): List<Pair<Int, AccessibilityWindowInfo>> {
        val perDisplay = runCatching { service.windowsOnAllDisplays }.getOrNull()
        if (perDisplay != null && perDisplay.size() > 0) {
            val entries = ArrayList<Pair<Int, AccessibilityWindowInfo>>()
            for (i in 0 until perDisplay.size()) {
                val displayId = perDisplay.keyAt(i)
                for (w in perDisplay.valueAt(i).orEmpty()) entries += displayId to w
            }
            if (entries.isNotEmpty()) return entries
        }
        return runCatching { service.windows }.getOrNull().orEmpty()
            .map { Display.DEFAULT_DISPLAY to it }
    }

    /**
     * 单行窗口:一行一个窗口,行内三行文本 ——
     * ① 序号 + 应用名 + 类型 + 层级 + 状态标记;② 包名 + Activity;③ 窗口 id + 边界 + 根类名。
     * 点击绘制该窗口的节点边界并最小化贴边,长按复制完整信息
     */
    private fun windowRow(index: Int, w: AccessibilityWindowInfo, displayId: Int): View {
        val pkg = runCatching { w.root?.packageName?.toString() }.getOrNull().orEmpty()
        val rect = Rect().also { runCatching { w.getBoundsInScreen(it) } }
        val marks = buildList {
            if (w.isFocused) add("焦点")
            if (w.isActive) add("激活")
            if (w.isAccessibilityFocused) add("无障碍焦点")
        }.joinToString(" ")
        val rootCls = runCatching { w.root?.className?.toString() }
            .getOrNull()?.substringAfterLast('.')
        val activity = windowActivity(w, pkg)
        val name = if (pkg.isEmpty()) "未知来源" else appLabel(pkg)

        val head = buildString {
            append("#").append(index).append("  ").append(name)
            append("  ").append(windowKind(w))
            append("  L").append(w.layer)
            if (marks.isNotEmpty()) append("  ").append(marks)
            if (displayId != Display.DEFAULT_DISPLAY) append("  D").append(displayId)
        }
        val activityShort = activity?.substringAfterLast('/')
        val detail = buildString {
            append(pkg.ifEmpty { "未知来源" })
            if (!activityShort.isNullOrEmpty()) append("  ").append(activityShort)
        }
        val geometry = buildString {
            append("id=").append(w.id)
            append("  ").append(rect.width()).append('x').append(rect.height())
            append(" @(").append(rect.left).append(',').append(rect.top).append(')')
            if (!rootCls.isNullOrEmpty()) append("  ").append(rootCls)
        }
        val copyText = "$head\n$detail\n${activity ?: "Activity 未记录"}\n$geometry"

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isClickable = true
            // 点击:绘制该窗口节点边界;读不到节点时给出提示(窗口已被系统重建且匹配失败等)
            setOnClickListener {
                if (a11yService?.showWindowBounds(w) != true) {
                    android.widget.Toast.makeText(
                        service, "无法读取该窗口的节点内容", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            // 长按:复制完整信息
            setOnLongClickListener {
                copyToClipboard(copyText)
                it.animate().alpha(0.4f).setDuration(80)
                    .withEndAction { it.animate().alpha(1f).setDuration(120) }
                true
            }
            addView(TextView(service).apply {
                text = head
                setTextColor(0xE6FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(service).apply {
                text = detail
                setTextColor(if (activityShort.isNullOrEmpty()) 0x80FFFFFF.toInt() else ACCENT_TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            })
            addView(TextView(service).apply {
                text = geometry
                setTextColor(0x99FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
    }

    /**
     * 窗口对应的 Activity:解析规则统一在服务端(activityOfWindow),
     * 避免节点悬浮窗与窗口列表各写一套。
     */
    private fun windowActivity(w: AccessibilityWindowInfo, pkg: String): String? {
        val cn = a11yService?.activityOfWindow(w) ?: return null
        if (cn.packageName != pkg) return null
        return "${cn.packageName}/${cn.shortClassName}"
    }

    private fun hint(text: String): TextView = TextView(service).apply {
        this.text = text
        setTextColor(0x99FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setPadding(dp(8), dp(16), dp(8), dp(16))
    }

    /** 窗口类别名:细分规则统一在服务端(windowKind),通知栏/导航栏/桌面都在那里分出 */
    private fun windowKind(w: AccessibilityWindowInfo): String =
        a11yService?.windowKind(w)?.label ?: typeName(w.type)

    /** 服务不可用时的类型名兜底:对齐 AccessibilityWindowInfo 常量 */
    private fun typeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "应用"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "输入法"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "系统"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "无障碍悬浮"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "分屏分割"
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "放大镜"
        else -> "其他($type)"
    }

    private fun copyToClipboard(text: String) {
        val cm = service.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("window", text))
        android.widget.Toast.makeText(service, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    }

    // ---- 拖动 ----

    /** 整卡拖动:子 View(按钮、滚动区)消费自己的 DOWN,事件不会冒泡到这里 */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(card: LinearLayout) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || dx * dx + dy * dy > DRAG_SLOP_PX * DRAG_SLOP_PX) {
                        dragging = true
                        // gravity=CENTER 时 x/y 为相对屏幕中心的偏移,clamp 到半屏范围
                        lp.x = clamp(startX + dx.toInt(),
                            -(screenW - v.width) / 2, (screenW - v.width) / 2)
                        lp.y = clamp(startY + dy.toInt(),
                            -(screenH - v.height) / 2, (screenH - v.height) / 2)
                        wm.updateViewLayout(v, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    dragging = false
                    wasDragging
                }
                else -> dragging
            }
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics
    ).toInt()

    private val screenW: Int get() = service.resources.displayMetrics.widthPixels
    private val screenH: Int get() = service.resources.displayMetrics.heightPixels

    private fun clamp(v: Int, min: Int, max: Int): Int = v.coerceIn(min, max.coerceAtLeast(min))

    private fun appLabel(pkg: String): String = runCatching {
        val pm = service.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai).toString()
    }.getOrDefault(pkg)
}
