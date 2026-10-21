# 后端选型评估与实施方案（BACKEND-PLAN v3）

- 制定：2026-10-20　｜　v2 修订：撤回"以本机环境为首要权重"的选型逻辑，改为按**工作负载**客观评估
- **v3 定稿：你拍板用 Java，方案按 Spring Boot 落地，代码已完成并跑通（见 §12）**
- 对应 `ROADMAP.md` 的 P6（内容层接后台）
- 前置状态：App 侧已完成「接口 + Mock 实现」解耦，`FootprintApp` 里三个 lazy 单例是唯一替换点

---

## 决策记录 D5：语言最终选 Java（你定的）

v2 的打分结论是 Python 4.15 / Java 3.90 / Go 3.88 —— 差距**不到 0.3 分**，属于"选哪个都能干"的区间，
不是"选错就翻车"。所以你选 Java，我按 Java 落地，并且不需要为这个选择找补理由。

但有 3 件事必须写清楚，避免以后误判：

| 项 | 实际情况 |
|---|---|
| **Java 的真实优势（对本项目）** | 长期可维护性强于 Python（编译期类型检查，重构 10 万行不心慌）；国内外包/招聘池大，将来想找人接手成本低；若真要做服务端地理计算（GraphHopper/JTS），同一语言直接加模块，不用再起一套 |
| **Java 的真实代价** | 同样 3 个接口，代码量约 Python 的 2–2.5 倍；本次实测：从零到跑通 4 个接口 + 12 个测试，用的是 Spring Boot 4 + MyBatis-Plus，配置/样板代码明显更多（实体、Mapper、DTO、异常处理器各一套） |
| **Spring Boot 4 的坑（已踩过）** | `spring-boot-starter-web` 已更名 **`-webmvc`**；Flyway 要独立 **`spring-boot-starter-flyway`**；测试注解搬到 **`org.springframework.boot.webmvc.test.autoconfigure`**；`@MockBean` → `@MockitoBean`。用 Boot 3 的写法直接起不来 |

> 结论不重要，**接口契约重要**。§5 的契约是语言无关的 —— 万一将来想换语言，App 端一行代码都不用改。

---

## 0. 结论先行（v2 修订，已被 D5 覆盖）

> 本节是"如果只看负载该怎么选"的原始结论，保留作评估记录。**实际执行方案见 §12（Java）。**

| 项 | 结论 |
|---|---|
| **推荐组合** | **Python 3.13 + FastAPI + SQLAlchemy 2.0 + Alembic + PostgreSQL 16+ + Docker Compose** |
| **改变结论的原因** | 本项目的后端是**纯 CRUD + 读多写少**，没有服务端地理计算。在这个负载下 Python 的开发效率优势压倒 Java 的生态优势；v1 把"本机装了 JDK"当成首要理由，是**错的**（那是降低我的开工成本，不是为项目最优） |
| **Java 什么时候反超** | 一旦服务端要做路由规划 / 瓦片切片 / DEM 处理 / 大规模轨迹分析（GraphHopper、JTS、GeoTools 都在 JVM），或要扩成团队协作项目 —— 那时 Java/Kotlin 更合适 |
| 部署 | 单台 2 核 2G 轻量服务器 + Docker Compose（nginx + api + postgres）；月成本 ¥60–100 |
| 工期 | 内容接口 v1（feed / routes / gear）：**2–3 个工作日**（比 Java 方案少 1 天量级） |

> **一句话**：这个项目现在需要的不是"企业级框架"，而是"三天能把真数据端上桌、以后还能长大"的东西。

---

## 1. 先回答"户外 App 的后台一般用什么"

这个问题没有统一答案，但**分域规律很明显**（已核对公开资料）：

