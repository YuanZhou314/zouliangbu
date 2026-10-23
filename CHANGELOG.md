# CHANGELOG — 走两步 (zouliangbu) 会话笔记

> 多端开发 / 不同会话间不互通，关键决策、踩坑、TODO 留这里给下次参考。
> `CLAUDE.md` 是硬约束清单（项目级，永驻），本文件是时间线（会话级，追加式）。
>
> **维护策略：B + C**
> - **B（关键节点）**：每修完一个 bug / 改完一段架构 / 发现新坑，主动追加一段
> - **C（会话结束）**：每次整次会话结束时，整合一份完整总结追加到最新日期

---

## [2026-10-23] 真机反馈修复 5 项（第二轮：离线/地图模式/天气表/转场）

**1 离线地图"已下载"闪现**：SDK 列表异步加载、finally 无条件 loading=false → 先渲染全"未下载"再 0.5s 跳变。VM 加 `amapListReady` 门控（downloaded 非空即 ready；重查轮跑完也置 ready，覆盖零下载场景），ready 前只显示加载态。

**2 预览页仍定位本机位置（上轮修复真机无效）**：排除法坐实根因＝`cityCenter(cityName)` 真机返回 null（SDK OfflineMapCity.city 真实命名与静态表不匹配，含"全国概览图"这类条目）→ initialCamera=null → 首点跟随拉回本机。修三层：① 预览页 initialCamera 永不为 null（未命中回落中国几何中心 zoom4，首点跟随恒禁用）；② AmapMapView 在 OnMapLoaded 后校验重试（250ms 一次，cameraPosition 偏差 >0.5° 就重发 moveCamera，上限 2s）；③ refreshAmap 打印 SDK 城市名列表（截断 20），真机跑一次即可补全静态表。
- 经验：**"静态链路全对但真机无效"时优先怀疑上游数据形态**（这里就是 city 名对不上表），日志埋点要先于猜测。

**3 地图模式拖拽热区 + 搜索框**：拖拽从 20dp 短横线扩到整个把手栏（~68dp）；中部加搜索框（实时过滤 name/region/tags，大小写不敏感），TextField 优先消费点击、竖拖过 slop 才转拖拽，避免"点搜索框却拖列表"。

**4 页面切换重叠**：NavHost 转场期间新旧两页同时组合——slide/fade 都如此，SurfaceView 地图页尤糟。改为：tab 间切换全 None（即切）；地图覆盖页 enter/popEnter 保留滑入、**exit/popExit 即时移除**（顺带根治 GL 地图延迟退出）；每个 destination 不透明背景。

**5 逐小时多维表改版（莉景天气式）**：行=指标（日期/小时/昼夜天气图标/温度/湿度/雨雪/风速/风向旋转箭头/AQI 国标六档着色/能见度/云量低中高）× 36 小时横滑，左列固定图标+名称+单位，当前小时整列高亮。风向行依赖服务端 hourly 补 `wind_direction_10m`（App 侧已就绪，未发布——下次发布生效）。云分层曲线图未做。

---

**1 离线地图重进"已下载"为空**：`OfflineMapManager` 构造后本地离线索引**异步加载**，首次同步读列表读到空快照且无重试 → initAmap 后 500ms/1.5s/3s 延迟重查，非空即停（`Log.d("OfflineMap")` 打三列表 size 可真机验证）。

**2 已下载地图"查看"定位当前位置**：MapViewWrapper/AmapMapView 加 `initialCamera/initialZoom` 参数（生效时禁用首点跟随）；城市中心查 `OfflineRegionCatalog.CITY_CENTERS` 静态表。
- 坑：表已扩全国（34 省级 + 20 主要城市），但**「简称+自治区」补后缀对新疆/西藏/广西命不中**（全称含民族名）——5 个自治区必须显式建简称键；省份条目中心点用省会。

**3 删除离线地图无反应**：高德 `OfflineMapManager.remove(String)` 参数是城市**名称**（传省份名会删全省），代码传了 `c.code` → 静默无效。改 `remove(c.name)`，onAmap 失败写 amapError。

**4 景观天气页增强**
- 蓝调时刻：Astronomy 补太阳 -4° 精确解（晨/暮两段，纯本地计算）；指标卡 2×2→5 卡，等级用 5 段色阶点亮条。
- 未来 3 天：服务端 OpenMeteoClient 加 `daily=` 块 + `forecast_days=4`，App 加 daily DTO + 区块。
- 逐小时多维表（天气/温度/湿度/降水/风速/空气/能见度 7 列，冻结首列，≤36 行）：空气质量走 Open-Meteo **独立端点** `air-quality-api.open-meteo.com`（服务端合并顶层 `airHourly`，上游挂掉只省略该字段）。
- **大坑：Open-Meteo 必须带 `timezone=Asia/Shanghai`**，否则 UTC 切日导致每天 0–8 点"明天/后天"标签差一天，且 App 时刻解析整体偏 8h——服务端常量 `QUERY_TIMEZONE` 与 App `queryZone` 两端必须一致。

**5 详情页 1/3 全览相机（第 4 遍，找到真根因）**：`applyZoom` 展开档 `availH.coerceAtLeast(60)` + `zoom.coerceIn(3.0,18)` → zoom 被钳 3.0 但南移补偿 dLat 仍按 zoom3 的 mpp（≈20km/px）算 → 相机甩数千公里外、轨迹跑出可视区上方；且 `pendingZoom` 无失效机制被 OnMapLoaded/切卫星图 reapply 反复重放坏相机。修法：availH<120 或 zoom<4.0 跳过 fit+清 pending；pending 只存 viewSize(0,0) 未生效请求，apply 成功即清。**用户说"记录页"实为轨迹详情页**（记录页是全屏地图）。

**6 轨迹卡片第三项**：改显示最高海拔——`observeCompleted` 投影加 `(SELECT MAX(p.altitude) FROM track_points p WHERE p.sessionId=s.id) AS maxAltitudeMeters` 子查询，无需 Room 迁移；0=无高程显示"—"。

**7 地图模式定位北京**：`autoZoom=true` 会禁用首点跟随（AmapMapView 防抢相机设计）→ 改传 false 走 firstFix 定位本机，选中线路后靠 fitKey 手动 zoomToTrack。

**8 地图模式视口卡片**（新功能）：服务端 feed `RouteItem` 加可空 `startLat/startLng`（route_track_point 首点，GCJ-02 直传，ContentApiTest 有深圳市域断言）；MapEngine 加 `visibleBounds/onCameraIdle/onMarkerClick/showPointMarkers`；相机停下按视口+20% 边距筛起点 marker（≤20 最近），点 marker=选中该线路。
- 坑：marker 点击映射**不能按下标平行对位**（addMarker 失败就错位、点 A 选成 B）——用 `Map<Marker, String>` 按引用直查。
- 拖拽列表三档吸附（地图比例 0.04/0.5/0.92，`nearestAnchor` 纯函数+单测）。**地图模式当前没有搜索框**，"下档只留搜索框"按把手栏可见取值。

**9 返回动画（地图模式/离线预览/轨迹预览）**：上轮只修了外层 NavHost，内层 `MainTabsScreen` NavHost 仍是默认 fade——SurfaceView 不吃 alpha → 文字先隐、GL 地图延迟退出。内层补同款 slide 四件套。

验证：App `:app:testDebugUnitTest` 142/142、服务端 70/70、compileDebugKotlin 绿；Ray 审查 P0=0（P1×1 已修，P2×6 记录在 `.agent-runs/2026-10-23/task-001/ray-review.md`）。未提交。

