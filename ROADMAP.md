# ROADMAP — 走两步 (zouliangbu) 开发计划（整合版 v2）

- 制定：2026-10-20　｜　整合人：栈师　｜　替代 v1（同日稿）
- 依据：`APK分析报告-两步路9.2.8.md`（功能域拆解 + 4.5 MVP 切片）+ 当前代码实况（`buildNumber=3`）+ 2026-10-20 五栏框架改造决策
- 本文与 v1 的差异：**v1 是"纯本地工具"视角；v2 融合了"工具 + 只读内容壳"的双层定位**，并把已完成项从计划里剔除（见 §1.3、§2）
- 配套文档：`BACKEND-PLAN.md`（后端选型评估与实施方案，D4 已确认启动）

---

## 0. 一句话判断

**「记录」站起来了，「看」还缺一条腿，「算」还没开始。**

- 已有：前台服务采点、精度软过滤、Room 落库、地图蓝点跟随、五栏导航框架、四个内容页壳
- 缺：**记录中地图不画轨迹线**、**历史列表 / 详情回看**、**GPX 导入导出**、**爬升统计**、**断点恢复**

所以 P1 的主线不是"加新功能"，而是把 **记录 → 回看 → 导出** 焊死。
用户按下停止后看不到自己刚走过的线，这个产品就还没成立——**内容页做得再漂亮也救不回来**。

---

## 1. 产品定位与边界（整合后的裁决）

### 1.1 双层定位

| 层 | 定位 | 原则 |
|---|---|---|
| **核心层**（出发 / 记录 / 轨迹 / 数据） | 本地优先的户外轨迹工具 | 无网可用、数据不上传、不依赖账号。**这是产品的根，不动摇** |
| **内容层**（首页 / 线路 / 装备 / 我的表层） | 对齐两步路产品形态的**只读内容壳** | 只展示、不生产。**不做 UGC 发布、不做评论互动、不做交易、不做广告** |

内容层现阶段数据全部为 mock（`data/mock/`），接口已收在 `FeedRepository / RouteRepository / GearRepository` 后面，
接真实数据时只换 `FootprintApp` 里三个 lazy 单例的实现类，**UI 零改动**。

### 1.2 v1 与新需求的两处冲突及裁决

| 冲突 | v1 主张 | 现实际需求 | 裁决 |
|---|---|---|---|
| **是否做内容页** | 「不做社交/动态/UGC 闭环」（纯单机工具） | 首页同城动态、线路、装备分类（已实现 UI） | **保留内容页，但降级为「只读展示壳」**：不做发布/评论/关注/私信，不引入账号体系。内容层不占用核心开发资源，P1 阶段不再投入 |
| **tab 结构** | 建议「首页/线路/记录/我的」，把装备换成记录 | 用户已拍板五栏「首页/线路/出发/装备/我的」 | **按用户决策执行**：保留装备；历史记录入口挂「我的 → 我的记录」卡片（已实现）。若真机验证后觉得入口过深（>2 步），再议是否换 tab |

### 1.3 本轮已完成（不再排期）

| 能力 | 状态 | 位置 |
|---|---|---|
| 双层导航（外层 tabs/record + 内层四 tab） | ✅ | `ui/AppRoot.kt`、`ui/tabs/MainTabsScreen.kt` |
| 底栏 + 中间凸起「出发」钮（记录中红点） | ✅ | `ui/components/AppBottomBar.kt` |
| 首页（城市栏 + Banner + 同城动态瀑布流） | ✅ UI 完成，mock 数据 | `ui/home/` |
| 线路页（搜索本地过滤 + 推荐位 + 分组网格） | ✅ UI 完成，mock 数据 | `ui/routes/` |
| 装备页（8 分类宫格，不接商城） | ✅ UI 完成，静态 | `ui/gear/` |
| 我的页（占位信息 + Room 真实统计） | ✅ 完成 | `ui/profile/` |
| 记录页迁移（原 MainScreen → RecordScreen + 返回键） | ✅ | `ui/record/RecordScreen.kt` |
| 城市定位（共享 client `isNeedAddress=true` + CityProvider 缓存） | ✅ | `location/CityProvider.kt` |
| 内容数据层（3 接口 + 3 Mock 实现 + 模型） | ✅ | `data/repo/`、`data/mock/`、`data/model/` |

---

## 2. 现状基线（2026-10-20，buildNumber=3）

