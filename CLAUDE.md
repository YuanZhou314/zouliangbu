# CLAUDE.md — 走两步 (zouliangbu) 项目速记

> 给 Claude Code 看的项目速记。`README.md` / `LOCAL_ENV.md` 是给人和复现用的——这个文件
> **只**记 Claude 下次进来时最可能踩坑的几个点，不重复它们的内容。

## 项目一句话
极简户外 GPS 轨迹记录 Android App（最小化两步路）。Kotlin + Compose + 高德 + Room。

## 验证纪律（用户明确要求，2026-10-20）
**默认不测。** 用户自己会装包实测，助手不要把 token 烧在反复验证上。

- 只有用户**明确说**「跑一下 / 验证 / 装模拟器」时才动手测，且**一轮测完就停**。
- 一轮测试的默认范围：装包 → 启动 → 跑完一条主链路（记录→停止→列表→详情）→ 报结论。
  不做像素采样、颜色统计、逐点 DB 遍历、多分支对比构建等**深挖式验证**。
- **不要**为了「测全」而反复 build / 反复切分支重装 / 反复跑单测。一次够。
- 测出 bug 直接改代码 + 改完**只做一次编译验证**，不要再来一轮全量回归。
- 遇到需要长时间连续注入坐标才能推进的流程，**说明即可，不要自己模拟**。
- 结论汇报用表格说清「测了什么 / 结果 / 没测什么」，不要贴大段 logcat。
- 单元测试只在**改动了被测逻辑**时跑一次；不改逻辑就别跑。

## 路线内容平台（2026-10-20，硬约束）

从两步路导入路线：链接→抓取→审批→展示。开源使用，不盈利。

### 架构
- **抓取**：`TwoBuluClient`（`/api/proSpecial/routeDetails` 明文 + `/api/destintion/getRoutePoints` XOR 0x4B）
- **坐标**：服务端 `CoordTransform.java`（Java 移植自 App 侧 `CoordTransform.kt`），WGS-84 → GCJ-02
- **鉴权**：`AdminAuthInterceptor`（固定密码 → token，2h 过期），只拦 `/api/v1/admin/**`
- **管理后台**：纯 HTML + 原生 JS（`resources/static/admin/`），零构建依赖
- **数据**：V3 迁移 4 张新表（`route_detail` / `route_track_point` / `route_marker` / `route_submission`）

### 必须记住
1. **两步路轨迹接口是 XOR 0x4B 加密**，不是 TLS/JWT，是简单的字节混淆。
2. **WGS-84 → GCJ-02 转换必须做**，不做会偏 300~500m（与 App 全链路 GCJ-02 对齐）。
3. **Jackson 3（Spring Boot 4）包名是 `tools.jackson`**，不是 `com.fasterxml.jackson`。
4. **管理后台密码在 `application.yml`**（`admin.password`），token 存内存 Map（2h TTL）。
5. **正文 HTML 渲染**：App 侧只做极简去标签（`<p>`/`<br>`/`<li>`），不引富文本库。
6. **开源合规**：详情页展示来源标注 + 免责声明，图片只存 URL 不复制。

## P2.5–P6 实现记录（2026-10-20）

本轮把 ROADMAP 的 P2 剩余三项、P3 全部、P4 全部、P5 全部、P6 S4 一次性做完，
只做静态验证（编译 + 单测），未做真机/模拟器实测（用户明确说晚点再实测）。

### 新增关键文件速查

