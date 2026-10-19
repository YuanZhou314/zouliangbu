# 本机构建环境（zouliangbu 实际跑通过的配置快照）

> 用于在另一台机器上 1:1 复现当前的构建结果。
> 时间：2026-10-19
> 仅记录 *这一步能跑通 assembleDebug* 所必需的依赖和路径。后续引入新依赖请同步更新本文。

## 1. 操作系统 & Shell

| 项 | 值 |
|---|---|
| OS | macOS（Darwin 23.6.0，Sonoma 14.x） |
| Shell | zsh（默认） |

## 2. JDK

| 项 | 值 |
|---|---|
| 版本 | OpenJDK 17.0.12 LTS |
| 路径 | `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home` |
| 启用方式 | `JAVA_HOME` 环境变量，gradle.properties 不需要强制指定 |

> 不要装 JDK 21+，AGP 8.5 不要求，但 KSP/Kotlin 1.9.24 在 JDK 21 上偶尔会触发元空间警告，**锁 JDK 17** 是当前最稳的组合。

## 3. Android SDK

| 项 | 值 |
|---|---|
| 安装位置 | `/Users/zhouyinglin/Apps/android-sdk`（**非默认路径**，已在 `local.properties` 显式指向） |
| ANDROID_HOME | `Env`（已导出） |
| platforms | `android-34`, `android-35`, `android-36`（compileSdk/targetSdk 用 34） |
| build-tools | `36.0.0`, `36.1.0` |
| platform-tools | adb |
| cmdline-tools | `latest`（提供 sdkmanager） |
| cmake | 已装 |
| ndk | 已装 |
| emulator / system-images | **未装**（本项目构建不需要） |

> `local.properties` 里的 `sdk.dir` 必须在每台机器上单独改写，**不要提交到 git**（.gitignore 已排除）。

## 4. Gradle & Wrapper

| 项 | 值 |
|---|---|
| 系统 Gradle | **未装**（用项目自带 wrapper） |
| Wrapper 版本 | Gradle 8.7 |
| Wrapper jar | `gradle/wrapper/gradle-wrapper.jar`（用 `gradle wrapper` 从完整 distribution 生成） |
| distributionUrl | `https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip`（**腾讯云镜像**，替代官方 services.gradle.org，国内下载更快） |

### 首次构建需要的 wrapper jar

如果仓库里没有 `gradle-wrapper.jar`，在一台装了 Gradle 8.7 的机器上执行：

```bash
mkdir -p gradle/wrapper
gradle wrapper --gradle-version 8.7 --distribution-type bin
# 然后把 gradle-wrapper.properties 里的 distributionUrl 改到腾讯云镜像
```

## 5. 关键 Gradle 插件版本（已经在 build.gradle.kts 里写死）

| 插件 | 版本 |
|---|---|
| Android Gradle Plugin (AGP) | 8.5.0 |
| Kotlin | 1.9.24 |
| KSP | 1.9.24-1.0.20 |
| Compose Compiler | 1.5.14（与 Kotlin 1.9.24 配对） |

## 6. 关键第三方依赖版本（app/build.gradle.kts）

| 库 | 版本 | 说明 |
|---|---|---|
| Compose BOM | 2024.06.00 | |
| Room | 2.6.1 | 用 KSP 处理注解 |
| Coroutines | 1.8.0 | |
| AMap (高德) 3D | **10.0.600** | 见下方「AMap 踩坑」 |

> AMap **不**需要单独的 `com.amap.api:location` 依赖：3D map 10.x 已经把 AMapLocation/AMapLocationClient 内嵌到自身 jar 里，再加 location 会触发 Duplicate class 错误。

## 7. 网络镜像（必须，国内网络下构建速度从 ~30min 降到 ~3min）

`settings.gradle.kts` 的 pluginManagement 和 dependencyResolutionManagement 都用了阿里云镜像，顺序是：

```
https://maven.aliyun.com/repository/gradle-plugin
https://maven.aliyun.com/repository/google
https://maven.aliyun.com/repository/public
https://maven.aliyun.com/repository/central
https://jitpack.io
google()
mavenCentral()
```

`gradle.properties` 里加了 `systemProp.java.net.preferIPv4Stack=true`（阿里云 IPv6 不通）。

## 8. 高德 AMap Key

**当前仓库里的 Key 是占位符 `REPLACE_WITH_YOUR_AMAP_KEY`。** 没有真实 Key 时 `app-debug.apk` 仍可编译/安装，但地图区域会显示「请配置高德 Key」的占位视图。

### 申请流程（首次跑真机/模拟器前必须）

1. 访问 https://lbs.amap.com 注册开发者账号。
2. 控制台 → 应用管理 → 创建新应用 → 平台选「Android」。
3. 填入：
   - Package Name = `com.zouliangbu.footprint`
   - SHA1 = `keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebug | grep SHA1`
     或 `./gradlew :app:signingReport`
4. 把高德下发的 32 字符 Key 替换 `app/src/main/res/values/strings.xml` 里的 `amap_api_key`。
5. 重新 `./gradlew assembleDebug`。

## 9. 验证构建

```bash
cd /Users/zhouyinglin/agent/projects/zouliangbu
./gradlew assembleDebug
ls -la app/build/outputs/apk/debug/app-debug.apk
# → 期望 ~52 MB（含 libAMapSDK_MAP_v10_0_600.so）
```

## 10. 已知不在此环境内 / 跑不通

- ❌ **Android 模拟器**：`system-images` 是空的，没有 AVD。`adb devices` 始终为空。
- ❌ **真机**：没插。
- ❌ **Android Studio**：未装（构建只靠命令行 Gradle + wrapper）。
- ❌ **安装到设备**：因上面 ❌，从未跑过 `adb install`。需要插手机或装模拟器后才能验证。