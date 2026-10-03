plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.composedemo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.composedemo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "yzjdev"
            keyAlias = "yzjdev"
            keyPassword = "yzjdev"
        }
    }

    buildTypes {
        debug {
            // debug 构建独立包名 + 版本/应用名后缀,与 release 可并存安装互不覆盖
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "自动化助手 Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // AGP 8.x+ 默认 R8 full mode;显式声明防止全局 gradle.properties 覆盖回 compat 模式
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // buildTypes.debug 的 resValue(app_name 后缀)需要开 resValues
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Shizuku: api 提供运行时/静态调用, provider 提供 ContentProvider 授权入口
    // aidl 提供 IShizukuService/IRemoteProcess stub,用于 newProcess 执行 shell 命令代授权限
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.aidl)

    // 绕过 hidden API 限制,解锁 IActivityTaskManager 反射(查前台 Activity,不依赖事件/Shizuku)
    implementation(libs.hidden.api.bypass)

    // v4 任务模型序列化(Task/Step/Trigger 密封类落盘 SharedPreferences)
    implementation(libs.kotlinx.serialization.json)
}
