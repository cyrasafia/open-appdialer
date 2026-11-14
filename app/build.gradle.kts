import java.util.Properties

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

    signingConfigs {
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.cyrasafia.openappdialer"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    applicationVariants.all {
        outputs.all {
            // 产物命名：应用名-v版本号（debug 追加 -debug）
            val suffix = if (buildType.name == "debug") "-debug" else ""
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "OpenAppDialer-v${versionName}${suffix}.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 本机 keystore.properties 存在时用正式签名；否则（如 CI）回退 debug 签名
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