| 能力 | 状态 | 代码位置 |
|---|---|---|
| 双轨地图（高德 / osmdroid 按 Key 路由） | ✅ 可用（osmdroid 仅兜底，见 D1） | `ui/map/MapViewWrapper.kt` |
| 全 App 单一定位源（共享 SharedFlow） | ✅ 可用 | `location/LocationProvider.kt` |
| 前台服务记录（START/STOP + 通知） | ✅ 可用，**无暂停/继续** | `service/RecordingTrackService.kt` |
| Room 持久化（sessions + points） | ✅ 可用，**v1 无 migration、exportSchema=false** | `data/db/*` |
| 记录页（地图 + 浮层 + 摘要卡 + 权限两步） | ✅ 可用 | `ui/record/RecordScreen.kt` |
| 距离计算（Haversine + 精度软过滤） | ✅ 可用，**只算平面距离** | `util/Distance.kt` |
| 轨迹折线绘制 | ✅ 记录中实时 + 回看静态（高德分支；osmdroid 按 D1 不画） | `ui/map/AmapMapEngine.kt` |
| 历史记录列表 / 详情回看 | ❌ 无（入口占位已在「我的」） | — |
| GPX 导出 / 导入 | ❌ 无 | — |
| 爬升/下降/配速统计 | ❌ 无 | — |
| 断点恢复（进程被杀后的悬挂会话） | ❌ 无 | — |
| 单元测试 | ❌ 零覆盖 | — |
| git 提交 | ⚠️ 两轮改动全部未提交（含 keystore 未入库） | — |

---

## 3. 与两步路的差距矩阵

| 能力域 | 两步路 9.2.8 做法 | 我们现状 | 阶段 |
|---|---|---|---|
| 轨迹采集 | 自研 locateprocess + 蓝牙 GPS 外设 | AMap 定位 + 前台服务 | P2 补暂停/恢复 |
| 轨迹可视化 | 记录中实时画线 + 回放 | **仅有蓝点，无线** | **P1** |
| 轨迹管理 | 列表/详情/筛选 | **无** | **P1** |
| 格式互操作 | GPX/KML（GpxUtil、EventImportKmlSuccess） | **无** | **P1** |
| 数据核算 | 距离/爬升/下降/配速 + DEM 校正 | 只有平面距离 | P3 |
| 离线地图 | 高德离线 + regions.txt 省市目录 | osmdroid 在线瓦片缓存 | P4 |
| 户外安全 | rescue 集群、安全守护、海拔表 | 无 | P5 |
| 坐标系 | UTM/MGRS/LatLon 抽象 | 只认 GCJ-02 | P3（随 DEM） |
| 内容/动态 | 服务端 + UGC + 运营 | 只读 mock 壳 | **P6（可选）** |
| 外设 | 私有 BLE 协议 + 北斗 SDK | 无 | P7（可选） |

---

## 4. 分期计划

> 工期为个人开发参考值，不含真机调试往返。每期结束必须出 APK + 手写验收清单。

### P0 · 收尾当前重构（✅ 已在本轮完成，保留记录）

| 原事项 | 结果 |
|---|---|
| `RecordScreen.kt` 缺 `statusBars` 导入 | ✅ 已修（编译零 warning） |
| 首页要有「我的记录」入口 | ✅ 我的页有统计卡；P1.4 时把它接成历史列表入口 |
| 四 tab 落地 | ✅ 已完成 |

**遗留待办**：git 提交（把两轮改动合成 1~2 个 commit）、README 功能范围已过时（还写着「列表之外一律不做」）。

---

### P1 · 焊死闭环：看得到、找得回、拿得走（建议 6–8 个工作日）

**目标**：一次徒步结束后，用户能看到轨迹线、在列表里找到它、导出给朋友。
**这是当前唯一主线，内容层在 P1 期间不投入。**

