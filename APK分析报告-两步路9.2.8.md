# APK 逆向分析报告 · 两步路 9.2.8（com.lolaage.tbulu.tools）

- 分析时间：2026-10-20
- 目标：`C:\Users\15797\Downloads\d1.apk`，172,480,026 字节
- SHA-256：`7f21b689a8fcb11931157ded0172eab1736b85241a0bad69c7675acf01712091`
- 用途：功能学习与研究 / 自研替代（不含整包克隆、不含资源复用）
- 工具链：REA 6.7.0（`inspect-artifact`）+ 自建 AXML 解析器 + dex 字符串池精确提取
- 证据 ID：`ev_f3252edff51d98c9bc829caefa3ad429588e3ac25d45282f9ca5b932b3e54eac`（`inspect_artifact`，`confidence: observed`，`authority: shipped-artifact`）；子步骤 `ev_4847b8b0ff1f9cd3d5d33184c954a882cc86fef753da96e6e14a92941219dffc`

---

## 0. 结论速览

| 维度        | 事实                                                                               |
| --------- | -------------------------------------------------------------------------------- |
| 技术栈       | **原生 Android**（Java/Kotlin + Compose），无 Flutter / React Native / Unity / Cordova |
| 架构        | **arm64-v8a 单一 ABI**，minSdk 26 / targetSdk 34，`extractNativeLibs=true`           |
| 体量        | 解压后 324.3 MB：`lib/` 144.2 MB（56 个 .so）、dex 102.1 MB（12 个）、`res/` 51.6 MB（8438 个） |
| 代码规模      | **100,670 个类**；其中自有代码 `com.lolaage.*` 仅 **20,161 个（20.0%）**                      |
| 广告/变现 SDK | **约 27,146 个类（27.0%）**，来自 13 个以上广告聚合/SSP                                         |
| 地图栈       | **双引擎并存**：高德 AMap SDK v10.1.700（29.57 MB）+ 高德导航资源 + Mapbox Maps（12.94 MB）        |
| GIS 内核    | GDAL / OGR / OSR（32.9 MB）+ OpenCV 4（17.57 MB）+ ONNX Runtime（17.37 MB）            |
| 组件暴露面     | 659 个 Activity 中 **445 个 exported**；56 Service / 27 Receiver / 45 Provider       |
| 权限        | **118 个**，含后台定位、身体传感器、`REQUEST_INSTALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`           |

一句话：**它是一台"户外工具 + 广告变现 + 社交电商"的复合体**。户外的技术内核（GDAL 坐标系、DEM 高程、双地图引擎、蓝牙外设）做得相当重；同时塞了超过四分之一的广告代码和 118 个权限。

---

## 1. 功能解释

### 1.1 技术栈判定（如何得出）

- 无 `libflutter.so` / `libhermes` / `libreactnativejni` / `libunity` → 排除跨平台框架
- 存在 `kotlin/*.kotlin_builtins`、`androidx.compose.*`（Compose UI/Foundation/Material 合计 5,000+ 类）→ **Kotlin + Jetpack Compose**
- 12 个 dex 而非 1 个 → 未做全局 R8 压缩合并（或保留了大量 SDK 的 `-keep`），这也是它 100k 类的直接原因
- 存在 `DebugProbesKt.bin` → 使用 kotlinx-coroutines 的调试探针（通常应剥离）

### 1.2 地图：两套引擎同时打包

| 引擎      | 证据                                                                                                                                           | 推断用途                                        |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| 高德 AMap | `lib/arm64-v8a/libAMapSDK_MAP_v10_1_700.so`（29.57 MB）、`assets/map_assets/VM3DRes/*`、`3d_navi_sky_day.data`、`assets/map_assets/*bktile*.data` | 主图 + **离线地图下载** + 导航（3D 楼块/天空资源=导航 SDK 资源包） |
| Mapbox  | `libmapbox-maps.so`（12.94 MB）、`libmapbox-common.so`、`com.mapbox.maps.*`（1,965 类）、`com.mapbox.common.*`（825 类）                                | 另一套渲染管线，推测用于**矢量瓦片/地形/自定义样式**场景             |
| GDAL 系  | `libgdaljni.so` 13.21 MB、`libogrjni.so` 12.77 MB、`libosrjni.so` 5.27 MB                                                                      | 矢量格式读写 + **坐标系（OSR）转换**                     |
| 离线索引    | `assets/regions.txt`（1.35 MB）、`assets/city.json`（0.33 MB）、`assets/datatest.sqlite`（2.61 MB）                                                  | 省市离线包目录 / 行政区索引                             |

