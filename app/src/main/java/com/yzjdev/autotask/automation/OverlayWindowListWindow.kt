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
 * 窗口列表悬浮窗:枚举无障碍服务当前可见的所有窗口,按应用名分组展示。
 *
 *  - 排除本应用自身的窗口(悬浮球/节点卡片/本卡片/一像素层),只列其他应用的窗口
 *  - 分组键 = 窗口根节点包名,组头显示应用名 + 包名 + 窗口数,组内保持窗口层级顺序
 *  - 每行显示 类型 / 层号 / 焦点激活标记 / 对应 Activity / 边界尺寸 / 根节点类名,点击复制该行文本
 *  - 刷新时机:显示时、点「刷新」、前台窗口切换(服务 TYPE_WINDOW_STATE_CHANGED 回调,去抖)
 *  - 整卡可拖动;子 View(关闭/刷新钮、列表滚动区)消费的触摸不参与拖动
 *  - TYPE_ACCESSIBILITY_OVERLAY:无需悬浮窗权限
 */
class OverlayWindowListWindow(private val service: AccessibilityService) {

    companion object {
        private const val DRAG_SLOP_PX = 12        // 拖动 vs 点击的位移阈值
        private const val REFRESH_DEBOUNCE_MS = 200L  // 前台切换期间的重排去抖
        private const val CARD_W_DP = 300
        private const val CARD_H_DP = 360
        private const val TAB_W_DP = 26         // 最小化贴边竖条
        private const val TAB_H_DP = 44
        private const val ACCENT = 0xFF22D3EE.toInt()      // 青色:与节点调试卡(琥珀)区分
        private const val ACCENT_DIM = 0x4022D3EE.toInt()
        private const val ACCENT_TEXT = 0xFF9AE8F5.toInt()
    }

    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 已注册 Activity 的查询入口(服务维护各应用最近确认过的真实 Activity) */
    private val a11yService: DramaAccessibilityService?
        get() = service as? DramaAccessibilityService

    private var cardView: LinearLayout? = null
    private var listView: LinearLayout? = null
    private var tabView: ImageView? = null   // 最小化后的贴边标签
    private var minimized = false

