# 服务端未来开发计划（SERVER-ROADMAP v1）

- 制定：2026-10-20
- 对应：`ROADMAP.md` P6 / `BACKEND-PLAN.md` §12（已落地的 v1）
- 定位：这份文档只讲**服务端往后做什么、什么时候做、和 APK 怎么对接**。
  选型论证看 `BACKEND-PLAN.md`，App 侧功能规划看 `ROADMAP.md`（**以它为准**，本文不重复 App 计划）

---

## 0. 一页结论

| 阶段 | 做什么 | 现在做吗 | 触发条件 |
|---|---|---|---|
| **S4** | App 接 Retrofit，把 mock 换成真数据 | ⏸ 排在 App P1 之后 | App P1 闭环完成 |
| **S5** | 上线：HTTPS + 备份 + 日志 + 守护 | ⏸ 有服务器再做 | 你要拿手机在外网访问 |
| **S6** | 内容补全：详情接口、分页、SQL 搜索、图片对象存储 | ⏸ 有条件做 | 内容条目 > 200 条，或要做详情页 |
| **S7** | 身份与互动：账号、收藏、点赞、评论、审核 | ⏸ 先不做 | 你决定开放 UGC |
| **S8** | 规模化：缓存、限流、可观测性、灰度 | ⏸ 先不做 | QPS > 200 或开始有真实用户投诉 |
| — | 轨迹上云 | ❌ **永远不做**（见 §2） | — |

> **判断标准只有一条**：这一项现在不做，会不会挡住用户完成"记录 → 回看 → 导出"？
> 不会，就往后放。**服务端是配角，App 的轨迹闭环才是主角。**

---

## 1. 现状盘点（2026-10-20 实测，非推测）

### 已具备

| 项 | 现状 |
|---|---|
| 接口 | 4 个只读 GET：`/api/v1/feed`（cursor 分页）、`/api/v1/routes`（推荐位 + 分组 + 关键词）、`/api/v1/gear/categories`、`/api/v1/healthz` |
| 数据 | 8 张表；Flyway 按方言分目录（`{sqlite,mysql}` + `common`），dev/test 用 **SQLite**，prod MySQL 8 |
| 导入 | 两步路 / 六只脚链接均可提交 → 审批 → 抓取入库；六只脚不抓脚印图片 |
| 契约 | 统一 `{code,msg,data}`，错误码 1001/1004/5000，字段与 App 模型对齐 |
| 质量 | 36 个测试 |
| 部署 | 单 jar / Dockerfile / compose / systemd / 一键脚本 |
| 跨平台 | 原生库只有 `sqlite-jdbc` 多平台预编译（含 aarch64），Win/Mac(ARM)/Linux 通吃 |

### 明显缺口

| 缺口 | 影响 | 对应阶段 |
|---|---|---|
| App 侧无任何网络库（无 Retrofit/OkHttp/serialization） | 数据仍是本地假数据 | S4 |
| 无详情接口（`/feed/{id}`、`/routes/{id}`） | 点不进详情页 | S6 |
| 关键词过滤在内存里做（全表捞出再 filter） | 数据量上千后变慢 | S6 |
| 无 `coverUrl` 真实图片、无对象存储 | 封面只能渐变占位 | S6 |
| 无压缩、无缓存头、无限流、无 actuator/metrics | 弱网首屏差、上量后无法诊断 | S4（压缩）/ S8（其余） |
| 无账号、无鉴权 | 无法收藏/点赞/评论 | S7 |
| 无 CI、无自动备份 | 靠手工，易漏 | S5 |

### 刻意不做

微服务拆分、消息队列、Kafka、Elasticsearch、GraphQL、Redis。
理由：单表 < 千行、QPS < 10 的场景用这些是纯负债。

---

## 2. 关键判断：服务端的边界在哪

行业里做户外 App 的（Strava、Komoot、两步路）服务端都很重，因为它们把**轨迹存云端**——所以要做瓦片、路网匹配、分段排行、热力图。

**我们的产品决策相反：轨迹永不上云。** 这一条决定了服务端的边界：

```
服务端负责：内容（动态/线路/装备文案）+ 图片 + （将来）身份与互动
服务端不碰：GPS 点、轨迹、爬升、心率、任何可定位到人的数据
```

