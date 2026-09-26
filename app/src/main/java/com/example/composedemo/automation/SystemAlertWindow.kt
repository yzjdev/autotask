package com.example.composedemo.automation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 系统 Alert 悬浮窗:TYPE_APPLICATION_OVERLAY 的普通界面面板,需要
 * 「显示在其他应用上层」权限。
 *
 *  - 无需无障碍服务,任意持有 Context 的地方即可显示/隐藏
 *  - 面板含标题栏(标题 + 关闭按钮)与正文内容,风格与节点检查悬浮窗的信息卡片一致
 *  - 按住标题栏可整屏拖动
 *  - 点击关闭按钮触发 [onDismiss] 回调,由持有方同步开关状态
 *  - 显示前请先用 [canDraw] 确认已授权,否则 addView 会抛 BadTokenException
 */
class SystemAlertWindow(
    private val context: Context,
    private val onDismiss: () -> Unit,
) {

    companion object {
        private const val PANEL_WIDTH_DP = 240   // 面板固定宽度
        private const val TITLE_TEXT = "系统悬浮窗"
        private const val BODY_TEXT = "这是一个系统 Alert 悬浮窗面板,\n按住标题栏可拖动。"

        /** 是否已授予「显示在其他应用上层」权限 */
        fun canDraw(context: Context): Boolean =
            Settings.canDrawOverlays(context)
    }

    private val wm by lazy { context.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var panelView: LinearLayout? = null

    private val lp by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    fun show() {
        mainHandler.post {
            if (panelView != null) return@post
            if (!canDraw(context)) return@post

            val title = TextView(context).apply {
                text = TITLE_TEXT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
            }

            val close = circleButton("✕") {
                hide()  // 先移除面板窗口,再通知持有方同步开关状态
                onDismiss()
            }

            val titleBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(close)
            }

            val body = TextView(context).apply {
                text = BODY_TEXT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFFDDDDDD.toInt())
                setPadding(dp(12), 0, dp(12), dp(12))
            }

            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0xF5222222.toInt())  // 近不透明深底,减少透出内容干扰
                }
                addView(titleBar)
                addView(body)
            }
            panelView = panel

            // 按住标题栏拖动整个面板
            attachDragHandler(titleBar)
            wm.addView(panel, lp)
        }
    }

    fun hide() {
        mainHandler.post {
            panelView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            panelView = null
        }
    }

    val isShowing: Boolean
        get() = panelView != null

    /** 按住标题栏拖动:MOVE 超阈值即拖动并消费事件 */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(handle: LinearLayout) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false
        handle.setOnTouchListener { _, e ->
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
                        lp.x = startX + dx.toInt()
                        lp.y = clamp(startY + dy.toInt(), -screenH, screenH)
                        wm.updateViewLayout(panelView, lp)
                    }
                    dragging
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

    /** 圆形图标按钮(与节点检查悬浮窗同风格):半透明圆底 + 白色图标 */
    private fun circleButton(icon: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener { onClick() }
        }

    private val DRAG_SLOP_PX = 12

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()

    private val screenH: Int get() = context.resources.displayMetrics.heightPixels

    private fun clamp(v: Int, min: Int, max: Int): Int = v.coerceIn(min, max.coerceAtLeast(min))
}
