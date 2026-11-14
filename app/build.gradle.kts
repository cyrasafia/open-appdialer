plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cyrasafia.openappdialer"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.cyrasafia.openappdialer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 本地试用阶段用 debug 签名；正式发布前换独立 keystore（spec 6.4）
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // F2 匹配引擎拼音数据（M2 以源码方式引入 —— 原坐标 com.github.promeg:tinypinyin
    // 发布在已关停的 jcenter，Maven Central/JitPack 均不可靠；Apache-2.0 允许 vendoring）

    // F5 主题偏好存储（M3 启用）
    implementation(libs.androidx.datastore.preferences)

    // F1 数据层缓存序列化（apps.json）
    implementation(libs.kotlinx.serialization.json)

    // vendored TinyPinyin 的传递依赖（Aho-Corasick，Apache-2.0）
    implementation(libs.ahocorasick)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
