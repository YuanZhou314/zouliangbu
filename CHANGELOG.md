# CHANGELOG — 走两步 (zouliangbu) 会话笔记

> 多端开发 / 不同会话间不互通，关键决策、踩坑、TODO 留这里给下次参考。
> `CLAUDE.md` 是硬约束清单（项目级，永驻），本文件是时间线（会话级，追加式）。
>
> **维护策略：B + C**
> - **B（关键节点）**：每修完一个 bug / 改完一段架构 / 发现新坑，主动追加一段
> - **C（会话结束）**：每次整次会话结束时，整合一份完整总结追加到最新日期

---

## [2026-10-20] P1 收尾：历史列表 + 详情回看 + GPX 导入（buildNumber=8）

### 触发
你确认按 1.3 → 1.4 → 2.1 → 1.7/1.8 推进。执行时**调整了一处顺序**：先做 2.1（Room schema 导出），
再改表。理由见「关键决策 1」。

### 产出

| 文件 | 变更 |
|---|---|
| `app/build.gradle.kts` | `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` |
| `data/db/AppDatabase.kt` | `exportSchema=true`、version=2、`MIGRATION_1_2`、`addMigrations()` |
| `app/schemas/.../{1,2}.json` | v1 与 v2 表结构存档（**必须入库**） |
| `data/db/TrackSession.kt` | 新增 `name: String?`（导入 GPX 带过来的轨迹名） |
| `data/db/TrackSummary.kt` | 新增。列表投影：会话 + `pointCount`，一条 SQL 带子查询（避免 N+1） |
| `data/db/TrackSessionDao.kt` | `observeCompleted()` / `deleteById()` |
| `data/db/TrackPointDao.kt` | `insertAll()` 批量写 / `deleteForSession()` |
| `data/repo/TrackRepository.kt` | 改持 `AppDatabase`；`observeCompleted` / `importTrack`（单事务）/ `deleteSession` |
| `export/GpxImport.kt` | 新增。解析结果 → TrackPoint（纯计算，可 JVM 测） |
| `export/GpxWriter.kt` | `defaultName` 优先用 `session.name`（导入再导出不丢名字） |
| `ui/tracks/TrackListScreen.kt` + `TrackListViewModel.kt` | 新增。列表 / 空态 / 长按删除 / SAF 导入 |
| `ui/tracks/TrackDetailScreen.kt` + `TrackDetailViewModel.kt` | 新增。地图 `autoZoom` + 起终点 marker + 统计卡 + 导出 |
| `ui/navigation/AppRoute.kt` | `TRACKS` / `TRACK_DETAIL` / `trackDetail(id)` |
| `ui/AppRoot.kt` | 详情页挂**外层**全屏路由 |
| `ui/tabs/MainTabsScreen.kt` | 内层加 `tracks` destination |
| `ui/profile/ProfileScreen.kt` | 「我的记录」卡片与「户外足迹→轨迹」接通 |
| `ui/record/RecordScreen.kt` | 摘要卡加「查看轨迹」；「关闭」移到 `dismissButton` |

### 关键决策

1. **顺序调整：2.1 先做**。schema 导出打开之前的历史版本补不回来——趁 version 还是 1 时先固化
   `1.json`，再改表升 2。若先做列表、后补 2.1，`1.json` 会永久缺失，以后想验迁移连基线都没有。
2. **`name` 字段走 version 2 + `Migration(1,2)`**，不用 `fallbackToDestructiveMigration`。
   改表顺手把迁移链路跑通一次：这条链路以后每次改表都要走，第一次就验证比第 5 次才发现问题便宜。
3. **详情页放外层 NavHost**：要占满屏幕看地图，不能被底栏和 Scaffold padding 挤压；
   列表放内层，保留底栏（它属于「我的」的下钻）。
4. **列表点数用子查询，不逐项查库**：LazyColumn 里逐项查是 N+1，滚动时每帧碰磁盘。
5. **导入 mime 用 `*/*`**：GPX 在不同来源被标成 octet-stream / text/xml / 甚至 application/zip，
   按 mime 过滤的结果是"用户在文件选择器里根本看不到自己的 gpx"。合法性交给解析器判。
6. **导入的转换逻辑单独抽 `GpxImport`**：整条链路只有这段有判断逻辑、也只有这段会算错
   （时间兜底 / 海拔兜底 / 距离累计），抽成纯函数就能在 JVM 里测。