| # | 任务 | 主要改动 | 验收标准 | 参考工时 |
|---|---|---|---|---|
| ✅1.1 | **地图引擎抽象**（必须先做，2026-10-20 完成） | `ui/map/MapEngine` + `MapPoint` + `AmapMapEngine`：`setTrack` / `setEndpoints` / `zoomToTrack` / `moveTo` / `followTo`。Osm 不实现（D1） | 上层业务代码不再出现 `aMap` / `LatLng` / `Polyline` 类型 ✅ | 0.5d |
| ✅1.2 | **记录中实时画线**（2026-10-20 完成） | `AmapMapEngine` 复用 Polyline 对象换点集；`RecordingTrackState.Snapshot.lastPoint` 推增量；`RecordViewModel.track` 从 DB 补齐 + 去重；附带相机跟随（用户拖图即停） | 走动时线跟着长 ✅；1 Hz 量级 O(n) 刷新，5000 点未实测 | 1.5d |
| 1.3 | **历史轨迹列表** | `TrackSessionDao.observeCompleted()`；`ui/tracks/TrackListScreen.kt`；从「我的 → 我的记录」进入 | 停止记录后立刻出现在列表；空态有引导文案 | 1d |
| 1.4 | **轨迹详情回看** | `ui/tracks/TrackDetailScreen.kt`：只读地图 + 整条轨迹 + 起终点 marker + 统计卡 | 点列表项进详情，地图自动缩放到轨迹 bbox | 1.5d |
| 1.5 | **坐标转换工具**（1.6 的前置） | `util/CoordTransform.kt`：`gcj02ToWgs84` / `wgs84ToGcj02` | 转换后与在线工具比对，误差 < 5m | 0.5d |
| 1.6 | **GPX 1.1 导出** | `export/GpxWriter.kt`（手写 XML，不引三方库）；DB 里的 GCJ-02 **必须转 WGS-84** 再写；`FileProvider` + 系统分享 | 导出的 GPX 能被 OsmAnd / 两步路 / Google Earth 正常打开且位置不偏 | 1d |
| 1.7 | **GPX 导入**（可与 1.6 合并） | `export/GpxParser.kt`（`XmlPullParser`）；导入后落成只读 session（WGS-84 → GCJ-02 再入库） | 能导入别人分享的 GPX 并在详情页看到 | 1d |
| 1.8 | 停止后跳详情 | `RecordScreen` 摘要卡加「查看轨迹」按钮 | 停止 → 摘要 → 一键进详情 | 0.3d |

**风险与坑**

- 高德 `Polyline` 必须在地图 `onCreate` 之后添加；`AmapMapView` 是 `AndroidView`，注意 `DisposableEffect` 里清理覆盖物，否则切页泄漏。
- 画线不要每来一个点就 `setPoints(全量)`：1000 点以上明显掉帧。
- **GPX 坐标系是 P1 最大的雷**：DB 存 GCJ-02，行业惯例 GPX 是 WGS-84，不转换会偏 50–500 m。先落 `CoordTransform` 再做导出/导入。
- `FileProvider` 的 authority 必须与 `applicationId` 一致，否则 `FileUriExposedException`。
- 详情页渲染前要抽稀（P2.5 若未做，先用简单距离阈值抽稀兜底）。

---

### P2 · 数据可靠性：进程被杀也不丢（建议 3–4 个工作日）

**目标**：解决"记录到一半 App 被系统杀掉，回来数据不见了 / 状态错乱"。

| # | 任务 | 主要改动 | 验收标准 |
|---|---|---|---|
| 2.1 | **Room 迁移策略**（越早越省事） | `exportSchema = true` + `room.schemaLocation`；`app/schemas/` 入库；正式写 `Migration` | 以后改表不再需要卸载重装 |

> **2.1 白话说明（为什么必须现在做）**
> Room 数据库就是手机里的几张表（会话表、轨迹点表）。你现在要加字段（比如 `ascentMeters` 爬升），
> **已经装过 App 的用户手机里是旧表结构**，新版本一打开就会崩（`IllegalStateException: Room cannot verify the data integrity`）。
> 两条路：① 卸载重装（用户轨迹全丢）；② 写 `Migration`（几行 SQL，把旧表数据搬到新表，用户无感）。
> `exportSchema=true` 就是把每一版表结构存档到 `app/schemas/`，让你能对照着写迁移、也能在测试里验证。
> 代价：一次性配置（30 分钟）+ 每次改表多写 10 行 SQL。**不做的话，第一次改表就得清库。**
| 2.2 | **悬挂会话恢复** | 启动时扫 `endTime IS NULL`：有残留点 → 提示"检测到未结束的记录，继续/结束"；无点 → 自动关闭 | 强杀进程后重进有明确提示，不出现永久"记录中" |
| 2.3 | **Service 与 UI 状态一致** | `RecordingTrackState` 现为进程内单例；改为启动时从 Room 反推（`endTime IS NULL` 且最近点 < 5 分钟） | 系统回收 Service 后重进，浮层数据从 DB 恢复 |
| 2.4 | **暂停 / 继续** | `TrackSession` 加 `pausedMs`；Service 加 `ACTION_PAUSE/RESUME`；通知加操作按钮 | 暂停期间不累加距离与时长；恢复后轨迹不断线 |
| 2.5 | **轨迹抽稀** | `util/Polyline.kt` 加 Douglas-Peucker；详情页渲染前抽稀，原始点不动 | 10 小时 / 2 万点详情页打开 < 1s |
| 2.6 | **写点批量化** | Service 内存缓冲 + 每 5–10 秒 `insertAll` | 记录 1 小时电量消耗 < 8%（中端机实测） |
| 2.7 | **计时基准修正** | 间隔过滤与时长统计改用 `SystemClock.elapsedRealtime()`（`currentTimeMillis` 受 NTP 校时跳变影响） | 改系统时间不影响记录 |
| 2.8 | **持锁** | 声明了 `WAKE_LOCK` 却未使用：记录中用 `PARTIAL_WAKE_LOCK` | 息屏 30 分钟点间隔仍 ≤ 3s |