---

**1 离线地图**（两个真根因）
- `OfflineMapStatus.LOADING == 0`，而**未下载城市的默认 state 就是 0** → 全页被判「下载中」转圈。
  改以 SDK `getDownloadOfflineMapCityList()`（已下载）/ `getDownloadingCityList()`（下载中）判定；
  暂停以 state(PAUSE/STOP) 为准（暂停后城市仍在下载队列里，只看队列会误判）。
- `OfflineMapManager` 构造里 `new Handler()`，**在 IO 线程创建会静默失败** → 点了下载没反应、退出再进还转圈。
  所有 SDK 调用改 `Dispatchers.Main`。
- 已下载置顶单独分区、可点击进全屏预览页；下载中 700ms 轮询刷进度。

**2 底部 Tab**：只有首页/线路/装备/我的四个主页显示，二级三级页全屏。

**3 景观天气页**重写（696 行）：渐变头部卡 + Canvas 日出弧（标当前时刻）+ 2×2 朝霞/晚霞/云海/观星 + 雷达占位。
- 修真 bug：`fmtMin` 把 UTC 分钟当本地时间打印，中国时区日出差 8 小时。

**4 详情页 1/3 总览相机**：`applyZoom` 改同步确定性算法（自算 Web Mercator zoom + 相机南移 covered/2 像素，一次 moveCamera）。
- 原「newLatLngBounds + 延时读相机再补偿」是两步异步，会被切卫星图触发的重放打断 → 一错就整体偏，必须下拉才看到轨迹。

**5 总运动累积剔除导入**：列表页与首页月度汇总都过滤 `source=imported`（常量 `TrackSession.SOURCE_IMPORTED`）。

**6 导入 0 米爬升**：`importTrack` 补 `Elevation.ascentOf()` 落库 ascent/descent。

**7 服务端深圳线路**（Flyway `common/V9__shenzhen_routes.sql`，已上线）
- 15 条深圳线路，各含正文/交通/难度/路况/景观 + 40~80 轨迹点 + 3~6 标注点（共 868 点 / 82 标注）。
- V9 末尾把「无 route_detail 的旧占位线路」置 `status=0` 下线，保证线路页条条有内容。
- 生成脚本 `zouliangbu-server/scripts/gen_shenzhen_routes.py`。
- 坑：① 脚本末行笔误 `ROUTESS` ② 正文混进乱码英文词，清理正则**只能清单引号字面量内部**，
     按「全文件删英文词」会误删 INSERT/route 等 SQL 关键字 ③ MySQL 对 UPDATE 目标表出现在子查询有 1093
     限制 → 用 `NOT IN`（子查询只查 route_detail）。
- 改测试后 `zlb-dev.db` 的 V9 校验和不匹配 → 删库重建（`*.db` 已 gitignore）。

**8 线路地图模式**（`RouteMapModeScreen`，466 行）：上半屏地图 + 下半屏列表，中间把手拖拽（25%~75%）。
- 地图按整屏高度测量、只移动列表，MapView 不重新 measure → 不闪。
- 坑：子 agent 漏 `import androidx.compose.ui.input.pointer.pointerInput`。

**验证**：`compileDebugKotlin` + `testDebugUnitTest` **119/119 绿**；服务端 `mvn test` **69/69 绿**；
发布 `20261023-182735-c9de946-dirty`，线上 17 条线路逐条 check（正文 390~712 字 / 轨迹 40~80 点 / 标注 3~6 个）全合格；
APK buildNumber=24。
**遗留**：线上「最新收录」两条同名「苏州中国地图徒步」（id 13/14，重复提交的审批数据，正文 128 字无轨迹）待清理。

---

## [2026-10-23] 竞品分析 + 循迹 a+b（线路离线缓存 + 记录页参考线）

**决策**（详见 `COMPETITOR-ANALYSIS.md`，跨会话沉淀）：
- 两步路押「安全+离线 GIS」、六只脚押「回顾+成品分享」，共同成功点＝记录之后的增值。
- 走两步唯一能赢的牌＝「经审核的线路库 + 干净的记录引擎」，接起来（循迹）最值。
- 用户明确**真机户外记录暂缓**（没空出门），先做不依赖户外的循迹 a+b。

**循迹 a：线路轨迹离线缓存**
- Room v7：新增 `route_cache`（routeId 唯一）+ `route_cache_point`（外键 CASCADE）两张表，
  `MIGRATION_6_7` + `schemas/7.json` 入库。
- `RouteCacheDao` + `TrackRepository` 新增 `getCachedRoutePoints / hasCachedRoute / saveRouteCache / deleteRouteCache`。
- `RouteDetailScreen`：线路轨迹地图下方加「下载离线」（写入缓存，覆盖式）+「跟着走」按钮（须先缓存）。

**循迹 b：记录页叠加参考线**
- `MapEngine.setReferenceTrack`（虚线、青色 `#00BCD4`、zIndex 5 压在实时轨迹下）+ `AmapMapEngine` 实现 + reapply。
- `MapViewWrapper` / `AmapMapView` 加 `referenceTrack` 参数。
- `RecordScreen` 加 `refRouteId`（可选）→ `RecordViewModel.loadReferenceRoute` 读缓存转 MapPoint；
  无缓存时 Toast 提示「先下载离线」。
- 导航：`AppRoute.RECORD_WITH_REF`（`record?refRouteId=`）；`MainTabsScreen.onFollowRoute` → `AppRoot` 跳记录页。

**验证**：`compileDebugKotlin` 通过；单测 **119/119 绿**（0 失败，含并行会话新增 18 例）。
**未做**：偏航/到点提醒（循迹 c，需真机）；`route_cache` 无 JVM 单测（Room 迁移靠 schema 比对，无迁移测试基建）。

---

## [2026-10-22] 服务端发布到线上（V7 社区共维护上线）

- **发布**：`bash deploy/release.sh`（默认用户已改成 ubuntu）。线上切到 `20261022-151907-6c07651-dirty`，
  健康检查通过，Flyway 跑到 **V8**。
- **数据库**：线上其实是 **dev profile + SQLite**（`/opt/zouliangbu-server/zlb.db`），不是原来文档写的 prod+MySQL；
  发布前已备份 `backup-20261022-211735.db`。CLAUDE.md / deploy/README 已校正。
- **修了两个 bash 脚本的 mac 兼容 bug**（都是中途失败、最难查的那种）：
  1. macOS 没有 `md5sum`（只有 `md5 -q`）→ 加 `md5_of()`；失败点在上传后、切软链前。
  2. `$REMOTE_REL，` 后跟中文标点被 bash 当成变量名的一部分，`set -u` 下报 `unbound variable`；
     失败点在**切软链并重启之后**（服务已经起来了，脚本却崩了，健康检查没跑）。改成 `${VAR}，`。
- **线上 smoke test**（公网直连，模拟手机）：
  - `POST /api/v1/proposals` UPLOAD（12 点）→ `PENDING`、`votesUp=1`（提案人自动一票）✓
  - `GET /api/v1/proposals?status=PENDING` 可查 ✓
  - admin 登录 → `GET /api/v1/admin/proposals` → `POST /{id}/reject` → PENDING 清空 ✓
  - `/admin/proposals.html` 200 ✓、`/api/v1/routes/1` 已返回 `version` 字段 ✓