    private val lp by lazy {
        WindowManager.LayoutParams(
            dp(CARD_W_DP),
            dp(CARD_H_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
    }

    /** 最小化贴边标签:贴右边缘的竖条,点击恢复卡片 */
    private val tabLp by lazy {
        WindowManager.LayoutParams(
            dp(TAB_W_DP),
            dp(TAB_H_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }  // x=0 即贴住右边缘
    }

    private val refreshRunnable = Runnable { render() }

    /** 前台窗口切换:去抖后重排列表,避免切换动画期间反复重建视图 */
    private val windowListener: () -> Unit = {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    fun show() {
        mainHandler.post {
            if (cardView == null) {
                buildCard()
                (service as? DramaAccessibilityService)?.addWindowListener(windowListener)
            }
            setMinimized(false)
            render()
            DramaAccessibilityService.notifyWindowListShowing(true)
        }
    }

    fun hide() {
        mainHandler.post {
            mainHandler.removeCallbacks(refreshRunnable)
            (service as? DramaAccessibilityService)?.removeWindowListener(windowListener)
            a11yService?.hideWindowBounds()
            removeTab()
            cardView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            cardView = null
            listView = null
            minimized = false
            // 卡片内 ✕ 关闭:同步主页开关状态(与 OverlayDebugWindow 一致)
            DramaAccessibilityService.notifyWindowListShowing(false)
        }
    }

    val isShowing: Boolean
        get() = cardView != null

    // ---- 最小化(贴边) ----

    /** 最小化:卡片隐藏并置 NOT_TOUCHABLE,右边缘显示可点击的竖条标签 */
    private fun minimize() {
        if (cardView == null || minimized) return
        setMinimized(true)
    }

    /** 点贴边标签恢复卡片:同时关闭点选窗口时绘制的节点边界 */
    private fun restore() {
        if (!minimized) return
        a11yService?.hideWindowBounds()
        setMinimized(false)
    }

    private fun setMinimized(min: Boolean) {
        val card = cardView ?: return
        minimized = min
        // 卡片窗口即使内容 GONE 仍会拦截触摸,必须同步 FLAG_NOT_TOUCHABLE 才能穿透
        card.visibility = if (min) View.GONE else View.VISIBLE
        lp.flags = if (min) {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        try { wm.updateViewLayout(card, lp) } catch (_: Exception) {}
        if (min) showTab() else removeTab()
    }

    /** 贴边标签:右边缘竖条,垂直位置与卡片对齐 */
    private fun showTab() {
        if (tabView != null) return
        val tab = ImageView(service).apply {
            setImageResource(R.drawable.ic_overlay_list)
            setColorFilter(ACCENT_TEXT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(0xE60A1418.toInt())
                setStroke(dp(1), ACCENT_DIM)
            }
            setOnClickListener { restore() }
        }
        tabLp.y = clamp(lp.y, -(screenH - dp(TAB_H_DP)) / 2, (screenH - dp(TAB_H_DP)) / 2)
        tabView = tab
        try { wm.addView(tab, tabLp) } catch (_: Exception) { tabView = null }
    }

    private fun removeTab() {
        tabView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        tabView = null
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
            addView(iconButton(R.drawable.ic_overlay_minimize) { minimize() })
            addView(iconButton(R.drawable.ic_overlay_refresh) { render() })
            addView(iconButton(R.drawable.ic_overlay_close) { hide() })
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

    /** 重新枚举窗口并按应用分组重建列表(排除本应用自身的窗口) */
    private fun render() {
        val container = listView ?: return
        container.removeAllViews()

        // 排除本应用窗口:悬浮球、节点卡片、本卡片、一像素层都会出现在 service.windows 里,
        // 不排除会把自己框进列表,干扰对其他应用窗口的观察
        val self = service.packageName
        val all = runCatching { service.windows }.getOrNull().orEmpty()
        // LinkedHashMap:保持 service.windows 自上而下的层级顺序,组按首次出现次序排列
        val groups = LinkedHashMap<String, MutableList<AccessibilityWindowInfo>>()
        for (w in all) {
            val pkg = runCatching { w.root?.packageName?.toString() }.getOrNull().orEmpty()
            if (pkg == self) continue
            groups.getOrPut(pkg) { mutableListOf() }.add(w)
        }
        if (groups.isEmpty()) {
            container.addView(hint(
                if (all.isEmpty()) "未获取到窗口\n请确认无障碍服务已连接"
                else "除本应用外无其他窗口"
            ))
            return
        }

        var index = 0
        for ((pkg, list) in groups) {
            container.addView(groupHeader(pkg, list.size))
            for (w in list) container.addView(windowRow(++index, w, pkg))
        }
    }

    /** 组头:应用名 + 窗口数(第一行),包名(第二行) */
    private fun groupHeader(pkg: String, count: Int): View = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(10), dp(8), dp(4))
        addView(TextView(service).apply {
            text = if (pkg.isEmpty()) "未知来源  ·  $count" else "${appLabel(pkg)}  ·  $count"
            setTextColor(ACCENT_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (pkg.isNotEmpty()) {
            addView(TextView(service).apply {
                text = pkg
                setTextColor(0x80FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
    }

    /**
     * 单行窗口:第一行 id/类型/层号/状态标记,第二行 对应 Activity,第三行 尺寸坐标/根类名;
     * 点击绘制该窗口的节点边界并最小化贴边,长按复制三行文本
     */
    private fun windowRow(index: Int, w: AccessibilityWindowInfo, pkg: String): View {
        val rect = Rect().also { runCatching { w.getBoundsInScreen(it) } }
        val marks = buildList {
            if (w.isFocused) add("焦点")
            if (w.isActive) add("激活")
            if (w.isAccessibilityFocused) add("无障碍焦点")
        }.joinToString(" ")
        val rootCls = runCatching { w.root?.className?.toString() }
            .getOrNull()?.substringAfterLast('.')
        val head = "#$index  ${typeName(w.type)}  layer ${w.layer}" +
            if (marks.isEmpty()) "" else "  $marks"
        val activity = windowActivity(w, pkg)
        val activityText = activity ?: "Activity 未记录"
        val tail = buildString {
            append("${rect.width()}x${rect.height()} @(${rect.left},${rect.top})")
            if (!rootCls.isNullOrEmpty()) append("  $rootCls")
        }
        val copyText = "$head\n$activityText\n$tail"

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            isClickable = true
            // 点击:绘制该窗口节点边界,卡片自动最小化贴边(点贴边标签恢复时关闭绘制)
            setOnClickListener {
                if (a11yService?.showWindowBounds(w) == true) minimize()
            }
            // 长按:复制该行信息
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
                text = activityText
                setTextColor(if (activity != null) ACCENT_TEXT else 0x66FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            })
            addView(TextView(service).apply {
                text = tail
                setTextColor(0x99FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.MONOSPACE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
    }

    /**
     * 窗口对应的 Activity:
     *  - 应用窗口的 title 即 Activity 的 flattenToShortString("pkg/.MainActivity"),直接采用
     *  - title 不是组件形式(对话框/系统窗口)时,回退到服务记录的各应用已注册 Activity
     */
    private fun windowActivity(w: AccessibilityWindowInfo, pkg: String): String? {
        val title = runCatching { w.title?.toString()?.trim() }.getOrNull().orEmpty()
        if (title.contains('/')) return title
        val cn = a11yService?.getValidActivity(pkg) ?: return null
        return "${cn.packageName}/${cn.shortClassName}"
    }

    private fun hint(text: String): TextView = TextView(service).apply {
        this.text = text
        setTextColor(0x99FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setPadding(dp(8), dp(16), dp(8), dp(16))
    }

    /** 窗口类型名:对齐 AccessibilityWindowInfo 常量 */
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