**风险与坑**

- 2.2/2.3/2.4 都要改 `TrackSession` 表结构 → **必须在 2.1 之后**，顺序不能反。
- `START_STICKY` 重启时 `intent == null`，当前是"空跑发空通知"。**建议改 `START_NOT_STICKY`**，恢复逻辑上移到 Application 层；Service 只在被明确要求时启动。
- 暂停/继续不要用"新 session"实现，否则轨迹被拆成两条。

---

### P3 · 算得准：爬升与高程（建议 5 个工作日）

**目标**：从"走了多远"升级到"爬了多少、多陡、多快"。

| # | 任务 | 主要改动 | 验收标准 |
|---|---|---|---|
| 3.1 | **爬升/下降累计** | `util/Elevation.kt`：阈值过滤（< 3 m 波动不计）；`TrackSession` 加 `ascentMeters` / `descentMeters` | 与两步路同一条 GPX 对比，误差 < 10% |
| 3.2 | **配速/速度** | 30s 滑动窗口 + 平均/最高配速；详情页展示 | 停走时不出现 0 / ∞ 抖动 |
| 3.3 | **GPS 海拔噪声处理** | AMap `altitude` 弱信号下 ±30 m：中值滤波 + 简化卡尔曼，或明确标注"海拔仅供参考" | 平地徒步爬升不虚报（< 50 m） |
| 3.4 | **DEM 离线高程**（可选增强，可延后 P4 之后） | Copernicus DEM 30m → 按需裁剪 → 本地双线性插值 | 同一轨迹两次记录，爬升差异 < 5% |
| 3.5 | **分段统计** | 每公里分段（距离/爬升/用时），详情页列表 | 分段合计 == 总计 |

**风险与坑**

- 爬升是户外 App 最容易翻车的指标（阈值不同可差 800 vs 1200 m）。**先定算法再写 UI，阈值写进 `CLAUDE.md`**。
- DEM 不要整包塞 APK，按轨迹 bbox 下载瓦片或随离线包发。

---

### P4 · 离线与地图底座（建议 8–10 个工作日，可拆两批）

**目标**：无信号山区也能看地图——户外场景的分水岭。

| # | 任务 | 主要改动 | 验收标准 |
|---|---|---|---|
| 4.1 | 离线地图包（MBTiles） | 按省/市切 MBTiles；清单 JSON（region_id/bbox/size/sha256/version）；`WorkManager` 断点续传 + 校验 | 飞行模式下能看已下载省份 |
| 4.2 | 区域选择 UI | 省市列表 + 已下载/大小/进度；删除/更新 | 能看见包大小与下载时间 |
| 4.3 | 离线包存储管理 | `getExternalFilesDir`（免存储权限）；总量上限 + 清理策略 | 卸载不残留 |
| 4.4 | 底座评估结论 | 高德（合规、省事、商用限制）vs MapLibre + 自建（自由、需审图号） | 出一份结论文档，附成本/风险 |

**风险与坑**

- osmdroid 只能读 MBTiles（`OfflineTileProvider`），高德不能读 → **离线能力与引擎绑死**，所以 P1.1 的 `MapEngine` 抽象必须先做。
- 国内公开发布地图服务涉及审图号/测绘资质：**自用不分发可不做，上架必须评估**（写进 4.4 结论）。
- 瓦片版权：OSM 数据 ODbL 可商用但需署名；GeoQ 是商业服务不能打包分发。自建瓦片才干净。

