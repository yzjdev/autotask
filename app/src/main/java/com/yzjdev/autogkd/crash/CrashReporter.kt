package com.yzjdev.autogkd.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * 全局崩溃捕获器。
 *
 * 工作流程:
 * 1. [install] 在 Application.onCreate 注册为线程未捕获异常处理器;
 * 2. 崩溃时先把 [CrashInfo] **同步落盘**(此刻进程还活着,写完就有据可查);
 * 3. 尝试启动 [CrashActivity](独立 :crash 进程),让用户看到错误详情而非系统的「应用已停止」;
 * 4. 最后把异常交给系统默认处理器 —— 这一步不可省略:抛出的异常会替代系统
 *    「应用无响应」对话框,用户最终看到的是本应用的崩溃页。
 *    不做自动重启:恢复由崩溃页上的「重启应用」按钮触发,避免重启循环。
 *
 * 递归防护:重启后的新进程在 [install] 时检查 [KEY_HAS_PENDING_RESTART],发现是
 * 自己刚拉起的就摘掉处理器,只保留系统默认行为。[CrashActivity] 读取并消费崩溃
 * 信息后,会再次调用 [install] 把处理器装回去 —— 因为从崩溃页点「重启应用」
 * 是在同一进程内 startActivity,不会重新触发 Application.onCreate。
 *
 * 死循环防护:[uncaughtException] 记录短时间内的连续崩溃次数,超过阈值后不再
 * 尝试重启,直接结束进程,避免「崩溃 → 崩溃页 → 重启 → 又崩溃」无限循环。
 */