| 文件 | 作用 |
|---|---|
| `util/Polyline.kt` | Douglas-Peucker 抽稀（显式栈迭代，防 2 万点 StackOverflow） |
| `util/Elevation.kt` | 中值滤波（FILTER_RADIUS=3）+ 累计爬升/下降（ASCENT_THRESHOLD_M=3.0） |
| `util/Pace.kt` | 30s 滑窗配速/速度（WINDOW_MS=30_000） |
| `util/TrackSplits.kt` | 每公里分段统计 |
| `offline/OfflineRegion.kt` | 离线包元信息 + 清单数据类 |
| `offline/OfflineRegionCatalog.kt` | 清单 JSON 解析（JVM 可测） |
| `offline/OfflinePackageManager.kt` | 下载 + SHA-256 校验 + 存储 + 总量上限 |
| `offline/OfflineTileSource.kt` | osmdroid 离线 provider（MapTileProviderArray） |
| `offline/OfflineMapScreen.kt` / `OfflineMapViewModel.kt` | 离线包管理 UI（我的页入口） |
| `notify/TrackAlerts.kt` | P5.1 超时未动 / P5.2 低电量提醒 |
| `share/Share.kt` | P5.3 位置分享 + P5.4 图片分享（FileProvider） |
| `share/ShareCardRenderer.kt` | P5.4 行程卡 PNG 渲染（640×960 纯 Canvas） |
| `data/remote/ContentApiFactory.kt` | Retrofit 的 Remote*Repository（网络失败自动回退 Mock） |
| `app/src/main/assets/offline/regions.json` | 内置兜底清单 |
| `docs/offline-map-baseline-decision.md` | P4.4 底座评估结论文档 |

### 必须记住的约束

1. Polyline.simplify 的 keep 数组必须按点数分配：早期写死长度 3，超过 3 个点就 ArrayIndexOutOfBoundsException。
   改成 BooleanArray(points.size) { it == 0 || it == points.size - 1 }。
2. RecordingTrackService.pendingPoints 所有读写必须在 flushMutex 下：采集协程 add + flush 协程 drain 是并发的，
   不锁会丢点甚至 IndexOutOfBoundsException。
3. Room v4 迁移三步：AppDatabase.version = 4 + MIGRATION_3_4（ALTER TABLE ADD COLUMN ascentMeters/descentMeters 
   REAL NOT NULL DEFAULT 0）+ app/schemas/.../4.json 必须提交。
4. 离线包只对 osmdroid 分支生效：高德 MapView 不支持 MBTiles；UI 上已明示。
5. MBTiles 打开用 MBTilesFileArchive.getDatabaseFileArchive(File)：构造器是 private，损坏的包抛 SQLiteException，
   必须 try/catch 跳过该包。
6. kotlinx-serialization 而不是 Gson；converter 走 com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter。
7. Remote*Repository 网络失败必须回退到 Mock（在 flow 里 collect fallback）：UI 零改动，离线不白屏。
8. P4.1 的 WorkManager 断点续传未做：目前用 viewModelScope，离开页面下载会中断，需后续改 WorkManager + .part + Range。
9. P3.4 DEM 离线高程未做（可选增强，可延后）。
10. P6 S5 部署（Docker Compose + nginx + MySQL）未做——需要运维环境，不在本轮范围。

### 单测基线

- 本轮前：45 用例（CoordTransform 6 / GpxWriter 9 / GpxParser 7 / GpxImport 9 / TrackMetrics 7 / TrackClock 7）。
- 本轮新增：PolylineTest / ElevationTest / PaceTest / TrackSplitsTest 共 22 用例。
- 当前：64 用例全绿。改抽稀、爬升、配速、分段任一逻辑，跑 testDebugUnitTest 保持全绿。

## 双轨地图架构（关键，本次会话产物）
地图入口 `ui/map/MapViewWrapper.kt`，按 `data/AmapKey.kt::isConfigured()` 路由：

- **有高德 Key** → `ui/map/AmapMapView.kt`（高德 MapView）
- **无高德 Key** → `ui/map/OsmMapView.kt`（osmdroid + 极图 GeoQ 瓦片，无需 token）

两条分支并存。Key 下发后只需替换 `res/values/strings.xml` 的 `amap_api_key`，业务代码零改动。

注意：
- 定位 SDK（`location/LocationProvider.kt` 用 `AMapLocationClient`）**不依赖 Key**，独立可用。
- 高德 Key 已配置在 `res/values/strings.xml`（debug keystore = `zlb-debug.keystore`，已入库）。
  真机若无网/Key 校验失败会自动落到 osmdroid 分支——**该分支按 D1 不画轨迹线，只有蓝点**。

## UI 导航框架（2026-10-20 会话产物，硬约束）
- **双层 NavHost**：外层 `tabs / record / track/{sessionId}`（`ui/AppRoot.kt`），
  内层 `home/routes/gear/profile/tracks`（`ui/tabs/MainTabsScreen.kt`）。
  record 与详情页都是外层独立 destination → 天然全屏无底栏，
  不要改成"单 NavHost + 条件 bottomBar"（innerPadding 跳变会让地图 resize）。