| 产品 | 后端技术栈 | 说明 |
|---|---|---|
| **Strava** | 早期 Ruby on Rails 单体（The Monorail）→ 服务化后 **Scala + Ruby + Go**；MySQL / Redis / Cassandra / Kafka | 官方工程博客与招聘 JD 明确写的 |
| **Komoot** | **Java + Python + Clojure** 微服务；自家地理编码器 Photon 是 **Java 21 + Javalin + OpenSearch** | Photon 开源，技术栈可查；早期博客明确"backend logic 用 Java / Python / Clojure" |
| **两步路（9.2.8）** | 服务端不可见（只有 APK）。客户端用 OkHttp3 + Netty + Jackson + ORMLite + MMKV | 国内同类产品服务端普遍是 Java/Spring Cloud 或 Go，但这是行业惯例推断，非证据 |

**行业真实规律：不是"选一个语言"，而是"按域分语言"**

| 域 | 常用语言/框架 | 原因 |
|---|---|---|
| 地理计算（路由引擎、瓦片切片、DEM） | **Java/Kotlin（GraphHopper、JTS、GeoTools）、C++、Go** | 库生态 + 计算密度，Python 在这里是弱项 |
| 业务 API（内容、账号、订单） | **Ruby / Python / Node / Java 都常见** | 拼的是开发速度与生态，不是极限性能 |
| 数据分析 / 推荐 / 内容审核 | **Python** | 生态无可替代 |

**关键结论：户外 App 之所以看起来"爱用 Java"，是因为它们的重活是地理计算，而不是因为内容接口需要 Java。**
我们这个 App 的地理计算**全在客户端**（GPS 采点、Haversine、将来的抽稀/爬升都在手机里），服务端只做内容 CRUD —— 所以不能直接套用"户外 App 都用 Java"这个结论。

---

## 2. 客观评估：Python vs Java vs Go（按本项目权重打分）

### 2.1 先明确负载特征（这是唯一该被当作依据的东西）

| 特征 | 本项目实况 |
|---|---|
| 接口类型 | 3 个只读接口（feed / routes / gear），未来加账号、收藏、评论 |
| 读写比 | 读 >> 写（内容由运营录入，用户只读） |
| QPS | 自用 < 10；小范围上架 < 200 |
| 数据量 | < 10 万行，单表 |
| 计算密度 | **几乎为零**（无路由计算、无瓦片生成、无图像处理） |
| 团队 | 1 人，主业 Android/Kotlin |
| 生命周期 | 长期自用 + 可能上架 |

### 2.2 加权评分（5 分制，权重按上面的负载定）

| 维度 | 权重 | Python+FastAPI | Java+Spring Boot | Go+Gin |
|---|---|---|---|---|
| 与负载匹配（CRUD/IO 密集） | 30% | **5**（正好） | 4（能打但过重） | 4 |
| 单人开发速度 | 25% | **5** | 3（配置/样板多） | 3.5 |
| 长期可维护性（类型、升级、依赖） | 20% | 3（mypy+Pydantic 可弥补） | **5** | 4 |
| 生态（账号/支付/短信/对象存储/将来地理计算） | 15% | 3（地理计算弱） | **5** | 3.5 |
| 运行成本（内存/镜像/规格） | 10% | 4（~120MB） | 2（~450MB） | **5**（~50MB） |
| **加权总分** | 100% | **4.15** | 3.90 | 3.88 |

> 分数差不多，但**分布完全不同**：Python 赢在"当下要干的活"，Java 赢在"以后可能干的活"。
> 如果你的目标是"长期做成多端产品 + 团队协作 + 服务端地理计算"，把第 4 项权重提到 30%，Java 立刻反超。

### 2.3 三个语言在本项目上的真实差异（不是营销话术）

