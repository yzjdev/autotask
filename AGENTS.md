# AGENTS.md

AutoTask(自动化助手)—— Android 自动化应用:基于无障碍的节点读取与手势执行、调试悬浮窗,以及经 Shizuku 实现的按应用禁网。单 Gradle 模块,Kotlin + Jetpack Compose,包名 `com.yzjdev.autotask`,rootProject `AutoTask`。在 Termux 上开发与构建(项目位于 `/storage/emulated/0`)。

## 构建
- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`(debug 带 `.debug` applicationId 后缀,可与 release 并存)
- `./gradlew :app:assembleRelease` —— 使用 `app/release.keystore` 签名(凭据内联在 `app/build.gradle.kts`)
- 无测试、无 lint/ktlint 配置、无 CI。用户未明确要求时,不要构建/安装/验证。
- 工具链:Gradle wrapper 9.6.1、AGP 9.1.1、Kotlin 2.2.10、compileSdk 36 / minSdk 26。所有版本只写在 `gradle/libs.versions.toml`。
- material3 1.4.0:`SmallTopAppBar` 与 `SearchBarDefaults.MinHeight` 已不存在;改用 `PrimaryTabRow`/`SecondaryTabRow`(裸 `TabRow` 已弃用);LazyColumn 的 `items()` 没有 `label` 参数。

## 架构
- `MainActivity.kt`(约 1.5k 行)承载全部 Compose UI:`AutomationScreen`(底部导航:首页 / 应用 / 日志)、`HomeScreen`(服务中控面板、悬浮窗开关、activity 记录开关、权限卡片)、带按应用禁网开关的应用列表,点卡片进入该应用的规则页(自动化开关在页内),以及日志页。
- `automation/` —— 核心是 `AutoTaskAccessibilityService`(事件源、节点遍历、手势执行、悬浮窗);`AutomationManager` 负责 Shizuku 连接 + `WRITE_SECURE_SETTINGS` + 无障碍开关状态;`ShizukuShell` 执行特权 shell;`InstalledApps` 查询已安装应用列表;`AppIconCache` 提供应用图标 LRU 缓存;`NodeSnapshotCollector` 为调试悬浮窗提供节点快照。
- `rule/` —— 内置自动化规则:规则写在代码里(`BuiltinRule` + `BuiltinRules` 是规则表 —— 包名 + Activity + 触发事件集合 `watchEvents` + 条件列表 `conditions` + 动作,加自动化就往表里加一条),界面只做展示与启停(`RuleScreen` 按应用列出内置规则,没有新增/编辑)。条件用 `NodeMatch` 描述(vid 去 `包名:id/` 前缀、类名只比简单名、描述全等),**列出的节点都不存在**才执行动作;任一个在场就只跳过本次动作、不结束会话,条件恢复后下一次事件照旧。规则**默认关闭**,由规则页上的开关按包名开启(`RulePolicy`,存 `rule_policy`);没开启的应用等同没有规则,运行时不会开会话;开关切换后经 `notifyRulePolicyChanged` 立刻重判会话,并在每次动作前复查开关 —— 不依赖窗口状态变化,否则已经在跑的会话会一直跑到下一次窗口事件。`BuiltinRuleRunner` 随服务存活且**完全事件驱动,没有定时轮询**:窗口状态变化决定会话开关(实时解析的 Activity 命中才开会话,离开即结束;必须走服务上的 `liveActivity()`,不能走 `currentActivity()` —— 后者会退回 `getValidActivity`/`lastActivity` 缓存值,同一个应用里别的页面也会被认成目标页面)。会话内按规则声明的 `watchEvents` 逐类订阅服务上的 `subscribeEvent(eventType, listener)`(退订走 `unsubscribeEvent`),服务在 `onAccessibilityEvent` 里按事件类型分发,只传来源包名 —— 事件对象会被系统回收,不能外传。新增事件类型只需在 `RuleEvent` 里加一项并在规则里声明,分发侧不用改(`RuleEvent` 已把 `AccessibilityEvent.TYPE_*` 全部枚举齐)。密集事件按合并窗口收敛成一次检测,上一次动作没走完则把检测排到动作间隔结束,不叠加手势。硬约束:规则查找**必须走服务上的 `hasNode`/`walkNodes`**,不能复用 `findFirst`/`traverseNodes` —— 后者命中节点即 `recycle()`,且 `processedNodes` 去重集合只在服务连接时清空,复用会第二次就查不到东西;运行时的无障碍调用(遍历、手势)都必须在主线程,故整体跑在主线程调度器上。
- 应用列表加载有性能约束:`InstalledApps.load` 内部已切到 `Dispatchers.IO` 并以信号量限流并发解析显示名(显示名要逐个加载 APK 资源,是主要耗时,不要退回 `parallelStream`/`commonPool`,会和渲染抢 CPU);图标由卡片进入组合时经 `rememberAppIcon` 按需加载,不要改回进 tab 就全量预解码;列表数据只保留一份状态,拆成多份状态会反复触发重组。
- 无障碍服务在 manifest 中以伪装系统名 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`(11 行空壳子类)注册,用于绕过校验服务包名的应用;全部逻辑仍在父类 `AutoTaskAccessibilityService` 中。不要「修正」这一点。
- `crash/` 运行在独立的 `:crash` 进程,使崩溃页在主进程死亡后仍存活。
- 持久化仅用普通 SharedPreferences(`recents_ui`、`net_policy`、`apps_ui`、`rule_policy`)。不依赖序列化库 —— 禁网包名集合与规则开关集合都以字符串集合形式存储。

