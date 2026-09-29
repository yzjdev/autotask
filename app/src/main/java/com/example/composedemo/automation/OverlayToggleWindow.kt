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
        private const val BALL_SIZE_DP = 44     // 悬浮球直径
    }

    private val wm by lazy { service.getSystemService(WindowManager::class.java) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var panelView: FrameLayout? = null
    private var ballView: BallView? = null

    private val lp by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    fun show() {
        mainHandler.post {
            if (panelView != null) return@post

            val ball = BallView(service)
            ball.layoutParams = FrameLayout.LayoutParams(dp(BALL_SIZE_DP), dp(BALL_SIZE_DP))
            ball.setOnClickListener { onToggle() }
            ballView = ball

            val panel = FrameLayout(service).apply { addView(ball) }
            // 拖动处理必须挂在子 View 上:子 View clickable 会消费 DOWN,
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

    /** 切换图标:抓取中显示录制样式,否则准星样式 */
    fun setActive(active: Boolean) {
        mainHandler.post { ballView?.active = active }
    }

    /** 悬浮球拖动:挂在其自身 onTouch 上,MOVE 超阈值判定为拖动并消费,否则留给 onClick */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(ball: BallView) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragging = false
        ball.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false
                    ball.pressedState = true
                    // 不消费:若最终是点击,让 onClick 正常触发
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || dx * dx + dy * dy > DRAG_SLOP_PX * DRAG_SLOP_PX) {
                        if (!dragging) {
                            dragging = true
                            ball.pressedState = false
                            ball.dragging = true   // 拖拽中显示光晕
                        }
                        // gravity=CENTER 时 x/y 为相对屏幕中心的偏移,clamp 到半屏范围
                        lp.x = clamp(startX + dx.toInt(), -(screenW - ball.width) / 2, (screenW - ball.width) / 2)
                        lp.y = clamp(startY + dy.toInt(), -(screenH - ball.height) / 2, (screenH - ball.height) / 2)
                        wm.updateViewLayout(panelView, lp)
                    }
                    dragging  // 拖动中消费 MOVE
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    dragging = false
                    ball.pressedState = false
                    ball.dragging = false
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

    /**
     * 悬浮球本体:自定义绘制,不依赖字体符号。
     *  - 球体:深色渐变底 + 琥珀细描边,拖拽中加外圈光晕
     *  - 图标:准星(未开启,琥珀) / 录制圆点+环(抓取中,红)
     *  - 按压:整体缩小 0.9 倍
     */
    private class BallView(context: android.content.Context) : View(context) {

        var active = false
            set(value) { field = value; invalidate() }
        var pressedState = false
            set(value) { field = value; invalidate() }
        var dragging = false
            set(value) { field = value; invalidate() }
            get() = field

        private val d = context.resources.displayMetrics

        private val ballPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
        private val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = d.density
            color = 0x66F59E0B.toInt()  // 琥珀描边
        }
        private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = d.density * 3
            color = 0x59F59E0B.toInt()
        }
        private val iconPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = d.density * 1.6f
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val c = w / 2f
            val scale = if (pressedState) 0.9f else 1f
            val r = (w / 2f - d.density * 2f) * scale
            if (r <= 0f) return

            // 拖拽光晕
            if (dragging) canvas.drawCircle(c, c, w / 2f - d.density, glowPaint)

            // 球体
            ballPaint.shader = android.graphics.RadialGradient(
                c, c, r,
                intArrayOf(0xE626262E.toInt(), 0xE6141418.toInt()),
                null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(c, c, r, ballPaint)
            canvas.drawCircle(c, c, r, edgePaint)

            // 图标
            val ir = r * 0.52f
            if (active) {
                // 录制:红色内圆 + 外环
                iconPaint.style = android.graphics.Paint.Style.FILL
                iconPaint.color = 0xFFEF4444.toInt()
                canvas.drawCircle(c, c, ir * 0.55f, iconPaint)
                iconPaint.style = android.graphics.Paint.Style.STROKE
                iconPaint.strokeWidth = d.density * 1.4f
                canvas.drawCircle(c, c, ir, iconPaint)
            } else {
                // 准星:圆环 + 四向短刻度
                iconPaint.style = android.graphics.Paint.Style.STROKE
                iconPaint.strokeWidth = d.density * 1.6f
                iconPaint.color = 0xFFE8B85C.toInt()
                canvas.drawCircle(c, c, ir, iconPaint)
                val tick = ir * 0.42f
                canvas.drawLine(c, c - ir - tick, c, c - ir + tick * 0.4f, iconPaint)
                canvas.drawLine(c, c + ir + tick, c, c + ir - tick * 0.4f, iconPaint)
                canvas.drawLine(c - ir - tick, c, c - ir + tick * 0.4f, c, iconPaint)
                canvas.drawLine(c + ir + tick, c, c + ir - tick * 0.4f, c, iconPaint)
            }
        }
    }
}