离线地图的行为链可从事件类推出：`EventGaodeOfflineMapOnDownload`、`OfflineMapFileChanged`、`OfflineMapPreviewActivity`、`GaodeOfflineMapActivity` —— 即"选区域 → 下载 → 落盘 → 文件变更广播 → 预览"。

### 1.3 轨迹采集与坐标系统

- **自有定位管线**：`com.lolaage.tbulu.tools.locateprocess`（40 类）；`gnss status` 相关类（`CustomGnssStatus`）
- **坐标系抽象**：`business/models/CoordinateSystem` + `CoordinateSystem$LatLonCoordinateSystem`、`EventCoordinateSystemChanged`、`utils/CoordinateConversion`（含 `LatLon2UTM` / `UTM2LatLon` / `LatLon2MGRUTM` / `MGRUTM2LatLon` / `LatZones`）→ 支持 UTM/MGRS 军事网格，不止经纬度
- **高程**：`AltimeterActivity`、`EventTrackAltitudeCorrectNotice`（轨迹海拔校正）、`RescueNavigationManager$loadElevations` → 有**在线/离线 DEM 取高程 + 校正**逻辑
- **运动数据**：`ACTIVITY_RECOGNITION`、`BODY_SENSORS(_BACKGROUND)` 权限 + 独立包 `com.lolaage.stepcounter.*`（sensor/db）→ 计步与心率
- **外设**：`com.lolaage.tbulu.bluetooth.*`（592 类）核心是 `BTGpsTrackerManager` + `BTGpsTrackerActivity` —— **连接外部蓝牙 GPS/北斗记录仪**；配合 `com.hailiao.hailiaosdk.BeidouListner`（北斗硬件 SDK，403 类）

### 1.4 户外安全（它的差异化重点）

`com.lolaage.tbulu.rescue.*`（1,235 类）：`RescueManagementActivity` / `RescueClueMapActivity` / `RescueTaskMapActivity` / `EmergencyRescueDTO`；  
另有 `SafetyGuardianActivity`（安全守护）、`AltimeterActivity`（海拔表）、`ZoneRouteMapActivity`（区域围栏路线）——这是"报平安/救援"类功能集群，也是它区别于普通记录 App 的地方。

### 1.5 路线与格式互操作

`RouteMapActivity`、`MustHikeRouteListActivity`、`CuratedRouteCollectionActivity`、`RouterChecklistDetailActivity`、`LineCommentListActivity`；  
格式侧：`GpxUtil`（GPX）、`EventImportKmlSuccess`（KML 导入，68 处 KML 相关类）——**以 GPX/KML 作为导入导出的事实标准**，这点和你做开源应用的方向完全一致。

### 1.6 变现与运营（你不需要的部分）

| 板块    | 证据                                                                           |
| ----- | ---------------------------------------------------------------------------- |
| 会员    | `UserVipItem`（73 处 vip）、`GetTrialMemberActivity`                             |
| 交易    | `OutingApplyOrderReturnByAlyPay`、alipay SDK、`cn.sharesdk`、`com.youzan`（有赞商城） |
| 赛事    | `com.lolaage.tbulu.tools.competition`（636 类）                                 |
| 景区/专区 | `prefecture.*`（`ScenicSpotListActivity`、`CreateLineEvaluationActivity`）      |
| 广告聚合  | 见下表                                                                          |

**广告/营销 SDK 清单（类数）**：