class CrashReporter private constructor(
    private val context: Context,
) : Thread.UncaughtExceptionHandler {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    companion object {
        private const val TAG = "CrashReporter"

        private const val PREFS_NAME = "crash_report"
        private const val PREFS_LOOP = "crash_loop"

        private const val KEY_HAS_PENDING_RESTART = "has_pending_restart"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_EXCEPTION_CLASS = "exception_class"
        private const val KEY_MESSAGE = "message"
        private const val KEY_STACK = "stack"
        private const val KEY_THREAD = "thread"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_VERSION_CODE = "version_code"

        private const val KEY_LOOP_COUNT = "loop_count"
        private const val KEY_LOOP_FIRST_TS = "loop_first_ts"

        /** 崩溃页只展示「本次」崩溃;超过该时间视为残留,直接丢弃 */
        private const val STALE_REPORT_MS = 120_000L

        /** 该时间窗内的连续崩溃上限,超过后不再自动重启 */
        private const val MAX_CRASHES_IN_WINDOW = 5
        private const val LOOP_WINDOW_MS = 60_000L

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun loopPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_LOOP, Context.MODE_PRIVATE)

        /**
         * 崩溃页用:读取并清除本次崩溃信息。
         *
         * 只清除崩溃详情键,**不触碰循环计数器** —— 计数若被误清,
         * 「启动即崩」的应用会陷入无限崩溃循环而无法被熔断。
         */
        fun consumeCrashInfo(context: Context): CrashInfo? {
            val p = prefs(context)
            if (!p.getBoolean(KEY_HAS_PENDING_RESTART, false)) return null
            val ts = p.getLong(KEY_TIMESTAMP, 0L)
            if (ts == 0L || System.currentTimeMillis() - ts > STALE_REPORT_MS) {
                p.edit().remove(KEY_HAS_PENDING_RESTART).apply()
                return null
            }

            val info = CrashInfo(
                exceptionClass = p.getString(KEY_EXCEPTION_CLASS, null),
                message = p.getString(KEY_MESSAGE, null),
                stackTrace = p.getString(KEY_STACK, null),
                threadName = p.getString(KEY_THREAD, null),
                deviceId = Build.MODEL,
                apiLevel = Build.VERSION.SDK_INT,
                packageName = context.packageName,
                versionName = versionName(context),
                versionCode = versionCode(context),
                timestamp = ts,
            )

            p.edit()
                .remove(KEY_HAS_PENDING_RESTART)
                .remove(KEY_TIMESTAMP)
                .remove(KEY_EXCEPTION_CLASS)
                .remove(KEY_MESSAGE)
                .remove(KEY_STACK)
                .remove(KEY_THREAD)
                .remove(KEY_VERSION_NAME)
                .remove(KEY_VERSION_CODE)
                .apply()
            return info
        }

        /**
         * 安装全局异常处理器。必须在 Application.onCreate 调用。
         *
         * @return true 表示已接管异常(新进程);false 表示本进程是崩溃后重启
         *         拉起的,已摘掉处理器,交回系统默认。
         */
        @Synchronized
        fun install(app: Application): Boolean {
            val ctx = app.applicationContext
            if (prefs(ctx).getBoolean(KEY_HAS_PENDING_RESTART, false)) {
                return false
            }
            armHandler(ctx)
            // 新进程成功接管 = 一次健康启动,清零崩溃循环计数
            resetLoopCounter(ctx)
            return true
        }

        /**
         * 崩溃页消费完崩溃信息后调用:把处理器重新装回去。
         *
         * 关键:**不清零**崩溃循环计数。若在这里清零,启动即崩的应用会变成
         * 「崩溃 → 崩溃页清零 → 重启 → 又崩」,永远打不到 [MAX_CRASHES_IN_WINDOW]
         * 熔断阈值。计数只在 [install] 成功(即应用真正被用户健康启动)时清零。
         */
        @Synchronized
        fun rearm(application: Application) {
            armHandler(application.applicationContext)
        }

        private fun armHandler(ctx: Context) {
            val reporter = CrashReporter(ctx)
            reporter.previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(reporter)
        }

        /** 供崩溃页/主界面在应用恢复正常后清零计数 */
        fun resetLoopCounter(context: Context) {
            loopPrefs(context).edit().clear().apply()
        }

        private fun versionName(context: Context): String = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }

        private fun versionCode(context: Context): Long = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "捕获到未处理异常", throwable)

        val info = CrashInfo(
            exceptionClass = throwable.javaClass.name,
            message = throwable.message,
            stackTrace = stackTraceToString(throwable),
            threadName = thread.name,
            deviceId = Build.MODEL,
            apiLevel = Build.VERSION.SDK_INT,
            packageName = context.packageName,
            versionName = versionName(context),
            versionCode = versionCode(context),
            timestamp = System.currentTimeMillis(),
        )

        // 顺序不可调换:先落盘(进程还活着),再跳页,最后自杀。
        // 反过来进程被杀的瞬间,崩溃页将读不到任何数据。
        saveCrashInfo(info)

        if (shouldShowCrashPage()) {
            launchCrashPage()
        } else {
            Log.w(TAG, "短时间内崩溃次数过多,跳过崩溃页以避免死循环")
        }

        // 交回系统默认处理器触发进程退出;
        // 抛出的 InterruptedException 会让系统跳过「应用已停止」对话框
        previousHandler?.uncaughtException(
            thread,
            InterruptedException("crash reported"),
        ) ?: terminateProcess()
    }

    /**
     * 判断本次是否还值得展示崩溃页。
     *
     * 计数存放在独立的 [PREFS_LOOP] 文件中,不被 [consumeCrashInfo] 清除;
     * 只有 [install] 成功(新进程健康启动)或崩溃页手动调用
     * [resetLoopCounter] 才会归零。
     */
    private fun shouldShowCrashPage(): Boolean {
        val now = System.currentTimeMillis()
        val edit = loopPrefs(context).edit()
        val first = loopPrefs(context).getLong(KEY_LOOP_FIRST_TS, 0L)
        val count = if (first != 0L && now - first < LOOP_WINDOW_MS) {
            loopPrefs(context).getInt(KEY_LOOP_COUNT, 0) + 1
        } else {
            edit.putLong(KEY_LOOP_FIRST_TS, now)
            1
        }
        edit.putInt(KEY_LOOP_COUNT, count).apply()
        return count < MAX_CRASHES_IN_WINDOW
    }

    /** 崩溃详情落盘,必须在跳页之前同步 commit */
    private fun saveCrashInfo(info: CrashInfo) {
        try {
            prefs(context).edit().apply {
                putBoolean(KEY_HAS_PENDING_RESTART, true)
                putLong(KEY_TIMESTAMP, info.timestamp)
                putString(KEY_EXCEPTION_CLASS, info.exceptionClass)
                putString(KEY_MESSAGE, info.message)
                putString(KEY_STACK, info.stackTrace)
                putString(KEY_THREAD, info.threadName)
                putString(KEY_VERSION_NAME, info.versionName)
                putLong(KEY_VERSION_CODE, info.versionCode)
            }.commit() // 用 commit 而非 apply:崩溃场景必须保证落盘完成
        } catch (t: Throwable) {
            Log.e(TAG, "崩溃信息落盘失败", t)
        }
    }

    /** 尝试直接启动崩溃页。崩溃瞬间 startActivity 可能被系统拒绝,静默忽略 */
    private fun launchCrashPage() {
        try {
            val intent = Intent(context, CrashActivity::class.java)
                .setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "启动崩溃页失败,信息已落盘", t)
        }
    }

    private fun stackTraceToString(throwable: Throwable): String {
        val writer = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(writer))
        return writer.toString().trimEnd()
    }

    private fun terminateProcess() {
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(2)
    }
}
