package com.example.composedemo.automation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService

/** 「写入安全设置」权限名,代授时传给 pm grant */
private const val WRITE_SECURE_SETTINGS = android.Manifest.permission.WRITE_SECURE_SETTINGS

/**
 * 自动化能力管理器,统一收敛三件事:
 * 1. 请求 Shizuku 连接(未就绪时通过静态广播向 Shizuku 申请授权);
 * 2. 判断 / 获取「写入安全设置」权限([android.Manifest.permission.WRITE_SECURE_SETTINGS]);
 * 3. 判断无障碍服务是否已启用。
 *
 * 规则(与需求一致):
 * - 不自动开启无障碍、不自动请求 Shizuku、不自动跳转系统设置;
 * - 已连 Shizuku → 点「授予写入安全设置」直接 pm grant 代授,不跳转;
 * - 已有写入安全设置权限 → 忽略 Shizuku,由本应用直写 Settings 开关无障碍;
 * - 缺写入安全设置权限 → 弹窗询问后才跳转系统无障碍设置页。
 *
 * 状态以 [State] 形式暴露,[observe] 挂载生命周期后状态变化自动回到主线程回调。
 */
class AutomationManager private constructor(appContext: Context) {

    /** UI 关心的全部状态 */
    data class State(
        val hasSecureSetting: Boolean = false,
        val isAccessibilityEnabled: Boolean = false,
        val shizukuStatus: ShizukuStatus = ShizukuStatus.Unready,
        val statusText: String = "",
    ) {
        /** Shizuku 服务是否真正连上(区别于「已授权但服务未启动」) */
        val isShizukuReady: Boolean get() = shizukuStatus == ShizukuStatus.Ready
    }

    enum class ShizukuStatus { Unready, Connecting, Requesting, Ready }

    /** 自动化整体可用性:供其它模块做门禁判断 */
    enum class AutomationState { Ready, Partial, Unready }