这不是"现在不做以后做"，是**产品差异化**：隐私是户外用户真正在意的东西，也是我们不用踩《个人信息保护法》里"敏感个人信息"那条线的原因。写进 README 当卖点，比抄两步路的功能列表更有价值。

**推论**：将来要做"分享我的轨迹"，正确做法是 **App 端导出 GPX / 生成海报图片，用户自己发微信**——而不是上传到我们服务器。既满足分享需求，又不破坏不上云的承诺。

---

## 3. 与 App ROADMAP 的关系（最重要的一节）

App 侧正在开发，`ROADMAP.md` 的 P1–P7 是主线。把两边的依赖关系摊开看：

| App 阶段 | 内容 | 需要服务端吗 | 服务端要做什么 |
|---|---|---|---|
| **P1** 焊死闭环 | 轨迹线 / 历史列表 / 详情 / GPX 导出导入 | ❌ 完全不需要 | **零** |
| **P2** 数据可靠 | Room 迁移 / 悬挂会话恢复 / 暂停继续 / 抽稀 | ❌ 不需要 | **零** |
| **P3** 算得准 | 爬升 / 配速 / DEM 高程 | ❌ 不需要（DEM 走本地瓦片） | **零** |
| **P4** 离线地图 | MBTiles / 离线包管理 | 🟡 可选 | 离线包清单分发（也可直接用静态文件，不必写代码） |
| **P5** 户外安全 | 超时提醒 / 低电量 / 位置分享 | ❌ 不需要（分享走系统分享） | **零** |
| **P6** 内容层接后台 | 首页动态 / 线路 / 装备的真实数据 | ✅ **需要** | **全部** |

**这张表说明一件事：服务端不在 App 的关键路径上。**

P1 到 P5 全部是纯本地能力，一行服务端代码都不需要。所以：

> **不要让服务端工作和 P1 抢时间。** 现在把 S4 做掉，收益只是"内容从假变真"，
> 但用户按停止后依然看不到自己的轨迹线——产品还是不成立。

**建议节奏**：

```
现在  →  P1 收尾（1.3 列表 → 1.4 详情 → 1.7 UI / 1.8 跳转）
        ↓
P1 完成  →  S4（App 接 Retrofit，1–2 天）+ P2（可靠性）
        ↓
P2 完成  →  P3 / P5 按兴趣挑，S5（上线）看要不要外网访问
        ↓
P4 之前  →  S6（详情接口 + 真实图片），因为详情页/离线包都需要内容规模化
```

---

## 4. S4 · App 接入（App P1 完成后立刻做）

### 4.1 服务端要为 S4 补的东西（很少）

| 项 | 内容 | 必要性 |
|---|---|---|
| 开启 gzip | `server.compression.enabled=true`，压 JSON | **必需**（弱网首屏差很多，代码 2 行） |
| `GET /api/v1/app/config` | 返回 `minVersion`（最低支持版本）+ 公告文案 | 建议（强制升级的唯一手段，无此接口就无法淘汰旧版本） |
| 字段冻结 | S4 期间不改任何 `/v1` 字段名 | **硬约束**（见 §9） |
| CORS | 只给调试期浏览器用 | 可选（APK 不需要 CORS） |

### 4.2 App 侧改动清单（只加文件，不改 UI）

```kotlin
// app/build.gradle.kts 新增；版本需与 Kotlin 1.9.24 兼容
plugins { kotlin("plugin.serialization") version "1.9.24" }
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
```

```
data/remote/ApiService.kt              Retrofit 接口（4 个 @GET）
data/remote/ApiClient.kt               OkHttp + JSON + 超时（连 10s / 读 20s）+ 日志拦截器（仅 debug）
data/remote/dto/*Dto.kt                与 ContentDtos.java 一一对应（@Serializable）
data/remote/RemoteFeedRepository.kt
data/remote/RemoteRouteRepository.kt
data/remote/RemoteGearRepository.kt
data/remote/ErrorMapper.kt             网络异常 → 领域错误（超时/离线/服务端错误分开提示）
src/debug/res/xml/network_security_config.xml
FootprintApp.kt                        只改 3 行：Mock → Remote
```

**Mock 实现必须保留**，作为离线兜底（见 §4.5）。

### 4.3 明文 HTTP 必须处理（否则真机一定连不上）

`targetSdk=33` 默认禁止明文 HTTP。**只放行 debug，绝不把 `usesCleartextTraffic=true` 带进 release**：