7. **列表长按删除**：ROADMAP 没要求，但导入坏文件会堆出 0 点会话，不给删除用户只能清应用数据。

### 校验
```
./gradlew :app:compileDebugKotlin   → 零 warning
./gradlew :app:testDebugUnitTest    → 31/31 绿
  CoordTransformTest 6 / GpxWriterTest 9 / GpxParserTest 7 / GpxImportTest 9
./gradlew :app:assembleDebug        → 0.1.0.000008 (10008) 53.8 MB
schema 比对：2.json 相对 1.json 只多 track_sessions.name TEXT(可空)，与 ALTER 语句完全对应
```

### 已知限制 / 待验证
- **迁移没在真机上跑过**（本机只出包）。装机后第一次启动若崩，看 logcat 里的
  `Room cannot verify the data integrity`——那说明迁移 SQL 与 schema 不一致。
- 详情页长轨迹未测性能：目前全量点直接喂地图，2 万点以上需要 P2.5 抽稀。
- 导入只认 `trk/trkseg/trkpt`；KML、`rte`、`wpt` 不支持。
- 列表暂无搜索 / 筛选（P1 范围外）。

### 下一步
**P1 八项全部完成（1.1–1.8）**。接着做 P2.2 悬挂会话恢复 → 2.3 Service/UI 一致 → 2.4 暂停继续，
这三项都依赖 2.1 刚铺好的表能力。

---

## [2026-10-20] 服务端未来计划成文（SERVER-ROADMAP v1）

### 触发
服务端 v1 跑通后需要一份"往后做什么"的计划，且必须和 App 侧 `ROADMAP.md` 对齐（App 仍在开发中）。

### 产出

| 文件 | 作用 |
|---|---|
| `SERVER-ROADMAP.md`（新增） | 服务端 S4–S8 分期、与 APK 的对接方案、接口演进规则、S4 验收标准 |
| `ROADMAP.md` | 顶部配套文档 + P6 段 + §8 B 线补交叉引用（双向可达，避免两份计划各自漂移） |

### 核心结论（写进文档的判断）

1. **服务端不在 App 的关键路径上**：P1 闭环 / P2 可靠性 / P3 数据 / P5 安全全部纯本地，一行服务端代码都不需要；
   只有 P4 离线包（可选）和 P6 内容层需要。→ **S4 排在 App P1 之后，不许抢时间。**
2. **轨迹永不上云**是产品差异化，不是暂缓项。将来做"分享轨迹"走 App 端导出 GPX/海报，不上传。
3. 实测确认 App 三个 Repository 签名与"UI 零改动"承诺有三处冲突
   （`observeFeed` 无分页 / `observeCatalog` 无 keyword / `categories()` 是同步函数），
   文档里给了 S4 过渡方案 + S6 正式方案 + 技术债上限（200 条 / 12 条）。
4. `network_security_config.xml` **不支持 CIDR**，`<domain>192.168.0.0</domain>` 无效，只能写具体 IP
   或 debug 下用 `base-config` 全放行——这是 S4 联调的常见卡点，提前写进文档。

---

## [2026-10-20] P1.5–P1.7：坐标转换 + GPX 导入导出 + 首批 21 个单测（buildNumber=7）

### 触发
真机画线暂时测不了 → 转做**不依赖设备、能在 JVM 上跑验证**的部分。
坐标转换和 GPX 正好是 P1 里风险最高、又纯计算的两块（ROADMAP 原话："GPX 坐标系是 P1 最大的雷"）。

### 产出

| 文件 | 作用 |
|---|---|
| `util/CoordTransform.kt` | `wgs84ToGcj02`（克拉索夫斯基椭球加偏）/ `gcj02ToWgs84`（不动点迭代，3 轮 <0.1 m）/ `isOutOfChina` |
| `util/GeoCoord.kt`（同文件内） | 经纬度纯数据类 |
| `export/GpxWriter.kt` | GPX 1.1 手写 XML；**GCJ-02 → WGS-84**；6 位小数；UTC 时间；非法点跳过；XML 转义 |
| `export/GpxParser.kt` | SAX 解析 `trk/trkseg/trkpt`；**WGS-84 → GCJ-02**；支持无命名空间 GPX；XXE 已关 |
| `export/GpxExporter.kt` | 写 `cacheDir/gpx/` → `FileProvider.getUriForFile` → 系统分享 Intent |
| `res/xml/file_paths.xml` + Manifest `<provider>` | authority `com.zouliangbu.footprint.fileprovider` |
| `ui/record/RecordViewModel.exportGpx()` | 读该会话所有点 → 导出 → 回调分享 Intent（IO 线程） |
| `ui/record/RecordScreen.kt` | 摘要卡加「导出 GPX」按钮；无点时 Toast 提示 |
| `app/src/test/java/**`（3 个测试类） | 项目**首批**单元测试，21 用例 |