```
com.byazt            8345   ← 混淆最重，仅见 com.byazt.a.hp 之类短名，具体产品名未确认（推断：字节系聚合）
cj.mobile            3963   ← 类名 CJBanner/CJBanner$1，明确是 banner 广告 SDK
com.smartdigimkt     2078   智能数字营销
com.anythink         2704   TopOn 聚合（含 519 个 network adapter）
com.beizi            2349   倍孜广告（含 DownloadService、BeiZiDownloadDialogActivity）
com.meishu           1543   美数广告
com.ubix              844   ubix SSP
com.fl.saas           829   混淆广告 SDK
com.octopus           686   章鱼广告（AdActivity、ADBidEvent）
com.alliance          632   alliance SSP
com.gameley           375   gameley templatesdk（激励视频模板）
com.tk               1250   混淆广告
cn.fly                666   FlySDK/FlyStrategy（广告策略）
com.miui.zeus         882   小米营销
─────────────────────────────
合计                27,146  ≈ 27.0% 的全部类
```

> 注：`com.hailiao`（403 类）是北斗硬件 SDK，**不计入**广告集群。

### 1.7 其他基础设施（可参考但不必照搬）

- 网络：OkHttp3、Netty（1,276 类，长连接）、`com.obs.services`（华为云 OBS 对象存储，609 类）
- 存储：ORMlite（`com.j256`）+ MMKV + SQLite
- 序列化：Jackson（906 类）、dom4j、`gnu.trove`
- 推送全家桶：小米 / 华为 HMS / 荣耀 / vivo / OPPO(heytap) / FCM(cronet, play-services) / 阿里 agoo —— **7 套推送并存**
- 分享：Mob ShareSDK + 抖音 openlive / 微博 / 小红书 / QQ
- 风控/认证：极验 `com.geetest`、腾讯优图活体（`libYTLiveness.so`、`libYTCommonLiveness.so`、`assets/models/face-tracker-v003/**/*.tnnmodel`）

---

## 2. 证据清单

| #   | 结论        | 证据位置                                                                               | 强度                |
| --- | --------- | ---------------------------------------------------------------------------------- | ----------------- |
| E1  | 目标身份      | Manifest `package=com.lolaage.tbulu.tools`，`versionName=9.2.8`，`versionCode=1652`  | 观测                |
| E2  | SDK 版本边界  | `uses-sdk minSdkVersion=26 targetSdkVersion=34`，`compileSdkVersion=34`             | 观测                |
| E3  | 仅 arm64   | `lib/` 下仅 `arm64-v8a`，56 个 .so 共 144.2 MB                                          | 观测                |
| E4  | 双地图栈      | `libAMapSDK_MAP_v10_1_700.so` + `assets/map_assets/VM3DRes` + `libmapbox-maps.so`  | 观测                |
| E5  | GIS 内核    | `libgdaljni.so` / `libogrjni.so` / `libosrjni.so`                                  | 观测                |
| E6  | 广告占比      | 13 个包前缀合计 27,146 类 / 100,670 类                                                     | 观测（类数统计可复现）       |
| E7  | 自有代码占比    | `com.lolaage.*` 20,161 类，`ui` 占 11,719                                             | 观测                |
| E8  | 暴露面       | `activity 659 / exported 445`；`service 56 / 8`；`receiver 27 / 9`；`provider 45 / 1` | 观测                |
| E9  | 权限胃口      | `uses-permission` 118 条                                                            | 观测                |
| E10 | 明文流量      | `application usesCleartextTraffic=true`、`networkSecurityConfig=@0x7f150011`        | 观测                |
| E11 | 备份可导出     | `application allowBackup=true`、`largeHeap=true`                                    | 观测                |
| E12 | 未混淆自有代码   | `com.lolaage.tbulu.tools.business.models.CoordinateSystem` 等类名语义完整                 | 观测                |
| E13 | 蓝牙 GPS 外设 | `com.lolaage.tbulu.bluetooth.BTGpsTrackerManager`                                  | 观测                |
| E14 | 活体认证      | `assets/models/face-tracker-v003/*.tnnmodel` + `libYTLiveness.so`                  | 观测                |
| E15 | 广告产品名归属   | `com.byazt` / `com.tk` / `com.fl.saas` 仅见 `a.hp`、`_0` 短名                           | **推断**（类名不足以确证产品） |

