# CLAUDE.md — 走两步 (zouliangbu) 项目速记

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

## UI 导航框架（2026-10-20 会话产物，硬约束）
- **双层 NavHost**：外层 `tabs / record`（`ui/AppRoot.kt`），内层 `home/routes/gear/profile`
  （`ui/tabs/MainTabsScreen.kt`）。record 是外层独立 destination → 天然全屏无底栏，
  不要改成"单 NavHost + 条件 bottomBar"（innerPadding 跳变会让地图 resize）。
- **navigation-compose 锁 2.7.7，禁止升 2.8+**：其传递依赖把 Compose 抬到 1.7.x，
  与 Kotlin 1.9.24 / compiler 1.5.14 冲突。改完依赖跑
  `./gradlew :app:dependencies --configuration debugRuntimeClasspath` 确认 compose 全 1.6.8。
- **底栏自绘**（`ui/components/AppBottomBar.kt`）：父 Box 高 84dp（60 底栏 + 24 凸起），
  中间圆钮 64dp 必须在父边界内，**负 offset 出界会裁掉点击区**。
  `navigationBarsPadding()` 放在 Box 上（背景铺到导航栏后），tab 页不要再加（防双重 inset）。
- **记录中状态订阅**：`RecordingTrackState` 每 2s 发 Snapshot，底栏只订阅
  `map { it != null }.distinctUntilChanged()`，否则主框架每 2s 整体重组。
- **Mock 替换点**：内容数据全收在 `FootprintApp.feedRepository / routeRepository /
  gearRepository`（lazy），接后台只换实现类（Mock→Remote Retrofit），UI 零改动。
- **城市定位**：共享 client `isNeedAddress=true` + `CityProvider`（first{} 取一帧即退订 +
  SharedPreferences 缓存）。不新建第二个 AMapLocationClient。
- 图标坑：material-icons-extended **没有 Tent**（帐篷用 Cabin）；返回键用
  `Icons.AutoMirrored.Filled.ArrowBack`。
- 记录中从记录页返回：只 popBackStack，Service 不停；重进记录页浮层数据自动恢复
  （RecordingTrackState 是进程内单例）。

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

## 统一定位架构（重要，本次会话产物）
**全 App 只有一个 AMapLocationClient**，通过 `LocationProvider` 的共享 `SharedFlow` 分发：
- `FootprintApp.locationProvider` 是全局唯一实例（by lazy）
- `LocationProvider.location` 用 `shareIn(WhileSubscribed(5000))` + `replay=1`，
  地图蓝点、自绘定位按钮、记录 Service 都订阅这一个流
- 地图**不自己发起 AMap 定位**：`AmapMapView` 把高德内置定位按钮关掉
  （`setMyLocationButtonEnabled(false)`），改用 `LocationSource` 桥接共享流；定位按钮
  在 Compose 里自绘（右下角，位置可控，解决 OPPO 上内置按钮偏高问题）
- 首点跟随：`AmapMapView` 拿到第一个定位点后 `animateCamera` 到真实位置，
  避免初始默认中心（AMap 默认在非用户位置）导致 WiFi 下点定位跳变

## 版本自增（构建产物）
- `app/build.gradle.kts` 每次 `assemble` 读 `version.properties` 的 `buildNumber` 自增 +1，
  versionName = `0.1.0.${buildNumber}`（`0.1.0.000001`...），versionCode = `10000 + buildNumber`
- `version.properties` 已 gitignore，每次打包自动 +1；改代码重打包时新版本号覆盖安装必通过
- 坑：改完源码要确保重打包生效（CLAUDE.md 第 10 条），但**版本号机制保证每次 assemble 都是新包**

## 精度过滤（记录距离）
- accuracy <= 30 m：可信点，正常入库 + 记距离
- accuracy > 30 m：低质量点**仍落库**（轨迹连续）但**不计距离**（避免抖动/漂移算进去）
- 不再"直接丢点"，避免真机上移动中距离不涨的现象

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
   当前 `app/build.gradle.kts` `compileSdk = 34`、`targetSdk = 33`（见第 8 条，compileSdk 不能降）。