- **数据现状**：`route_detail` 只有 3 行（13 条线路多数没有详情），对这些线路提勘误会报
  「线路详情不存在」——不是 bug，是种子数据没详情；要给哪条提勘误得先补 detail。

## [2026-10-22] 详情页沉浸式：卫星图 / 无标题栏 / 深色模式 / 圆角

用户提的四条，逐条落：

1. **圆角处发黑**：根因是根容器 `background(Color.Black)`。卡片顶部两个圆角缺口露的就是它。
   改成 `MaterialTheme.colorScheme.surface`，浅色深色都不会再露黑边。
2. **卫星图**：给 `MapViewWrapper` 加 `satellite`，地图建好后 `setMapType(SATELLITE)`。
   **坑**：切卫星图会清空已添加的覆盖物，且清理是异步的 —— 紧接着重画也会被清。
   对策：`setMapType` 后延迟 350ms 再 `reapply()`。
   **连带坑**：原来的品牌绿线 `#2E7D32` 在深绿卫星图上等于隐身（截图里完全看不到线）。
   改成亮黄 `#FFD400` + 40% 黑描边（底层多画一条 17px 宽的半透明黑线）。
3. **深色模式**：卡片原来用 `Column + Modifier.background()`，只画了底色，
   `LocalContentColor` 还是外层的黑色 → 深色模式下所有正文黑字压黑底。
   改成 `Surface(color = surface)`，`Surface` 会提供 `contentColorFor(surface) = onSurface`，
   所有 `Text` 不用逐个指定颜色就自动跟随主题。
4. **沉浸式**：删掉标题栏，地图从状态栏顶上开始铺，左上/右上各一个 44dp 半透明圆按钮
   （返回 / 更多）。标题只在卡片里出现，不再在顶端重复。

**试过但放弃的方案**：地图铺满整屏 + 在引擎里用 `coveredBottomPx` 把 bbox 上移半个卡片高度。
`moveCamera` 的相机状态是异步生效的：紧接着读 `cameraPosition` 拿到旧 zoom、读
`projection.fromScreenLocation` 甚至返回 (0,0)，照着它再 moveCamera 会把地图扔到赤道。
最终改成「地图高度 = 卡片顶边位置」，可视区即地图区，bbox 适配天然正确。
（引擎里保留了 coveredBottom 分支，传 0 即不启用。）

## [2026-10-22] 地图全览真正修好：高度算反 + 顶栏遮挡

上一版「加了 reapply 补缩放」只解决了一半，用户仍然看到轨迹只有上半截。这次截图逐帧对比后找到两个真因：

1. **地图高度算反**：`layout { h = screenPx - settledTop }` 得到的是**卡片自身高度**，
   不是可视区高度。地图比可视区高出一倍，bbox 适配后轨迹中心落在卡片底下。
   正解是 `h = settledTop`（卡片顶边位置就是地图底边）。
2. **顶栏与地图重叠**：顶栏浮在地图上，fit 时要么留一大圈空白（轨迹被压得很小），
   要么起终点 marker 被顶栏盖住。正解：地图放到顶栏**下面**（`padding(top = 状态栏+56dp)`），
   两者不重叠，fit 留白只要 12dp 就够，轨迹能铺满可视区。

顺带：顶栏从「黑色 22% 半透明」改成 surface 94%，图标文字转深色 —— 原来压在白色地图上是一条灰带。

新增 `MapViewWrapper(fitPaddingDp)`，控制 bbox 适配留白；`AmapMapEngine` 加了 `TrackMap` 日志
（applyZoom/reapply 时会打印目标 bbox 与相机位置），下次地图问题可以直接看 logcat 定位。

## [2026-10-22] 图表加坐标轴 + 分段表格等宽

- **分段表格四列改成等宽**：之前 `weight(0.85/1/1/0.8)` 让表头和数据行对不齐；
  现在统一 `weight(1f)`，实测四列 228/228/229/229 px。
- **曲线图补完整坐标轴**（`TrackChart`）：
  - 左侧 Y 轴 3 个刻度（上/中/下，与横向网格对齐）；
  - 底部 X 轴 5 个刻度（0 / 1/4 / 1/2 / 3/4 / 全长，末尾带 km）；
  - Y 轴线加粗、5 条纵向网格 + 3 条横向网格；主曲线末端加圆点；平均线改虚线。
  - 刻度文字用 Compose 布局画（不用 Canvas.drawText），字体跟主题一致，也不用换算 sp→px。
- **数值摘要单独一行**：标题下面独立一行（如「平均 1.1 · 最高 9.6 km/h（虚线为平均值）」），
  不再挤在标题右边。
- 新增 `TrackDetailUi.minAltitude`（海拔图摘要要显示最低点）。
- **Y 轴刻度槽收紧 + 左对齐（同日二次修）**：槽 44dp 变 30dp，标签从右对齐改左对齐。
  原来数字右对齐贴着轴线，多出来的宽度全落在屏幕最左边（实测数字只占 43px、槽占 132px，
  左侧 128px 空着），用户第一眼就看出是「留了一大片空白」。现在数字与标题同为 50px 左边界。
- 配速刻度改用紧凑格式（总分钟:秒，如 98:10），否则 h:mm'ss 那种 7 字符写法放不进窄槽。

## [2026-10-22] 详情页补图：地图定位 bug + 四张曲线 + 分段表格

- **地图停在默认中心（北京）的根因**：`zoomToTrack` 在 `OnMapLoaded` 之前被调用，AMap 直接丢弃，
  而 `reapply()` 只重放轨迹线和起终点、**不重放缩放**，那次请求永久丢失。
  修法：`AmapMapEngine` 缓存 `pendingZoom/pendingPaddingPx`，`reapply()` 里补一次 `applyZoom()`。
  验证方式：数截图里轨迹线颜色 `#2E7D32` 的像素，修复前 236 个（只有 marker），修复后 **4451 个**。
- **地图视口改成只到卡片顶边**：原来地图铺满整屏、卡片盖住下面 2/3，`newLatLngBounds` 按整屏适配，
  轨迹正好落在被盖住的部分。现在地图高度 = `屏高 - 卡片顶`（松手后才更新，拖拽中不改，避免 AMap 每帧重排），
  并给 `MapViewWrapper` 加了 `fitKey`，换档后重新适配 bbox。
- **新增四张曲线图**（`ui/tracks/TrackChart.kt`，手写 Canvas，不引图表库）：速度、配速、海拔、累计爬升+下降。
  纵轴用 1%/99% 分位而不是极值，单个 GPS 尖峰不会把图压扁；面积用主题色到透明的渐变。
- **新增 `util/TrackSeries`**：按**距离**切 100 m 桶生成序列（按点数切会让采样密的地方看起来更慢）。
  距离规则复用 `TrackMetrics.countedSegment`，静止桶不出速度点，时间戳倒退直接跳过。
- **分段改成表格**：公里/用时/配速/爬升四列、数字右对齐、隔行浅底色，替代原来一行串起来的写法。
- 单测：**108 例全绿**（+4 `TrackSeriesTest`）。

## [2026-10-22] 轨迹详情页重做：地图 1/3 + 可拖拽三档卡片

按六只脚的布局重排详情页（截图见 `docs/shot-detail-*.png`）。

- **地图**：铺满整屏做背景，默认档只露出上面约 1/3（`mediumTop = 屏高 * 0.35`）。
- **详情卡三档**（拖顶部把手）：展开（56dp，地图只剩顶栏）/ 默认（1/3 地图）/ 收起（210dp）。
  收起档**隐藏概览与六宫格**，只留「标题 + 大字号里程」——否则三行概览就把 210dp 吃光，
  折叠后反而看不到关键数字。判定用 `derivedStateOf`，避免拖拽每帧重组整棵内容树。
