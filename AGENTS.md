# AGENTS.md - open-appdialer

开源复刻 AppDialer Pro：T9 九宫格搜索启动 Android 应用。规格见 `docs/spec.md`（实现以它为准，先改文档再改代码）。

## Build Environment

Android SDK: `~/Android/Sdk`（local.properties 已指向）。JDK 用系统的 21：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
```

JDK 27（默认 java）对 Gradle 9.3.1 太新，务必先 export 上面的 JAVA_HOME。

## Commands

```bash
./gradlew assembleDebug          # 构建
./gradlew testDebugUnitTest      # 单测（匹配引擎表驱动测试在 app/src/test）
adb install -r app/build/outputs/apk/release/OpenAppDialer-v*.apk
```

APK 产物命名：`OpenAppDialer-v<版本>[-debug].apk`（如 `OpenAppDialer-v0.1.1.apk`）。

## Conventions

- 文档驱动：`docs/spec.md` 是唯一实现依据，行为变更先改 spec（版本号 +1，变更记录追加）
- **打包/发版仅限用户明确要求**：不要主动 assembleRelease / 打 tag / gh release；日常代码提交照常
- 匹配引擎（`search/` 包）必须纯 Kotlin、无 Android 依赖，表驱动单测先行（spec 7.1）
- 无网络权限、无运行时权限弹窗；权限清单见 manifest 注释
- 设备安全：不要在用户的物理手机上安装/运行测试构建，除非用户明确要求
