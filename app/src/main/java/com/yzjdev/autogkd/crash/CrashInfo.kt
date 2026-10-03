package com.yzjdev.autogkd.crash

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一次崩溃的完整上下文。
 *
 * 设计要点:崩溃发生在主线程 UncaughtExceptionHandler 内,此时进程随时可能被杀,
 * 所以这个对象必须在跳页**之前**同步落盘,而不是等 CrashActivity 启动后再读 ——
 * 进程一死就什么都读不到了。
 *
 * [stackTrace] 为 null 表示「未捕获异常」而非「无异常」:部分崩溃路径(如 Native 层、
 * Runtime.exit)可能拿不到 throwable,此时 [processName] 等字段仍是有用的诊断信息。
 */
data class CrashInfo(
    val exceptionClass: String?,
    val message: String?,
    val stackTrace: String?,
    val threadName: String?,
    val deviceId: String,
    val apiLevel: Int,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val timestamp: Long,
) {
    /** 人类可读的时间,用于崩溃页展示 */
    val formattedTime: String get() =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    /** 崩溃页顶部展示的一句话摘要 */
    val summary: String
        get() = when {
            exceptionClass != null && message != null -> "$exceptionClass: $message"
            exceptionClass != null -> exceptionClass
            else -> "程序发生未知崩溃"
        }

    /** 供用户复制到日志系统的完整文本 */
    val fullLog: String
        get() = buildString {
            appendLine("时间: $formattedTime")
            appendLine("应用: $packageName ($versionName / $versionCode)")
            appendLine("设备: $deviceId · API $apiLevel")
            appendLine("线程: ${threadName ?: "unknown"}")
            appendLine("异常: $summary")
            if (stackTrace != null) appendLine(stackTrace)
        }
}
