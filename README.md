# 走路簿 (zouliangbu)

一个极简、无广告的户外 GPS 轨迹记录 Android 应用。两步路的最小化版本。

> 本仓库目前只包含 **可编译、可安装的骨架 + 核心记录闭环**。
> 列表之外的功能（离线瓦片、GPX、航点、循迹、社交、广告、iOS 等）一律不做。

## 功能范围（本轮）

- ✅ Kotlin + Jetpack Compose + Material 3
- ✅ 高德 AMap 3D 地图（GCJ-02 坐标系，从定位到显示全程一致）
- ✅ AMap 高精度 GPS 定位
- ✅ 前台服务（Foreground Service）记录轨迹
- ✅ 常驻通知，实时显示距离和已用时间
- ✅ Room 本地存储（`TrackSession` + `TrackPoint`）
- ✅ Haversine 距离增量计算 + 质量过滤（accuracy > 30m 丢弃，间隔 < 1s 丢弃，单段 > 500m 视为漂移）
- ✅ 全屏地图 + 底部一个开始/停止按钮
- ✅ 记录中顶部浮层（距离 + 时长）
- ✅ 停止后弹摘要卡（距离、时长、点数）
- ✅ 权限申请：FINE_LOCATION → BACKGROUND_LOCATION → POST_NOTIFICATIONS（按 API 级别分别请求）
- ❌ 没有 Key 时显示提示占位（**编译和安装不受影响**）

## 技术栈

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 8.7 |
| AGP | 8.5.0 |
| Kotlin | 1.9.24 |
| KSP | 1.9.24-1.0.20 |
| Compose BOM | 2024.06.00 |
| Room | 2.6.1 |
| AMap 3D | 10.0.600 |

> ⚠️ AMap 3D 10.x 已把 location 类内嵌，单独加 `com.amap.api:location` 会 Duplicate class。

## 快速开始

```bash
# 1. 申请高德 Key（必做，否则地图区域显示提示占位）
#    详见 LOCAL_ENV.md 第 8 节。
# 2. 编辑 app/src/main/res/values/strings.xml，替换 amap_api_key

# 3. 构建 debug APK
./gradlew assembleDebug

# 4. APK 位置
app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/zouliangbu/footprint/
├── FootprintApp.kt           # Application：持有 DB + Repository 单例
├── MainActivity.kt           # Compose 宿主
├── data/
│   ├── AmapKey.kt            # AMap Key 读取 + 有效性判断（含完整 TODO 注释）
│   ├── RecordingTrackState.kt# Service ↔ UI 的实时状态桥（StateFlow）
│   ├── db/                   # Room：TrackSession、TrackPoint、DAO、Database
│   └── repo/TrackRepository.kt
├── location/
│   └── LocationProvider.kt   # AMapLocationClient 的 Flow 封装
├── service/
│   └── RecordingTrackService.kt # 前台服务：定位 → 过滤 → 累计 → 落库
├── ui/
│   ├── MainScreen.kt         # 全屏地图 + 底部按钮 + 浮层 + 摘要卡
│   ├── MainViewModel.kt
│   ├── map/AmapMapView.kt    # AMap MapView 包进 AndroidView + Lifecycle
│   └── theme/                # Material 3 主题
└── util/
    ├── Distance.kt           # Haversine 距离计算
    └── TimeFmt.kt            # mm:ss / h:mm:ss + km/m 展示
```

## 距离/时长过滤规则

| 规则 | 阈值 | 原因 |
|---|---|---|
| 精度过滤 | `accuracy > 30 m` 丢弃 | 低质量 GPS 点（特别在室内、隧道口） |
| 时间过滤 | 间隔 `< 1 s` 丢弃 | 防止静止时连续打点刷距离 |
| 漂移过滤 | 单段增量 `> 500 m` 跳过距离累加 | 城市峡谷偶尔出现的 GPS 跳点 |

## 本机构建环境

详见 [LOCAL_ENV.md](LOCAL_ENV.md)。

## 后续路线（暂未开始，按需取用）

- GPX 导入/导出
- 离线瓦片下载
- 航点（waypoint）
- 单次会话地图回放
- 历史会话列表 / 统计