---

### P5 · 户外安全（建议 4–5 个工作日）

| # | 任务 | 说明 | 验收标准 |
|---|---|---|---|
| 5.1 | 超时未动提醒 | 记录中 30 分钟位移 < 100 m 且非暂停 → 本地通知 | 模拟静止可触发 |
| 5.2 | 低电量提醒 | `BatteryManager`，< 15% 且记录中 → 建议结束并保存 | 真机可触发 |
| 5.3 | 位置分享 | 当前点/轨迹生成 GPX 或文本坐标，走系统分享 | 微信里能收到可点开的坐标 |
| 5.4 | 行程卡（可选） | 本地生成"轨迹缩略图 + 统计"分享图 | 不依赖服务端 |

**不做**：实时位置追踪（需服务端 + 常驻上传，与本地优先冲突）、SOS 一键救援（涉及外部服务与责任）。

---

### P6 · 内容层接后台（✅ 已确认启动，方案见 `BACKEND-PLAN.md`）

**触发已发生**：需要首页动态/线路的真实数据，不再用 mock。

**已定选型**（2026-10-20 评估 v2 客观打分 → 你拍板 Java，按 D5 落地；完整论证与踩坑见 `BACKEND-PLAN.md`）：

| 项 | 结论 |
|---|---|
| 语言/框架 | **Java 17 + Spring Boot 4.1.1**（4.x 的 starter 改名为 `-webmvc`，Flyway 要独立 starter） |
| 数据层 | **MyBatis-Plus 3.5.17**（`mybatis-plus-spring-boot4-starter`）+ **Flyway** 迁移 + 开发/测试用 H2(`MODE=MySQL`)、生产 **MySQL 8** |
| 部署 | 单机 Docker Compose（nginx + api + mysql），2C2G 轻量约 ¥60–100/月 |
| 接口 | `/api/v1/feed`、`/api/v1/routes`、`/api/v1/gear/categories`、`/api/v1/healthz`；统一 `{code,msg,data}`，cursor 分页 |
| 进度 | **S1–S3 已完成并验证**（4 接口跑通 + 12 个回归测试全绿）；S4 App 接 Retrofit、S5 部署待做 |

**若将来服务端要算路线**（GraphHopper）、切片/离线包生成、批量轨迹分析、对外地理 API —— 同语言直接加模块即可，
这也是当初选 Java 的一项实际收益。参考行业实况：Strava = Ruby + Scala + Go；Komoot = Java + Python + Clojure 并存。

**不做**：账号体系、UGC 发布、评论、支付、微服务、Redis（当前 QPS 用不上）。

**关键约束**：App 侧只改 `FootprintApp` 三个 lazy 单例的实现类（Mock → Remote），UI/ViewModel 零改动；
Mock 实现保留作为离线 fallback，不要删。

---

### P7 · 可选扩展（有真实需求再做）

- BLE 心率（标准服务 `0x180D`）、BLE GPS（NMEA over BLE `0x1819`）
- 天气（和风 / OpenWeather 免费额度）
- 步数（`SensorManager` `TYPE_STEP_COUNTER`）
- 等高线渲染（服务端预切瓦片，别在客户端算）

---

## 5. 决策表（已裁决 / 待确认）

| # | 决策 | 结论 | 状态 |
|---|---|---|---|
| D1 | **地图底座** | 短期继续高德；**osmdroid 只作 Key 失效兜底，不做功能对等**（不实现 polyline/详情/离线），避免"两套 bug 修两遍"。P1.1 先抽 `MapEngine` 接口，将来换引擎只改实现类 | ✅ 已确认（2026-10-20） |
| D2 | **tab 结构** | 保持五栏（用户已拍板）：首页/线路/**出发**/装备/我的。历史记录入口走「我的 → 我的记录」。若真机验证入口过深再议 | ✅ 已按用户决策执行 |
| D3 | **Room schema 策略** | 立刻 `exportSchema=true` + schema 入库 + 正式 Migration（P2.1）。等改到第 5 版再补，历史 schema 补不回来 | ⏸ 待理解后确认（见 §P2.1 白话说明） |
| D4 | **内容层接后台** | **已确认启动**。选型：Spring Boot 4.1.1 + Java 17 + MyBatis-Plus(boot4) + MySQL 8 + Flyway + Docker Compose，详见 `BACKEND-PLAN.md` | ✅ 已确认（2026-10-20） |

