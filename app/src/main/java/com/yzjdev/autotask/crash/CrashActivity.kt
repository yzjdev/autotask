package com.yzjdev.autotask.crash

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yzjdev.autotask.MainActivity
import com.yzjdev.autotask.R

/*
 * 崩溃页视觉常量(暗色底 + 红色强调):
 * 设计意图 —— 崩溃是异常状态,页面本身就该"看起来不对劲";
 * 深底减少刺眼感,红色只用于错误内容与主操作,视觉动线:错误 → 复制/重启。
 */
private val PAGE_BG = Color.parseColor("#0D0D0F")
private val CARD_BG = Color.parseColor("#16181C")
private val CHIP_BG = Color.parseColor("#1F2228")
private val DIVIDER = Color.parseColor("#2A2E35")
private val TEXT_PRIMARY = Color.parseColor("#F2F3F5")
private val TEXT_SECONDARY = Color.parseColor("#9AA0A8")
private val TEXT_TERTIARY = Color.parseColor("#6B7280")
private val RED_ACCENT = Color.parseColor("#F87171")
private val RED_ERROR = Color.parseColor("#FCA5A5")
private val RED_ERROR_BG = Color.parseColor("#2A1517")
private val RED_BTN = Color.parseColor("#DC2626")
private val MONO: Typeface = Typeface.create("monospace", Typeface.NORMAL)

