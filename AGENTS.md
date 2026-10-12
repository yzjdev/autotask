# AGENTS.md

AutoTask(自动化助手)—— Android 自动化应用:基于无障碍的节点读取与手势执行、调试悬浮窗,以及经 Shizuku 实现的按应用禁网。单 Gradle 模块,Kotlin + Jetpack Compose,包名 `com.yzjdev.autotask`,rootProject `AutoTask`。在 Termux 上开发与构建(项目位于 `/storage/emulated/0`)。

## 构建
- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`(debug 带 `.debug` applicationId 后缀,可与 release 并存)
- `./gradlew :app:assembleRelease` —— 使用 `app/release.keystore` 签名(凭据内联在 `app/build.gradle.kts`)
- 无测试、无 lint/ktlint 配置、无 CI。用户未明确要求时,不要构建/安装/验证。
- 工具链:Gradle wrapper 9.6.1、AGP 9.1.1、Kotlin 2.2.10、compileSdk 36 / minSdk 26。所有版本只写在 `gradle/libs.versions.toml`。
- material3 1.4.0:`SmallTopAppBar` 与 `SearchBarDefaults.MinHeight` 已不存在;改用 `PrimaryTabRow`/`SecondaryTabRow`(裸 `TabRow` 已弃用);LazyColumn 的 `items()` 没有 `label` 参数。

## 架构
- `MainActivity.kt`(约 1.5k 行)承载全部 Compose UI:`AutomationScreen`(底部导航:首页 / 应用)、`HomeScreen`(服务中控面板、悬浮窗开关、权限卡片),以及带按应用禁网开关的应用列表。
- `automation/` —— 核心是 `AutoTaskAccessibilityService`(事件源、节点遍历、手势执行、悬浮窗);`AutomationManager` 负责 Shizuku 连接 + `WRITE_SECURE_SETTINGS` + 无障碍开关状态;`ShizukuShell` 执行特权 shell;`InstalledApps` 查询已安装应用列表;`AppIconCache` 提供应用图标 LRU 缓存;`NodeSnapshotCollector` 为调试悬浮窗提供节点快照。
- 应用列表加载有性能约束:`InstalledApps.load` 内部已切到 `Dispatchers.IO` 并以信号量限流并发解析显示名(显示名要逐个加载 APK 资源,是主要耗时,不要退回 `parallelStream`/`commonPool`,会和渲染抢 CPU);图标由卡片进入组合时经 `rememberAppIcon` 按需加载,不要改回进 tab 就全量预解码;列表数据只保留一份状态,拆成多份状态会反复触发重组。
- 无障碍服务在 manifest 中以伪装系统名 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`(11 行空壳子类)注册,用于绕过校验服务包名的应用;全部逻辑仍在父类 `AutoTaskAccessibilityService` 中。不要「修正」这一点。
- `crash/` 运行在独立的 `:crash` 进程,使崩溃页在主进程死亡后仍存活。
- 持久化仅用普通 SharedPreferences(`recents_ui`、`net_policy`、`apps_ui`)。不依赖序列化库 —— 禁网包名集合以字符串集合形式存储。

- 设备安装:`~/install-app.sh --debug`(先构建,再经 `am start` 拉起系统安装器 —— 由你在屏幕上确认)。此处直接 `adb install` 会得到 `INSTALL_FAILED_ABORTED`。

## 主题
`ui/theme/` 是静态的:`Color.kt` 硬编码一套品牌配色(紫罗兰主色 / 青色次色 / 玫瑰三级色),`AutoTaskTheme` 依 `isSystemInDarkTheme()` 切换明暗,动态取色硬编码关闭。没有面向用户的主题设置。若要新增,注意 JVM 单元测试下 `android.graphics.Color` 与 `androidx.core.graphics.ColorUtils` 是桩实现(抛 "not mocked")—— 需要可测试的颜色运算请用纯 Kotlin。

## 注意事项
- 悬浮窗(`OverlayDebugWindow`/`OverlayToggleWindow`/`OverlayWindowListWindow`)使用 `TYPE_ACCESSIBILITY_OVERLAY`,不需要 `SYSTEM_ALERT_WINDOW` 权限;不要添加。三者共用 `OVERLAY_ALPHA`(定义在 `OverlayDebugWindow.kt` 顶层)控制窗口不透明度。调试悬浮窗由主页开关控制(服务上的 `showDebugOverlay`),其悬浮球单击绘制/收起焦点激活窗口的节点边界、长按唤起窗口列表;窗口列表在调试悬浮窗显示时就被预加入(隐藏态,层级落在信息卡之后、悬浮球之前),打开期间隐藏悬浮球、关闭后恢复;点选某窗口即绘制该窗口的节点边界。全部在 unbind/destroy 时彻底移除。
- manifest 的权限块注释记录了理由(WRITE_SECURE_SETTINGS 必须声明,否则 Shizuku 的 `pm grant` 静默失败;`getInstalledPackages` 与 `getActivityInfo` 在 Android 11+ 的包可见性都依赖 QUERY_ALL_PACKAGES)。改动权限前先读。
- 按应用禁网经 Shizuku 执行 `cmd connectivity set-chain3-enabled` / `set-package-networking-enabled`;系统应用(appId 1000)会被平台拒绝。命令以互斥锁串行化,因为 Shizuku binder 在并发 `transact` 下会挂。
- `shizuku-aidl` 是 `newProcess` 所必需;只有 `shizuku-api` + `provider` 不包含 IShizukuService 桩。
- 注释使用中文;保持简短,不要写变更历史叙述。

## 维护
当此处记录的构建/测试命令、结构、架构边界、版本或约定发生变化时,在同一次改动中更新本文件。