### 关键决策

1. **用 `javax.xml.parsers` 的 SAX，不用 `android.util.Xml` 的 XmlPullParser**（ROADMAP 原写的是后者）。
   原因：`android.util.Xml` 是框架类，在 JVM 单测里是 stub，一调就抛
   `RuntimeException: Method ... not mocked`。GPX 是"格式一错就全错"的解析，必须能回归测试，
   所以选同一份代码在真机和 JVM 都能跑的 SAX。**这是对 ROADMAP 的有意偏离，记在这里。**
2. **坐标边界只在 writer / parser 两处**：DB 与地图全链路恒为 GCJ-02，GPX 恒为 WGS-84，
   不要在别的地方顺手转（转两次就偏几百米，且很难发现）。
3. **反解用不动点迭代**：`wgs += gcj - forward(wgs)`，3 轮残差 < 0.1 m。没有解析逆，别去找公式。
4. **文件走 cacheDir + FileProvider**，不申请任何存储权限（Android 10+ 分区存储下
   `WRITE_EXTERNAL_STORAGE` 已无效），也不在用户相册/文档里留垃圾。
5. **没有轨迹点时不导出**：空 GPX 传给别的 App 只会让人困惑；UI 用 Toast 明确提示。
6. **导入的 UI 入口暂不接**：等 P1.3 列表页落地后再挂"导入 GPX"，否则没有合理的落点。

### 校验（本轮全部可执行，不依赖真机）
```
./gradlew :app:testDebugUnitTest
  CoordTransformTest   6 用例  0 失败
  GpxWriterTest        8 用例  0 失败
  GpxParserTest        7 用例  0 失败
  --------------------------------
  合计 21 用例，0 失败 0 错误
./gradlew :app:assembleDebug → 0.1.0.000007 / versionCode 10007 / 53.8 MB
aapt2 dump xmltree … | provider authority = com.zouliangbu.footprint.fileprovider ✓
```

测试锁的三类性质：
- 坐标：境外不偏移 / 京津广拉哈昆五城偏移量级 300–800 m / 往返残差 < 0.3 m / 方向应北偏东
- 导出：产出能被标准 DOM 解析 / 坐标确实是 WGS-84（且与 GCJ 原值有可见差异）/
  越界点跳过 / 海拔 0 不写 `<ele>` / 空轨迹仍是合法文档
- 导入：有命名空间与无命名空间都能解析 / 缺 ele+time 不崩 / 非法坐标忽略 /
  格式错误抛 `IllegalArgumentException` / **写出再读回坐标无损**

### 已知限制
- 单测是**自洽性质**验证（量级、往返、境内外），没有与权威在线工具逐点比对。
  真机上导出一次，用 Google Earth / OsmAnd 打开看位置对不对，是唯一能证明精度的方法。
- 只解析 `<trk>/<trkseg>/<trkpt>`；`<rte>/<rtept>`（路线）、`<wpt>`（航点）未支持。
- 分享 mime 用 `application/gpx+xml`；若微信等 App 在分享面板里不出现，
  改 `GpxExporter` 里的 type 为 `application/octet-stream`（文件名仍是 .gpx，不影响识别）。

### 下一步
P1.3 历史列表（`observeCompleted` + `ui/tracks/TrackListScreen`）→ P1.4 详情回看（复用
`MapViewWrapper(autoZoom=true, showEndpoints=true)`）→ 把「导出 / 导入 GPX」挂到详情与列表上。

---

## [2026-10-20] P1 起步：地图引擎抽象 + 记录中实时折线（buildNumber=6）

### 触发
你的指令：README 暂时不更新，**先把当前工作区 commit 一版当回滚点，然后开始做 P1**。

### 提交基线（回滚点）
- `5bcaf63` feat: 五栏导航框架 + 内容层 Mock + 统一定位架构（App 侧全部未提交改动 + keystore 入库）
- `ec97458` feat(server): 内容服务端 v1（与 App 解耦，单独一个 commit，便于单独回滚）
- `README.md` 按你的要求**保持未提交**（工作区仍显示 `M README.md`），P1 收尾时再统一改