```xml
<!-- app/src/debug/res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>     <!-- 模拟器回环到宿主机 -->
        <domain includeSubdomains="false">192.168.1.100</domain> <!-- 换成你电脑/Mac 的实际局域网 IP -->
    </domain-config>
</network-security-config>
```

放 `src/debug/res/xml/`，release 不合并这份 overlay，天然只影响调试包。

> 注意 `network_security_config.xml` **不能**用 `<domain>192.168.0.0</domain>` 表示整个网段——
> 它只做精确域名/后缀匹配，不支持 CIDR。要么写具体 IP，要么 debug 下用 `<base-config cleartextTrafficPermitted="true">`。

### 4.4 三处"UI 零改动"承诺会被打破的地方（诚实预警）

现在的 Repository 接口是按 mock 设计的，接真数据时有三处对不上（实测签名）：

| 现状签名（实测） | 问题 | S4 过渡方案 | 正式方案（S6） |
|---|---|---|---|
| `observeFeed(): Flow<List<FeedItem>>` | 无分页参数，而服务端是 cursor 分页 | Remote 实现内部循环拉取，最多 5 页（≤200 条）后一次性下发 List | 换 Paging 3：`Flow<PagingData<FeedItem>>`，ViewModel 改一行 |
| `observeCatalog(): Flow<RouteCatalog>` | 无 keyword 参数，搜索在 `RoutesViewModel` 本地过滤 | Remote 拉全量（12 条，一次搞定）后本地过滤，行为与现在一致 | 签名加 `keyword: String`，搜索下沉到服务端 |
| `categories(): List<GearCategory>` | **同步返回**，Remote 实现不能阻塞主线程 | Application 启动时预热 + 内存/SP 缓存，失败用内置兜底；首次返回空列表让 UI 走 loading | 改 `suspend`，或在启动页预加载 |

**这不是设计失误，是有意为之的过渡**：先用最小改动让真数据上桌，架构升级留到数据量真需要时。
但要写清技术债的**上限**（200 条 / 12 条），到线就还。

> 第三条最容易被忽略：`categories()` 是同步函数，在 Remote 实现里 `runBlocking` 会卡主线程（ANR 风险）。
> 正确做法是**预热**——App 启动时后台拉一次写缓存，UI 读缓存。

### 4.5 离线降级策略（户外 App 必须做）

```
请求成功 → 写 Room 缓存（新表 content_cache：键 = 接口+参数，值 = JSON+时间戳）→ 发数据
请求失败 → 有缓存且未过期（24h）→ 发缓存 + 顶部提示"离线数据"
         → 无缓存 → 落回 Mock 数据（保留 Mock 实现的意义就在这）
```

理由很实际：徒步的人常在山里没信号，打开 App 白屏是最差体验。
**缓存放 Room 而不是 SharedPreferences**，因为要按 key 查、要按过期清理。

### 4.6 S4 验收标准

- [ ] 飞行模式下打开首页，仍能看到上次的内容（缓存生效）
- [ ] 关掉服务后打开 App，不崩溃、有明确提示、显示 Mock 数据
- [ ] 首页动态 / 线路 / 装备三个 tab 都来自服务端（改 `V2__seed.sql` 一条标题 → 重启服务 → App 可见变化）
- [ ] 弱网（模拟器限速 50KB/s）首屏 < 3s
- [ ] release 包抓包确认无明文 HTTP 请求

---

## 5. S5 · 上线运维（有服务器就做）

> **2026-10-21 进展**：用户已把 server 部署到云服务器 `http://82.156.84.127:8080`，
> 四接口（healthz / feed / routes / gear/categories）curl 全通，`coverStyle` 字段在位；
> App 侧 `BASE_URL` 已切到该地址，明文 HTTP 已用 network_security_config 放行（debug 全放开，release 精确 IP）。
> **S5 状态从 ⏸ 变为 🟡**：HTTPS / 备份 / 日志 / 监控四项仍未做。上表"IP 只能 HTTP + App 禁明文"的死锁，
> 实际解法是 NSC 按域名放行（见 `app/src/main/res/xml/network_security_config.xml` 注释），
> 但长期仍应上 HTTPS（域名 + 备案或 hk 域名）。

你说过"先跑通本地"，所以这一节是**将来真要外网访问时**的清单。