- **卡内顺序**（先概览后细节）：时间区间 → 轨迹点 → 起点坐标 → 标题 → 46sp 里程 + 难度 →
  六宫格（总时间/最快速度/平均速度 · 最高海拔/累计爬升/累计下降）→ 配速 → 分段明细。
- **右上角三点菜单**：分享行程卡 / 导出 GPX / 与上次对比 / 贡献到线路库。顶栏不再堆两个分享图标。
- **底栏**只留两个社区动作（与上次对比、贡献到线路库）。
- **难度是本地估算**：`difficultyOf(km, ascentM)`，8km+300m 以下算简单、20km+1200m 以下算中等。
  六只脚那个「17km/1000m 标简单」是上传者手选的，我们没有人工难度字段，只能按经验分档。
- **坑**：`Modifier.height(dp)` 每帧改会让整棵子树重组（18 段分段明显掉帧），
  改用 `Modifier.layout` 只让测量阶段失效。
- **坑**：`assembleDebug` 连跑几次都返回 BUILD SUCCESSFUL，但 APK 时间戳不变、装上去还是旧代码。
  必须 `--rerun-tasks`（CLAUDE.md 第 10 条的老问题，这次是真踩到了）。

新增 `TrackDetailUi.maxAltitude`（最高海拔，`raw.maxOfOrNull { it.altitude }`）。

## [2026-10-22] KML 导入修 bug：标注点被当成轨迹点

用真实的「赣南齐云山穿越轨迹」两步路 KML（229 KB / 1658 个 gx:coord / 3 个分段）实测，
模拟器走完整条链路：我的 → 我的记录 → 导入 → SAF 选文件 → 详情页。

- **Bug**：导入出 **1676 个点**（多 18 个），且**前 18 个点时间全为 null**。
  根因：`<coordinates>` 只按标签名判断，把「标注点」文件夹里 `<Point><coordinates>` 的
  起点/终点标记也收进了轨迹点列表，还插在**最前面**，把后面所有点的 when/coord 下标配对
  整体错位 18 位。修复：`KmlParser` 增加 `lineDepth`，只有 `<LineString>` 内的 `<coordinates>`
  才当轨迹几何。修复后 1658 点、时间零缺失。
- **顺手发现**：两步路的 `gx:Track` 是「先全部 `gx:coord`，再全部 `<when>`」，
  不是教科书里的 when/coord 交替。原按下标配对两种布局都对，但**单测只覆盖了交替布局**，
  所以真实文件一测就炸。新增 `KmlParserRealLayoutTest` 3 例锁住真实布局。
- **单测**：App 全量 **104 例全绿**（+3）。
- **模拟器实测结果**（android-33 AVD `zouliangbu33`）：
  17.74 km / 16:54:10 / 1658 点 / ↑1078 ↓1445 m / 分段正常。
  DB 核对：session 3 的 1658 点与文件里的 gx:coord 数一致，时间戳首尾对得上。
- **不是 bug**：导入的 session 表里 `ascentMeters=0`，但详情页显示 ↑1078 ——
  这是 `TrackDetailViewModel` 的既定逻辑（存的值 >0 才用，否则从原始点重算），导入轨迹不落爬升。
- **新 APK**：0.1.0.000024。

## [2026-10-22] 轨迹导入新增 KML 格式支持

- 新增 `export/KmlParser.kt`：SAX + namespace aware + XXE 防护，复用 `GpxParser.ParsedTrack`
  与 `GpxImport.toPayload`（时间兜底 / 距离累计零改动）。
  - 支持两种写法：`gx:Track`（when[i] ↔ coord[i] 按下标配对，缺时间记 null）与
    `LineString/coordinates`（"lng,lat[,alt]"）。
  - KML 坐标是 **lng lat alt 顺序**（和 GPX 相反），WGS-84，解析边界转 GCJ-02。
  - 兼容无命名空间的 KML（localName 为空时靠 qName 剥 `gx:` 前缀）。
- `TrackListViewModel.runImport`：读文件头 2KB 嗅探 `<kml` 分流 KmlParser/GpxParser，
  **不信扩展名**（重命名过的文件也能导）。
- 文案改「导入 GPX / KML」（`TrackListScreen` 空态 + 顶栏）。
- 坑：SAX 测试样例里用 `gx:` 前缀**必须声明 `xmlns:gx="http://www.google.com/kml/ext/2.2"`**，
  否则 XML 格式错误（parser 没开外部 DTD 也一样）。
- 验证：单测 101/101 绿（新增 `KmlParserTest` 7 条）；真实两步路样例
  （赣南齐云山 1676 点）JVM 冒烟解析通过，名称/点数/海拔正确。

---

## [2026-10-22] 首页改本地轨迹时间轴，首页动态模块整体下线

**决策**：App 定位＝本地优先工具为主体 + 可选在线层，**唯一红线是不做广告**。
动态信息流必须依赖服务端、断网即空，与「户外默认没信号」前提冲突，且 `docs/product-philosophy.md`
本就写着「社交 0 投入」——代码与哲学打架，以哲学为准。

**App**（`compileDebugKotlin` 通过，单测 94/94 绿，删了 2 个 feed 序列化用例）：
- 首页 `HomeScreen` 重写：城市栏 + 本月汇总（`TrackSummaryCard`）+ 按月分组的轨迹卡片，数据全走
  `TrackRepository.observeCompleted()`，**零网络请求**；空态引导「去记录」。
- 轨迹展示组件从 `ui/tracks/TrackListScreen`（全是 private）提到 `ui/components/TrackCards.kt`：
  `TrackCard` / `TrackThumb` / `MonthHeader` / `TrackSummaryCard` / `MonthCell` 等，两处共用。
- 删除：`ui/feed/`（2 文件）、`HomeViewModel`（并入 HomeScreen）、`FeedRepository` / `MockFeedRepository` /
  `FeedItem` / `FeedDetail`、`AppRoute.FEED_DETAIL`、`ContentApiFactory` 的 3 个 feed 端点、`MockData.feed`。
- 顶部运营 Banner 一并删除（硬编码占位、无接口、无跳转）。

**服务端**（`mvn test` 69/69 绿）：
- 新增 `common/V8__drop_feed.sql`（`DROP TABLE feed_post`）。V1/V2/V4/V5 已在线执行，**不改**。
- 删 `FeedPost` / `FeedPostMapper` / `AdminFeedController` / `admin/feed.html`；
  `ContentDtos` 的 3 个 Feed record、`ContentQueryService.feed/feedDetail`、`ContentController` 两个端点、
  `AdminDashboardController` 的 4 个 feed 计数字段（含 `dashboard.html` 对应卡片）全部移除。

**注意**：
- `feed_post` 无外键、无其他表引用，删除是零依赖操作；审计表里的 `feed.*` 历史记录按「审计不删」保留。
- `OfflineDemoBanner` / `CoverPlaceholder` / `ContentSourceState` / `ContentApiFactory` 都不是孤儿
  （线路页与 Auth/Proposal 在用），保留。
- 首页 banner 删除后 `OfflineDemoBanner` 不再出现在首页，只剩线路页在用。

---

## [2026-10-21] 社区共维护提案机制 + 同路段对比

**产品口径**（`docs/product-philosophy.md`）：记录私有、线路公共、社交不做。
个人轨迹是数据不是内容，优先把「我的」做深；社区只维护线路，且不开放自由建线。

