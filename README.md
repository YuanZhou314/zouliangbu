# 走两步 (zouliangbu)

一个极简、无广告、本地优先的户外 GPS 轨迹记录 Android 应用。两步路的最小化版本。

> **数据边界**：轨迹**只存本机**，永不上云、不需要账号。内容层（首页 / 线路 / 装备）是只读展示壳，
> 不做 UGC 发布、不做评论互动、不做交易、不做广告。

## 当前进度（2026-10-20）

| 阶段 | 状态 | 说明 |
|---|---|---|
| P0 收尾重构 | ✅ 完成 | 五栏导航框架 + 内容层 Mock + 统一定位架构 |
| P1 焊死闭环 | ✅ 8/8 完成 | 记录 → 看线 → 列表找到 → 详情回看 → 导出/导入 |
| P2 数据可靠性 | 🟡 5/8 完成 | 迁移策略 / 悬挂恢复 / 状态一致 / 暂停继续 / 计时基准 |
| P3 算得准 | ⬜ 未开始 | 爬升、配速、高程 |
| P4 离线地图 | ⬜ 未开始 | MBTiles 分包、区域下载 |
| P6 内容接后台 | 🟡 服务端已跑通 | App 侧 Retrofit 待接（不阻塞本地功能） |

**暂停位置**：P2 剩余 2.5 抽稀 / 2.6 批量写 / 2.8 持锁——三项都是性能与耗电项，
必须长时间真机实测才能验收，因此在这里停一轮。完整计划见 [ROADMAP.md](ROADMAP.md)。

## 功能清单

**轨迹记录**

- ✅ 高德 AMap 3D 地图（GCJ-02，从定位到入库到显示全程一致）
- ✅ 前台服务记录 + 常驻通知（实时距离与时长）
- ✅ 记录中**实时画线** + 相机跟随（手指拖图即停，右下角按钮恢复跟随）
- ✅ 暂停 / 继续（不采点、不计距离、不计时长；恢复后同一条轨迹继续写）
- ✅ 停止后摘要卡（距离 / 时长 / 点数），可一键导出 GPX 或进入详情
- ✅ 进程被杀后启动自动收尾：悬挂会话按最后一点时间保存，不静默丢失

**轨迹管理**

- ✅ 历史列表（按开始时间倒序、距离/时长/点数、长按删除、空态引导）
- ✅ 详情回看（地图自动缩放到轨迹 bbox、起终点标记、统计卡）
- ✅ GPX 1.1 导出（分享给其它软件；库里 GCJ-02 → 写出 WGS-84）
- ✅ GPX 导入（SAF 选文件 → 解析 → WGS-84 → GCJ-02 → 落库 → 跳详情）

**内容层（只读）**

- ✅ 首页（城市栏 + Banner + 同城动态）、线路（搜索 + 推荐位 + 分组）、装备（8 分类宫格）、我的
- ⚠️ 数据仍为本地 Mock，接口收在 `FeedRepository / RouteRepository / GearRepository` 后面，
  接后台只换 `FootprintApp` 里三个 lazy 单例的实现类，UI 零改动

**明确不做**：UGC 发布 / 评论 / 关注、广告、应用内交易、iOS 版。

## 技术栈

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Gradle / AGP | 8.7 / 8.5.0 |
| Kotlin / KSP | 1.9.24 / 1.9.24-1.0.20 |
| Compose BOM | 2024.06.00 |
| Room | 2.6.1（**v3 + 迁移链 + schema 入库**） |
| AMap 3D | 10.0.600（已内嵌 location 类） |
| osmdroid | 6.1.20（无 Key 时的兜底分支，不画轨迹线） |
| navigation-compose | 2.7.7（**禁止 2.8+**，会传递 Compose 1.7.x） |

> ⚠️ AMap 3D 10.x 已把 location 类内嵌，单独加 `com.amap.api:location` 会 Duplicate class。

## 快速开始

```bash
# 1. 申请高德 Key（必做，否则地图区域显示提示占位）
#    详见 LOCAL_ENV.md 第 8 节。
# 2. 编辑 app/src/main/res/values/strings.xml，替换 amap_api_key

# 3. 构建 debug APK（版本号自动 +1，见 version.properties）
./gradlew assembleDebug
#    → app/build/outputs/apk/debug/app-debug.apk

# 4. 单元测试（纯 JVM，不需要设备）
./gradlew :app:testDebugUnitTest
```

Android SDK 路径写在 `local.properties`（不入库）：`sdk.dir=D:\\Android\\Sdk`。

## 项目结构