| 项 | 做法（业内常规） | 备注 |
|---|---|---|
| HTTPS | Nginx 反代 + Let's Encrypt（certbot 自动续） | 国内服务器域名需备案；用 IP 只能 HTTP（而 App 又禁明文，死锁） |
| 备份 | `mysqldump --single-transaction` 每日 cron + 保留 7 份 + 异地（对象存储） | **只靠服务器磁盘等于没备份** |
| 日志 | logback 输出 + 按天滚动 + 保留 14 天 | 排查问题靠这个 |
| 监控 | `spring-boot-starter-actuator` 只暴露 `/actuator/health` + 外部拨测 | 也顺手监 CPU/内存/磁盘 |
| 守护 | systemd `Restart=on-failure`（unit 文件已给） | 或 compose `restart: unless-stopped` |
| 部署 | `scp jar + systemctl restart` 足够 | 一个人维护别上 K8s |
| 安全 | 只开 443 和 SSH；MySQL 不对外；`.env` 权限 600 | 最基本的三条 |

**成本**：2 核 2G 轻量 ¥60–100/月 + 域名 ¥30/年。当前负载（QPS < 10、< 1 万行）一台机器绰绰有余。

---

## 6. S6 · 内容补全（数据量到了再做）

| 项 | 触发条件 | 做法 |
|---|---|---|
| 详情接口 | 要做"点进去看"的页面 | `GET /api/v1/feed/{id}`、`GET /api/v1/routes/{id}`，返回单条 + 关联 |
| 搜索下沉到 SQL | 线路 > 200 条 | `WHERE (name LIKE ? OR region LIKE ?)`，给 `name`/`region` 建索引；再大才考虑全文索引/ES |
| 图片对象存储 | 要真实封面图 | 阿里云 OSS / 腾讯云 COS + CDN；接口只存 URL，**不要从 API 服务器吐图**；列表图带缩放参数（`?w=400`） |
| 缓存头 | 内容接口变多 | 静态类（装备分类）`Cache-Control: max-age=86400`；列表类 `ETag` + `If-None-Match` → 304 |
| 运营后台 | 内容要频繁改 | 先给最小 HTML 表单 + Basic Auth，别一上来搭 Vue 后台 |

---

## 7. S7 · 身份与互动（你决定开放 UGC 时）

**顺序不能颠倒**：先账号 → 再收藏 → 再评论。跳过账号直接做互动，等于给自己埋刷接口的坑。

| 模块 | 业内常规做法 | 我们要额外注意 |
|---|---|---|
| 注册/登录 | 手机号 + 短信验证码（国内主流）或微信 OAuth | 短信约 ¥0.04/条，必须加图形验证码防刷 |
| 凭证 | JWT access（2h）+ refresh（30d），或服务端 session | 单机用 session 更简单；要扩容再上 JWT |
| 收藏/点赞 | `POST /favorites`（幂等：唯一索引 `(user_id, target_id)`）+ 计数异步更新 | 计数别用 `count(*)`，用冗余字段 + 定时校正 |
| 评论 | 分页 + 软删除 + 举报入口 | **必须接内容审核**（文本/图片都要过云厂商审核 API） |
| 合规 | 手机号实名、用户协议/隐私政策、举报处理、日志留存 ≥ 6 个月 | 国内运营硬要求，别等被下架才补 |

**只在 App 内本地做的**（不上云）：收藏的"离线书签"、阅读历史。放 Room，隐私友好且零成本。

---

## 8. S8 · 规模化（有真实用户后）

| 项 | 阈值 | 做法 |
|---|---|---|
| 限流 | 有刷接口迹象 | Spring 侧按 IP 令牌桶；Nginx `limit_req` 也够 |
| 缓存 | DB CPU > 50% | 单机首选 Caffeine（进程内），不要先上 Redis |
| 可观测性 | "用户说卡"但无法定位 | Micrometer + Prometheus + Grafana；慢 SQL 日志；P99 打点 |
| 灰度/强更 | 客户端版本碎片化 | `/app/config` 下发 `minVersion`；接口只增不改 |
| 多实例 | 单机扛不住 | 先垂直扩容（2C→4C）；再水平 + 无状态化（session 移出进程） |

---

## 9. 接口演进规则（前后端契约的硬约定）

这部分比功能列表重要——契约一旦随便改，已安装的 APK 旧版本就会白屏。

