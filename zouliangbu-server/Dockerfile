# syntax=docker/dockerfile:1
# 多阶段构建：Maven 编译 → JRE 运行（镜像只留 jar + JRE）
#
# 跨架构说明：官方 maven / eclipse-temurin 镜像同时发布了 linux/amd64 与 linux/arm64，
# 因此在 Intel 服务器和 Mac M1/M2 上 `docker build` 都能直接出对应架构的镜像，无需 --platform。
# 若要给另一种架构构建（例如 Mac 上出 x86 服务器镜像）：
#   docker buildx build --platform linux/amd64 -t zouliangbu-server:amd64 .

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY maven-settings.xml pom.xml ./
# 先只拉依赖，源码改动时可复用这一层缓存
RUN mvn -s maven-settings.xml -B -q dependency:go-offline
COPY src ./src
RUN mvn -s maven-settings.xml -B -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

# alpine 基础镜像**不自带 tzdata**，不装的话 ENV TZ 会被静默忽略、日志时间变 UTC。
RUN apk add --no-cache tzdata \
    && addgroup -S app && adduser -S -G app app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

WORKDIR /app
COPY --chown=app:app --from=build /app/target/zouliangbu-server-0.1.0.jar app.jar

# 不要用 root 跑业务进程
USER app

EXPOSE 8080

# 有 healthcheck，compose / k8s 才能判断"启动完成"而不是"进程还活着"
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/api/v1/healthz >/dev/null 2>&1 || exit 1

# exec 让 java 成为 1 号进程，docker stop 的 SIGTERM 才能直达 JVM（优雅停机）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