| 关注点 | Python | Java | Go |
|---|---|---|---|
| 加一个字段/接口的改动量 | 改 model + 一个函数 | 改 entity + mapper + service + DTO | 改 struct + handler + repo |
| 数据库迁移 | Alembic（和 Room migration 思路一致） | Flyway | golang-migrate |
| 上手成本（对你） | 低（语法接近伪代码） | 中（注解/依赖注入/分层） | 中（接口/错误处理风格独特） |
| 类型安全 | 中（mypy 严格模式可到中上） | **高** | 高 |
| 内存 | ~120 MB | ~450 MB | ~50 MB |
| 冷启动 | ~1s | ~10s | ~0.1s |
| 将来接 AI（内容审核/推荐/自动打标签） | **原生优势** | 需要跨服务 | 需要跨服务 |
| 将来做服务端路由计算 | 弱（要调外部引擎） | **强（GraphHopper/JTS）** | 中（自己实现图算法） |
| 依赖升级风险 | 中（大版本偶有破坏性变更） | 低（Spring 生态克制） | **最低**（Go 1 兼容承诺） |

### 2.4 我的修正结论

**这个阶段选 Python + FastAPI，理由是"负载匹配"，不是"你机器上有 Python"。**
同时明确写死一条**重新评估的触发条件**：

> 如果服务端出现以下任一需求，就为它单独起一个 JVM 服务（不是把整个后端重写）：
> ① 服务端计算路线（GraphHopper）；② 切片/离线包生成；③ 轨迹批量分析（抽稀、匹配路网）；
> ④ 需要给第三方提供地理 API。

届时架构是"Python 业务 API + JVM 计算服务"，两者通过 HTTP/队列通信——这也是 Komoot 的实际做法（Java + Python 并存）。

---

## 3. 推荐技术栈（Python 版，可直接落地）

| 组件 | 版本 | 说明 |
|---|---|---|
| Python | **3.13**（本机已有 3.13.14） | 3.12+ 均可；用 `uv` 或 `venv` 隔离 |
| Web 框架 | **FastAPI 0.11x** | 自带 OpenAPI/Swagger（`/docs`），Pydantic 校验 |
| ASGI Server | **uvicorn**（`--workers 2`） | 单机 2 worker 足够；不要上 gunicorn+gevent 那套老组合 |
| ORM | **SQLAlchemy 2.0**（异步 `asyncpg` 或同步 `psycopg3` 均可） | 本项目建议**同步 psycopg3**：简单、调试直观、QPS 完全够 |
| 迁移 | **Alembic** | 与 Android Room migration 同思路：版本化 SQL |
| 数据库 | **PostgreSQL 16+** | 理由：将来"附近线路"用 PostGIS 一行 SQL 搞定；JSONB 存 tags；约束比 MySQL 严。若你更熟 MySQL 8 也完全可以换 |
| 配置 | pydantic-settings + `.env` | 不要把密码写进代码 |
| 日志 | 标准 logging + JSON 格式 | 便于排查 |
| 部署 | Docker Compose（nginx + api + postgres） | 你已有 Docker |

**为什么不用 Django**：Django 自带 admin（对内容运营很香），但它的 ORM 与异步生态不如 FastAPI 直接，且模板/全家桶对本项目偏重。
**如果"要一个后台管理界面"变成刚需**，再引 Django admin 或直接用 SQL/低代码后台，不要为此换框架。

---

## 4. 目录结构与关键代码

```
zouliangbu-server/
├── docker-compose.yml
├── Dockerfile
├── pyproject.toml
├── .env.example
├── alembic.ini
├── alembic/versions/0001_init.py
└── app/
    ├── main.py                 # FastAPI 实例 + 路由注册 + 异常处理
    ├── core/
    │   ├── config.py           # pydantic-settings
    │   ├── db.py               # engine / SessionLocal / get_db 依赖
    │   └── response.py         # ApiResponse、分页游标编解码
    ├── content/
    │   ├── models.py           # SQLAlchemy: FeedPost / Route / RouteSection / GearCategory
    │   ├── schemas.py          # Pydantic 响应模型（字段名与 App 侧完全一致）
    │   ├── repository.py       # 查询（cursor 分页、关键词过滤）
    │   └── router.py           # /api/v1/feed、/routes、/gear/categories
    └── seed.py                 # 灌种子数据（直接复用 MockData.kt 的文案）
```

**统一响应与分页（消除样板）**