| 规则 | 说明 |
|---|---|
| **只增不改** | `/v1` 内只允许新增字段；改名/删除/改语义 → 开 `/v2`，旧接口至少保留一个版本周期 |
| **新增字段必须可缺省** | 客户端解析要容错；服务端返回 null 时 App 不能崩（已踩过：`RouteSection.subtitle` 返回 null 会让 kotlinx.serialization 抛异常，现在强制返回空串） |
| **错误码不复用** | 1001 参数错 / 1004 资源不存在 / 5000 服务端错；新增错误类型新开码 |
| **不做破坏性默认值变更** | 分页 `size` 默认值改动也算破坏性变更 |
| **枚举只加不减** | 客户端对未知枚举值要有 default 分支 |

**联调纪律**：每次动接口，先跑 `mvn test`（12 个回归用例），再 `curl` 四个接口确认，最后才发 APK。
服务端有任何字段变动，必须同步更新 `data/remote/dto/` 里的 DTO。

---

## 10. 排期建议（一句话版）

```
App P1 收尾  →  S4（2 天，让内容从假变真）
             →  App P2（可靠性，服务端零参与）
             →  S5（要不要外网访问？要就做）
             →  App P3/P5 按兴趣挑
             →  S6（详情页 + 真实图片，配合 P4 离线包）
             →  S7/S8（等真实用户反馈再说）
```

**唯一不变的原则**：服务端永远不追功能数量，只解决"App 现在缺什么数据"。
缺就补一个接口，不缺就一行代码都别写。

---

## 11. 社区共维护（2026-10-21 落地）

设计口径见 `docs/product-philosophy.md`：**记录私有、线路公共、社交不做**。
线路不开放自由创作，只做「提案 + 认票 + 版本回滚」。

| 提案类型 | 事实来源 | 处理 |
|---|---|---|
| IMPORT 外部导入 | 第三方 | 管理员**合规**审核（不是事实审核） |
| UPLOAD 自有轨迹 | 提交者本人 | 规则层校验 → 建线（`upload-autopublish` 默认 false，走人工） |
| EDIT 勘误 | 亲历者 | OBJECTIVE 类达认票阈值自动合并；SUBJECTIVE/PRIVACY 必须人工 |

关键机制：

1. **认票**：提案人自动一票，阈值默认 2（= 提案人 + 另一人确认）。可改票，反对票直接阻塞自动合并。
2. **版本**：每次合并 `route_detail.version` +1，同时写 `route_detail_revision` 全量快照。
3. **冲突**：提案的 `base_version` ≠ 当前版本 → `CONFLICT`，不覆盖。
4. **回滚**：`POST /api/v1/admin/proposals/routes/{id}/rollback {version}`，版本号继续 +1。
5. **防刷**：同一 `proposer_uuid` 每小时上限 `rate-limit-per-hour`(10)。

表：`content_proposal` / `content_vote` / `route_detail_revision`（V7，sqlite+mysql 双份）。
旧的 `route_submission` 数据在 V7 里已平移成 `IMPORT` 提案，旧后台页面照常可用。

### 接口与入口（已接通，不是骨架）

| 层 | 位置 | 说明 |
|---|---|---|
| 公开 API | `POST/GET /api/v1/proposals`、`POST /api/v1/proposals/{id}/vote` | 免登录，身份是客户端设备 UUID |
| 管理 API | `GET /api/v1/admin/proposals`、`/approve`、`/reject`、`/routes/{id}/revisions`、`/rollback` | 走 AdminAuthInterceptor |
| 线路详情 | `GET /api/v1/routes/{id}` | 追加 `version` / `curatorName`（只增不改） |
| 管理后台 | `static/admin/proposals.html` | 按状态筛选、通过/驳回 |
| App | `ui/routes/RouteMaintenanceSection.kt`、`ui/tracks/TrackCompareCard.kt`、`ContributeTrackDialog.kt` | 勘误/认票、同路段对比、轨迹贡献 |

### 还没做

- 提案评论（目前只有认票，没有讨论位）——有争议时只能靠管理员裁决
- 审核页看不到字段级 diff：`ProposalRow` 没返回 `payloadJson`（轨迹 payload 可能很大，
  要加就加摘要字段，别无脑塞原文）
- 到点自动合并的定时任务：现在只在「有人投票」时触发，凑够票但没人再动就会停在 PENDING
  等管理员。真要无人值守，加一个 `@Scheduled` 扫描 PENDING 即可