**服务端**（V7 迁移，sqlite+mysql）：
- 三张表 `content_proposal` / `content_vote` / `route_detail_revision`；`route_detail` 加 version/curator_name/updated_at。
- `ProposalService`：提案三型（IMPORT / UPLOAD / EDIT）；客观勘误达认票阈值自动合并；
  冲突按 base_version 判；每次合并写全量快照；回滚版本号继续 +1。
- 公开接口 `/api/v1/proposals`（免登录，设备 UUID 当身份）+ 管理接口 `/api/v1/admin/proposals`。
- 规则层校验 UPLOAD（点数/国境坐标/时间戳递增），不合格直接 400，不进人工队列。
- `route_submission` 历史数据在 V7 里平移成 IMPORT 提案，旧后台页面不受影响。

**App**：`util/SegmentMatch.kt` + 单测（7 例），同一条路线多次记录的共同路段匹配与对比。
`docs/route-comparison-design.md` 记录算法与待做项；UI 入口见同日下一条，逐公里对比仍未做。

**踩过的坑**（都写进 CLAUDE.md 了）：
1. `uk_revision(route_id, version)` 唯一索引 → 测试不清库会撞车，表现为 500 而不是断言失败。
2. 测试类共享同一个内存库：ProposalApiTest 写完 route 1 的详情不清，会让 ContentApiTest 的
   「最新收录」分组多一条而随机失败 → 加 `@AfterEach` 清理。
3. 轨迹端点正好落在折线顶点时，只记「命中段序号」会随机多算/少算一整段（100 m），
   必须记段的参数 t 再换算成顶点索引。
4. javac 对 `Map.of` 12 参数 + 嵌套转义字符串的报错位置离真实原因很远（报「需要';'」），
   测试里构造 JSON 一律用字符串拼接。

## [2026-10-21] 社区共维护 UI（App + 管理后台）

上一轮只做了服务端，这轮把入口补齐（不加新表、不加新接口，只消费已有接口）：

| 入口 | 文件 | 行为 |
|---|---|---|
| 线路详情「社区维护」 | `ui/routes/RouteMaintenanceSection.kt` | 版本/维护者 + 待处理勘误列表 + 认可/反对 + 提勘误对话框 |
| 轨迹详情「与上次对比」 | `ui/tracks/TrackCompareCard.kt` | 找同一条线的历史记录，只比共同路段（时长/爬升/均速） |
| 轨迹详情「贡献到线路库」 | `ui/tracks/ContributeTrackDialog.kt` | 名称/地区/说明/昵称 → UPLOAD 提案 |
| 管理后台「社区提案」 | `static/admin/proposals.html` | 按状态筛选、通过/驳回 |

数据层：`data/remote/ProposalApi.kt`、`data/repo/ProposalRepository.kt`、`data/DeviceId.kt`。
服务端 `GET /api/v1/routes/{id}` 追加 `version` / `curatorName` 两个字段（只增不改）。

坑：
1. TS 模板串里写 `\n` 会变成真实换行并打断 Kotlin 字符串（`ContributeTrackDialog` 撞了一次），
   要写 `\\n`。
2. `Modifier.matchParentSize()` 是 BoxScope 成员，不要 import。
3. 对比不能全量两两比：先按起终点 <300 m + 距离差 <40% 粗筛，再进几何匹配。

## [2026-10-21] 管理后台扩展：从「只有路线审核」到 7 模块骨架

- **新包 `com.zouliangbu.server.admin`**：`AdminSupport`（ok/badRequest/notFound/parseId/trimToNull）、
  `AuditService`、`AdminContext`（ThreadLocal 操作者）、`PageResult`（页码分页统一形状）。
  **没做泛型 CRUD 基类**：7 个模块的筛选参数与 DTO 各不相同，基类要么加一堆钩子要么被逐个覆盖，抽象反而更难读。
- **模块**：看板 / 首页动态（完整样板）/ 装备分类 / 线路分组 / 配置中心 / 操作审计 / 图片上传。
  前端 `layout.js` 左菜单外壳 + `app.css`，7 个页面全部落地，不是占位。
- **V4 迁移**（sqlite+mysql）：`feed_post` 加 `content`/`cover_author`；新增 `app_config`、`admin_audit`。
- **公开新接口** `GET /api/v1/config/app`（强更 / 公告）。硬规则：这个接口会被老版本 App 调用，
  **不能删字段、不能改语义**；未配全的开关一律返回安全默认值（强更开但无下载地址 → 当作关闭，
  否则用户点弹窗没反应）。
- **修 `ApiResponse.fail` 恒返回 HTTP 200**（10 处）→ 新增 `failResponse(code,msg)` + `httpStatusOf(code)`。
  新测试立刻暴露：`detailOfMissingSubmissionIs404` 报 `expected 404 but was 200`。
  同时 4 条旧测试曾断言"错误也返回 200"（固化了 bug），已改为 `isBadRequest()/isNotFound()`。
- **修 `PUT`/`DELETE /admin/routes/{id}` 空实现**：原来返回 ok 但不落库，点保存提示成功却毫无变化。
- **`GET /admin/submissions/{id}/detail`**：任意状态（PENDING/APPROVED/REJECTED）都能打开，
  根因是编辑页读的是公开接口 `/api/v1/routes/{id}`，它依赖 `route_detail`（审批通过才写入）。
- **直接新建线路** `POST /admin/routes` + `GET /admin/routes/{id}/detail`：
  运营不再被"必须先有用户提交才能建线"卡住。
- **密码** `zouliangbu-admin` → **`admin`**（yml 与 `@Value` 兜底两处同步）。
- **踩坑**：
  - `@MapperScan` 必须加 `admin.mapper`，否则启动即 `NoSuchBeanDefinitionException`（新包默认不扫）。
  - Spring Boot 4 测试注解包名变了：`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`；
    Jackson 也迁到 `tools.jackson`（`com.fasterxml` 不存在）。
  - MyBatis-Plus `last(String)` **不接受占位符参数**，分页要用 `Page` 对象，别手拼 LIMIT/OFFSET。
  - `LambdaQueryWrapper` 不能用 `BaseMapper<?>` + `wrapper<?>` 传参，泛型对不上，工具方法要写 `<T>`。
  - application.yml 里**重复的 `spring:` 键**会让 SnakeYAML 直接拒启（duplicate key）。
  - 写库的测试类必须加 `@Transactional`，否则污染共享内存库，把"种子数据恰好 N 条"的断言全部搞挂。
  - 本机全局代理会拦 `127.0.0.1`（curl 报 502 / `--noproxy *` 被 shell 展开），验证脚本要显式禁用代理。
- `mvn test` → **54/54 绿**（新增 `AdminModulesTest` 14 例）；端到端 38 项全通（含上传安全、静态映射）。

---

## [2026-10-21] 双平台路线导入 + 数据库 H2 → SQLite


- **六只脚接入**：`SixFoot`（`/trip/{id}/` 页面正则 + `/v4/api/trip/{id}/trackjson`）+ `SixFootClient`；
  `RoutePlatform.detect` 按域名路由（`2bulu.com` / `foooooot.com`），未知域名提交即 1001。
- 解析结果载体从 `TwoBulu.ParsedRoute` 抽成独立 `RouteData`，两平台共用落库逻辑；
  `route_detail.source_name` 按平台写。正文所有 `<img>` 剥掉（**不抓脚印图片**），六只脚无标注点。