```python
# app/core/response.py
from typing import Generic, TypeVar
from pydantic import BaseModel

T = TypeVar("T")

class ApiResponse(BaseModel, Generic[T]):
    code: int = 0
    msg: str = "ok"
    data: T | None = None

def ok(data: T) -> ApiResponse[T]:
    return ApiResponse(code=0, msg="ok", data=data)
```

**一个接口的完整样子（feed）**

```python
# app/content/router.py
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from app.core.db import get_db
from app.core.response import ApiResponse, ok
from app.content import repository, schemas

router = APIRouter(prefix="/api/v1")

@router.get("/feed", response_model=ApiResponse[schemas.FeedPage])
def get_feed(
    city: str | None = Query(None, description="城市，如 深圳"),
    cursor: str | None = Query(None),
    size: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
):
    page = repository.list_feed(db, city=city, cursor=cursor, size=size)
    return ok(page)
```

```python
# app/content/repository.py（cursor 分页，避免深翻页 offset 退化）
def list_feed(db: Session, city: str | None, cursor: str | None, size: int) -> schemas.FeedPage:
    stmt = select(FeedPost).where(FeedPost.status == 1)
    if city:
        stmt = stmt.where(FeedPost.city == city)
    if cursor:
        last_id = decode_cursor(cursor)          # base64 的 id
        stmt = stmt.where(FeedPost.id < last_id)
    rows = db.execute(stmt.order_by(FeedPost.id.desc()).limit(size + 1)).scalars().all()
    has_more = len(rows) > size
    rows = rows[:size]
    return schemas.FeedPage(
        items=[schemas.FeedItem.model_validate(r) for r in rows],
        nextCursor=encode_cursor(rows[-1].id) if has_more and rows else None,
    )
```

---

## 5. 接口契约（与语言无关，保持不变）

| 方法 | 路径 | 参数 | 用途 |
|---|---|---|---|
| GET | `/api/v1/feed` | `city`、`cursor`、`size=20` | 首页同城动态 |
| GET | `/api/v1/routes` | `keyword`、`city` | 线路页（搜索 + 推荐 + 分组） |
| GET | `/api/v1/gear/categories` | — | 装备分类（`Cache-Control: max-age=86400`） |

统一响应：`{ "code": 0, "msg": "ok", "data": {...} }`；分页用 `nextCursor`（`null` 表示没有更多）。

**字段名必须与 App 侧现有 model 完全一致**（`FeedItem` / `RouteCatalog` / `RouteItem` / `GearCategory`，camelCase），这样 App 接入只写反序列化。

```json
{
  "code": 0, "msg": "ok",
  "data": {
    "items": [{
      "id": "1001",
      "title": "武功山反穿：两天一夜看云海日出全记录",
      "author": "山野拾光",
      "likeCount": 128,
      "city": "萍乡",
      "coverUrl": "https://cdn.example.com/feed/1001.jpg",
      "coverAspectRatio": 1.25
    }],
    "nextCursor": "MTAwMQ"
  }
}
```

---

## 6. 表结构（PostgreSQL / Alembic `0001_init.py` 等价 DDL）

