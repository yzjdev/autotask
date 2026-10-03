# AGENTS.md — 短剧助手

Android 单模块应用,包名 `com.yzjdev.autogkd`。Kotlin + Jetpack Compose(Material 3 Expressive 风格在 `ui/theme/` 手工实现,因 material3 1.4.0 稳定版尚无 Expressive API)。核心功能:`automation/` 下的无障碍服务自动化引擎 —— GKD 订阅对齐的规则引擎,选择器/规则/订阅三层模型(见下),经 kotlinx-serialization 落盘 SharedPreferences。

## 编译(Termux 本机 — 有坑)

- 项目在 sdcard(FUSE 挂载)上,`chmod` 不生效,必须用 `sh gradlew <task>` 调用 wrapper,不能用 `./gradlew`。
- `sh gradlew assembleDebug` → 产物 `app/build/outputs/apk/debug/app-debug.apk`。
- debug 包名带 `.debug` 后缀(`com.yzjdev.autogkd.debug`)+ versionName `-debug` 后缀 + 应用名「自动化助手 Debug」(`resValue` 覆盖 `app_name`,需 `buildFeatures.resValues = true`),与 release(`com.yzjdev.autogkd`)可并存安装互不覆盖(见 `app/build.gradle.kts` buildTypes.debug)。
- 安装:`PATH=/data/data/com.termux/files/usr/bin:/system/bin adb install -r app/build/outputs/apk/debug/app-debug.apk`(PATH 里的系统 adb 是坏的;Termux android-tools 锁定在 37.0.0,勿升级到 37.0.0-1)。
- 运行时 JDK 21,但 `compileOptions` target 17;SDK 在 `ANDROID_HOME=/data/data/com.termux/files/home/android-sdk`;aapt2 经 `~/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride` 覆盖。这些都别"修"。
- 没有 `kotlin-android` 插件:AGP 9.1.1 内置 Kotlin,只应用 `org.jetbrains.kotlin.plugin.compose` 和 `kotlin.plugin.serialization`。版本集中在 `gradle/libs.versions.toml`。
- 无单元/仪器测试;`sh gradlew assembleDebug` 就是验证步骤。用户没说"编译/安装"就不要编译安装,改完代码即停。

## 架构

- `MainActivity.kt` + `ui/` — Compose 界面(任务列表/编辑器/远程订阅管理弹窗,主页含订阅入口卡片)。
- `automation/` — 引擎核心,GKD 订阅对齐(gkd.li/guide 三层):
  - `GkdSelector.kt` + `GkdSelectorRuntime.kt` — GKD 选择器,移植自 gkd-li/gkd 仓库 gkd-selector 模块(源码在 `~/tmp/gkd-src`,上游 github.com/gkd-kit/gkd):ParserCursor 直解文法(PropertySyntaxParser/RelationSyntaxParser/SelectorParser 逐文件照抄)、帧式逻辑表达式(逻辑运算符两侧必须 () 包裹)、元组/an+b 多项式关系(最多两个单项式,含 12-n / n+6 / -3n+10)、@ 目标锚定 + MatchContext.prev 链回溯匹配(CompiledUnitSelector.matchPath 语义)、BuiltinMembers 内建方法(or/and/ifElse 短路、equal/notEqual 容 null、Int 算术/比较、String get/at/substring/toInt/indexOf);属性集对齐 A11yContext.getCacheAttr(id/vid/name/text/desc/…/left/top/right/bottom/width/height/index/depth/childCount/parent + getChild 调用)。表达式字符串持久化,进程内 LRU 缓存编译结果。
  - `GkdTask.kt` — 规则模型(v6):`GkdTask`(= GKD 规则组,packageName 空 = 全局规则)+ `Rule`(matches/anyMatches/excludeMatches/preKeys/action/position/swipeArg)+ `Action` 全量动作枚举;RawCommonProps 字段(actionCd/actionDelay/matchTime/actionMaximum/resetMatch/order/forcedTime/priorityTime/disableIfAppGroupMatch 等)组级默认+规则级覆盖。
  - `GkdSubscription.kt` — 订阅 JSON5 ↔ 模型双向映射:内置 JSON5 清洗器(字符串感知:单引号/裸键/尾逗号/注释),rules 数组与单对象两形态、globalGroups 全局规则均导入;导出回 GKD 订阅格式。
  - `SubscriptionStore.kt` / `SubscriptionFetcher.kt` — 远程订阅:URL 列表 + 原文落盘,标准库 HTTP 拉取(手动跨协议重定向);规则合并带 `sub_<id>_` 前缀去重。大订阅(1MB+)解析在 Default/IO 线程。
  - `TaskRunner.kt` — 调度执行器:优先级(order/priorityTime)排序、preKeys 顺序链、actionCd/actionMaximum 及其 Key 共享、matchTime 休眠、resetMatch 三策略(app/activity/match)、forcedTime 主动轮询(250ms)。
  - 其余:`DramaAccessibilityService`(无障碍入口)、`TaskStore`/`LogStore`(持久化)、`AutomationManager`(编排)、悬浮窗(`SystemAlertWindow`、`Overlay*Window`)。
- `crash/` — 全局崩溃捕获(`CrashReporter` → `CrashActivity`)。
- `java/google/android/accessibility/` — 内嵌的无障碍类;用 HiddenApiBypass 绕过 hidden API 限制,反射 `IActivityTaskManager` 查前台 Activity。
- Shizuku 集成:api + provider + aidl 三个依赖 — aidl stub 通过 `newProcess` 执行 shell 命令并代授权限。
- 设计文档/方案:`docs/superpowers/plans/`(如 task-runner 重设计 spec);`ENV.md` 记录编译环境和改包名复用步骤。

## 约定

- 提交信息用 Conventional Commits(`feat(automation):`、`fix(ui):`、`refactor:`),英文 subject。
- 代码库中中文注释/中文命名属正常情况,需保留。
- 当项目结构、编译/测试命令、架构边界、开发约定或本文档记录的其他事实发生变化时,在同一次修改中同步更新本文件。