复现脚本：`tools/apk_survey.py`、`tools/apk_survey2.py`、`tools/axml.py`、`tools/dex_strings.py`、`tools/analyze_classes.py`、`tools/sample_pkgs.py`

---

## 3. 局限与未知

1. **没有做方法级反编译**。本机无 jadx / apktool，`rea inspect-android-package` 直接返回 `capability_unavailable`（要求 `REA_JADX_MCP_JAR`）。所以"某功能具体怎么算的"（例如轨迹纠偏算法、离线包切分策略）目前**只能到类与结构层，不能到算法语句层**。
2. **类名不等于调用关系**。`BTGpsTrackerManager` 位于哪个进程、是否被 `RescueNavigationManager` 调用，属于**静态推断**，未验证运行时。
3. 未解析 `resources.arsc`，因此 `application.label`、`theme` 等资源引用只显示为 `@0x7f...`；`AndroidManifest.xml` 里的字符串资源（如应用名"两步路"是字面量，已确认）。
4. `assets/TrackNet-1.xml` / `TrackNet-2.xml`、`assets/models/detect.caffemodel`、`sr.caffemodel`（OpenCV DNN 格式）与 `libonnxruntime.so` 的**具体用途未定位**——可能是图片超分、目标检测或轨迹识别，需要方法级分析才能确认。
5. 未做签名校验与证书指纹提取，也未验证是否含第三方加固（未见明显的梆梆/爱加密壳特征，但未系统性排查）。
6. 所有结论来自静态文件，**不代表运行时行为**；`445 个 exported 组件`的实际可利用性未做验证。

---

## 4. 重建建议（自研"干净"版）

### 4.1 先明确：哪些要学、哪些绝对不能抄

| 学（思路/架构）              | 不抄（法律与工程双重理由）                                  |
| --------------------- | ---------------------------------------------- |
| 双地图引擎的分层抽象（渲染层与业务解耦）  | 高德/Mapbox 的商业 SDK 与其地图数据（授权不可转让，且违反其 ToS）      |
| 以 GPX/KML 作为对外互操作格式   | 它的 UI 素材、图标、字体、文案、"两步路"商标                      |
| 省市行政区为粒度的离线包目录设计      | 它的私有蓝牙协议、`BTGpsTrackerManager` 实现              |
| 独立定位管线 + 坐标系统抽象       | 广告/推送/分享 SDK 全家桶                               |
| DEM 取高程 + 轨迹海拔校正的产品构思 | `assets/datatest.sqlite`、`regions.txt` 等随包数据文件 |

### 4.2 推荐技术选型（对应它的每一块）

| 能力     | 它用什么                             | 你用什么（开源/可商用）                                                                                                 |
| ------ | -------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 地图渲染   | 高德 + Mapbox（双商业 SDK）             | **MapLibre Native Android**（BSD-2）+ 自建样式；国内公开发布需用**合规底图源**并办理审图号，OSM 数据在国内公开发布的合规性需自行评估                      |
| 矢量瓦片生产 | 商业                               | **Planetiler / tilemaker** → PMTiles / MBTiles，`tileserver-gl` 自托管                                           |
| 离线包    | 高德离线 + regions.txt               | 按省/市边界切 **MBTiles**，清单 JSON（region_id / bbox / size / sha256 / version），WorkManager 断点续传                     |
| 定位与轨迹  | 自研 locateprocess                 | **FusedLocationProvider** 或原生 `LocationManager`（GNSS raw 可选）+ 前台服务（`FOREGROUND_SERVICE_LOCATION`）+ Room 批量写点 |
| 坐标转换   | `CoordinateConversion`（UTM/MGRS） | **proj4j**（Apache-2.0）做 WGS84 ↔ CGCS2000 ↔ GCJ02 ↔ UTM；GCJ02 偏移算法本身是公开的                                      |
| 高程/DEM | GDAL + 在线取点                      | **Copernicus DEM 30m → COG 切片**，本地双线性插值；比在线 API 更快且离线可用                                                      |
| 格式读写   | GDAL/OGR（32.9 MB）                | **jpx / GPX 1.1 手写解析**（户外场景只需 GPX/KML/KMZ），**不要**为格式支持引入 GDAL —— 这是最大的体积陷阱                                   |
| 步数/心率  | 系统传感器 + 私有包                      | `SensorManager` + `ACTIVITY_RECOGNITION` + BLE GATT 心率服务（标准 0x180D）                                          |
| 蓝牙外设   | 私有 SDK                           | 自己写 GATT 客户端，支持标准 NMEA over BLE（0x1819 位置服务）                                                                 |
| 崩溃/日志  | 友盟 + crashsdk                    | 自建或 Firebase Crashlytics（可选）                                                                                 |
| 推送     | **7 套并存**                        | 按需 1 套（国内 FCM 不可用时选厂商推送之一），不要聚合                                                                              |

