package com.yzjdev.autotask

import android.app.Application
import com.yzjdev.autotask.crash.CrashReporter

/**
 * 应用入口。
 *
 * 只干一件事:在 [Application.onCreate] 尽早安装全局崩溃捕获器。
 * 必须放在这一层而非 Activity —— 否则 Application 初始化期间、或任何 Activity
 * 尚未创建时的崩溃都抓不到。
 *
 * [CrashReporter.install] 返回 false 表示本次进程是「崩溃后重启」拉起的
 * (见 CrashReporter 的递归防护),此时不再接管异常,交由系统默认处理器。
 */
class AutoTaskApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