- **数据库 H2 → SQLite**（dev/test）：`sqlite-jdbc:3.50.3.0`。Flyway 12.4 core 已内置 SQLite，
  `flyway-database-sqlite` 构件不存在。脚本按方言分目录 `db/migration/{sqlite,mysql}` + `common`，
  profile 显式指定 locations；SQLite 只需把自增主键写成 `INTEGER PRIMARY KEY AUTOINCREMENT`。
- dev 库 `zlb-dev.db`（启动目录，`SQLITE_PATH` 可改；连接池必须 1 + `busy_timeout`），**重启不丢数据**；
  test 用共享内存库（`application-test.yml` + `@ActiveProfiles("test")`）；prod 仍 MySQL。
- 验证：`mvn test` 36/36；六只脚 100142397 全链路（2795 点、11.6km/647m、正文无图），
  两步路 244439 解析正常，重启后数据仍在。顺手修 `extractRegion`（"深圳户外…"→"深圳户"→"深圳"）。


## [2026-10-21] App 接入云服务器（S5 半程）：BASE_URL 切换 + 明文 HTTP 放行

### 触发
用户已把 server 部署到云服务器 `http://82.156.84.127:8080`，要求验证并把 App 的服务端地址改过去。

### 部署验证（curl 实测，非推测）
| 接口 | 结果 |
|---|---|
| `/api/v1/healthz` | HTTP 200 `{"status":"ok"}`，195ms |
| `/api/v1/feed?size=2` | 200，`coverStyle` 字段**在位**（c624cc6 修的那个 S4 首屏必崩点已闭环） |
| `/api/v1/routes` | 200，recommendations + sections 结构正确 |
| `/api/v1/gear/categories` | 200，8 分类齐全 |

注意：云上库还是**种子数据**（武功山/四姑娘山那 12 条，与 Mock 文案一致），
App 首页从 Mock 切到 Remote 后**视觉上不会有任何变化**——这是预期行为，不是没生效。

### App 侧改动（3 个文件）
1. `ContentApiFactory.kt`：`BASE_URL` `10.0.2.2:8080`（模拟器回环）→ `82.156.84.127:8080`
2. **新增** `res/xml/network_security_config.xml` + Manifest 引用：
   targetSdk 28+ 默认禁明文 HTTP，不放行的话真机 Retrofit **静默连不上**、悄悄回退 Mock、
   无任何报错——这是最容易"以为没生效"的坑。release 精确放行该 IP（NSC 不支持 CIDR）。
3. **新增** `src/debug/res/xml/network_security_config.xml`（debug 全放开，方便以后本地调试任意 IP；
   release 不进此 sourceSet，仍按白名单）。

### 校验
- `testDebugUnitTest` 64/64 绿（NSC 不影响 JVM 测试）；`assembleDebug` → **0.1.0.000013 / 54.8MB**
- aapt2 确认 APK：Manifest 带 `networkSecurityConfig`、NSC 文件已打进 res/xml/、
  新 IP 存在于编译产物 `ContentApiFactory.class`

### 遗留（S5 剩余）
- HTTPS：现在靠 NSC 放行 IP 只能 HTTP；长期应上域名 + Let's Encrypt（国内服务器需备案）
- MySQL 备份 / 日志滚动 / 拨测监控：仍无
- 真机验证"首页/线路页显示的是云数据"仍未做（本机只出包）

---


## [2026-10-20] 路线内容平台：两步路链接导入 + 审批 + 管理后台 + 线路详情页

### 功能
- **用户提交路线链接**（App 线路页"导入路线"按钮）→ 粘贴两步路 URL → 提交
- **管理后台**（`/admin/`）：登录（固定密码）→ 审批队列（待审批/已上线/已驳回）→ 通过触发抓取
- **两步路抓取**：`/api/proSpecial/routeDetails`（明文）+ `/api/destintion/getRoutePoints`（XOR 0x4B）
- **坐标转换**：WGS-84 → GCJ-02（服务端 Java 版 CoordTransform，与 App 侧算法一致）
- **线路详情页**（App）：地图轨迹 + 标注点 + 统计卡 + 正文渲染 + 来源标注
- **管理员鉴权**：固定密码（`application.yml` 里 `admin.password`）→ 签发 token（2h 过期）→ 拦截 `/api/v1/admin/**`

### 新增文件
- `zouliangbu-server/src/main/java/com/zouliangbu/server/route/`：CoordTransform / TwoBulu / TwoBuluClient / RouteImportService / RouteContentController / AdminController / AdminAuthInterceptor / AdminWebConfig + 4 个 Entity + 4 个 Mapper
- `zouliangbu-server/src/main/resources/db/migration/V3__route_content.sql`（4 张新表）
- `zouliangbu-server/src/main/resources/static/admin/`：login.html / index.html / edit.html
- `app/src/main/java/com/zouliangbu/footprint/ui/routes/RouteDetailScreen.kt`
- `app/src/main/java/com/zouliangbu/footprint/data/remote/ContentApiFactory.kt`：新增 SubmitRouteRequest / SubmissionStatus / RouteDetail / TrackPointDto / MarkerDto

### 测试
- 服务端：29 用例全绿（原 15 + TwoBuluTest 6 + RoutePlatformTest 8）
- App：64 用例全绿（不变）

### 已知限制
- 路线详情接口暂未 join route 表取 name/region（返回 null，App 已容错）
- 管理后台编辑页保存接口暂未实现（PUT /admin/routes/{id} 返回空）
- 只支持两步路链接，不支持其他平台
- 开源使用，图片不复制（后台可填 URL）

---

## [2026-10-20] P6 S4 收尾：Remote 接线 + 接口契约对齐 + GearScreen 异步化

### 本次补丁（静态验证继续通过）

1. **FootprintApp 三个 lazy 单例从 Mock 切换到 Remote**（P6 S4 关键接线）：
   `RemoteFeedRepository(MockFeedRepository())` / `RemoteRouteRepository(...)` / `RemoteGearRepository(...)`，
   网络失败自动回退 Mock，UI 零改动。
2. **接口契约对齐**：服务端 `/feed` 返回 `FeedPage{items,nextCursor}`、`/routes` 返回 `RouteCatalog{recommendations,sections}`，
   但 App 之前声明成 `List<FeedItem>` / `List<RouteItem>`，反序列化会直接失败。已修正为对齐服务端真实响应体，
   并新增 `FeedPage` DTO。
3. **GearScreen 异步化**：`gearRepository.categories()` 之前是主线程同步调用，切到 Remote 后内部 runBlocking
   走网络会 ANR。改成 `LaunchedEffect + withContext(Dispatchers.IO)` 异步加载。

### 验证

| 项 | 结果 |
|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | 64 用例全绿 |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL，app-debug.apk 54.8 MB |
| 服务端 `mvn test` | 15/15 全绿 |

---

## [2026-10-20] P2.5-P6 全量开发完成 + 单测扩充（静态验证通过）

### 触发
用户：后面的开发计划先开始写，全部写完，写完只做静态逻辑验证，晚点再实测。

### 本次完成

#### P2（剩余三项）
- P2.5 轨迹抽稀：util/Polyline.kt（Douglas-Peucker 显式栈迭代，避免 2 万点 StackOverflow），接进 TrackDetailViewModel。
- P2.6 写点批量化：RecordingTrackService 加 pendingPoints 缓冲 + flushJob 定时批量 insertAll（FLUSH_INTERVAL_MS = 5s / FLUSH_MAX_BATCH = 20）。
- P2.8 PARTIAL_WAKE_LOCK：RecordingTrackService 加 acquireWakeLock / releaseWakeLock，记录中持锁防 Doze；stopTracking / onDestroy 释放。