```sql
CREATE TABLE feed_post (
  id                 BIGSERIAL PRIMARY KEY,
  title              VARCHAR(120) NOT NULL,
  author             VARCHAR(40)  NOT NULL,
  like_count         INTEGER      NOT NULL DEFAULT 0,
  city               VARCHAR(32)  NOT NULL,
  cover_url          VARCHAR(512),
  cover_aspect_ratio NUMERIC(4,2) NOT NULL DEFAULT 1.00,
  status             SMALLINT     NOT NULL DEFAULT 1,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_feed_city_created ON feed_post (city, created_at DESC, id DESC);

CREATE TABLE route_section (
  id          BIGSERIAL PRIMARY KEY,
  title       VARCHAR(40) NOT NULL,
  subtitle    VARCHAR(80),
  sort_weight INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE route (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(80)  NOT NULL,
  region        VARCHAR(32)  NOT NULL,
  distance_km   NUMERIC(6,1) NOT NULL,
  ascent_meters INTEGER      NOT NULL DEFAULT 0,
  cover_url     VARCHAR(512),
  tags          TEXT[],                     -- PG 数组；MySQL 则用逗号分隔
  section_id    BIGINT REFERENCES route_section(id),
  is_recommend  BOOLEAN NOT NULL DEFAULT false,
  sort_weight   INTEGER NOT NULL DEFAULT 0,
  status        SMALLINT NOT NULL DEFAULT 1,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_route_section ON route (section_id, sort_weight);
CREATE INDEX idx_route_region  ON route (region);

CREATE TABLE gear_category (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(16) UNIQUE NOT NULL,   -- 对应 App 侧 g1..g8
  name        VARCHAR(32) NOT NULL,
  note        VARCHAR(64),
  icon_key    VARCHAR(32),
  sort_weight INTEGER NOT NULL DEFAULT 0
);
```

不做 `user` / `feed_like` 表：本期只读，点赞数是冗余字段；等有账号体系再 `0002_user.py`，不动老表。

---

## 7. 部署（Docker Compose）

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: zouliangbu
      POSTGRES_USER: zlb
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      TZ: Asia/Shanghai
    volumes: ["./data/pg:/var/lib/postgresql/data"]
    restart: unless-stopped

  api:
    build: .
    environment:
      DATABASE_URL: postgresql+psycopg://zlb:${POSTGRES_PASSWORD}@db:5432/zouliangbu
      TZ: Asia/Shanghai
    depends_on: [db]
    command: uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 2
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./nginx/certs:/etc/nginx/certs:ro
    depends_on: [api]
    restart: unless-stopped
```

**成本**：2C2G 轻量约 ¥60–100/月（首年活动常见 ¥99/年）；本地 Docker 跑通 ¥0。
**备案**：只自用可 IP + 高位端口，不涉及；用域名/上架，国内服务器 80/443 必须 ICP 备案（个人可办，7–20 天）。

---

## 8. App 端接入（改动很小）

```kotlin
// app/build.gradle.kts
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
// 需要显示网络图片时再加：io.coil-kt:coil-compose:2.6.0（与 Compose 1.6.8 兼容）
```

```kotlin
// data/remote/RemoteFeedRepository.kt
class RemoteFeedRepository(private val api: ContentApi) : FeedRepository {
    override fun observeFeed(): Flow<List<FeedItem>> = flow {
        emit(api.feed(city = null, cursor = null, size = 20).data?.items.orEmpty().map { it.toModel() })
    }.flowOn(Dispatchers.IO)
}

