package com.example.composedemo.automation

import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Shizuku shell 执行器:以 com.android.shell(即 adb)身份执行任意命令。
 *
 * 通用化自 [AutomationManager.grantWriteSecureSettingsViaShizuku] 的模式:
 * 直接使用 IShizukuService.Stub + IRemoteProcess(13.1.5 API 未导出
 * getRemoteProcess(),ShizukuRemoteProcess 构造函数包私有)。
 *
 * 注意:调用方需先确认 Shizuku 已授权(AutomationManager 状态为 Ready),
 * 否则 exec 返回 ShizukuShell.Result(-1, "Shizuku 未授权")。
 */
object ShizukuShell {

    /** 执行结果:exitCode 为 0 表示命令成功;stdout/stderr 用于读取命令输出与失败原因 */
    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /** Shizuku binder 是否可用且已授权 */
    fun isReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * 执行一条 shell 命令(阻塞,最多等 [timeoutSec] 秒)。
     * 命令以 adb(shell)身份运行,可用于 netpolicy/pm/dumpsys 等。
     */
    fun exec(cmd: Array<String>, timeoutSec: Long = 10L): Result {
        val service = try {
            IShizukuService.Stub.asInterface(Shizuku.getBinder())
        } catch (_: Throwable) {
            null
        } ?: return Result(-1, "", "Shizuku 服务不可用")

        val proc = try {
            service.newProcess(cmd, null, null)
        } catch (e: Throwable) {
            return Result(-1, "", "启动命令失败:${e.message}")
        }

        return try {
            val exitCode = try {
                if (proc.waitForTimeout(timeoutSec, TimeUnit.SECONDS.name)) {
                    proc.exitValue()
                } else {
                    -1
                }
            } catch (_: Throwable) {
                -1
            }
            Result(exitCode, drainStream(proc.inputStream), drainStream(proc.errorStream))
        } finally {
            try {
                proc.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    /** 排空并读取 shell 进程的一个输出流(stdout/stderr) */
    private fun drainStream(fd: android.os.ParcelFileDescriptor?): String {
        return try {
            fd ?: return ""
            val stream = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(stream)).use { reader ->
                val buf = CharArray(512)
                var n = reader.read(buf)
                while (n > 0 && sb.length < 4096) {
                    sb.append(buf, 0, n)
                    n = reader.read(buf)
                }
            }
            sb.toString().trim()
        } catch (_: Throwable) {
            ""
        }
    }
}
