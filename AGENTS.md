# AGENTS.md — 短剧助手

Android 单模块应用,包名 `com.example.composedemo`。Kotlin + Jetpack Compose(Material 3 Expressive 风格在 `ui/theme/` 手工实现,因 material3 1.4.0 稳定版尚无 Expressive API)。核心功能:`automation/` 下的无障碍服务自动化引擎,按脚本执行任务(Task/Step/NodeLocator 密封类,经 kotlinx-serialization 落盘 SharedPreferences)。

## 编译(Termux 本机 — 有坑)

- 项目在 sdcard(FUSE 挂载)上,`chmod` 不生效,必须用 `sh gradlew <task>` 调用 wrapper,不能用 `./gradlew`。
- `sh gradlew assembleDebug` → 产物 `app/build/outputs/apk/debug/app-debug.apk`。
- 安装:`PATH=/data/data/com.termux/files/usr/bin:/system/bin adb install -r app/build/outputs/apk/debug/app-debug.apk`(PATH 里的系统 adb 是坏的;Termux android-tools 锁定在 37.0.0,勿升级到 37.0.0-1)。
- 运行时 JDK 21,但 `compileOptions` target 17;SDK 在 `ANDROID_HOME=/data/data/com.termux/files/home/android-sdk`;aapt2 经 `~/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride` 覆盖。这些都别"修"。
- 没有 `kotlin-android` 插件:AGP 9.1.1 内置 Kotlin,只应用 `org.jetbrains.kotlin.plugin.compose` 和 `kotlin.plugin.serialization`。版本集中在 `gradle/libs.versions.toml`。
- 无单元/仪器测试;`sh gradlew assembleDebug` 就是验证步骤。用户没说"编译/安装"就不要编译安装,改完代码即停。

## 架构

- `MainActivity.kt` + `ui/` — Compose 界面(任务列表/编辑器)。
- `automation/` — 引擎核心:`DramaAccessibilityService`(无障碍服务入口)、`TaskRunner`(协程步骤执行器)、`Task.kt`(v4 Task/Step/NodeLocator 模型)、`TaskStore`/`LogStore`(持久化)、`AutomationManager`(编排)、悬浮窗(`SystemAlertWindow`、`Overlay*Window`)提供悬浮控制/调试。
- `crash/` — 全局崩溃捕获(`CrashReporter` → `CrashActivity`)。
- `java/google/android/accessibility/` — 内嵌的无障碍类;用 HiddenApiBypass 绕过 hidden API 限制,反射 `IActivityTaskManager` 查前台 Activity。
- Shizuku 集成:api + provider + aidl 三个依赖 — aidl stub 通过 `newProcess` 执行 shell 命令并代授权限。
- 设计文档/方案:`docs/superpowers/plans/`(如 task-runner 重设计 spec);`ENV.md` 记录编译环境和改包名复用步骤。

## 约定

- 提交信息用 Conventional Commits(`feat(automation):`、`fix(ui):`、`refactor:`),英文 subject。
- 代码库中中文注释/中文命名属正常情况,需保留。
- 当项目结构、编译/测试命令、架构边界、开发约定或本文档记录的其他事实发生变化时,在同一次修改中同步更新本文件。
