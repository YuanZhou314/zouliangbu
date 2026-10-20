# CHANGELOG — 走两步 (zouliangbu) 会话笔记

> 多端开发 / 不同会话间不互通，关键决策、踩坑、TODO 留这里给下次参考。
> `CLAUDE.md` 是硬约束清单（项目级，永驻），本文件是时间线（会话级，追加式）。
>
> **维护策略：B + C**
> - **B（关键节点）**：每修完一个 bug / 改完一段架构 / 发现新坑，主动追加一段
> - **C（会话结束）**：每次整次会话结束时，整合一份完整总结追加到最新日期

---

## [2026-10-20] 内容服务端 v1 落地（Java / Spring Boot 4）

### 触发
你拍板「用 Java 也可以，那就用 Java吧」。此前 BACKEND-PLAN v2 的客观打分是
Python 4.15 / Java 3.90 / Go 3.88（差距 < 0.3，属"选哪个都能干"区间），故按你的决策落地 Java，
决策与取舍记录在 `BACKEND-PLAN.md` **D5**。

### 产出
`zouliangbu-server/`（独立 Maven 工程，与 App 同仓不同模块）
- Spring Boot **4.1.1** + Java 17 + MyBatis-Plus **3.5.17**（boot4 starter）+ Flyway
- 开发/测试 **H2 内存库**（`MODE=MySQL`），生产 **MySQL 8**；同一套 DDL 两库通用
- 4 个接口：`/api/v1/feed`（cursor 分页）、`/routes`（推荐位+分组+关键词）、`/gear/categories`、`/healthz`
- `V1__init.sql` 建 4 表 + `V2__seed.sql` 种子（文案与 `MockData.kt` 一致）
- Dockerfile / docker-compose.yml（mysql + api，nginx 留到 S5）
- **测试 12 个用例全绿**（`mvn test`），含游标翻页不重不漏、4 类非法入参必须 4xx

### 过程中修掉的 3 个真 bug
1. **`keyword=武功` 搜不到「武功山反穿线」** —— 推荐位线路 `section_id` 为 NULL，
   而搜索只遍历"有分组归属"的线路。改为搜索覆盖全部已上线线路，无分组的命中项归到
   「搜索结果」分组置顶。**App 侧 `RoutesViewModel` 有同样缺陷，已同步修复**
   （在搜索态把命中的 recommendations 合成一个 section，接远程后自然不触发）。
2. **`size=9999` / `size=abc` / 非法 URL 转义 全返回 500** —— 只处理了请求体校验异常，
   漏了 `@RequestParam` 上的 `@Min/@Max`、类型转换失败、Tomcat 解码失败。
   补齐 4 个 4xx 处理器，明确分层：客户端传错→400/404/405+code 1001/1004；服务端故障→500+code 5000。
3. **未知路径返回 500** —— `NoResourceFoundException` 落到兜底 handler，单独映射为 404。

