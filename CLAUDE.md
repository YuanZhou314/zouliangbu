# CLAUDE.md — 走路簿 (zouliangbu) 项目速记

> 给 Claude Code 看的项目速记。`README.md` / `LOCAL_ENV.md` 是给人和复现用的——这个文件
> **只**记 Claude 下次进来时最可能踩坑的几个点，不重复它们的内容。

## 项目一句话
极简户外 GPS 轨迹记录 Android App（最小化两步路）。Kotlin + Compose + 高德 + Room。

## 双轨地图架构（关键，本次会话产物）
地图入口 `ui/map/MapViewWrapper.kt`，按 `data/AmapKey.kt::isConfigured()` 路由：

- **有高德 Key** → `ui/map/AmapMapView.kt`（高德 MapView）
- **无高德 Key** → `ui/map/OsmMapView.kt`（osmdroid + 极图 GeoQ 瓦片，无需 token）

两条分支并存。Key 下发后只需替换 `res/values/strings.xml` 的 `amap_api_key`，业务代码零改动。

注意：
- 定位 SDK（`location/LocationProvider.kt` 用 `AMapLocationClient`）**不依赖 Key**，独立可用。
- 当前实际跑的是 osmdroid 分支（高德 Key 还在申请中）。

## 不要踩的坑（来自本会话真实经验）
1. **AMap 10.x 已内嵌 location 类**：不要单独加 `com.amap.api:location` 依赖，会
   `Duplicate class`。`app/build.gradle.kts` 只声明 `com.amap.api:3dmap`。
2. **OSM 官方瓦片国内被墙**：`tile.openstreetmap.org` 加载超时会让 MapView 空白。
   `OsmMapView.kt` 用的是极图 GeoQ（`map.geoq.cn`，GCJ-02 坐标系）。
3. **后台定位必须单独请求**：`ACCESS_BACKGROUND_LOCATION` 跟其他 dangerous 权限
   同组请求时，Android 10+ 系统静默忽略整组 → 啥都不弹窗。`MainScreen.kt` 已拆两步。
4. **Android 14+ `startForeground(type=LOCATION)`** 需要 `ACCESS_FINE_LOCATION` 已授予，
   否则 SecurityException 把进程拖崩。`RecordingTrackService.onStartCommand` 已加
   try/catch + `Log.e("TrackService", ...)` 兜底。
5. **`permissionLauncher` 不能在自己的 callback 里再 `launch` 自己**：会触发
   "未初始化前引用"编译错误。需要拆两个 launcher（见 `MainScreen.kt`）。

## 模拟器与构建（装机实测后）
6. **AMap SDK 隐私合规需要在两个类上分别调用**（**关键坑，必读**）：
   - `MapsInitializer.updatePrivacyShow/Agree` → 高德地图 SDK
   - `AMapLocationClient.updatePrivacyShow/Agree` → 高德定位 SDK
   只调一个，`AMapLocationClient` 构造时仍会抛异常
   （`***确保调用SDK任何接口前先调用更新隐私合规***`），把进程拖崩。
   修复在 `FootprintApp.onCreate` + `LocationProvider.init` 两处冗余调用。
7. **android-34 上只有 ATD 镜像能装且 ATD 不渲染 framebuffer**
   （screencap 永远返回同一张纯黑 PNG，screenrecord 0 字节），
   **用 android-33 的 `system-images;android-33;default;arm64-v8a`** 替代：
   纯 AOSP、无 Google 服务、最轻、framebuffer 正常输出。
   当前 `app/build.gradle.kts` `compileSdk = 33`、`targetSdk = 33`。
8. **compileSdk=33 下不能用 `Build.VERSION_CODES.UPSIDE_DOWN_CAKE`**（API 34 常量
   不存在于 33 jar）。`RecordingTrackService.startForegroundCompat` 改用数字字面量 `34`。
9. **`Modifier.navigationBarsPadding()` 是必须的**：`enableEdgeToEdge()` 让
   Compose 画到 system bar 后面，没这个 padding 底部按钮会被 nav bar 盖住一半，
   `uiautomator dump` 的 app bounds 是 `0..2072`（不含 nav bar），但视觉上按钮
   看着在 y=2200 附近，tap 落到 nav bar home 键 → app 被切到后台。
10. **Gradle 增量构建坑**：改完 Kotlin 文件后如果只跑 `./gradlew assembleDebug`，
    有时会 `UP-TO-DATE` 不重打 APK，导致修改没生效（但返回 BUILD SUCCESSFUL）。
    **必须 `touch <改的文件> + ./gradlew assembleDebug` 或加 `--rerun-tasks`**。
11. **AMapLocationClient 构造失败的 try/catch 必须在 callbackFlow 同步块里**，
    异步抛出的异常不会被外层 catch 到——崩溃堆栈停在 `LocationProvider.kt:27`
    （callbackFlow 入口），不是因为 try 没生效。**对策：构造本身就 try/catch
    包住，捕获后 `close(t)` 让 Flow 结束。**
12. **`adb emu geo fix <lng> <lat>`** 注入 GPS 给 AMapLocationClient（Hight_Accuracy
    模式）会被接受，配合 `adb shell pm grant` 一次性授权后无需 UI 对话框。
    坐标用 GCJ-02 没问题（注入的值会被当成 AMap 原始输出）。
13. **`adb shell input tap` 落点看 view 树**：uiautomator 拿不到 Compose
    `Button` 的完整 bounds（dump 时是 (0,0,0,0)），但能拿到内部 icon 的 bounds
    （如 `(230,1607)-(437,1965)`）。Tap icon center 比 tap 视觉位置可靠。

## 关键文件速查

| 文件 | 作用 |
|---|---|
| `app/build.gradle.kts` | 依赖：AMap 10.0.600 + osmdroid 6.1.20 |
| `data/AmapKey.kt` | Key 读取 + 占位符检测 |
| `location/LocationProvider.kt` | `AMapLocationClient` Flow 封装（GCJ-02 输出） |
| `service/RecordingTrackService.kt` | 前台服务：定位 → 过滤 → 累计 → 落库 |
| `ui/MainScreen.kt` | 全屏地图 + 底部按钮 + 浮层 + 摘要卡 + 权限两步 |
| `ui/map/MapViewWrapper.kt` | 地图路由入口 |
| `ui/map/AmapMapView.kt` | 高德分支 |
| `ui/map/OsmMapView.kt` | osmdroid 分支 |

## 构建 & 调试

```bash
# 构建
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (~54 MB)

# 装机
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 抓 Service 崩溃堆栈
adb logcat -d -s TrackService:* AndroidRuntime:E *:F
```

## 坐标系小抄

- **GCJ-02**（火星坐标）：高德 / 百度 / 腾讯 国内地图和定位输出。国内用。
- **WGS-84**（地球坐标）：GPS 原始值、OSM、Google Maps 海外。
- 高德定位 SDK 直接输出 GCJ-02，所以从 `LocationProvider` 拿到的点**不用**再转换就能
  在高德地图上正确显示。
- osmdroid 的 `GpsMyLocationProvider` 从 Android `LocationManager` 拿 WGS-84，跟极图
  GeoQ 瓦片（GCJ-02）有 50–500 m 偏移——目前没处理，因为真机定位还没跑通。