---

## 6. 明确不做（对齐 APK 报告 4.1「不抄」清单）

| 不做 | 原因 |
|---|---|
| 广告 SDK（两步路 27% 的类） | 与产品定位冲突，拖垮体积与启动 |
| UGC 生产闭环（发布/评论/关注/私信） | 需要服务端 + 审核；内容页只读即可 |
| 商城（有赞）、会员、赛事、景区专区 | 商业模块，与"干净版"目标相反 |
| 7 套推送并存 | 0 套（本地通知足够）；要远程再选 1 家 |
| GDAL / OGR / OSR（32.9 MB） | GPX/KML 手写解析 200 行足够，是最大体积陷阱 |
| 双地图引擎对等投入 | 一套主用 + 一套兜底足够 |
| 活体认证 / 风控 / 分享全家桶 | 无账号体系则不需要 |
| 私有蓝牙协议逆向 | 只做标准 BLE 服务（0x180D / 0x1819） |

---

## 7. 每个迭代的固定动作

1. **构建**：`./gradlew :app:assembleDebug --rerun-tasks`（`CLAUDE.md` 第 10 条：增量构建会假 UP-TO-DATE）
2. **版本**：`version.properties` 自动 +1；产物 `app/build/outputs/apk/debug/app-debug.apk`
3. **装机验证清单**（每次手写，别跳）：
   - 冷启动 → 首页不空白、底栏正常
   - 定位 → 城市名正确、蓝点在真实位置
   - 出发 → 权限两步、记录中距离涨、**轨迹线跟着长**（P1 后）
   - 停止 → 摘要对得上、列表出现新条目（P1 后）
   - 详情 → 轨迹完整、bbox 自适应（P1 后）
   - 导出 → 外部 App 能打开且位置不偏（P1 后）
4. **归档**：`CHANGELOG.md` 追加时间线；跨会话硬约束进 `CLAUDE.md`

---

## 8. 建议起手顺序（两条并行线）

**A 线 · App 核心闭环（P1，主线，不可跳过）**

1. **git 提交**当前两轮改动（先留回滚点）+ 更新 README 功能范围（10 分钟）
2. 抽 `ui/map/MapEngine` 接口（0.5d）—— 越早抽越便宜
3. `AmapMapView` 加 Polyline（P1.2）——**当下投入产出比最高的一件事**
4. `TrackSessionDao.observeCompleted()` + `TrackListScreen`（P1.3），把「我的 → 我的记录」接上
5. 详情页 + `CoordTransform` + `GpxWriter`（P1.4 / 1.5 / 1.6）
6. GPX 导入 + 停止后跳详情（P1.7 / 1.8）

**B 线 · 后端（P6，已确认启动，可与 A 线并行）**

1. ✅ `zouliangbu-server` 脚手架 → `/api/v1/healthz` 跑通（S1）
2. ✅ Flyway 建 4 张表 + 种子数据（文案直接复用 `MockData.kt`）（S2）
3. ✅ 三个只读接口 + 统一响应 + cursor 分页 + 12 个回归测试（S3）
4. ⏭ App 侧 Retrofit + `Remote*Repository`，只改 `FootprintApp` 三行（S4）
5. ⏭ HTTPS + 备份（S5）

> 两条线互不阻塞：B 线改的是"数据从哪来"，A 线改的是"轨迹怎么看"。**但 A 线优先级更高**——
> 内容再真，用户看不到自己的轨迹线，产品依然不成立。

**节奏判断**：P1 做完「能用」，P2 做完「敢用」，P4 做完「户外能用」，P6 才有资格谈"像两步路"。

---

## 9. 文档一致性待修（本轮发现）

| 文档 | 问题 | 处理 |
|---|---|---|
| `README.md` | 还写着「列表之外的功能（离线瓦片、GPX、航点、循迹、社交）一律不做」，且功能清单停在单页时代 | P1 开始前更新为五栏结构 + 最新范围 |
| `LOCAL_ENV.md` | 整篇是 macOS 路径（`/Users/zhouyinglin/...`），与当前 Windows 环境不符 | 补 Windows 段（JDK17 + `D:\Android\Sdk` + keystore 配置） |
| `CLAUDE.md` | 已更新（导航框架硬约束），但「关键文件速查」之外的旧章节仍描述 MainScreen | 下轮顺手清理 |
