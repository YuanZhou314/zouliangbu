# 走两步 · 内容服务端（zouliangbu-server）

App 内容层（首页动态 / 线路 / 装备分类）的后端。**轨迹数据不上云**，这里不含任何轨迹表。

- 技术栈：Java 17 + Spring Boot 4.1.1 + MyBatis-Plus 3.5.17（boot4 starter）+ Flyway + MySQL 8 / H2
- 接口版本：`/api/v1`，统一响应 `{ code, msg, data }`

## 一、本地跑（零依赖，推荐先这样验）

dev profile 用 H2 内存库 + Flyway 自动建表灌种子数据，**不需要 Docker、不需要装 MySQL、不需要装 Maven 之外的任何东西**。

最省事（自动校验 JDK、打包、检查端口）：

```bash
cd zouliangbu-server
./run-dev.sh            # macOS / Linux / Git-Bash
.\run-dev.ps1           # Windows PowerShell
```

手动跑也行：

```bash
cd zouliangbu-server
mvn -s maven-settings.xml -DskipTests spring-boot:run
# 或先打包再跑（注意加 --server.port，原因见 §五 最后一行）：
mvn -s maven-settings.xml -DskipTests package
java -jar target/zouliangbu-server-0.1.0.jar --server.port=8080
```

验证：

```bash
curl http://127.0.0.1:8080/api/v1/healthz
curl "http://127.0.0.1:8080/api/v1/feed?size=3"
curl "http://127.0.0.1:8080/api/v1/routes?keyword=武功"
curl "http://127.0.0.1:8080/api/v1/gear/categories"
```

> dev 每次重启数据重置为种子数据（`V2__seed.sql`）。

## 二、生产跑（Docker + MySQL）

```bash
cp .env.example .env         # 改掉两个密码
docker compose up -d --build
curl http://127.0.0.1:8080/api/v1/healthz
```

MySQL 与 api 同 compose 内网，3306 **不对外暴露**。正式对外请在 compose 里打开 nginx 段并配 HTTPS。

## 三、接口

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| GET | `/api/v1/feed` | `city`、`cursor`、`size`(1–50，默认 20) | 首页同城动态，cursor 分页 |
| GET | `/api/v1/routes` | `keyword`、`city` | 线路目录（推荐位 + 分组）；有关键词时隐藏推荐位 |
| GET | `/api/v1/gear/categories` | — | 装备分类（静态，可长缓存） |
| GET | `/api/v1/healthz` | — | 存活检查 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "items": [
      {
        "id": "10",
        "title": "武功山—明月山穿越，三天两晚行程贴",
        "author": "赣西驴友",
        "likeCount": 64,
        "city": "宜春",
        "coverUrl": null,
        "coverAspectRatio": 1.1
      }
    ],
    "nextCursor": "MTA"
  }
}
```

字段名与 App 侧 `data/model/FeedItem.kt`、`RouteItem.kt`、`GearCategory.kt` **完全一致**，
App 接网络时只需加反序列化，不改 UI / ViewModel。

## 四、表结构

```
feed_post      动态（title/author/like_count/city/cover_url/cover_aspect_ratio/status）
route_section  线路分组（title/subtitle/sort_weight）
route          线路（name/region/distance_km/ascent_meters/tags 逗号分隔/section_id/is_recommend）
gear_category  装备分类（code=g1..g8 与 App 图标映射键一致）
```

改表流程：新增 `src/main/resources/db/migration/V3__xxx.sql` → 重启自动执行。**不要改已执行的 V1/V2**。

## 五、踩坑记录（Spring Boot 4 专有）

| 坑 | 正确做法 |
|---|---|
| `spring-boot-starter-web` 在 Boot 4 已改名 | 用 **`spring-boot-starter-webmvc`**（旧名仍可用但已废弃） |
| Boot 4 里 Flyway 放 jar 不再自动配置 | 必须引 **`spring-boot-starter-flyway`** |
| Jackson 换成 3.x | 包名从 `com.fasterxml.jackson` 变为 **`tools.jackson`**（注解仍在 `com.fasterxml.jackson.annotation`） |
| MyBatis-Plus 用 Boot 3 的 starter 会启动失败 | 必须 **`mybatis-plus-spring-boot4-starter` ≥ 3.5.15**（当前 3.5.17） |
| 分页插件自 3.5.9 起从核心包拆出 | 本项目用 cursor 分页，**不需要** `mybatis-plus-jsqlparser` |
| MySQL 容器默认 UTC | compose 里加 `--default-time-zone=+08:00` |
| 测试注解包被重组 | `@AutoConfigureMockMvc` 在 **`org.springframework.boot.webmvc.test.autoconfigure`**；`@MockBean` → **`@MockitoBean`** |
| 本机有环境变量 `SERVER__PORT` 抢占端口 | Spring 的宽松绑定会把它读成 `server.port`。本地起服务用 **`--server.port=8080`**（命令行参数优先级最高），或 `unset SERVER__PORT` |

## 六、测试

```bash
mvn -s maven-settings.xml test
```

`ContentApiTest` 共 12 个用例，覆盖：健康检查、列表倒序与 size 约束、游标翻页不重不漏、
同城过滤、关键词命中推荐位线路（回归用例）、跨分组搜索保留分组、装备排序、
以及 4 类非法入参必须返回 4xx 而非 500。

> 用 `@SpringBootTest` 而非 `@WebMvcTest`：要覆盖 Flyway 建表 → 种子数据 → MyBatis-Plus 查询的完整链路。
> "搜武功搜不到" 这类缺陷正好出在查询层，切掉 DB 就测不出来。

## 七、三平台启动 & 为什么 jar 能跨架构

### 7.1 一键脚本（推荐，本地开发用这个）

| 系统 | 命令 |
|---|---|
| Windows PowerShell | `.\run-dev.ps1` |
| Windows PowerShell（端口被占） | `.\run-dev.ps1 -Force`（先杀掉占用进程） |
| macOS / Linux / Git-Bash | `./run-dev.sh` |
| 换端口 | `.\run-dev.ps1 -Port 8081` / `./run-dev.sh 8081` |
| 跳过打包直接跑 | `-SkipBuild` / `--skip-build` |

脚本会依次做：校验 JDK ≥ 17 → 必要时打包 → 检查端口占用（`lsof` 没有时用 `/dev/tcp` 兜底）→
**显式带 `--server.port`** 启动（绕开环境里的 `SERVER__PORT`）。

### 7.2 Mac M1/M2（arm64）能跑吗？—— 能，且不用改任何东西

判断依据不是"试一下"，是下面这条可验证的事实：

```bash
# 扫描 fat jar 里有没有原生库（.so/.dll/.dylib/.jnilib）
unzip -l target/zouliangbu-server-0.1.0.jar | grep -icE "\.(so|dll|dylib|jnilib)$"
# → 0
```

**jar 内 60 个依赖、0 个原生二进制**，全是纯 Java 字节码（Spring / Tomcat 内嵌 / MyBatis-Plus /
H2 / MySQL Connector-J 都没有 JNI）。字节码与 CPU 架构无关 → **arm64 上直接跑**。

唯一要求：装 **arm64 的 JDK 17**（不要装成 x86 版走 Rosetta，能跑但明显慢）。

```bash
# macOS Apple Silicon
brew install --cask temurin@17
java -version          # 应显示 17.x
# 若 shell 里找不到 java（装了但没上 PATH）：
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### 7.3 服务器上怎么起（两条路，按有没有 MySQL 选）