- 设备安装:`~/install-app.sh --debug`(先构建,再经 `am start` 拉起系统安装器 —— 由你在屏幕上确认)。此处直接 `adb install` 会得到 `INSTALL_FAILED_ABORTED`。

## 主题
`ui/theme/` 是静态的:`Color.kt` 硬编码一套品牌配色(紫罗兰主色 / 青色次色 / 玫瑰三级色),`AutoTaskTheme` 依 `isSystemInDarkTheme()` 切换明暗,动态取色硬编码关闭。没有面向用户的主题设置。若要新增,注意 JVM 单元测试下 `android.graphics.Color` 与 `androidx.core.graphics.ColorUtils` 是桩实现(抛 "not mocked")—— 需要可测试的颜色运算请用纯 Kotlin。

## 注意事项
- 悬浮窗(`OverlayDebugWindow`/`OverlayToggleWindow`/`OverlayWindowListWindow`/`OverlayActivityRecorder`)使用 `TYPE_ACCESSIBILITY_OVERLAY`,不需要 `SYSTEM_ALERT_WINDOW` 权限;不要添加。四者共用 `OVERLAY_ALPHA`(定义在 `OverlayDebugWindow.kt` 顶层)控制窗口不透明度。调试悬浮窗由主页开关控制(服务上的 `showDebugOverlay`),其悬浮球单击绘制/收起焦点激活窗口的节点边界、长按唤起窗口列表;窗口列表在调试悬浮窗显示时就被预加入(隐藏态,层级落在信息卡之后、悬浮球之前),打开期间隐藏悬浮球、关闭后恢复;点选某窗口即绘制该窗口的节点边界。全部在 unbind/destroy 时彻底移除。`OverlayActivityRecorder` 是常驻右上角的圆点按钮,随服务连接自动显示:点按开始/停止记录,记录中在按钮下方展开一块只读信息板(独立窗口,`FLAG_NOT_TOUCHABLE`,不抢触摸、不挡下层应用),每次无障碍事件都重新解析 Activity(经服务上的 `addAnyEventListener` 订阅,事件不限类型,只在记录期间订阅,同一轮主循环内的多次事件合并成一次刷新;窗口状态变化且类名经 `isRealActivity` 确认时直接采用事件里的包名/类名 —— 窗口标题在部分 ROM 上不含组件名,只认 `liveActivity()` 会一直拿不到活动 —— 其余情况退回 `currentActivity()`)显示包名/类名并保留最近 6 次变化;面板下半部分是事件日志,只记 `AutoTaskAccessibilityService.DEBUG_PACKAGE`(红果 `com.phoenix.read`)每次事件的 eventType 值(数字 + `eventTypeToString` 名称),保留最近 8 条 —— 活动与事件都只进悬浮窗,**不写日志**;按钮上不显示 Activity 名,主页的「activity 记录」开关与它共用同一份状态(服务上的 `isActivityRecording`/`setActivityRecording`/`addActivityRecordingListener`)。
- manifest 的权限块注释记录了理由(WRITE_SECURE_SETTINGS 必须声明,否则 Shizuku 的 `pm grant` 静默失败;`getInstalledPackages` 与 `getActivityInfo` 在 Android 11+ 的包可见性都依赖 QUERY_ALL_PACKAGES)。改动权限前先读。
- 按应用禁网经 Shizuku 执行 `cmd connectivity set-chain3-enabled` / `set-package-networking-enabled`;系统应用(appId 1000)会被平台拒绝。命令以互斥锁串行化,因为 Shizuku binder 在并发 `transact` 下会挂。
- `shizuku-aidl` 是 `newProcess` 所必需;只有 `shizuku-api` + `provider` 不包含 IShizukuService 桩。
- 注释使用中文;保持简短,不要写变更历史叙述。

## 维护
当此处记录的构建/测试命令、结构、架构边界、版本或约定发生变化时,在同一次改动中更新本文件。