// FootprintApp.kt：只改这一行
val feedRepository: FeedRepository by lazy { RemoteFeedRepository(api) }
```

**UI / ViewModel / 导航零改动。** Mock 实现保留作为离线 fallback（OkHttp 缓存 10 分钟 + 无网降级）。

---

## 9. 实施顺序（2–3 个工作日）

| 步骤 | 内容 | 验收 |
|---|---|---|
| S1 | `zouliangbu-server` 脚手架（FastAPI + SQLAlchemy + Alembic + Docker Compose）；`/healthz` 通 | `curl localhost:8000/healthz` → `{"status":"ok"}` |
| S2 | Alembic `0001_init` 建 4 表 + `seed.py` 灌数据（文案复用 `MockData.kt`） | `psql` 能查到 10 条动态 / 12 条线路 / 8 分类 |
| S3 | 三个接口 + 统一响应 + cursor 分页 + 关键词过滤 | `/docs` 里三个接口都返回正确 JSON，`keyword=武功` 能过滤 |
| S4 | App 接 Retrofit + `Remote*Repository`，真机联调（先本地 IP + debug 明文白名单） | 首页/线路/装备显示服务端数据 |
| S5 | VPS 部署 + Nginx + HTTPS + `pg_dump` 定时备份 | 外网 https 可访问；备份文件生成 |

---

## 10. 风险与坑（Python 版）

1. **异步 vs 同步的选择别纠结**：本项目用**同步 SQLAlchemy + psycopg3**，代码直白、调试简单，QPS 200 以下完全够。不要为了"看起来现代"全链路 async，那会引入 `asyncpg` 连接池与事务的坑。
2. **依赖升级**：Python 依赖偶有破坏性变更（Pydantic 1→2 那种）。对策：锁版本（`uv lock` / `requirements.txt` 带 `==`），每季度一次性升级。
3. **类型检查**：不跑 mypy 的 Python 项目半年后必然变成"能跑但不敢改"。建议 CI/本地预提交跑 `mypy --strict app/` + `ruff`。
4. **时区**：`created_at` 用 `TIMESTAMPTZ`，容器 `TZ=Asia/Shanghai`，App 端解析 ISO8601。别用 naive datetime。
5. **Android 明文流量**：`targetSdk=33` 下 HTTP 默认被拦（`CLEARTEXT communication not permitted`）。开发期用 `network_security_config` 只放行调试 IP，**不要把 `usesCleartextTraffic=true` 带进 release**。
6. **N+1 查询**：feed 里若要显示作者信息，用 `selectinload`，否则每条一次查询。
7. **备份**：内容数据在服务器，`pg_dump` 定时 + 异地（对象存储）。别只靠服务器磁盘。
8. **图片**：不要从 API 服务器直接吐图，走对象存储 + CDN；否则 3M 带宽很快被打满。

---

## 11. 需要你确认

| # | 问题 | 默认建议 |
|---|---|---|
| 1 | 语言：接受 **Python + FastAPI**？（若你更看重"以后可能做服务端地理计算/扩团队"，就选 Java） | ~~Python~~ → **已定 Java（D5）** |
| 2 | 数据库：**PostgreSQL 16+**（将来附近线路可上 PostGIS）还是 MySQL 8（你更熟）？ | **已按 MySQL 8 实现**（`MODE=MySQL` 的 H2 跑开发/测试），建表语句两库兼容 |
| 3 | 部署：本地 Docker 先跑通，还是直接买 2C2G 轻量服务器？ | 先本地跑通（单机 jar 已可跑） |
| 4 | 域名/备案：自用可暂不做；上架必须 | 暂不做 |

---

## 12. 实际实现（Java，已完成并验证）

### 12.1 技术栈落地版本

| 组件 | 版本 | 备注 |
|---|---|---|
| JDK | 17 | LTS；与 Android 侧 JDK 17 同版本，本机一套即可 |
| Spring Boot | **4.1.1** | 注意 4.x 的 starter 改名（见 D5 表格），照 Boot 3 教程写会起不来 |
| Web | `spring-boot-starter-webmvc` | 不是 `-web`，那个在 4.x 里已不存在 |
| ORM | **MyBatis-Plus 3.5.17**（`mybatis-plus-spring-boot4-starter`） | 选它而不是 JPA：SQL 可控、分页/条件构造器够用、不需要学一套 JPQL |
| 迁移 | **Flyway**（`spring-boot-starter-flyway`） | `V1__init.sql` 建表 + `V2__seed.sql` 灌数据，与 App 的 `MockData.kt` 文案一致 |
| 数据库 | 开发/测试 **H2 内存库**（`MODE=MySQL`）；生产 **MySQL 8** | 同一套 DDL 两库通用（未用 `ENGINE=` / `CHARSET=` 等 MySQL 专属语法） |

> **2026-10-21 变更（覆盖上表这一行）**：开发/测试库 **H2 → SQLite**（用户决定：刚起步测试阶段用单文件库更省事，
> 数据落盘、重启不丢；后续直接换 PostgreSQL，不再回 MySQL 系）。
> 迁移脚本因此拆成 `db/migration/{sqlite,mysql}` + `common`，两份 DDL 只差自增主键写法
> （SQLite 必须 `INTEGER PRIMARY KEY AUTOINCREMENT`）。prod 仍为 MySQL 8（`application-prod.yml` 未改）。
| 打包 | `zouliangbu-server-0.1.0.jar`（30MB，内嵌 Tomcat） | `java -jar` 直接跑，不需要装 Tomcat |

### 12.2 目录结构

```
zouliangbu-server/
├── pom.xml
├── Dockerfile                     # 多阶段：maven 构建 → jre17-alpine 运行
├── docker-compose.yml             # mysql + api（nginx 先注释，S5 再开）
├── .env.example
└── src/
    ├── main/java/com/zouliangbu/server/
    │   ├── ServerApplication.java          # @MapperScan
    │   ├── common/
    │   │   ├── ApiResponse.java            # {code,msg,data} + ErrorCode + Cursor
    │   │   └── GlobalExceptionHandler.java # 4xx/5xx 分层，见 12.4
    │   └── content/
    │       ├── entity/                     # 4 张表的实体
    │       ├── mapper/                     # 4 个 BaseMapper
    │       ├── dto/ContentDtos.java        # 与 App 模型逐字段对齐
    │       ├── ContentQueryService.java    # 查询/过滤/游标分页
    │       └── ContentController.java      # 4 个 GET
    ├── main/resources/
    │   ├── application.yml / -dev.yml / -prod.yml
    │   └── db/migration/V1__init.sql, V2__seed.sql
    └── test/java/.../content/ContentApiTest.java   # 12 个用例