8. **compileSdk 不要跟着模拟器 API 降级**。`compileSdk` 只决定编译期可用 API，与运行设备/模拟器的
   API 级别无关。之前为跑 android-33 模拟器把 `compileSdk` 降到 33，结果 **Room 2.6.1 的传递依赖
   （`androidx.sqlite:2.4.0`、`annotation-experimental:1.4.0`）AAR metadata 要求 API 34**，构建直接
   失败。正确组合：`compileSdk = 34` + `targetSdk = 33`（AGP 明确支持二者分离）。
   反过来说，compileSdk=34 后代码里可以用 `Build.VERSION_CODES.UPSIDE_DOWN_CAKE` 常量
   （`RecordingTrackService.startForegroundCompat` 里的 `34` 字面量可以换回去，但非必要）。
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

## 地图引擎抽象（P1.1 产物，硬约束）

业务层**不许**出现 `LatLng` / `Polyline` / `Marker` / `org.osmdroid.util.GeoPoint` 这些厂商类型。
统一走 `ui/map/MapEngine` 接口 + `ui/map/MapPoint`（GCJ-02 纯数据）：

- 记录页：`MapViewWrapper(track = 实时增长的列表)` —— 不画起终点、不自动缩放、相机跟随
- 详情页：`MapViewWrapper(track = 整条轨迹, showEndpoints = true, autoZoom = true)`

三条实现约束（踩过就别再踩）：
1. **Polyline / Marker 只创建一次**，之后只换点集（`polyline.points = …` → `setPoints`）。
   每点 `addPolyline` 会让覆盖物堆积 + AMap 重建纹理。
2. **地图没加载完时加覆盖物可能不渲染** → `AmapMapEngine` 缓存最后一次设置，
   `OnMapLoadedListener` 里 `reapply()` 重放。
3. **相机跟随必须可被用户打断**：`OnMapTouchListener` 一触发就 `followEnabled = false`，
   自绘定位按钮重新开启。否则用户拖图看周围时地图一直把自己拽回来。
4. `newLatLngBounds` 对零面积 bbox（单点 / 点全重合）会抛异常，先判跨度再决定用不用。
5. osmdroid 分支**不实现** MapEngine（ROADMAP D1：只兜底），无 Key 时只有蓝点没有线，属既定取舍。

## 坐标系与 GPX（P1.5–P1.7 产物，硬约束）

**全链路 GCJ-02，只有 GPX 边界转 WGS-84**：
- 定位 SDK 输出 GCJ-02 → Room 存 GCJ-02 → 地图渲染 GCJ-02（全程不转）
- 导出：`GpxWriter` 里 `gcj02ToWgs84()`；导入：`GpxParser` 里 `wgs84ToGcj02()`
- **不要在其他地方顺手转**，重复转换会偏几百米且极难排查

其他约束：
1. 反解没有解析公式，用不动点迭代 `wgs += gcj - forward(wgs)`，3 轮残差 < 0.1 m。
2. GPX 解析用 `javax.xml.parsers` 的 **SAX**，不要用 `android.util.Xml`——
   后者在 `testDebugUnitTest` 里是 stub，一调就 `not mocked` 崩掉。
3. `FileProvider` authority = `${applicationId}.fileprovider`，Manifest 与 `GpxExporter`
   两处必须一致；导出目录 `cacheDir/gpx/` 与 `res/xml/file_paths.xml` 的 `<cache-path>` 对应。
4. GPX 时间写 **UTC + `Z`**（本地时区会让别的软件算错配速）；坐标 6 位小数；
   海拔 0 不写 `<ele>`（高德拿不到海拔时给 0）。
5. 单测基线：`./gradlew :app:testDebugUnitTest` → 21 用例全绿
   （CoordTransform 6 / GpxWriter 8 / GpxParser 7）。改坐标或 GPX 逻辑必须保持全绿。