- **详情页必须在外层**：它要占满屏幕看地图；放进内层会被底栏 + Scaffold padding 挤压。
  列表页 `tracks` 放内层（属于「我的」下钻，保留底栏，用户可随时切 tab）。
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
5. 单测基线：`./gradlew :app:testDebugUnitTest` → **45 用例全绿**
   （CoordTransform 6 / GpxWriter 9 / GpxParser 7 / GpxImport 9 / TrackMetrics 7 / TrackClock 7）。
   改坐标、GPX、距离规则或时长算法必须保持全绿。

## 记录状态与时长（P2.2–2.4 产物，硬约束）

1. **UI 判断"是否在记录"只看 `RecordingTrackState.current`（进程内），不要去查 DB**。
   进程被杀后 DB 里会留 `endTime IS NULL` 的悬挂会话，查 DB 会永远显示"记录中"。
   悬挂会话由 `SessionRecovery` 在 **Application.onCreate** 收尾（0 点删除 / 有点收尾保存）。
2. **不要给悬挂会话做"继续记录"**：进程死后 GPS 已断，硬接会在轨迹里留跨公里直线。
   现在的口径是"自动保存 + 提示一次"。
3. **时长一律用 `activeDurationMs`（= endTime - startTime - pausedMs）**，
   不要直接 `endTime - startTime`：暂停两小时的长椅休息会算进运动时长。
   记录中的实时时长走 `TrackClock.activeElapsedMs()`。
4. **时间基准：只有落库的 `TrackPoint.timestamp` 用 `System.currentTimeMillis()`**，
   其余（间隔过滤、时长、看门狗）全用 `SystemClock.elapsedRealtime()`——用户改系统时间不能影响记录。
5. **距离规则只有一份**：`util/TrackMetrics`（精度 > 30 m 或单段 > 500 m 不计）。
   Service 累加、崩溃恢复重算、导入校验都调它，不要各写一套。
6. Service 是 `START_NOT_STICKY` + `null intent → stopSelf`：别再改回 STICKY，
   Android 14 上"重启后没在 5 秒内 startForeground"会 ANR。
7. Service 每秒心跳（`tickerJob`）是"服务是否还活着"的唯一信号；
   看门狗 `RecordingTrackState.clearIfStale()` **只清 UI 状态，绝不落库**（无法区分"死"和"被冻"）。

## Room 表结构与迁移（P2.1 产物，硬约束）

**任何 entity 改动都必须走这三步，缺一就会让已装机的用户崩在启动页**：
1. `AppDatabase` 的 `version` +1
2. 在 `MIGRATIONS` 里加 `Migration(old, new)`，写清 ALTER / 建表 SQL
3. 编译后 `app/schemas/<db>/<new>.json` **必须跟着提交**

其他约束：
- **禁止 `fallbackToDestructiveMigration()`**：那等于用户轨迹全丢。宁可启动时报错让人发现。
- `exportSchema=true` 是 2026-10-20 打开的，`1.json` 是当时固化的 v1 基线。
  历史 schema 补不回来，所以**改表前先确认当前版本 json 已在仓库里**。
- 当前 **v3**：v2 加了 `track_sessions.name TEXT`（可空），v3 加了
  `pausedMs INTEGER NOT NULL DEFAULT 0`（累计暂停时长）。**不要**给已有列改类型，
  SQLite 的 ALTER 不支持，得走"建新表 → 拷数据 → 重命名"那一套。
- 加 NOT NULL 列必须带 `DEFAULT`（SQLite 的 ALTER 要求），且 entity 里**不要**写
  `@ColumnInfo(defaultValue=...)`，否则 Room 会拿它跟实际库比对，老库对不上就崩。
- 列表的点数用 `TrackSummary`（非 @Entity 的 POJO）：SQL 里**必须显式 `AS` 别名**，
  Room 靠列名映射。不要在 LazyColumn 里逐项查库（N+1）。
- 新增查询后看一遍编译日志：Room 在编译期校验 SQL 列与返回类型是否匹配，编译过 = 映射对。

## 关键文件速查

