#!/usr/bin/env bash
# 本地起内容服务（macOS / Linux / Git-Bash）
#
# 用法：
#   ./run-dev.sh          # 默认 8080
#   ./run-dev.sh 8081     # 换端口
#   ./run-dev.sh 8080 --skip-build   # 跳过打包，直接跑已有 jar
#
# 做的事：校验 JDK → 必要时打包 → 检查端口 → 启动（显式 --server.port，绕开 SERVER__PORT 环境变量）

set -uo pipefail
cd "$(dirname "$0")"

PORT="${1:-8080}"
SKIP_BUILD="${2:-}"
JAR="target/zouliangbu-server-0.1.0.jar"

# ---------- 1. JDK 检查 ----------
if ! command -v java >/dev/null 2>&1; then
    # macOS 上常见：装了 JDK 但 shell 里没有 java
    if [ -x /usr/libexec/java_home ]; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        [ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi
if ! command -v java >/dev/null 2>&1; then
    echo "✗ 找不到 java。请安装 JDK 17："
    echo "  macOS (Apple Silicon) : brew install --cask temurin@17"
    echo "  Ubuntu/Debian         : sudo apt install openjdk-17-jre-headless"
    exit 1
fi

JAVA_VER="$(java -version 2>&1 | head -1)"
echo "java: $JAVA_VER"
# 取主版本号：1.8.0 → 8；17.0.19 → 17
MAJOR="$(printf '%s' "$JAVA_VER" | sed -n 's/.*version "\([0-9]*\)\.\([0-9]*\).*/\1/p')"
if [ "${MAJOR:-0}" = "1" ]; then
    MAJOR="$(printf '%s' "$JAVA_VER" | sed -n 's/.*version "1\.\([0-9]*\).*/\1/p')"
fi
if [ "${MAJOR:-0}" -lt 17 ] 2>/dev/null; then
    echo "✗ 需要 JDK 17+，当前是 ${MAJOR:-未知}。"
    echo "  macOS 若装多版本：export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    exit 1
fi

# Apple Silicon 提示（不阻断：x86 JDK 走 Rosetta 也能跑，只是慢）
if [ "$(uname -s)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ]; then
    echo "arch: arm64 (Apple Silicon) —— 本 jar 内没有任何原生库，arm64 JDK 直接可跑"
fi

# ---------- 2. 打包 ----------
if [ "$SKIP_BUILD" != "--skip-build" ] || [ ! -f "$JAR" ]; then
    echo "→ 打包中（跳过测试）..."
    mvn -q -s maven-settings.xml -DskipTests package || { echo "✗ 打包失败"; exit 1; }
fi
echo "jar: $JAR"

# ---------- 3. 端口检查 ----------
# lsof 在 macOS/Linux 有，但 Git-Bash(Windows) 没有 → 用 bash 的 /dev/tcp 兜底，
# 否则检查会被静默跳过，最后只能看到 Spring 那段不友好的 "Port already in use"。
port_in_use() {
    local p="$1"
    if command -v lsof >/dev/null 2>&1; then
        [ -n "$(lsof -ti tcp:"$p" 2>/dev/null || true)" ]
    elif command -v ss >/dev/null 2>&1; then
        ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$p$"
    else
        (exec 3<>"/dev/tcp/127.0.0.1/$p") 2>/dev/null && { exec 3>&- 2>/dev/null; return 0; } || return 1
    fi
}

if port_in_use "$PORT"; then
    echo "✗ 端口 $PORT 已被占用。"
    command -v lsof >/dev/null 2>&1 && lsof -i tcp:"$PORT" 2>/dev/null | head -3
    echo "  停掉占用进程，或换端口：$0 $((PORT + 1))"
    exit 1
fi

# ---------- 4. 启动 ----------
echo "→ http://127.0.0.1:$PORT/api/v1/healthz"
# 显式 --server.port：命令行参数优先级最高，可盖掉环境里的 SERVER__PORT
exec java -jar "$JAR" --server.port="$PORT"
