# 离线地图底座评估（ROADMAP P4.4）

> 结论先行：**保持高德作为在线底座，离线包走 osmdroid + MBTiles。**
> 本项是设计决策记录，不是代码；后续换底座时以本文为依据。

## 一、结论

**底座：高德（AMap）为主 + osmdroid 兜底（当前双轨实现）。**

1. **在线底座用高德**——已有 Key、国内覆盖/定位/坐标系（GCJ-02）一致、开发成本已经付过；
   这一条不动。
2. **离线包用 MBTiles + osmdroid 的 `MapTileFileArchiveProvider`**——这是当前唯一能在纯 Android
   端读 MBTiles 的可行方案，见 P4.1 已实现的 `offline/OfflineTileSource.kt`。
3. **高德分支暂不支持离线**——高德 3D MapView 不开放 MBTiles / 自定义归档瓦片读取接口。
   在离线场景下，用户要么下载后**只用 osmdroid 分支看离线地图**，要么等高德官方离线 SDK
   （目前没有公开可用版本）。
4. **不引入 MapLibre**——MapLibre Native Android 是 C++/JNI 链路，会显著增加包体积
   （+10~20 MB）与集成复杂度，且会破坏现有的 `MapEngine` 抽象（AmapMapView / MapEngine 是围绕
   高德 API 设计的）。当前离线诉求是「有包就能看」，不需要 GL 渲染。

## 二、备选方案对比

| 方案 | 在线底座 | 离线能力 | 包体积影响 | GCJ-02 一致性 | 接入成本 | 评价 |
|---|---|---|---|---|---|---|
| **A. 高德 + osmdroid + MBTiles** | 高德 | osmdroid 读 MBTiles | 0（osmdroid 已在用） | ✅ 全链路一致 | 已完成 | **采用** |
| B. MapLibre 全替换 | MapLibre | MBTiles / PMTiles | +10~20 MB（libmaplibre.so） | ⚠️ 需自管 | 高（重写 MapEngine） | 不采用 |
| C. 高德离线 SDK | 高德 | 高德官方离线 | 0 | ✅ | 低（若存在） | 不存在公开版本 |
| D. 自研瓦片渲染（Canvas） | 任意 | 任意 | 0 | ✅ | 极高（渲染、投影、缓存全要写） | 不采用 |

## 三、关键决策点

### 3.1 为什么 MBTiles 而不是 PMTiles / 其他格式

- **MBTiles 就是一个 SQLite 库**：标准格式、单文件、可校验、可断点续传。
  osmdroid 6.1.20 内置 `MBTilesFileArchive`，零额外依赖（已验证 jar 里有该类）。
- **PMTiles 是 HTTP range-based 归档**：适合 CDN 直读，但 Android 端没有官方 Kotlin 客户端，
  自己实现需要写 range 请求 + 索引解析，投入产出比低。
- **MBTiles 的限制**：不能像 MBTilesFileArchive 那样做 partial-update；所以「增量更新」只能
  是「下载新版本整包覆盖旧版本」（manifest 里的 `version` 字段就是为此）。

### 3.2 为什么不做高德分支的离线

- 高德 3D MapView 的 `AMap` API **没有开放**自定义瓦片源（`UrlTileProvider` 只能改 URL，
  不能读本地归档）。
- 反编译高德 jar 里的内部类是**逆向工程**，不属于合规方案。
- 因此**双轨方案里，离线包只对 osmdroid 分支生效**，UI 上明示这一点
  （见 `OfflineMapScreen` 顶部提示文案）。

### 3.3 为什么不做矢量地图

- 矢量（MVT / PBF）需要解析 protobuf + 渲染引擎，等价于重写一个 MapLibre；
- 我们要的只是「在山里能看地图」，栅格瓦片足够；
- 矢量的好处（标注清晰、缩放无损、体积小）在徒步场景下不值得。

### 3.4 坐标系

- 离线 MBTiles 按 **GCJ-02** 切，与在线极图 GeoQ 一致；
- 全链路 GCJ-02（见 CLAUDE.md「坐标系与 GPX」）；
- MBTiles 内部是 Web Mercator 栅格，无坐标系元数据，由 ITileSource 解释。

### 3.5 审图号（合规提醒）

- 国内公开地图服务**必须使用有审图号的瓦片源**，否则有合规风险。
- **极图（GeoQ）是公开的国内地图服务，瓦片源有审图号**；这一点在 P4 实施时确认过。
- **生产环境建议**：离线包的切割源直接使用「极图 GeoQ 的 GCJ-02 瓦片」，
  不要自己拼 OSM/Google 瓦片；这样审图号随原服务走，风险最小。
- 上线前需要法务/合规再确认一次，不在本文件结论范围。

## 四、当前实现状态

| 项 | 状态 | 文件 |
|---|---|---|
| 清单解析（manifest JSON） | ✅ 完成 | `offline/OfflineRegionCatalog.kt` |
| 下载 + SHA-256 校验 + 存储 | ✅ 完成 | `offline/OfflinePackageManager.kt` |
| MBTiles 离线瓦片接入 | ✅ 完成 | `offline/OfflineTileSource.kt` |
| 区域选择 UI + 存储管理 | ✅ 完成 | `offline/OfflineMapScreen.kt` |
| 内置清单（兜底） | ✅ 完成 | `app/src/main/assets/offline/regions.json` |
| 服务端清单接口 | ⏳ 待 P6（S4） | — |
| 真实可下载的 MBTiles 资源 | ⏳ 待运维提供 | — |

## 五、后续可选优化（非阻塞）

1. **下载用 WorkManager**：目前是 `viewModelScope`，用户离开页面就中断。
   Roadmap 里 P4.1 的「可断点续传」需要改成 WorkManager + Resume 能力（.part 文件 + Range 请求）。
2. **自动清理 LRU**：目前是手动删除 + 总量上限硬卡，可以加「最近 N 天没用就自动清」。
3. **离线瓦片缓存预热**：在 WiFi 下预下载常用区域。
4. **离线详情页地图回退**：详情页打开时如果该轨迹 bbox 跨多个离线包，提示用户下载相关区域。

## 六、变更记录

- 2026-10-20：初版结论（高德 + osmdroid + MBTiles）。
