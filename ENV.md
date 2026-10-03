# AutoTask 编译环境与复用说明

## 项目配置

- Android Compose 单模块工程,包名 `com.yzjdev.autotask`
- minSdk 26 / targetSdk 36 / compileSdk 36
- 主题:Material 3 Expressive(基于 material3 1.4.0 稳定版手工实现:
  形状 8/10/12/16/28dp 圆角 + 字重加强的字阶 + 动态取色)

## 编译环境(本机 Termux)

| 组件 | 版本 | 说明 |
|---|---|---|
| Gradle wrapper | 9.6.1 | `gradle/wrapper/gradle-wrapper.properties` 已指向 |
| 系统 Gradle | 9.7.0 | 仅用于生成 wrapper(`gradle wrapper --gradle-version 9.6.1`) |
| JDK | OpenJDK 21.0.12 | 路径 `/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk` |
| AGP | 9.1.1 | 内置 Kotlin(KGP 2.2.10),无需 `kotlin-android` 插件 |
| Compose 编译器插件 | 2.2.10 | `org.jetbrains.kotlin.plugin.compose`,版本与内置 Kotlin 一致 |
| Compose BOM | 2025.10.00 | |
| material3 | 1.4.0 | 稳定版(Expressive API 需 1.5.0-alpha+,要 compileSdk 37,本机无 platform 37) |
| core-ktx / lifecycle-runtime-ktx / activity-compose | 1.17.0 / 2.10.0 / 1.13.0 | |
| JDK target | 17 | `compileOptions` source/target 17 |

## 本机路径与缓存

- Android SDK:`ANDROID_HOME=/data/data/com.termux/files/home/android-sdk`
  (platforms/android-36、build-tools/36.0.0、platform-tools、cmdline-tools)
- aapt2 覆盖:`~/.gradle/gradle.properties` 中
  `android.aapt2FromMavenOverride=/data/data/com.termux/files/home/.androidide/aapt2`
- Gradle 依赖缓存:`~/.gradle/caches`(~2.8G)
- Gradle 发行包:`~/.gradle/wrapper/dists/gradle-9.6.1-bin`

## 构建命令

```sh
# 注意:项目位于 sdcard(FUSE 挂载)时 chmod 不生效,用 sh 调用 wrapper
sh gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 复用步骤(主要改包名)

1. 解压 zip:`unzip AutoTask.zip`
2. 改包名(例如改成 `com.other.newapp`):
   - `app/build.gradle.kts`:`namespace` 与 `applicationId` 两处
   - 移动目录 `app/src/main/java/com/yzjdev/autotask/`
     到 `app/src/main/java/com/other/newapp/`
   - 各 Kotlin 文件首行 `package com.yzjdev.autotask`(及子包)
     → `package com.other.newapp`
   - `MainActivity.kt` 中 `import com.yzjdev.autotask.ui.theme.AutoTaskTheme`
     改为新包名
3. 如需换主题颜色/字体,修改 `ui/theme/` 下的 `Theme.kt`、`Type.kt`、`Shape.kt`
4. `sh gradlew assembleDebug` 构建验证

## 升级到官方 MaterialExpressiveTheme(可选)

material3 1.5.0 稳定版发布后(需 compileSdk 37):
- 将 `material3` 升到稳定版,同步升级 Compose BOM 与 AGP
- `Theme.kt` 改用 `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), shapes, typography)`
- 删除 `Shape.kt`/`Type.kt` 中的手工实现即可获得官方 Expressive 形状/字体/动效