    companion object {
        private val MAIN = Handler(Looper.getMainLooper())
        // manifest 注册的服务是 SelectToSpeakService(系统级服务名,绕过应用检测),
        // 业务逻辑在其父类 DramaAccessibilityService 中;开关读写必须与之一致
        private const val SERVICE_CLASS =
            "com.google.android.accessibility.selecttospeak.SelectToSpeakService"

        @Volatile
        private var sInstance: AutomationManager? = null

        fun get(app: Context): AutomationManager {
            sInstance?.let { return it }
            synchronized(this) {
                sInstance?.let { return it }
                val created = AutomationManager(app.applicationContext)
                sInstance = created
                return created
            }
        }

        fun clear() {
            sInstance?.stopConnect()
            sInstance = null
        }

        /** 已创建的单例(未创建过返回 null);供悬浮窗等读取 Shizuku 查询能力 */
        fun getInstance(): AutomationManager? = sInstance

        // 以下三个为纯状态查询,不依赖实例

        /**
         * 写入安全设置权限(android.permission.WRITE_SECURE_SETTINGS)。
         *
         * 用 checkPermission(perm, myPid, myUid) 直接按本进程 pid/uid 查,
         * 语义与 checkSelfPermission 一致,但不依赖 ContextCompat。
         *
         * 注意:本方法的返回值完全取决于「权限是否已声明在 manifest 中且已被授予」。
         * 未声明时 checkPermission 抛 SecurityException 而不是返回 PERMISSION_DENIED,
         * 因此 try/catch 不可省。此前本应用未声明 WRITE_SECURE_SETTINGS,
         * 这里必然恒返回 false —— 那才是「显示未授权」的真正原因,与检查 API 无关。
         */
        fun hasSecureSetting(context: Context): Boolean {
            val perm = WRITE_SECURE_SETTINGS
            return try {
                context.checkPermission(perm, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED
            } catch (_: SecurityException) {
                false
            }
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            val value = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expectedPackage = context.packageName
            val expectedClass = SERVICE_CLASS
            for (item in TextUtils.split(value, ":")) {
                val cn = ComponentName.unflattenFromString(item) ?: continue
                if (cn.packageName == expectedPackage && cn.className == expectedClass) return true
            }
            return false
        }

        fun getLifecycleState(context: Context): AutomationState {
            val secure = hasSecureSetting(context)
            val service = isAccessibilityEnabled(context)
            return when {
                secure && service -> AutomationState.Ready
                secure || service -> AutomationState.Partial
                else -> AutomationState.Unready
            }
        }
    }

    // ---- 运行状态 ----

    private val context: Context
    private var secureGranted: Boolean
    private var serviceEnabled: Boolean
    private var shizukuStatus = ShizukuStatus.Unready
    private var statusText = ""
    private var bindCallbackInstalled = false
    private var pollRunnable: Runnable? = null
    private val observers = mutableListOf<LifecycleOwner>()
    private val callbacks = mutableListOf<(State) -> Unit>()

    init {
        context = appContext.applicationContext
        secureGranted = hasSecureSetting(context)
        serviceEnabled = isAccessibilityEnabled(context)
    }

    // ---- 对外状态 ----

    @MainThread
    fun currentState(): State = State(
        hasSecureSetting = secureGranted,
        isAccessibilityEnabled = serviceEnabled,
        shizukuStatus = shizukuStatus,
        statusText = statusText,
    )

    fun hasSecureSetting(): Boolean = secureGranted
    fun isAccessibilityEnabled(): Boolean = serviceEnabled
    fun shizukuStatus(): ShizukuStatus = shizukuStatus
    fun statusText(): String = statusText

    /** 订阅状态:挂载生命周期,状态变化自动回到主线程回调 */
    @MainThread
    fun observe(owner: LifecycleOwner, onState: (State) -> Unit) {
        if (owner in observers) return
        observers += owner
        callbacks += onState
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                notifyStateNow()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                val removed = observers.remove(owner)
                if (removed) callbacks.remove(onState)
            }
        })
    }

    /**
     * 启动 / 回到前台时只做状态刷新,不触发任何自动动作。
     *
     * 刻意不做的事(按需求):
     * - 不自动开启无障碍服务;
     * - 不自动请求 Shizuku;
     * - 不自动跳转无障碍设置。
     * 所有权限申请与跳转都必须由用户显式点击后,再经弹窗确认。
     */
    @MainThread
    fun bootstrap() {
        refresh()
    }

    /**
     * 申请「写入安全设置」权限:已连 Shizuku 就直接 pm grant 授权,无需跳转任何设置页。
     *
     * 已有该权限则忽略 Shizuku 直接返回;
     * 连不上时只提示,由用户再次点击重试 —— 不自动跳转系统设置。
     */
    @MainThread
    fun requestShizukuAndGrantSecure() {
        // 兼容旧调用点:授权成功即自动代授写入安全设置,无需单独按钮
        requestShizuku()
    }

    /** 跳转系统无障碍设置页 —— 只能由用户点击并经弹窗确认后才允许调用 */
    @MainThread
    fun openAccessibilitySettings() {
        try {
            context.startActivity(
                android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (e: Throwable) {
            setStatus("无法打开无障碍设置:" + e.message)
        }
    }

    // ---- Shizuku 连接 ----

    @MainThread
    fun requestShizuku(onReady: (() -> Unit)? = null, onFail: (() -> Unit)? = null) {
        // 授权成功先代授写入安全设置,成功后再执行 onReady(如继续开无障碍);
        // onReady 为 null 时仅代授(兼容旧调用点)
        val ready: () -> Unit = {
            tryGrantSecureSetting { granted ->
                if (granted) onReady?.invoke()
            }
        }
        if (secureGranted) {
            setStatus("已有写入安全设置权限,无需再授权")
            notifyState()
            return
        }
        // 守卫只拦「连接/请求在途」(Connecting/Requesting)。
        // 注意 Ready 也曾被拦 —— Shizuku 已授权后点「授予写入安全设置」
        // 会在这一行静默返回,既不代授也无提示,表现为「点了没反应」。
        if (shizukuStatus == ShizukuStatus.Connecting || shizukuStatus == ShizukuStatus.Requesting) {
            return
        }

        if (shizukuStatus == ShizukuStatus.Ready) {
            // 已就绪:跳过连接与授权流程,直接进入 ready 链路(代授)
            setStatus("Shizuku 已连接,已授权")
            notifyState()
            ready()
            return
        }

        installBinderCallbacks()
        setStatus("正在连接 Shizuku…")
        shizukuStatus = ShizukuStatus.Connecting
        notifyState()

        // binder 尚未送达时 requestPermission 内部的 requireService() 会抛
        // IllegalStateException("binder haven't been received") —— 服务端没运行。
        val binder = try {
            Shizuku.getBinder()
        } catch (_: Throwable) {
            null
        }
        if (binder == null || !pingBinder()) {
            stopPoll()
            shizukuStatus = ShizukuStatus.Unready
            setStatus(
                "Shizuku 服务未运行,无法弹出授权框。" +
                    "请打开 Shizuku 应用,点击「开始」(ADB 无线调试 / root)启动后重试",
            )
            notifyState()
            onFail?.invoke()
            return
        }

        // 官方流程(README「Request permission」):binder 存活后先
        // checkSelfPermission() 判断授权态,未授权才 requestPermission() 弹框。
        // 之前把 pingBinder()==true 直接当「已连接就绪」,跳过了授权检查 ——
        // binder 通了但未授权时,既不弹框也不报错,直接走 pm grant 必然失败,
        // 用户看到的就是「点了没反应」。
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            stopPoll()
            shizukuStatus = ShizukuStatus.Unready
            setStatus("Shizuku 授权态查询失败:${e.message}")
            notifyState()
            onFail?.invoke()
            return
        }

        if (granted) {
            shizukuStatus = ShizukuStatus.Ready
            setStatus("Shizuku 已连接,已授权")
            notifyState()
            ready()
            return
        }

        // 官方流程:shouldShowRequestPermissionRationale()==true 表示用户
        // 曾选「拒绝且不再询问」,此时 requestPermission 不会再弹框。
        val rationale = try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Throwable) {
            false
        }
        if (rationale) {
            stopPoll()
            shizukuStatus = ShizukuStatus.Unready
            setStatus("Shizuku 授权被永久拒绝,请到 Shizuku 应用 → 设置中手动允许本应用后重试")
            notifyState()
            onFail?.invoke()
            return
        }

        // 未授权:弹授权确认框,结果异步经 onRequestPermissionResult 回来
        shizukuStatus = ShizukuStatus.Requesting
        setStatus("等待 Shizuku 授权确认…")
        notifyState()

        pendingOnReady = ready
        pendingOnFail = onFail
        try {
            Shizuku.requestPermission(0)
        } catch (e: Throwable) {
            stopPoll()
            shizukuStatus = ShizukuStatus.Unready
            pendingOnReady = null
            pendingOnFail = null
            setStatus("发起 Shizuku 授权请求失败:${e.message}")
            notifyState()
            onFail?.invoke()
            return
        }

        // 授权确认是异步的:结果统一由 addRequestPermissionResultListener 分发,
        // 这里只做「用户长时间不点确认框」的兜底提示。轮询条件用授权态而非
        // pingBinder() —— binder 始终存活,若以它为条件会在用户点「允许」前
        // 提前触发 onReady,与监听器重复代授。
        startPoll(
            condition = {
                try {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) {
                    false
                }
            },
            onReady = { /* 结果由监听器分发,避免重复触发 */ },
            onTimeout = {
                // 超时只提示;授权确认是异步的,后续结果仍由监听器处理
                if (shizukuStatus == ShizukuStatus.Requesting) {
                    setStatus("等待 Shizuku 授权超时。请在弹出的确认框中点「允许」,或到 Shizuku 应用中允许本应用")
                    notifyState()
                }
            },
        )
    }

    /** 授权请求发起时挂起的回调,结果经 onRequestPermissionResult 回来后消费并清空 */
    private var pendingOnReady: (() -> Unit)? = null
    private var pendingOnFail: (() -> Unit)? = null

    private fun installBinderCallbacks() {
        if (bindCallbackInstalled) return
        bindCallbackInstalled = true
        Shizuku.addBinderReceivedListenerSticky {
            // binder 到达只代表服务在运行,是否 Ready 取决于授权态(官方语义)
            if (shizukuStatus != ShizukuStatus.Ready) {
                val granted = try {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) {
                    false
                }
                if (granted) {
                    shizukuStatus = ShizukuStatus.Ready
                    setStatus("Shizuku 已连接,已授权")
                    notifyState()
                    // 用户可能是在 Shizuku 应用里手动授权后返回 ——
                    // 只要此时还缺写入安全设置权限,立即代授并同步 UI
                    if (!secureGranted) tryGrantSecureSetting()
                }
            }
        }
        Shizuku.addBinderDeadListener {
            if (shizukuStatus == ShizukuStatus.Ready) {
                shizukuStatus = ShizukuStatus.Unready
                setStatus("Shizuku 已断开")
                notifyState()
            }
        }
        // 官方流程:授权结果经此监听器异步返回(grantResult 为 PackageManager 常量)
        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            val ok = grantResult == PackageManager.PERMISSION_GRANTED
            val onReady = pendingOnReady
            val onFail = pendingOnFail
            pendingOnReady = null
            pendingOnFail = null
            if (ok) {
                shizukuStatus = ShizukuStatus.Ready
                setStatus("Shizuku 授权成功")
                notifyState()
                onReady?.invoke()
            } else {
                stopPoll()
                shizukuStatus = ShizukuStatus.Unready
                setStatus("Shizuku 授权被拒绝,请重新授权后重试")
                notifyState()
                onFail?.invoke()
            }
        }
    }

    private fun stopConnect() {
        stopPoll()
        try {
            Shizuku.exit()
        } catch (_: Throwable) {
        }
        shizukuStatus = ShizukuStatus.Unready
        setStatus("")
    }

    // ---- 写入安全设置权限:仅通过 Shizuku 代授 ----

    @MainThread
    private fun tryGrantSecureSetting(onDone: ((Boolean) -> Unit)? = null) {
        if (secureGranted) {
            setStatus("Shizuku 已连接")
            notifyState()
            onDone?.invoke(true)
            return
        }
        setStatus("正在通过 Shizuku 授予写入安全设置权限…")
        notifyState()
        // 后台线程执行 shell 命令:Shizuku 服务端只自动代授 API_V23,
        // 不会代授 WRITE_SECURE_SETTINGS,必须显式执行 pm grant
        MAIN.postDelayed({
            val ok = grantWriteSecureSettingsViaShizuku()
            if (ok) {
                markSecureGranted()
                onDone?.invoke(true)
            } else {
                setStatus("Shizuku 代授写入安全设置权限失败,请确认 Shizuku 以 shell/adb 方式启动")
                notifyState()
                onDone?.invoke(false)
            }
        }, 0)
    }

    /**
     * 通过 Shizuku 远程进程(以 com.android.shell 身份)执行
     * `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` 代授权限。
     *
     * 背景:Shizuku 服务端在客户端授权后只自动 grant 自身的
     * moe.shizuku.manager.permission.API_V23(见官方 ShizukuService.dispatchPermissionConfirmationResult),
     * 不会代授 WRITE_SECURE_SETTINGS,因此必须显式走 shell 命令。
     * 13.1.5 的 Shizuku API 未导出 getRemoteProcess(),且 ShizukuRemoteProcess
     * 构造函数为包私有,故直接使用 IShizukuService.Stub 与 IRemoteProcess。
     */
    private fun grantWriteSecureSettingsViaShizuku(): Boolean {
        val service = try {
            IShizukuService.Stub.asInterface(Shizuku.getBinder())
        } catch (_: Throwable) {
            null
        } ?: return false

        val proc: IRemoteProcess = try {
            service.newProcess(
                arrayOf("pm", "grant", context.packageName, WRITE_SECURE_SETTINGS),
                null,
                null,
            )
        } catch (_: Throwable) {
            return false
        }

        return try {
            val exitCode = try {
                if (proc.waitForTimeout(10L, TimeUnit.SECONDS.name)) {
                    proc.exitValue()
                } else {
                    -1
                }
            } catch (_: Throwable) {
                -1
            }
            val err = drainStream(proc)
            if (exitCode != 0) {
                setStatus(
                    if (err.isNotBlank()) {
                        "Shizuku 代授写入安全设置权限失败(pm exit=$exitCode: $err)"
                    } else {
                        "Shizuku 代授写入安全设置权限失败(pm exit=$exitCode)"
                    },
                )
            }
            // 严格以系统侧实际授权结果为准。pm 返回 0 不等于已生效 ——
            // 之前用 `hasSecureSetting() || exitCode == 0` 会把「未真正授权」
            // 误判成成功,导致后续写入无障碍设置时抛 SecurityException。
            hasSecureSetting(context)
        } catch (e: Throwable) {
            setStatus("Shizuku 代授写入安全设置权限失败:${e.message}")
            false
        } finally {
            try {
                proc.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    /** 排空 shell 进程的 stderr,用于诊断 pm 命令失败原因 */
    private fun drainStream(proc: IRemoteProcess): String {
        return try {
            val fd = proc.getErrorStream() ?: return ""
            val stream = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(stream)).use { reader ->
                val buf = CharArray(512)
                var n = reader.read(buf)
                while (n > 0 && sb.length < 256) {
                    sb.append(buf, 0, n)
                    n = reader.read(buf)
                }
            }
            sb.toString().trim()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun markSecureGranted() {
        secureGranted = true
        // 只更新权限状态,不自动开启无障碍 —— 开启必须由用户点击开关后触发
        setStatus("已通过 Shizuku 授予写入安全设置权限")
        notifyState()
    }

    @MainThread
    fun refresh() {
        secureGranted = hasSecureSetting(context)
        serviceEnabled = isAccessibilityEnabled(context)
        // 官方流程:binder 存活 ≠ 已授权,以 checkSelfPermission 的实际结果为准。
        // 之前把 pingBinder()==true 直接当 Ready,导致服务运行但未授权时点按钮
        // 静默返回(见 requestShizuku 开头的状态守卫),永远不弹授权框。
        shizukuStatus = if (!pingBinder()) {
            ShizukuStatus.Unready
        } else {
            val granted = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
            if (granted) ShizukuStatus.Ready else ShizukuStatus.Unready
        }
        notifyState()
    }

    /**
     * 无障碍开关:有 WRITE_SECURE_SETTINGS 就直写 Settings。
     * 缺权限时**不跳转任何设置页**,只回调 [onNeedPermission] 由 UI 弹窗询问用户。
     * Shizuku 不参与此链路 —— 它只用于申请「写入安全设置」权限本身。
     */
    @MainThread
    fun toggleAccessibility(onNeedPermission: (() -> Unit)? = null) {
        if (!hasSecureSetting(context)) {
            // 缺权限:不自动跳转,交由 UI 弹窗确认
            setStatus("缺少「写入安全设置」权限,无法开启无障碍服务")
            notifyState()
            onNeedPermission?.invoke()
            return
        }
        if (isAccessibilityEnabled(context)) {
            disableAccessibilityService()
        } else {
            enableAccessibilityService()
        }
    }

    /**
     * 通过「写入安全设置」权限直接写入 ENABLED_ACCESSIBILITY_SERVICES 开启无障碍服务。
     * 兼容 V13/V3 与旧版 URI 两种 ComponentName 格式,写入后轮询确认生效。
     */
    private fun enableAccessibilityService() {
        if (isAccessibilityEnabled(context)) {
            serviceEnabled = true
            setStatus("无障碍服务已开启")
            notifyState()
            return
        }
        setStatus("正在开启无障碍服务…")
        notifyState()

        val resolved = try {
            context.packageManager.resolveService(
                android.content.Intent("android.accessibilityservice.AccessibilityService")
                    .setComponent(serviceComponent()),
                0,
            )
        } catch (_: Throwable) {
            null
        }
        if (resolved == null) {
            setStatus("未找到无障碍服务配置,请检查 manifest")
            notifyState()
            return
        }

        val resolver = context.contentResolver
        val current = Settings.Secure.getString(
            resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        val entries = current.split(":").filter { it.isNotBlank() }
        // flattenToString() 是平台标准序列化格式,
        // 与读取侧 ComponentName.unflattenFromString() 对应
        val flat = ComponentName(
            resolved.serviceInfo.packageName, resolved.serviceInfo.name,
        ).flattenToString()
        val toAdd = listOf(flat).filter { it.isNotBlank() && it !in entries }

        if (toAdd.isNotEmpty()) {
            // WRITE_SECURE_SETTINGS 缺失时不能写 Settings(会抛 SecurityException)。
            // 权限守卫已在 toggleAccessibility() 入口统一处理,正常不会走到这里。
            if (!hasSecureSetting(context)) {
                setStatus("缺少「写入安全设置」权限,无法开启无障碍服务")
                notifyState()
                return
            }
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (entries + toAdd).joinToString(":"),
            )
        }
        try {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: Throwable) {
            // putString 已成功但开关置位失败时,服务通常仍会被系统拉起,继续轮询确认
        }

        // 写入后系统需要一段时间才会真正拉起服务,轮询确认生效
        startPoll(
            condition = { isAccessibilityEnabled(context) },
            onReady = {
                serviceEnabled = true
                setStatus("无障碍服务已开启")
                notifyState()
            },
            onTimeout = {
                serviceEnabled = isAccessibilityEnabled(context)
                setStatus(
                    if (serviceEnabled) "无障碍服务已开启"
                    else "无障碍服务开启失败,请在系统设置中手动开启",
                )
                notifyState()
            },
        )
    }

    /**
     * 用「写入安全设置」权限从 ENABLED_ACCESSIBILITY_SERVICES 移除本应用条目并关闭总开关。
     *
     * 与 [enableAccessibilityService] 完全对称:只删自己的条目,保留其他应用的无障碍服务。
     * 关闭不需要 Shizuku —— WRITE_SECURE_SETTINGS 本身就是设计用途。
     */
    private fun disableAccessibilityService() {
        if (!isAccessibilityEnabled(context)) {
            serviceEnabled = false
            setStatus("无障碍服务已关闭")
            notifyState()
            return
        }
        if (!hasSecureSetting(context)) {
            // 同上:缺权限时不跳转,只提示。正常情况下已由 toggleAccessibility() 拦截。
            setStatus("缺少「写入安全设置」权限,无法关闭无障碍服务")
            notifyState()
            return
        }

        setStatus("正在关闭无障碍服务…")
        notifyState()

        val resolver = context.contentResolver
        val current = Settings.Secure.getString(
            resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        val entries = current.split(":").filter { it.isNotBlank() }
        val own = serviceComponent()
        // 只移除属于自己的条目(packageName + className 双匹配,避免误删其他应用)
        val remaining = entries.filterNot { item ->
            val cn = ComponentName.unflattenFromString(item) ?: return@filterNot false
            cn.packageName == own.packageName && cn.className == own.className
        }
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            remaining.joinToString(":"),
        )
        try {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        } catch (_: Throwable) {
            // 条目已移除,总开关置位失败不影响关闭结果
        }

        // 系统需要一点时间真正卸载服务,轮询确认
        startPoll(
            condition = { !isAccessibilityEnabled(context) },
            onReady = {
                serviceEnabled = false
                setStatus("无障碍服务已关闭")
                notifyState()
            },
            onTimeout = {
                serviceEnabled = isAccessibilityEnabled(context)
                setStatus(
                    if (serviceEnabled) "无障碍服务关闭失败,请在系统设置中手动关闭"
                    else "无障碍服务已关闭",
                )
                notifyState()
            },
        )
    }

    private fun serviceComponent(): ComponentName {
        val pkg = context.packageName
        return ComponentName(pkg, SERVICE_CLASS)
    }

    // ---- 不依赖无障碍事件的前台 Activity 查询(Shizuku shell)----

    /**
     * 通过 Shizuku 执行 `dumpsys activity activities` 解析当前前台 Activity,
     * 优先取 topResumedActivity(Android 10+),回退 mResumedActivity/ResumedActivity。
     * 不依赖无障碍事件,实时准确;Shizuku 不可用/超时返回 null。
     * 阻塞操作,禁止在主线程调用。
     */
    fun queryTopActivityViaShizuku(): ComponentName? {
        val service = try {
            IShizukuService.Stub.asInterface(Shizuku.getBinder())
        } catch (_: Throwable) {
            return null
        } ?: return null

        val proc: IRemoteProcess = try {
            service.newProcess(
                arrayOf("dumpsys", "activity", "activities"),
                null,
                null,
            )
        } catch (_: Throwable) {
            return null
        }

        return try {
            val out = java.util.concurrent.CompletableFuture.supplyAsync {
                try {
                    val stream = android.os.ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream)
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            // 匹配 topResumedActivity=ActivityRecord{... pkg/.Cls ...}
                            val m = ACTIVITY_RECORD_REGEX.find(l) ?: continue
                            // topResumedActivity 优先;先遇到 mResumedActivity 先记下
                            if (l.contains("topResumedActivity")) {
                                return@supplyAsync m.groupValues[1]
                            }
                            if (l.contains("ResumedActivity")) {
                                return@supplyAsync m.groupValues[1]
                            }
                        }
                        null
                    }
                } catch (_: Throwable) {
                    null
                }
            }.get(10, java.util.concurrent.TimeUnit.SECONDS)

            out?.let { flat ->
                val pkg = flat.substringBefore('/')
                val cls = flat.substringAfter('/')
                ComponentName(pkg, if (cls.startsWith(".")) pkg + cls else cls)
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { proc.destroy() } catch (_: Throwable) {}
        }
    }

    private val ACTIVITY_RECORD_REGEX =
        Regex("""ActivityRecord\{[^}]*\s([a-zA-Z0-9._]+/[^}\s]+)\s""")

    // ---- 内部:轮询等待 Shizuku 就绪 / 权限生效 ----

    private fun startPoll(
        condition: () -> Boolean,
        onReady: () -> Unit,
        onTimeout: () -> Unit,
    ) {
        stopPoll()
        var attempts = 0
        val maxAttempts = 40 // 40 * 200ms = 8s
        val runnable = object : Runnable {
            override fun run() {
                attempts++
                val ok = try {
                    condition()
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    stopPoll()
                    onReady()
                    return
                }
                if (attempts >= maxAttempts) {
                    stopPoll()
                    onTimeout()
                    return
                }
                MAIN.postDelayed(this, 200)
            }
        }
        pollRunnable = runnable
        MAIN.postDelayed(runnable, 200)
    }

    private fun stopPoll() {
        pollRunnable?.let(MAIN::removeCallbacks)
        pollRunnable = null
    }

    private fun pingBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun setStatus(text: String) {
        statusText = text
    }

    private fun notifyStateNow() {
        val state = currentState()
        callbacks.toList().forEach { it(state) }
    }

    private fun notifyState() {
        MAIN.post { notifyStateNow() }
    }
}
