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
        versionCode = 4
        versionName = "0.1.3"
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

// AGP 9 已移除 outputFileName API：打包后自动重命名产物（应用名-v版本号，debug 追加 -debug）
listOf("release" to "", "debug" to "-debug").forEach { (buildType, suffix) ->
    val cap = buildType.replaceFirstChar { it.uppercase() }
    val renameTask = tasks.register("rename${cap}Apk") {
        group = "build"
        doLast {
            val dir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
            val src = dir.resolve("app-$buildType.apk")
            val ver = android.defaultConfig.versionName ?: "0.0.0"
            val dst = dir.resolve("OpenAppDialer-v$ver$suffix.apk")
            if (src.exists()) {
                src.renameTo(dst)
                logger.lifecycle("APK renamed -> ${dst.name}")
            }
        }
    }
    tasks.matching { it.name == "package$cap" }.configureEach { finalizedBy(renameTask) }
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