/**
 * 崩溃页。
 *
 * 两个进入路径:
 * 1. **崩溃后自动拉起**:[CrashReporter] 先同步落盘再 startActivity,本 Activity 用
 *    [CrashReporter.consumeCrashInfo] 读取本次崩溃;
 * 2. **兜底进入**:若崩溃瞬间 startActivity 失败(部分厂商会拦截),但进程已退出,
 *    用户手动点开应用时仍能用落盘记录展示错误 —— 崩溃信息不会因为竞态彻底丢失。
 *
 * 布局刻意用 XML View 而非 Compose:崩溃页要在设备最差的时刻**尽可能快**地渲染,
 * 且不能引入额外依赖链。
 *
 * 防自激:本 Activity 的 onCreate 用 try/catch 包住布局组装,即便构建 UI 本身抛异常,
 * 也不会再次触发 [CrashReporter] 的处理器(它已在拉起新进程时被摘除)。
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()

        val info = CrashReporter.consumeCrashInfo(applicationContext)
        if (info == null) {
            // 既非崩溃拉起,也无待展示的历史崩溃 —— 直接回主界面
            finishAndGoHome()
            return
        }

        // 把处理器装回去:从崩溃页点「重启应用」是同进程 startActivity,
        // 不会重新触发 Application.onCreate,不重装的话后续崩溃将无人接管
        CrashReporter.rearm(application)

        try {
            setContentView(buildLayout(info))
            bindActions(info)
        } catch (t: Throwable) {
            // 极端情况:崩溃信息里带了会导致布局崩溃的内容(理论上不会)。
            // 直接回主界面,避免用户在崩溃页又卡死。
            finishAndGoHome()
        }
    }

    private fun setupWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawable(ColorDrawable(PAGE_BG))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun buildLayout(info: CrashInfo): View {
        val screen = FrameLayout(this).apply {
            setBackgroundColor(PAGE_BG)
            isClickable = true // 吞掉触摸,避免穿透到下层窗口
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }
        scroll.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
        )
        screen.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // 顶部:图标 + 标题 + 副标题
        card.addView(
            TextView(this).apply {
                text = "⚠️"
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = 44f
                setPadding(0, dp(4), 0, dp(12))
            },
        )
        card.addView(
            TextView(this).apply {
                text = getString(R.string.crash_title)
                gravity = Gravity.CENTER_HORIZONTAL
                setTypeface(null, Typeface.BOLD)
                textSize = 21f
                setTextColor(TEXT_PRIMARY)
                setPadding(0, 0, 0, dp(6))
            },
        )
        card.addView(
            TextView(this).apply {
                text = getString(R.string.crash_subtitle)
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, 0, 0, dp(18))
            },
        )

        // 红色错误卡片:摘要一行 + 完整堆栈;点按/长按均可复制
        val errorBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(dp(14), RED_ERROR_BG)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            // 点击整卡复制红色错误内容(摘要 + 堆栈)
            setOnClickListener { copyError(info) }
            isClickable = true
            isLongClickable = true
            setOnLongClickListener { copyError(info); true }
        }
        errorBox.addView(
            TextView(this).apply {
                text = info.summary
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
                setTextColor(RED_ERROR)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setTextIsSelectable(false)
                setPadding(0, 0, 0, dp(4))
            },
        )
        errorBox.addView(
            TextView(this).apply {
                text = getString(R.string.crash_hint_tap_copy)
                textSize = 11f
                setTextColor(Color.parseColor("#B87A7A"))
                setPadding(0, 0, 0, dp(8))
            },
        )
        errorBox.addView(
            TextView(this).apply {
                val stack = info.stackTrace ?: getString(R.string.crash_no_stack)
                text = if (stack.length > 2000) stack.take(2000) + "…" else stack
                // 文本可长按自由选择;整卡点击复制作为快捷路径
                setTextIsSelectable(false)
                typeface = MONO
                textSize = 11f
                setTextColor(RED_ERROR)
                maxLines = 12
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(dp(2).toFloat(), 1f)
            },
        )
        card.addView(errorBox)

        // 元信息卡片:时间 / 设备 / 版本 / 线程,键左值右
        val metaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(dp(14), CHIP_BG)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        fun metaRow(label: String, value: String, last: Boolean = false) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                TextView(this).apply {
                    text = label
                    textSize = 12f
                    setTextColor(TEXT_TERTIARY)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0f)
                    .apply { width = dp(52) },
            )
            row.addView(
                TextView(this).apply {
                    text = value
                    textSize = 12f
                    setTextColor(TEXT_SECONDARY)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            metaBox.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, if (last) 0 else dp(6)) })
        }
        metaRow("时间", info.formattedTime)
        metaRow("设备", getString(R.string.crash_detail_device_value, info.deviceId, info.apiLevel))
        metaRow("版本", getString(R.string.crash_detail_version_value, info.versionName, info.versionCode))
        metaRow("线程", info.threadName ?: "unknown", last = true)
        card.addView(
            metaBox,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) },
        )

        // 按钮:复制(描边)/ 重启(红色主按钮)/ 退出(次要),重启在视觉中心
        val copyButton = Button(this).apply {
            id = R.id.crash_button_copy
            text = getString(R.string.crash_action_copy)
            setTextColor(RED_ACCENT)
            textSize = 14f
            isAllCaps = false
            background = rippleBackground(dp(12), Color.TRANSPARENT, strokeColor = 0x66F87171)
        }
        val restartButton = Button(this).apply {
            id = R.id.crash_button_restart
            text = getString(R.string.crash_action_restart)
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = rippleBackground(dp(12), RED_BTN)
        }
        val exitButton = Button(this).apply {
            id = R.id.crash_button_exit
            text = getString(R.string.crash_action_exit)
            setTextColor(TEXT_SECONDARY)
            textSize = 14f
            isAllCaps = false
            background = rippleBackground(dp(12), CHIP_BG)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttonRow.addView(copyButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(10) })
        buttonRow.addView(restartButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(10) })
        buttonRow.addView(exitButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        card.addView(
            buttonRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(20) },
        )
        return screen
    }

    private fun bindActions(info: CrashInfo) {
        findViewById<Button>(R.id.crash_button_copy).setOnClickListener { copyToClipboard(info) }
        findViewById<Button>(R.id.crash_button_restart).setOnClickListener { restartApp() }
        findViewById<Button>(R.id.crash_button_exit).setOnClickListener { exitApp() }
    }

    private fun copyToClipboard(info: CrashInfo) {
        copyToClipboardInternal(info.fullLog, R.string.crash_copy_done)
    }

    /** 复制红色错误卡片内容:错误摘要 + 堆栈(比 fullLog 更聚焦) */
    private fun copyError(info: CrashInfo) {
        val text = buildString {
            append(info.summary)
            if (info.stackTrace != null) appendLine().append(info.stackTrace)
        }
        copyToClipboardInternal(text, R.string.crash_copy_done)
    }

    private fun copyToClipboardInternal(text: String, doneRes: Int) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash_log", text))
            toast(getString(doneRes))
        } catch (t: Throwable) {
            toast(getString(R.string.crash_copy_failed))
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun restartApp() {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        startActivity(intent)
        finish()
        Process.killProcess(Process.myPid())
    }

    private fun exitApp() {
        finish()
        Process.killProcess(Process.myPid())
        System.exit(0)
    }

    private fun finishAndGoHome() {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    private fun roundedBackground(radiusDp: Int, color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    /** 按钮背景:圆角 + 可选描边 + 涟漪反馈(API 21+) */
    private fun rippleBackground(radiusDp: Int, fillColor: Int, strokeColor: Int? = null): RippleDrawable {
        val content = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusDp * resources.displayMetrics.density
            strokeColor?.let {
                setStroke(dp(1), it)
            }
        }
        return RippleDrawable(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), content, null)
    }

    private fun dpF(value: Int): Float = value * resources.displayMetrics.density

    companion object {
        private const val TAG = "CrashActivity"
    }
}