### 踩坑（Spring Boot 4 与 Boot 3 教程不兼容）
- `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**
- Flyway 必须单独引 **`spring-boot-starter-flyway`**（放 jar 里不再自动配置）
- 测试注解搬到 **`org.springframework.boot.webmvc.test.autoconfigure`**；`@MockBean` → `@MockitoBean`
- **本机环境变量 `SERVER__PORT=57182` 会被 Spring 宽松绑定成 `server.port`**，而该端口被 IDE 占用 →
  本地起服务必须显式 `--server.port=8080`（命令行参数优先级最高）
- Windows 下 `mvn package` 会被正在运行的 java 进程锁住 jar（`Unable to rename ... to .original`）→ 先停进程

### 校验基线
```
mvn test   → Tests run: 12, Failures: 0, Errors: 0
mvn package → BUILD SUCCESS，target/zouliangbu-server-0.1.0.jar（30MB）
curl 4 接口 → 全部 200 且 JSON 正确（feed 同城过滤 2 条、3 页游标翻页 4+4+2 不重不漏）
./gradlew :app:compileDebugKotlin → BUILD SUCCESSFUL（RoutesViewModel 改动后）
```

### 下一步（S4）
App 接 Retrofit + `Remote*Repository`，只改 `FootprintApp` 三个 lazy 单例，UI/ViewModel 零改动；
Mock 保留作离线 fallback。**但 P1 轨迹闭环优先级更高**（用户看不到自己的轨迹线，产品不成立）。

---

## [2026-10-20] 底部五栏导航框架（首页/线路/出发/装备/我的）

### 触发
参照两步路主页截图重构：全屏地图+记录应属于「出发」模块，不应是 App 首页。
首页只需同城定位 + 动态展示；线路可搜索；装备先列分类不接商城；我的做简单版。

### 用户决策（已拍板）
1. 出发 = 中间凸起绿钮，点击进入**独立全屏页**（无底栏），返回回主框架，Service 不中断
2. 首页「同城」= 高德定位取真实城市，拿不到降级（无权限显示"开启定位"，可点授权）
3. 内容数据 = 本地 mock + Repository 接口；将来接后台只换 `FootprintApp.*Repository` 实现类
4. osmdroid 双轨分支**保留**

### 架构改动
- **双层 NavHost**：外层 `tabs / record`（AppRoot），内层四 tab（MainTabsScreen）。
  record 独立 destination 天然无底栏，避免单 NavHost + 条件 bottomBar 的 innerPadding 跳变
- 新增 `androidx.navigation:navigation-compose:2.7.7`（锁死；2.8+ 传递 Compose 1.7.x 与
  Kotlin 1.9.24/compiler 1.5.14 冲突）。依赖树已验证：compose 全 1.6.8 无 1.7.x 混入
- 底栏自绘：父 Box 84dp（60 底栏 + 24 凸起），64dp 圆钮在父边界内（负 offset 会裁点击区）
- `ui/MainScreen.kt` → `ui/record/RecordScreen.kt`（加返回键；权限两步逻辑原样保留），
  `MainViewModel` → `RecordViewModel`，旧文件删除
- `LocationProvider` 开 `isNeedAddress=true`（AMapLocation.city 才有值；Service 只读
  lat/lng/accuracy 无副作用）；新增 `CityProvider`（first{} 取一帧即退订 + SP 缓存）
- 「我的」页统计：`observeSessions().map`（只算 endTime!=null），不改 Room schema

### 文件变更

| 类型 | 文件 | 备注 |
|---|---|---|
| 改 | `app/build.gradle.kts` | +navigation-compose 2.7.7 |
| 改 | `MainActivity.kt` | setContent → AppRoot() |
| 改 | `FootprintApp.kt` | +feed/route/gear Repository、cityProvider（lazy，Mock 替换点） |
| 改 | `location/LocationProvider.kt` | isNeedAddress=true |
| 新 | `ui/AppRoot.kt`、`ui/navigation/*`、`ui/tabs/MainTabsScreen.kt` | 导航骨架 |
| 新 | `ui/components/AppBottomBar.kt`、`CoverPlaceholder.kt`、`SectionHeader.kt` | 底栏 + 通用组件 |
| 新 | `ui/home/*`、`ui/routes/*`、`ui/gear/GearScreen.kt`、`ui/profile/*` | 四内容页 |
| 新 | `ui/record/RecordScreen.kt`、`RecordViewModel.kt` | 迁移自 MainScreen/MainViewModel |
| 新 | `data/model/*`、`data/repo/{Feed,Route,Gear}Repository.kt`、`data/mock/*`、`location/CityProvider.kt` | Mock 数据层 |
| 删 | `ui/MainScreen.kt`、`ui/MainViewModel.kt` | 已迁移 |

### 编译
- `./gradlew :app:compileDebugKotlin`：BUILD SUCCESSFUL（无 warning）
- 最终 `assembleDebug --rerun-tasks`：见 version.properties buildNumber

### TODO（下次会话可继续）
- [ ] 真机验收：五栏/凸起钮、城市名、mock 四页、出发页与改造前行为一致、记录中返回红点
- [ ] 动态/线路详情页（需要后台接口）
- [ ] 通知点击直达记录页（PendingIntent 落 MainActivity，需 deep link 或 extra 消费）
- [ ] 城市 `isNeedAddress=true` 若真机耗电明显 → CityProvider 内部换"一次性定位 client"方案（UI 不动）
- [ ] RecordScreen 浮层 top padding 与返回键的视觉微调（OPPO 等异形屏）
- [ ] git 提交（上一轮统一定位改造 + 本轮导航框架一起，见未提交清单）

---

## [2026-10-20] 高德 Key 缺失 → osmdroid 双轨地图 + 三个 bug 修复

### 触发
高德 LBS 实名认证卡住，API Key 拿不下来，原先 `AmapMapView` 没 Key 时只显示占位文字，
没法跑通"地图显示 + 定位 + 轨迹记录"的完整流程。

### 架构改动
引入 **双轨地图**：新增 `MapViewWrapper`，按 `data/AmapKey.kt::isConfigured()` 路由两条分支。

| Key 状态 | 走的分支 | 文件 |
|---|---|---|
| 有 Key | `AmapMapView`（高德 MapView） | `ui/map/AmapMapView.kt`（保留原状） |
| 无 Key | `OsmMapView`（osmdroid + 极图 GeoQ 瓦片） | `ui/map/OsmMapView.kt`（新增） |

两条分支并存。Key 下发后只需替换 `res/values/strings.xml` 的 `amap_api_key`，重新 build，业务代码零改动。

定位 SDK（`AMapLocationClient`）**不依赖 Key**，独立可用，所以 `LocationProvider` 不动。

### 文件变更

| 类型 | 文件 | 备注 |
|---|---|---|
| 改 | `app/build.gradle.kts` | 加 `org.osmdroid:osmdroid-android:6.1.20` |
| 改 | `FootprintApp.kt` | `onCreate` 加 osmdroid 初始化；瓦片缓存走 `filesDir/osmdroid`（避开存储权限） |
| 改 | `ui/MainScreen.kt` | import 切到 `MapViewWrapper`；权限拆两步（前台→后台），拆两个 launcher |
| 改 | `service/RecordingTrackService.kt` | `onStartCommand` 加 try/catch + `Log.e("TrackService", ...)` 兜底，崩溃落 logcat |
| 新 | `ui/map/OsmMapView.kt` | osmdroid 实现；极图 GeoQ 瓦片源（自定义 `OnlineTileSourceBase` 重写 `getTileURLString` 匹配 `{z}/{y}/{x}`）；`MyLocationNewOverlay` + `GpsMyLocationProvider` 显示我的位置 |
| 新 | `ui/map/MapViewWrapper.kt` | 统一入口，按 `AmapKey.isConfigured()` 路由 |
| 留 | `ui/map/AmapMapView.kt` | 原状保留，Key 配好后走这条 |
| 新 | `CLAUDE.md` | 项目级速记（给 Claude 看，硬约束清单） |
| 新 | `CHANGELOG.md`（本文件） | 会话笔记（多端同步用，时间线） |

### 第一次装机实测 → 三个 bug

| 现象 | 根因 | 修复 |
|---|---|---|
| 地图空白，可缩放，只有白色方向箭头 | OSM 官方瓦片 `tile.openstreetmap.org` 国内被墙，瓦片超时 | 瓦片源换极图 GeoQ（`map.geoq.cn`，国内 CDN，无需 token） |
| 点"开始记录"没弹权限对话框 | `ACCESS_BACKGROUND_LOCATION` 跟其他 dangerous 权限同组请求时，Android 10+ 系统静默忽略整组 | 拆两步：第一步 FINE+COARSE+NOTIFICATIONS（Android 13+），第二步单独 BACKGROUND_LOCATION（Android 10+） |
| 点"开始记录"直接闪退 | 高度怀疑 Android 14+ `startForeground(type=LOCATION)` 没拿到 FINE_LOCATION 时抛 SecurityException；具体堆栈待 logcat 确认 | `onStartCommand` 入口加 try/catch + `Log.e`，下次崩溃有迹可循 |

### 踩到的坑（已部分同步到 CLAUDE.md）

1. **AMap 10.x 已内嵌 location**：不要单独加 `com.amap.api:location` 依赖，会 `Duplicate class`
2. **OSM 官方瓦片国内被墙**：`tile.openstreetmap.org` 加载超时导致 MapView 空白
3. **后台定位必须单独请求**：Android 10+ 强制要求 `ACCESS_BACKGROUND_LOCATION` 单独弹窗
4. **Android 14+ `startForeground(type=LOCATION)`** 需要 FINE_LOCATION 已授予，否则 SecurityException
5. **`permissionLauncher` 不能在自己的 callback 里再 `launch` 自己**：Kotlin "未初始化前引用" 编译错。要拆两个 launcher
6. **osmdroid 6.x `MapTileIndex` 包路径**：`org.osmdroid.util.MapTileIndex`（不是 `org.osmdroid.tileprovider.MapTileIndex`）

### 编译
- 首次构建：`BUILD SUCCESSFUL in 10s`（54 MB APK）
- 三个 bug 修复后：`BUILD SUCCESSFUL in 4s`
- APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

### TODO（下次会话可继续）
- [ ] 高德 Key 下发后填 `strings.xml` 的 `amap_api_key`，build 验证自动切回高德
- [ ] 装机实测三个 bug 是否真修好（地图可见 / 权限弹窗两步 / Service 不闪退）
- [ ] 抓一次 `adb logcat -d -s TrackService:* AndroidRuntime:E *:F`，看 Service 兜底日志工作 + 闪退根因
- [ ] 评估是否需要为 osmdroid 补轨迹叠加（polyline + 起点/终点 marker），目前 `OsmMapView` 只显示地图和我的位置
- [ ] 真实定位跑通后，看"我的位置" marker 是否需要 WGS-84 → GCJ-02 转换（极图瓦片是 GCJ-02，osmdroid 默认拿 WGS-84，可能偏移 50–500 m）

### 待确认 / 用户决策
- 高德 Key 申请进度（实名认证卡在哪里？）
- 是否要做 release 签名（目前只有 debug APK，release 没配 `signingConfigs`）

---

## [2026-10-20] 模拟器装机实测：ATD 镜像不渲染 → 换 android-33 + AMap 隐私合规双类调用

### 触发
用户要求在模拟器上验证「地图显示 + 定位 + 记录」闭环。装了 ATD 镜像后模拟器启动成功、
App 也启动成功、`uiautomator dump` 能拿到 Compose view 树，但 `adb exec-out screencap`
返回的 PNG **永远是同一张 15 KB 纯黑图**（MD5 一致），`screenrecord` 输出 0 字节。

### 关键发现
- **ATD = Automated Test Device，是 CI 无头测试用镜像**，故意不渲染 system UI 合成。
  App 的 Compose 内容确实画在 framebuffer 里（uiautomator 能看到），但 SurfaceFlinger
  没把它合成到 screencap 能读的 layer，所以截图黑。换 `-gpu host`、换 `-gpu swiftshader_indirect`、
  重启、重装 APK，都无法解决——**这是 ATD 镜像本身的性质**。
- android-34 上只有 `aosp_atd` 和 `google_apis_playstore` 两种非空 arm64 镜像。
  `google_apis_playstore` 是 1.2 GB、带 Google Play 的完整用户镜像。
- android-33 多一个 **`default`（纯 AOSP、无 Google 服务）**，是最轻量的**非 ATD** 镜像。

### 决策
降到 **android-33 + `default` 镜像**。改动清单：
1. `app/build.gradle.kts`: `compileSdk = 33`、`targetSdk = 33`
2. 装 `system-images;android-33;default;arm64-v8a`（~500 MB）
3. 用 `-d pixel_5` 重建 AVD `zouliangbu33`
4. 因 `Build.VERSION_CODES.UPSIDE_DOWN_CAKE`（API 34 常量）在 compileSdk 33 下不可见，
   `RecordingTrackService.startForegroundCompat` 改用数字字面量 `34` 判断。
5. `MainScreen.kt` 加 `Modifier.navigationBarsPadding()` 到底部按钮栏，避免按钮被 nav bar 盖住一半。

### 换镜像后：能看见 UI 了，但 AMap 又崩

装上 android-33 default 镜像后，screencep 返回 93 KB，App UI 完整渲染（状态栏、
osmdroid 网格地图、「开始记录」绿色按钮、我的位置箭头）。但点开始按钮立刻进程崩溃：

```
java.lang.Exception: ***确保调用SDK任何接口前先调用更新隐私合规updatePrivacyShow、
updatePrivacyAgree两个接口并且参数值都为true，若未正确设置有崩溃风险***
  at com.amap.api.location.AMapLocationClient.a(AMapLocationClient.java:111)
  at com.amap.api.location.AMapLocationClient.<init>(AMapLocationClient.java:44)
  at LocationProvider$locationUpdates$1.invokeSuspend(LocationProvider.kt:27)
```

errorCode `555570` = 隐私合规未确认。踩坑轨迹（**别重复**）：
1. 第一次修：`AMapLocationClient.updatePrivacyShow/Agree` 两次调用 → 仍崩
2. 第二次修：以为方法在 `MapsInitializer` 上 → 仍崩
3. 第三次修：**两个类都调**（`MapsInitializer` + `AMapLocationClient`）→ 仍崩
4. 第四次修：`LocationProvider.init` 里再冗余调一次 + `AMapLocationClient` 构造
   包 try/catch（`close(t)` 关闭 Flow 而不是崩进程）→ 代码看起来正确，但**没实测验证**
   （用户要求停下提交）

**根因（推测但高置信）**：AMap SDK 的隐私状态是**进程级静态变量**。当
`RecordingTrackService` 因 `START_STICKY` 被系统单独重启（`Start proc ... for service`）
时，Service 只在它自己的进程里跑，但这个进程**没有走过** `FootprintApp.onCreate()`
（Application 惰性初始化，Service 单独启动时不走 Application 的初始化路径）。
所以静态隐私 flag 没被设置。

`LocationProvider.init` 里的冗余调用是**理论上应该修好这个 bug** 的（它在
`AMapLocationClient(appContext)` 构造之前同步执行），但因为 build cache 坑
（见 CLAUDE.md 第 10 条）**一直没实测验证**——前几次构建是 `UP-TO-DATE` 命中
旧 APK，导致"改了代码但没进 APK"。下次先强制 rebuild 再测。

### 附带发现
- **`adb shell input tap` 落点看 view 树**：uiautomator 拿不到 Compose `Button`
  的完整 bounds（dump 是 (0,0,0,0)），但能拿到内部 icon 的 bounds
  （`(230,1607)-(437,1965)`）。Tap icon center `(333, 1786)` 才能真正命中按钮。
  **Tap 视觉位置（(540, 2200)）反而经常落进 nav bar**。
- **模拟器 partialConnectivity 假阳性**：`AndroidWifi has limited connectivity`
  只是 captive portal probe 失败（探 `www.google.com`），不影响实际网络。
  `settings put global captive_portal_mode 0` 可关掉。
- **`adb shell` 里没 `curl`**：测试网络要用 `ping` 或装 busybox curl，
  不然报 `exec curl: No such file or directory`。

### TODO（下次会话继续）
- [ ] 先 `./gradlew :app:assembleDebug --rerun-tasks` 强制重打 APK，验证
  `LocationProvider.init` 的隐私冗余调用是否真修好了 Service 单进程重启的崩溃
- [ ] 抓 `adb logcat -d -s TrackService:* AndroidRuntime:E LocationProvider:W`
  看是否出现 `AMapLocationClient 构造失败` 的 `Log.e`（说明 try/catch 生效）
  还是仍然 FATAL（说明 try/catch 前就崩）
- [ ] 如果 `LocationProvider.init` 那条路还是不行，考虑在
  `RecordingTrackService.onCreate()` 里也调一遍隐私接口（第三次冗余）
- [ ] 高德 Key 下发后切回 `AmapMapView` 分支，走真机/有 Key 模拟器测试完整闭环
- [ ] 评估是否需要为 osmdroid 补轨迹叠加（polyline + 起点/终点 marker）

### 本次会话未完成的功能
- ❌ 地图瓦片在模拟器上下载成功（GeoQ 有下载到内存缓存 50-60 个）但**未上屏**（仍显示空白网格）
- ❌ 完整「开始记录 → 浮层显示距离/时长 → 停止 → 摘要卡」闭环在模拟器上未跑通
  （Service 启动即崩于 AMap 隐私合规，见上文）

---

## [2026-10-20] 高德 Key 下发 + 统一定位架构（三个真机问题修复）

### 触发
用户下发高德 API Key `4e4785dbccc99da83531f51e643a63ab`（SHA1 匹配 `zlb-debug.keystore`）。
首次填 Key 后装机实测，暴露三个真机问题：

| # | 现象 | 根因 |
|---|---|---|
| 1 | WiFi 下点右上角定位 → 地图空白 → 缩放后在非洲 → 手动移到中国定位标志才在真位置 | 高德 MapView 初始中心不在国内用户位置；`moveCamera(zoomTo)` 只设缩放不设中心；WiFi 定位首次点延迟暴露竞态 |
| 2 | 点开始记录后走动，距离不涨 | `accuracy > 30m` 直接丢点；真机弱信号下点被全过滤；另疑地图内置定位与 Service 定位抢 AMap 资源 |
| 3 | 右上角内置定位按钮太高（OPPO Find X9） | 高德内置 `MyLocationButton` 位置由 SDK 定死，Compose 控制不了 |

### 架构改动：统一定位
**全 App 只跑一个 `AMapLocationClient`**，通过共享 `SharedFlow` 分发：

| 文件 | 改动 |
|---|---|
| `location/LocationProvider.kt` | 改为共享实例：`shareIn(WhileSubscribed(5000))` + `replay=1` + `lastLocation` 记录；`init` 隐私防御保留 |
| `FootprintApp.kt` | 暴露全局唯一 `locationProvider`（by lazy） |
| `service/RecordingTrackService.kt` | 订阅共享流；精度过滤改为软过滤（accuracy>30m 仍落库但不计距离） |
| `ui/map/AmapMapView.kt` | 关内置定位按钮（`setMyLocationButtonEnabled(false)`）；`LocationSource` 桥接共享流做蓝点；Compose 自绘右下角定位按钮（问题 3 解决）；首点 `animateCamera` 跟随（问题 1 解决） |
| `ui/map/OsmMapView.kt` | 不动（仅无 Key 时用） |
| `CLAUDE.md` | 新增"统一定位架构"、"版本自增"、"精度软过滤"三节 |

### 版本自增机制（新增）
- `app/build.gradle.kts` 读 `version.properties` 的 `buildNumber` 每次 assemble 自增 +1
- versionName = `0.1.0.${buildNumber}`（`0.1.0.000001`、`0.1.0.000002`...）
- versionCode = `10000 + buildNumber`（首个 = 10001 > 旧包 versionCode=1，覆盖安装通过）
- `version.properties` 已 gitignore
- 背景：真机提示"已安装相同签名应用"——旧包 versionCode=1 不变，覆盖安装被拒

### 编译
- `./gradlew :app:compileDebugKotlin`：BUILD SUCCESSFUL（`first()` 无参版修正过一处编译错）
- `./gradlew :app:assembleDebug`：打包待最终确认

### TODO（下次会话继续）
- [ ] 装机验证三问题是否真修好：
  - 地图首次定位是否自动移到真实位置（不再初始默认中心）
  - 走动距离是否正常累加（精度软过滤生效）
  - 自绘定位按钮位置是否合适
- [ ] 若记录距离仍不涨，抓 logcat：`adb logcat -d -s TrackService:* LocationProvider:* AndroidRuntime:E`
- [ ] 高德 Key 是否有效（地图瓦片正常加载、无 INVALID_USER_SCODE）
- [ ] `OsmMapView` 分支评估是否也用共享定位（当前 osmdroid 用系统 LocationManager，与 AMap 不冲突，暂缓）