### 产出（P1.1 + P1.2，另附相机跟随）

| 文件 | 变更 |
|---|---|
| `ui/map/MapPoint.kt` | 新增。与引擎无关的经纬度点（GCJ-02），业务层只认这个类型 |
| `ui/map/MapEngine.kt` | 新增。`setTrack` / `clearTrack` / `setEndpoints` / `zoomToTrack` / `moveTo` / `followTo` |
| `ui/map/AmapMapEngine.kt` | 新增。高德实现：Polyline 复用 + Marker 复用 + bbox 缩放 + 退化保护 + `reapply()` |
| `ui/map/AmapMapView.kt` | 加 `track` / `showEndpoints` / `autoZoom` 三个**声明式**参数；相机跟随 + 用户拖图即停 |
| `ui/map/MapViewWrapper.kt` | 透传三个参数；osmdroid 分支不接收（D1：只兜底不画线） |
| `data/RecordingTrackState.kt` | `Snapshot` 加 `lastPoint`（最近一个已落库的点，供地图画线） |
| `service/RecordingTrackService.kt` | `publishSnapshot()` 带上 `lastPoint` |
| `ui/record/RecordViewModel.kt` | 新增 `track: StateFlow<List<MapPoint>>`：进页面从 DB 补齐 + 按 timestamp 去重追加 |
| `ui/record/RecordScreen.kt` | 把 `track` 传给地图（记录中实时画线） |

### 关键决策（下次别再纠结）

1. **osmdroid 分支不实现 MapEngine**。ROADMAP D1 已经定过"兜底不做功能对等"，
   所以无 Key 时地图上只有蓝点没有线——这是个**既定取舍，不是 bug**，别去补。
2. **Polyline 只创建一次**，之后 `polyline.points = 新点集`（底层 `setPoints`）。
   每点 `addPolyline` 会让覆盖物持续堆积且 AMap 内部重建纹理。
3. **地图没加载完就 addPolyline 可能不渲染** → `AmapMapEngine` 缓存最后一次 track，
   由 `OnMapLoadedListener` 调 `reapply()` 重放。
4. **相机跟随必须可被用户打断**：`OnMapTouchListener` 一触发就关跟随，
   右下角定位按钮重新开启。否则用户想看看周围，地图会一直把自己拽回去。
5. **`newLatLngBounds` 遇到零面积 bbox 会抛异常**（单点 / 所有点重合）→ 先算跨度，
   退化时改 `newLatLngZoom`。
6. **类型名用 `MapPoint` 不用 `GeoPoint`**：osmdroid 有 `org.osmdroid.util.GeoPoint`，
   同名会撞（同包 + 显式 import 的优先级问题，排查起来很费时间）。
7. **停止记录后不清空折线**：摘要卡弹着的时候地图上还能看见刚走的线；
   下一次开始按新 sessionId 重新加载（顺带清掉上一条）。

### 校验
```
./gradlew :app:compileDebugKotlin  → BUILD SUCCESSFUL（零 warning）
./gradlew :app:assembleDebug       → BUILD SUCCESSFUL
app/build/outputs/apk/debug/app-debug.apk
  versionName 0.1.0.000006 / versionCode 10006 / 53.7 MB
新类已进产物：ui/map/{MapPoint,MapEngine,AmapMapEngine}.class（17:33 编译，dex 已重打）
```
**未做真机验证**（按项目约定：Windows 端只产出 APK，装机由你来做）。

### 真机验收清单（本轮只需看这 4 条）
1. 开始记录 → 走动 20 m 以上，地图上出现**绿色轨迹线**（ForestGreen `#2E7D32`，10px 宽）
2. 走动时相机**跟着自己走**，线始终在屏幕内
3. 手指拖动地图 → 相机**不再自动回位**；点右下角蓝白定位按钮 → 恢复跟随
4. 停止记录 → 摘要卡弹出，地图上**线还在**（不消失）

### 下一步（P1 剩余）
`1.3 历史列表`（`TrackSessionDao.observeCompleted` + `ui/tracks/TrackListScreen`，入口接「我的 → 我的记录」）
→ `1.4 详情回看`（直接复用 `MapViewWrapper(track=…, showEndpoints=true, autoZoom=true)`）
→ `1.5 CoordTransform` → `1.6 GPX 导出` → `1.7 导入` → `1.8 停止后跳详情`

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