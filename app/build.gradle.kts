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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
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