### 4.3 体量与性能目标（对照它的 164 MB）

| 项   | 它       | 你的目标                                                             |
| --- | ------- | ---------------------------------------------------------------- |
| APK | 164 MB  | **25–40 MB**（MapLibre .so ≈ 8 MB + 业务 dex ≈ 5 MB + 样式/字体 ≈ 3 MB） |
| 类数  | 100,670 | **≤ 8,000**（自有 3–5k + AndroidX）                                  |
| dex | 12 个    | 1–2 个（R8 全量压缩 + `-repackageclasses`）                             |
| 权限  | 118     | **6–10**（定位前后台、前台服务、通知、媒体读取、BLE、相机可选）                            |
| 冷启动 | 未知      | 地图 SDK 懒加载 + 首屏不初始化定位                                            |

### 4.4 直接可避免的坑（它在这些地方做了坏示范）

1. `usesCleartextTraffic=true` —— 明文流量放行，自研版应 `false` + 仅白名单域名。
2. `allowBackup=true` —— 用户数据可被 adb 备份导出，含轨迹隐私，应 `false` 或配置 `dataExtractionRules`。
3. `largeHeap=true` —— 掩盖内存问题，不要继承。
4. `extractNativeLibs=true` —— 安装后膨胀，新版本应设 `false`（`useLegacyPackaging=false`）。
5. 445 个 exported Activity —— 绝大多数是 SDK 的 intent-filter 副作用，自研版应显式 `exported=false`，仅保留必要的 deeplink。
6. 保留 `DebugProbesKt.bin` —— 发布包应剥离。

### 4.5 建议的最小可行版本（MVP）切片

按"户外核心闭环"排序，先做前三步就能替代它的主干：

1. **记录**：前台服务 + Room 存点 + GPX 导出（含暂停/继续、自动分段）
2. **看**：MapLibre + 离线 MBTiles（省级按需下载）+ 轨迹回放
3. **算**：距离/爬升/下降/配速 + DEM 高程校正 + 轨迹抽稀（Douglas-Peucker）


4. **安全**：低电量/超时未动提醒 + 位置分享（分享用 GPX 而不是抄它的社交闭环）
5. **扩展**：BLE 外设、心率、天气（可选）

---

## 附：可复现命令

```bash
# 容器清点（REA，带 Evidence 契约）
npx rea inspect-artifact "C:\Users\15797\Downloads\d1.apk" --json

# 自建：ZIP 结构与 ABI/dex/SDK 指纹
python tools/apk_survey.py
python tools/apk_survey2.py

# 自建：二进制 Manifest 解码（AXML）
python tools/axml.py "C:\Users\15797\Downloads\d1.apk" apk-manifest-tree.json

# 自建：dex 字符串池精确提取 + 包分布
python tools/dex_strings.py        # 产出 dex-classes.txt
python tools/analyze_classes.py
python tools/sample_pkgs.py
```