## 关键文件速查

| 文件 | 作用 |
|---|---|
| `app/build.gradle.kts` | 依赖：AMap 10.0.600 + osmdroid 6.1.20 + navigation-compose 2.7.7；版本自增（version.properties） |
| `ui/AppRoot.kt` | 外层 NavHost（tabs / record） |
| `ui/tabs/MainTabsScreen.kt` | 主框架：Scaffold + 内层四 tab NavHost + 底栏 |
| `ui/components/AppBottomBar.kt` | 五槽底栏 + 凸起出发圆钮 + 记录中红点 |
| `ui/home` `ui/routes` `ui/gear` `ui/profile` | 四个内容 tab（mock 数据 + Repository 接口） |
| `ui/record/RecordScreen.kt` | 出发页（原 MainScreen 全部能力 + 返回键），权限两步逻辑在此 |
| `data/AmapKey.kt` | Key 读取 + 占位符检测 |
| `location/LocationProvider.kt` | **共享** `AMapLocationClient` Flow 封装（GCJ-02 输出），全 App 唯一 |
| `location/CityProvider.kt` | 从共享定位流取城市名（first{} + 缓存），首页「同城」用 |
| `util/CoordTransform.kt` | GCJ-02 ↔ WGS-84（导出/导入的唯一转换点） |
| `export/GpxWriter.kt` / `GpxParser.kt` / `GpxExporter.kt` | GPX 写出 / 解析 / 落盘 + 分享 |
| `service/RecordingTrackService.kt` | 前台服务：订阅共享定位流 → 过滤 → 累计 → 落库 |
| `ui/map/MapViewWrapper.kt` | 地图路由入口（透传 track / showEndpoints / autoZoom） |
| `ui/map/MapEngine.kt` / `AmapMapEngine.kt` | 引擎接口 + 高德实现（折线 / 起终点 marker / bbox 缩放 / 跟随） |
| `ui/map/MapPoint.kt` | 与引擎无关的 GCJ-02 坐标点（业务层只认这个） |
| `ui/map/AmapMapView.kt` | 高德分支：LocationSource 桥接共享定位 + 自绘定位按钮 + 首点跟随 + 轨迹渲染 |
| `ui/map/OsmMapView.kt` | osmdroid 分支（仅无 Key 时） |
| `zouliangbu-server/` | 内容服务端（Java 17 + Spring Boot 4.1.1 + MyBatis-Plus + Flyway） |

## 服务端硬约束（2026-10-20 v1）

- **Spring Boot 4 ≠ 3，不要照 Boot 3 教程写**：
  `spring-boot-starter-web` → **`-webmvc`**；Flyway 必须显式引 **`spring-boot-starter-flyway`**；
  测试注解在 **`org.springframework.boot.webmvc.test.autoconfigure`**；`@MockBean` → **`@MockitoBean`**。
- **本机起服务必须带 `--server.port=8080`**：环境变量 `SERVER__PORT=57182` 会被 Spring 宽松绑定
  成 `server.port`，而该端口被 IDE 占用，不加参数会 `Port already in use` 起不来。
- **打包前先停 java 进程**：Windows 下 jar 被占用会报 `Unable to rename ... to .jar.original`。
- **`RouteSection.subtitle` 必须返回空串，不能返回 null**：App 侧是非空 `String`，
  null 会让 kotlinx.serialization 直接抛异常。
- **轨迹数据永不上云**：服务端只有内容表（feed_post / route_section / route / gear_category）。
- 响应统一 `{code,msg,data}`；客户端传错走 4xx（code 1001/1004），服务端故障走 500（code 5000）。
- 改表：只新增 `V{n}__xxx.sql`，**不要改已执行的 V1/V2**。
- 接口字段名必须与 App 的 `data/model/*.kt` 逐字段一致——这样换 Remote 实现时 UI/ViewModel 零改动。

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