#### P3（五项）
- P3.1 / P3.3 爬升/下降 + 海拔中值滤波：util/Elevation.kt（阈值 3 m，参考高度累加），TrackSession 加 ascentMeters / descentMeters，AppDatabase v3 到 v4（MIGRATION_3_4 + 4.json 已生成并提交）。
- P3.2 配速/速度 30s 滑窗：util/Pace.kt，详情页新增平均配速。
- P3.5 分段统计（每公里）：util/TrackSplits.kt，详情页新增分段列表。
- 详情页 UI：新增第二排累计爬升 / 平均配速 + 分段列表（最多展示 20 段）。

#### P4 离线地图
- 数据层：offline/OfflineRegion.kt、OfflineRegionCatalog.kt（清单 JSON 解析，JVM 可测）、OfflinePackageManager.kt（下载 + SHA-256 校验 + 存储 + 总量上限 2 GB）。
- 接入层：offline/OfflineTileSource.kt（MapTileProviderArray = MapTileFileArchiveProvider + MapTileDownloader）。
- UI：offline/OfflineMapScreen.kt + OfflineMapViewModel.kt，我的页入口。
- 内置清单：app/src/main/assets/offline/regions.json（兜底，真实清单走服务端）。
- 文档：docs/offline-map-baseline-decision.md（P4.4 底座评估，结论高德 + osmdroid + MBTiles）。

#### P5 户外安全
- P5.1 超时未动提醒 / P5.2 低电量提醒：notify/TrackAlerts.kt（两 channel、每会话最多一次），心跳每秒 checkAlerts。
- P5.3 位置分享 / P5.4 行程卡分享图：share/Share.kt + share/ShareCardRenderer.kt（640x960 PNG，纯 Canvas，含起终点 + 轨迹缩略图 + 三格数据）。

#### P6 S4 App 侧 Retrofit
- 新增依赖：retrofit 2.11.0 + okhttp 4.12.0 + kotlinx-serialization-json 1.6.3 + converter-kotlinx-serialization。
- data/remote/ContentApiFactory.kt：ContentService / ApiResponse / RemoteFeedRepository / RemoteRouteRepository / RemoteGearRepository，网络失败自动回退 Mock（UI 零改动）。

#### 单元测试
- 新增 4 个测试类：PolylineTest / ElevationTest / PaceTest / TrackSplitsTest，共 22 用例。
- 修了一个真 bug：Polyline.simplify 的 keep 数组长度写死 3，超过 3 个点就 ArrayIndexOutOfBoundsException。
- 全量单测 64 用例全绿（原 45 + 新 22）。

### 静态验证结论

| 项 | 结果 |
|---|---|
| ./gradlew :app:compileDebugKotlin | BUILD SUCCESSFUL |
| ./gradlew :app:testDebugUnitTest | 64 用例全绿 |
| Room schema | 4.json 已生成并入库 |
| 单测覆盖的新模块 | 抽稀 / 爬升 / 配速 / 分段 |
| 未做 | 真机/模拟器实测（用户明确说晚点我再实测，按 CLAUDE.md 验证纪律不在本轮测） |

### 已知未做 / 待用户实测

- P6 S5 部署（Docker Compose + nginx + MySQL）——需要运维环境。
- P3.4 DEM 离线高程（可选增强）——已延后。
- 离线包实际 MBTiles 资源——manifest 里 url 是占位，需要运维提供真实瓦片包。
- WorkManager 断点续传——目前用 viewModelScope，用户离开页面会中断下载；ROADMAP P4.1 验收要求的可断点续传还需 WorkManager 改造。
- 离线包只对 osmdroid 分支生效（高德 MapView 不支持 MBTiles），UI 上已明示。

### 下一步（等用户实测）

1. 用户真机测试记录到停止到列表到详情主链路。
2. 实测通过后一次性 commit + push 到 YuanZhou314/zouliangbu。
3. 运维提供 MBTiles 真实资源后接入真实下载。

## [2026-10-20] 阶段性暂停：进度文档同步 + 仓库全量入库（buildNumber=12）

### 触发
用户决定暂停功能开发，要求把进度文档更新到位，然后整个仓库全量提交 + 推送到 GitHub。

### 本轮只改文档，不动代码
| 文档 | 变更 |
|---|---|
| `README.md` | **整篇重写**。旧版停在"单页 + 只做记录闭环"时代，还写着「列表之外的功能一律不做」，
与现在五栏结构 + 列表/详情/GPX 全部落地的现实矛盾。新版含：进度表（P0–P6）、功能清单、
项目结构（按当前 61 个 kt 实况）、关键约定（坐标系/距离/时长/改表/地图类型）、测试清单、文档索引 |
| `ROADMAP.md` | §0 一句话判断改为「记录、看、不丢都站起来了，算还没开始」；§2 基线从 `buildNumber=3`
更新为 `buildNumber=12`（61 kt / 5479 行 / 45 用例 / 53.8 MB）；§9 的 README 条目标为已修 |
| `CHANGELOG.md` | 本条（会话收尾总结） |

### 提交范围说明
- 全量 `git add -A`：App 源码、`zouliangbu-server`、`app/schemas/*.json`、全部 `.md`、
  `zlb-debug.keystore`、`.gitattributes`。
- **仍按 `.gitignore` 排除**：`version.properties`（每 build +1，属构建产物）、`*.apk`、`build/`、
  `local.properties`（本机 SDK 路径）、`.workbuddy/`（本地工作记忆）。
- 一并带入**并行会话**的服务端静态检查改动：`ContentDtos.FeedItem` 补 `coverStyle` 字段
  （App 侧该字段无默认值，缺了会让 kotlinx.serialization 抛 `MissingFieldException`，S4 接网络首屏必崩）
  + 3 个字段集契约测试，`mvn test` 15/15。

### 暂停时的现状快照
- 代码：61 个 kt（main）/ 5479 行；6 个测试文件 / **45 用例全绿**；Room v3 + schemas 1/2/3 入库
- 产物：`app-debug.apk` 0.1.0.000012 / versionCode 10012 / 53.8 MB
- P1 八项清零；P2 完成 5/8（2.1 / 2.2 / 2.3 / 2.4 / 2.7）

### 遗留待验证（下次开工先看这里）
1. **P2.5 抽稀**：详情页长轨迹（> 2 万点）全量喂地图，性能未测。
2. **P2.6 批量写**：`TrackPointDao` 仍逐点 insert，1 小时耗电未实测。
3. **P2.8 持锁**：`WAKE_LOCK` 已声明但未使用，息屏后点间隔可能拉长。
4. **真机从未跑过**：本机只出包。画线、暂停、GPX 坐标精度、v1→v2→v3 迁移全部待装机验证；
   迁移若失败，logcat 关键字 `Room cannot verify the data integrity`。
5. `LOCAL_ENV.md` 仍是 macOS 路径，Windows 段未补。

---

## [2026-10-20] P2 开工：悬挂会话恢复 + Service/UI 一致 + 暂停继续（buildNumber=12）

### 触发
P1 八项清零后按 ROADMAP 顺序推进 **2.2 → 2.3 → 2.4**，顺手把 2.7（计时基准）一并修掉。

### 产出

