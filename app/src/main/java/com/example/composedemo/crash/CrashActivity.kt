package com.example.composedemo.crash

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
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
import com.example.composedemo.MainActivity
import com.example.composedemo.R

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
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun buildLayout(info: CrashInfo): View {
        val screen = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true // 吞掉触摸,避免穿透到下层窗口
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(32), dp(28), dp(28))
            background = roundedBackground(dp(20), Color.parseColor("#F7F8FA"))
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

        card.addView(
            TextView(this).apply {
                text = getString(R.string.crash_warning_symbol)
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = 40f
                setPadding(0, dp(4), 0, dp(16))
            },
        )
        card.addView(
            TextView(this).apply {
                text = getString(R.string.crash_title)
                gravity = Gravity.CENTER_HORIZONTAL
                setTypeface(null, Typeface.BOLD)
                textSize = 22f
                setTextColor(Color.parseColor("#1A1A1A"))
                setPadding(0, 0, 0, dp(8))
            },
        )
        card.addView(
            TextView(this).apply {
                text = getString(R.string.crash_subtitle)
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = 14f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, 0, 0, dp(20))
            },
        )
        card.addView(
            TextView(this).apply {
                text = info.summary
                gravity = Gravity.CENTER_HORIZONTAL
                setTypeface(null, Typeface.BOLD)
                textSize = 15f
                setTextColor(Color.parseColor("#DC2626"))
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, dp(14))
            },
        )

        // 详情卡片
        val detailBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(dp(12), Color.parseColor("#EEF0F3"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        detailBox.addView(
            TextView(this).apply {
                text = getString(R.string.crash_detail_time, info.formattedTime)
                textSize = 12f
                setTextColor(Color.parseColor("#374151"))
                setPadding(0, 0, 0, dp(4))
            },
        )
        detailBox.addView(
            TextView(this).apply {
                text = getString(R.string.crash_detail_device, info.deviceId, info.apiLevel)
                textSize = 12f
                setTextColor(Color.parseColor("#374151"))
                setPadding(0, 0, 0, dp(4))
            },
        )
        detailBox.addView(
            TextView(this).apply {
                text = getString(R.string.crash_detail_version, info.versionName, info.versionCode)
                textSize = 12f
                setTextColor(Color.parseColor("#374151"))
                setPadding(0, 0, 0, dp(4))
            },
        )
        detailBox.addView(
            TextView(this).apply {
                text = getString(R.string.crash_detail_thread, info.threadName ?: "unknown")
                textSize = 12f
                setTextColor(Color.parseColor("#374151"))
                setPadding(0, 0, 0, dp(10))
            },
        )
        detailBox.addView(
            TextView(this).apply {
                val stack = info.stackTrace ?: getString(R.string.crash_no_stack)
                text = if (stack.length > 800) stack.take(800) + "…" else stack
                setTextIsSelectable(true)
                // Typeface.MONOSPACE 是 int 字体族常量,不是 Typeface 对象,
                // 需经 Typeface.create 转换,否则解析到 (Typeface, Int) 重载而报错
                typeface = Typeface.create("monospace", Typeface.NORMAL)
                textSize = 11f
                setTextColor(Color.parseColor("#111827"))
            },
        )
        card.addView(detailBox)

        card.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(20),
                )
            },
        )

        // 按钮
        val copyButton = Button(this).apply {
            id = R.id.crash_button_copy
            text = getString(R.string.crash_action_copy)
            setTextColor(Color.parseColor("#DC2626"))
            background = roundedBackground(dp(12), Color.parseColor("#FEE2E2"))
            isAllCaps = false
        }
        val restartButton = Button(this).apply {
            id = R.id.crash_button_restart
            text = getString(R.string.crash_action_restart)
            setTextColor(Color.WHITE)
            background = roundedBackground(dp(12), Color.parseColor("#DC2626"))
            isAllCaps = false
        }
        val exitButton = Button(this).apply {
            id = R.id.crash_button_exit
            text = getString(R.string.crash_action_exit)
            setTextColor(Color.parseColor("#6B7280"))
            background = roundedBackground(dp(12), Color.parseColor("#F3F4F6"))
            isAllCaps = false
        }

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttonRow.addView(copyButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(10) })
        buttonRow.addView(restartButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttonRow.addView(exitButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48),
        ).apply { topMargin = dp(12) })

        card.addView(buttonRow)
        return screen
    }

    private fun bindActions(info: CrashInfo) {
        findViewById<Button>(R.id.crash_button_copy).setOnClickListener { copyToClipboard(info) }
        findViewById<Button>(R.id.crash_button_restart).setOnClickListener { restartApp() }
        findViewById<Button>(R.id.crash_button_exit).setOnClickListener { exitApp() }
    }

    private fun copyToClipboard(info: CrashInfo) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash_log", info.fullLog))
            toast(getString(R.string.crash_copy_done))
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

    companion object {
        private const val TAG = "CrashActivity"
    }
}