| 文件 | 作用 |
|---|---|
| `app/build.gradle.kts` | 依赖：AMap 10.0.600 + osmdroid 6.1.20 + navigation-compose 2.7.7；版本自增（version.properties） |
| `ui/AppRoot.kt` | 外层 NavHost（tabs / record / track/{id}） |
| `ui/tabs/MainTabsScreen.kt` | 主框架：Scaffold + 内层 NavHost（home/routes/gear/profile/tracks）+ 底栏 |
| `ui/tracks/TrackListScreen.kt` | 历史列表：空态 / 长按删除 / SAF 导入 GPX |
| `ui/tracks/TrackDetailScreen.kt` | 详情回看：地图 autoZoom + 起终点 marker + 统计卡 + 导出 |
| `data/SessionRecovery.kt` | 启动时收尾悬挂会话（0 点删 / 有点存），并推 `orphaned` 给记录页提示 |
| `util/TrackMetrics.kt` | 距离规则（精度 30m / 单段 500m）+ `TrackClock`（扣暂停的活动时长） |
| `data/db/TrackSummary.kt` | 列表投影（会话 + pointCount + pausedMs），非 @Entity |
| `export/GpxImport.kt` | 解析结果 → TrackPoint（纯计算，JVM 可测） |
| `app/schemas/**/*.json` | Room 表结构存档，**随表结构变更一起提交** |
| `ui/components/AppBottomBar.kt` | 五槽底栏 + 凸起出发圆钮 + 记录中红点 |
| `ui/home` `ui/routes` `ui/gear` `ui/profile` | 四个内容 tab（mock 数据 + Repository 接口） |
| `ui/record/RecordScreen.kt` | 出发页（原 MainScreen 全部能力 + 返回键），权限两步逻辑在此 |
| `data/AmapKey.kt` | Key 读取 + 占位符检测 |
| `location/LocationProvider.kt` | **共享** `AMapLocationClient` Flow 封装（GCJ-02 输出），全 App 唯一 |
| `location/CityProvider.kt` | 从共享定位流取城市名（first{} + 缓存），首页「同城」用 |
| `util/CoordTransform.kt` | GCJ-02 ↔ WGS-84（导出/导入的唯一转换点） |
| `export/GpxWriter.kt` / `GpxParser.kt` / `GpxExporter.kt` | GPX 写出 / 解析 / 落盘 + 分享 |
| `service/RecordingTrackService.kt` | 前台服务：START/PAUSE/RESUME/STOP + 每秒心跳 + 通知动作按钮 → 过滤 → 累计 → 落库 |
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
- 改表：只新增 `V{n}__xxx.sql`，**不要改已执行的 V1/V2/V3**。
- **迁移脚本按方言分目录**（2026-10-21 起）：`db/migration/sqlite/`、`db/migration/mysql/`、`db/migration/common/`。
  `{vendor}` 占位符没用，**locations 在 profile 里显式指定**：dev/test → `sqlite,common`；prod → `mysql,common`。
  写新迁移时：纯数据（INSERT/UPDATE）放 `common`，DDL 两边各写一份；SQLite 自增主键必须写
  `INTEGER PRIMARY KEY AUTOINCREMENT`（`BIGINT AUTO_INCREMENT` 在 SQLite 里不自增，且是语法错误）。
- **SQLite 连接池必须 1**（`maximum-pool-size: 1` + `PRAGMA busy_timeout=5000`）：SQLite 单写者模型，
  调大必然撞 `SQLITE_BUSY`。dev 库文件 `zouliangbu-server/zlb-dev.db`（gitignore），可用 `SQLITE_PATH` 改路径。
- **测试必须 `@ActiveProfiles("test")`**：test profile 用 `jdbc:sqlite:file:zlb-test?mode=memory&cache=shared`，
  不写就会写进 dev 的实体库文件、且数据跨次累积。
- **路线导入走平台路由**：`RoutePlatform.detect(url)` → `TWOBULU`（`2bulu.com`）/ `SIXFOOT`（`foooooot.com`），
  解析结果统一填 `RouteData.ParsedRoute`（GCJ-02）。加新平台 = 枚举 + `RouteImportService.fetch` 分支 + 一个 Client。
  六只脚**只取行程详情/正文/数字/轨迹，脚印图片一律剥掉**（`SixFoot.stripFootprintImages`）。
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