| 文件 | 变更 |
|---|---|
| `util/TrackMetrics.kt` | 新增。距离规则（精度 30m / 单段 500m）+ `TrackClock.activeElapsedMs`（扣暂停）。纯函数，JVM 可测 |
| `data/SessionRecovery.kt` | 新增。启动时扫 `endTime IS NULL`：0 点删除、有点收尾保存；结果推给 UI 提示 |
| `data/db/TrackSession.kt` | 新增 `pausedMs`；派生属性 `activeDurationMs`（= end-start-paused） |
| `data/db/AppDatabase.kt` | version=3 + `MIGRATION_2_3`（ALTER ADD COLUMN pausedMs INTEGER NOT NULL DEFAULT 0） |
| `app/schemas/.../3.json` | 新 schema（相对 2.json 只多 pausedMs 一列） |
| `data/db/TrackSessionDao.kt` | `unfinished()`；`finalize()` 增加 pausedMs；`observeCompleted` 投影带 pausedMs |
| `data/db/TrackSummary.kt` | 加 `pausedMs` + `activeDurationMs` |
| `data/RecordingTrackState.kt` | Snapshot 加 `paused` / `updatedAtElapsed`；新增 `clearIfStale()` |
| `service/RecordingTrackService.kt` | 重写：ACTION_PAUSE/RESUME、每秒心跳、通知动作按钮、`START_NOT_STICKY`、null intent 自杀、间隔过滤改单调时钟 |
| `FootprintApp.kt` | 启动时在 IO 线程跑一次 `SessionRecovery.run()`（appScope） |
| `ui/record/RecordViewModel.kt` | `orphaned` 提示流、看门狗（心跳超时清 UI）、`dismissRecovery()` |
| `ui/record/RecordScreen.kt` | 暂停/继续按钮、「已暂停」角标、恢复提示对话框、摘要时长扣暂停 |
| `ui/tracks/Track{List,Detail}Screen.kt` | 时长/均速统一用 `activeDurationMs`；详情页显示「含暂停 xx」 |
| `app/src/test/.../TrackMetricsTest.kt` | 新增 7 用例 |
| `app/src/test/.../TrackClockTest.kt` | 新增 7 用例 |

### 关键决策

1. **悬挂会话不做「继续记录」，改为自动收尾保存**（**对 ROADMAP 2.2 的有意偏离**）。
   ROADMAP 原写「有点就问『继续/结束』」。实际做不下去：进程死后 GPS 采集已断，
   继续写会在轨迹里留一个跨几公里的空洞（时间断层 + 直线跳变），比丢一次记录更难解释。
   现在的做法是：用**最后一个点的时间**当结束时间、按统一规则重算距离，收尾成一条正常历史记录，
   并在记录页提示一次「上次记录异常中断，已保存」。用户随时能看到、导出、删除。
2. **看门狗只清 UI 状态，绝不落库**。光凭「心跳停了」分不清「Service 真死了」和
   「被国产 ROM 冻了一分钟」。在活着的记录脚下 finalize，会把一条轨迹拆成两段。
   落库收尾只在 Application 启动时做——那时能确定本进程没有任何 Service 在跑。
3. **暂停不新建 session**（ROADMAP 明确要求的）。同一条会话继续写；恢复后把 `lastPoint`
   置空，于是**暂停期间走的路不计距离**（地图上的直线连接是坐标系层面的必然结果）。
4. **`START_STICKY` → `START_NOT_STICKY`**。重启时拿不到 sessionId，起来只能空跑，
   还会在 Android 14 上因「5 秒内没 startForeground」被判 ANR。恢复逻辑上移到 Application。
5. **加每秒心跳**。原来时长只在新点落库时更新——站在山顶不动看风景，计时器会停住，
   用户以为记录卡死。心跳顺带成了「Service 是否还活着」的唯一信号。
6. **`SessionRecovery` 跳过本进程刚建的会话**（`startTime >= processStartWallMs`）：
   恢复是异步跑的，手速快的用户可能在它查库前就点了开始记录，那条新会话不能被当残留收尾。

### 验证

- `testDebugUnitTest` **45 用例全绿**（原 31 + TrackMetrics 7 + TrackClock 7）
- 迁移用 SQLite 实测：按 2.json 建表 → 插入老数据 → 跑 `MIGRATION_2_3` 的 ALTER →
  与 3.json 逐列比对（列齐、affinity/notNull 一致），老行 `pausedMs` 自动为 0
- `compileDebugKotlin` 零 warning；`assembleDebug` → **0.1.0.000012 / 53.8 MB**

### 未验证 / 已知限制

- **迁移和暂停都还没上真机**。装机会从 v1/v2 库升到 v3，若启动崩看 logcat 的 `Room cannot verify`
- 通知上的「暂停/继续/停止」动作按钮，部分 ROM 的锁屏通知可能不展示（系统限制），正文仍可点进 App
- 2.5 抽稀 / 2.6 写点批量 / 2.8 WakeLock 仍未做

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

## [2026-10-20] 服务端静态检查：修掉 FeedItem 漏字段（S4 必崩的 P0）

### 触发
用户要求停服务、只做静态检查。编译零 warning，但逐字段比对"服务端 DTO ↔ App model"时发现缺陷。

### 缺陷
服务端 `ContentDtos.FeedItem` **没有返回 `coverStyle`**，而 App 侧：

```kotlin
data class FeedItem(
    val id: String, val title: String, val author: String, val likeCount: Int,
    val city: String, val coverStyle: Int,          // ← 没有默认值
    val coverAspectRatio: Float = 1f, val coverUrl: String? = null
)
```

`coverStyle` 无默认值 → kotlinx.serialization 遇到缺字段直接抛 `MissingFieldException`，
**不是降级成 0**。S4 一接网络，首页第一屏就崩。
（`RouteItem` 服务端是返回 `coverStyle` 的，所以这条是 FeedItem 单独漏了，不是设计如此。）

### 修复
- `ContentDtos.FeedItem` 加 `int coverStyle`，位置与 App 模型一致（city 之后）
- `ContentQueryService.toFeedItem` 用 `id % 5` 计算，与 `toRouteItem` 同一套规则
- 新增 3 个**字段集契约测试**：`$.data.items[0].length() == 8` + 逐字段 `.exists()`；
  `coverUrl` 恒为 null，用 `hasJsonPath()` 断言"字段在"而非"值非空"；
  同时锁住 `RouteSection.subtitle` 必须是 string（不能 null）

`mvn test` → **15/15 绿**（原 12 + 3）

### 其余静态发现（只记录，未改）
1. `city` 精确匹配：App 定位返回"萍乡市"、库里是"萍乡"→ 0 条。S4 前必须统一口径。
2. 分组顺序/条目数与 Mock 不同（服务端 `sort_weight ASC` = 进阶→新手→口碑；Mock 是口碑→新手→进阶，
   且 Mock 有跨分组重复）→ 接后端后首页卡片会变少、顺序会变，属预期需提前告知。
3. `application.yml` 硬编码 `spring.profiles.active: dev` → 生产忘传 profile 会**静默跑 H2 内存库**。
   建议改 `spring.profiles.default: dev`，并在 prod 下检测到 H2 就 fail fast。
4. 校验消息中英混杂（`@Max` 的 msg 走 Hibernate Validator 本地化包，Linux `LANG=C` 会变英文）。
5. `qw.last("LIMIT " + n)` 字符串拼 SQL（当前安全，但属危险写法）；
   非法 cursor 静默从头返回 200（S4 循环拉页时可能重复拉第一页）。

---



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