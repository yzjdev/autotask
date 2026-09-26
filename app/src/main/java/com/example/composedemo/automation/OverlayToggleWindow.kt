package com.example.composedemo.automation

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 节点调试悬浮窗的开关悬浮球:独立的圆形按钮 UI。
 *
 *  - 显示十字准星(未开启)/ 叉号(抓取中)两种状态图标
 *  - 可整屏拖动(带拖动 vs 点击的位移阈值判定)
 *  - 点击触发 [onToggle] 回调,开关状态由持有方维护,通过 [setActive] 回填图标
 *  - TYPE_ACCESSIBILITY_OVERLAY:无需悬浮窗权限
 */
class OverlayToggleWindow(
    private val service: AccessibilityService,
    private val onToggle: () -> Unit,
) {

    companion object {
        private const val DRAG_SLOP_PX = 12     // 拖动 vs 点击的位移阈值
        private const val BALL_SIZE_DP = 36     // 悬浮球直径
        private const val ICON_IDLE = "⊕"       // 未开启抓取
        private const val ICON_ACTIVE = "⊗"     // 抓取中
    }

    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var panelView: FrameLayout? = null
    private var ballView: TextView? = null

    private val lp by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    fun show() {
        mainHandler.post {
            if (panelView != null) return@post

            val ball = TextView(service).apply {
                textSize = 18f
                text = ICON_IDLE
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x99000000.toInt())
                }
                layoutParams = FrameLayout.LayoutParams(dp(BALL_SIZE_DP), dp(BALL_SIZE_DP))
                setOnClickListener { onToggle() }
            }
            ballView = ball

            val panel = FrameLayout(service).apply { addView(ball) }
            // 拖动处理必须挂在子 TextView 上:子 View clickable 会消费 DOWN,
            // 外层容器永远收不到后续 MOVE 事件
            attachDragHandler(ball)
            panelView = panel
            wm.addView(panel, lp)
        }
    }

    fun hide() {
        mainHandler.post {
            panelView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            panelView = null
            ballView = null
        }
    }

    val isShowing: Boolean
        get() = panelView != null

    /** 把悬浮球重新置顶:同层级窗口按添加顺序叠放,重新 addView 即排到最后(最上层) */
    fun bringToFront() {
        mainHandler.post {
            val panel = panelView ?: return@post
            try {
                wm.removeView(panel)
                wm.addView(panel, lp)
            } catch (_: Exception) {}
        }
    }

    /** 切换图标:抓取中显示叉号,否则十字准星 */
    fun setActive(active: Boolean) {
        mainHandler.post { ballView?.text = if (active) ICON_ACTIVE else ICON_IDLE }
    }

    /** 悬浮球拖动:挂在其自身 onTouch 上,MOVE 超阈值判定为拖动并消费,否则留给 onClick */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(ball: TextView) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false
        ball.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false
                    // 不消费:若最终是点击,让 onClick 正常触发
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || dx * dx + dy * dy > DRAG_SLOP_PX * DRAG_SLOP_PX) {
                        dragging = true
                        lp.x = clamp(startX + dx.toInt(), 0, screenW - ball.width)
                        lp.y = clamp(startY + dy.toInt(), 0, screenH - ball.height)
                        wm.updateViewLayout(panelView, lp)
                    }
                    dragging  // 拖动中消费 MOVE
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    dragging = false
                    wasDragging  // 拖动结束消费 UP,抑制 onClick;点按则放行触发 onClick
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
}