```
app/src/main/java/com/zouliangbu/footprint/
├── FootprintApp.kt              # Application：DB / Repository 单例 + 启动时 SessionRecovery
├── MainActivity.kt              # Compose 宿主
├── data/
│   ├── RecordingTrackState.kt   # Service ↔ UI 实时状态桥（含心跳与看门狗）
│   ├── SessionRecovery.kt       # 启动时收尾 endTime=null 的悬挂会话
│   ├── db/                      # Room：TrackSession / TrackPoint / DAO / TrackSummary 投影
│   └── mock/  model/  repo/     # 内容层：3 个 Repository 接口 + Mock 实现
├── location/
│   ├── LocationProvider.kt      # AMapLocationClient 的共享 Flow（全局唯一定位源）
│   └── CityProvider.kt          # 城市名解析 + SP 缓存
├── service/
│   └── RecordingTrackService.kt # 前台服务：订阅定位 → 过滤 → 累计 → 落库
├── export/                      # GPX 读写与分享（GpxWriter / GpxParser / GpxExporter / GpxImport）
├── ui/
│   ├── AppRoot.kt               # 外层 NavHost：tabs / record / track{id}
│   ├── tabs/MainTabsScreen.kt   # 内层四 tab：home / routes / gear / profile / tracks
│   ├── components/AppBottomBar.kt
│   ├── map/                     # MapEngine 抽象 + Amap 实现 + osmdroid 兜底
│   ├── record/  tracks/         # 记录页 / 历史列表 / 详情页
│   ├── home/ routes/ gear/ profile/
│   └── theme/
└── util/
    ├── CoordTransform.kt        # GCJ-02 ↔ WGS-84
    ├── Distance.kt              # Haversine
    ├── TrackMetrics.kt          # 距离规则 + TrackClock（活动时长）
    └── TimeFmt.kt               # mm:ss / h:mm:ss + km/m 展示
app/schemas/                     # Room 每版表结构存档（1.json / 2.json / 3.json）
```

## 关键约定（改动前必读）

| 主题 | 约定 |
|---|---|
| 坐标系 | 全链路 GCJ-02；**只在 GPX 读写边界**转 WGS-84 |
| 距离规则 | 只有 `util/TrackMetrics` 一份实现（精度 > 30 m 丢弃、间隔 < 1 s 丢弃、单段 > 500 m 不计） |
| 时长 | 一律用 `activeDurationMs`（已扣 `pausedMs`）；进程内计时用 `elapsedRealtime`，落库 timestamp 用墙上时钟 |
| 改表 | version+1 + 写 `Migration` + 提交新 `schemas/*.json`；**禁止 `fallbackToDestructiveMigration`** |
| 地图类型 | 业务层不出现 `LatLng` / `Polyline` 等厂商类型，只用 `ui/map/MapEngine` |
| 详情页位置 | 必须挂外层 NavHost（全屏看地图），不能塞进内层 tab |

更细的硬约束见 [CLAUDE.md](CLAUDE.md)，决策与踩坑时间线见 [CHANGELOG.md](CHANGELOG.md)。

## 测试

```
./gradlew :app:testDebugUnitTest     # 45 用例全绿
```

| 套件 | 覆盖 |
|---|---|
| `CoordTransformTest` 6 | 境内外、偏移量级、往返残差 |
| `GpxWriterTest` 9 | XML 合法性、坐标确实转成 WGS-84、脏数据跳过、命名回退 |
| `GpxParserTest` 7 | 无命名空间 GPX、缺 `<ele>` / `<time>`、格式错误 |
| `GpxImportTest` 9 | 时间/海拔兜底、距离累计、往返无损 |
| `TrackMetricsTest` 7 | 三类过滤规则边界 |
| `TrackClockTest` 7 | 暂停扣减、异常区间 |

> 未覆盖：UI、地图渲染、Room 迁移（迁移逻辑目前靠 schema json 比对 + SQLite 手工验证）。

## 文档索引

| 文档 | 内容 |
|---|---|
| [ROADMAP.md](ROADMAP.md) | 分期计划（P0–P7）+ 现状基线 + 决策表 |
| [CHANGELOG.md](CHANGELOG.md) | 会话时间线：决策、踩坑、待验证项 |
| [CLAUDE.md](CLAUDE.md) | 硬约束清单（修改代码前速查） |
| [LOCAL_ENV.md](LOCAL_ENV.md) | 本机构建环境（**Windows 段待补**） |
| [BACKEND-PLAN.md](BACKEND-PLAN.md) | 后端选型评估与实施方案 |
| [SERVER-ROADMAP.md](SERVER-ROADMAP.md) | 服务端后续计划与 App 对接（S4 / S5） |
| [APK分析报告-两步路9.2.8.md](APK分析报告-两步路9.2.8.md) | 对标产品功能域拆解 |