```

### 12.3 验证结果（2026-10-20 实测）

```
GET /api/v1/healthz          → {"status":"ok"}
GET /api/v1/feed?city=萍乡    → code 0，返回 2 条（id 8、1，均 city=萍乡），nextCursor=null
GET /api/v1/feed?size=4      → 10 条分 3 页（4+4+2），游标不重不漏
GET /api/v1/routes?keyword=武功 → 搜索结果 → 武功山反穿线
GET /api/v1/routes?keyword=莫干山 → 新手友好线 + 口碑山野线（跨分组保留原分组）
GET /api/v1/routes（无关键词）  → 推荐位 3 条 + 分组 3 个
GET /api/v1/gear/categories  → 8 个分类，按 sortWeight 排序
```

`mvn test` → **Tests run: 12, Failures: 0, Errors: 0**。

### 12.4 过程中发现并修掉的 3 个真问题

| # | 问题 | 根因 | 修法 |
|---|---|---|---|
| 1 | `keyword=武功` 搜不到"武功山反穿线" | 推荐位线路的 `section_id` 为 NULL，而搜索只遍历"有分组归属"的线路 | 搜索改为覆盖全部已上线线路；无分组归属的命中项归到"搜索结果"分组置顶。**App 侧 `RoutesViewModel` 有同样缺陷，已同步修复** |
| 2 | `size=9999` / `size=abc` / 非法 URL 转义 → 全部 500 | 只处理了 `MethodArgumentNotValidException`（请求体校验），漏了 `@RequestParam` 上的 `@Min/@Max`、类型转换失败、Tomcat 层解码失败 | 补齐 4 个 4xx 处理器，异常分层：客户端传错 → 400/404/405 + code 1001/1004；服务端故障 → 500 + code 5000 |
| 3 | 未知路径返回 500 | `NoResourceFoundException` 落到兜底 handler | 单独映射为 404 |

### 12.5 已知未做（有意留到下一步）

- **无鉴权**：4 个接口全是公开只读数据，本期不需要账号体系。将来加收藏/评论时必须先做 token 校验。
- **关键词过滤在内存里做**：`< 千行` 数据量下没问题；上量后改成 SQL `LIKE` + 索引（已在代码注释标注）。
- **`section.subtitle` 用空串而非 null**：App 端 `RouteSection.subtitle` 是非空 `String`，返回 null 会让 kotlinx.serialization 直接抛异常。
- **未接 CI**：`mvn test` 现在是手动跑，接 GitHub Actions 只需一个 yml。