**A. 有 MySQL → prod profile + systemd（最省资源，推荐）**

```bash
# 1. 打包（在任意机器上打，产物跨平台通用）
mvn -s maven-settings.xml -DskipTests package

# 2. 拷到服务器
scp target/zouliangbu-server-0.1.0.jar user@server:/opt/zouliangbu-server/
scp deploy/zouliangbu-server.service user@server:/tmp/

# 3. 服务器上装 JDK17 + 注册服务
sudo apt install openjdk-17-jre-headless
sudo cp /tmp/zouliangbu-server.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now zouliangbu-server
sudo systemctl status zouliangbu-server
curl http://127.0.0.1:8080/api/v1/healthz
```

`deploy/zouliangbu-server.service` 里数据库密码建议改成 `EnvironmentFile=/etc/zlb/db.env`（`chmod 600`），
否则 `systemctl show` 能看到明文。

**B. 暂时没有 MySQL → 直接用 dev profile（H2 内存库）**

```bash
java -jar zouliangbu-server-0.1.0.jar --spring.profiles.active=dev --server.port=8080
```

能跑、有完整种子数据，**但重启后数据回到种子状态**。内容这个阶段本来就来自 Flyway 种子 + 只读，
所以完全够用；等要录入真实内容了再上 MySQL。

**C. 想用 Docker（需要 Docker 可用）**

```bash
docker compose up -d --build
```

> 注意：`api` 服务的 Dockerfile 已加 non-root 用户、healthcheck、`exec` 让 JVM 成为 1 号进程（SIGTERM 可直达）。
> 镜像基础件（`maven:3.9-eclipse-temurin-17`、`eclipse-temurin:17-jre-alpine`）官方同时发布 amd64/arm64，
> 所以 Mac M1 上 `docker build` 直接出 arm64 镜像；要给 x86 服务器构建时加
> `--platform linux/amd64`（需 buildx）。

### 7.4 三个平台的差异只有"怎么装 JDK"

| 项 | Windows | macOS M1 | Linux 服务器 |
|---|---|---|---|
| JDK 安装 | Temurin/Zulu 17 installer | `brew install --cask temurin@17` | `apt install openjdk-17-jre-headless` |
| 启动脚本 | `run-dev.ps1` | `run-dev.sh` | `run-dev.sh` 或 systemd |
| 端口变量坑 | 有 `SERVER__PORT`，脚本已绕开 | 无 | 无 |
| jar 兼容性 | ✅ | ✅（arm64 原生） | ✅ |
| 常驻方式 | 前台窗口 | 前台 / `launchd` | systemd / compose |

### 7.5 换行符（跨平台协作的隐形坑）

仓库根目录加了 `.gitattributes`：`.sh`/`gradlew`/`Dockerfile`/`.sql` 强制 **LF**，`.ps1` 强制 **CRLF**。
否则在 Windows 上 checkout 后拿到 CRLF 的 `run-dev.sh`，到 Mac 上会直接报
`bad interpreter: /usr/bin/env: 'bash\r': No such file or directory`。
