#!/usr/bin/env bash
# 首次部署：在服务器上把「目录结构 + systemd 单元 + JDK」准备好。
# 只需跑一次；之后发布一律用 release.sh。
#
#   ./deploy/bootstrap.sh              # 在服务器上执行
#   ./deploy/bootstrap.sh --from-local # 从本机远程执行（需已配好 ssh 免密）
#
# 幂等：重复执行不会破坏已有数据（不会覆盖 zlb.env、不会删库）。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_DIR="${ZLB_REMOTE_DIR:-/opt/zouliangbu-server}"
SERVICE="${ZLB_SERVICE:-zouliangbu-server}"

c_grn=$'\033[32m'; c_ylw=$'\033[33m'; c_red=$'\033[31m'; c_off=$'\033[0m'
info() { printf '%s==>%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s[!]%s %s\n' "$c_ylw" "$c_off" "$*"; }
die()  { printf '%s[x]%s %s\n' "$c_red" "$c_off" "$*" >&2; exit 1; }

# ---------- 远程模式：把本脚本推过去执行 ----------
if [[ "${1:-}" == "--from-local" ]]; then
  SCRIPT_DIR_LOCAL="$SCRIPT_DIR"
  [[ -f "$SCRIPT_DIR_LOCAL/deploy.env" ]] && source "$SCRIPT_DIR_LOCAL/deploy.env"
  HOST="${ZLB_HOST:-82.156.84.127}"; USER_="${ZLB_USER:-ubuntu}"; PORT="${ZLB_PORT:-22}"
  KEY="${ZLB_KEY:-$HOME/.ssh/id_ed25519}"
  # 非 root 用户需要 sudo；root 直接跑
  if [[ "$USER_" == "root" ]]; then SUDO_C=""; else SUDO_C="sudo"; fi

  echo "==> 上传 bootstrap 到 $USER_@$HOST"
  scp -P "$PORT" ${KEY:+-i "$KEY"} -q "$SCRIPT_DIR_LOCAL/bootstrap.sh"        "$USER_@$HOST:/tmp/zlb-bootstrap.sh"
  scp -P "$PORT" ${KEY:+-i "$KEY"} -q "$SCRIPT_DIR_LOCAL/zouliangbu-server.service" "$USER_@$HOST:/tmp/zlb.service"
  scp -P "$PORT" ${KEY:+-i "$KEY"} -q "$SCRIPT_DIR_LOCAL/zlb.env.example"     "$USER_@$HOST:/tmp/zlb.env.example"

  if [[ -n "$SUDO_C" ]]; then
    ssh -p "$PORT" ${KEY:+-i "$KEY"} "$USER_@$HOST" "sudo -n true" 2>/dev/null || {
      echo "[x] $USER_ 无法免密 sudo。先执行：" >&2
      echo "    echo '$USER_ ALL=(ALL) NOPASSWD:ALL' | sudo tee /etc/sudoers.d/zlb-deploy && sudo chmod 440 /etc/sudoers.d/zlb-deploy" >&2
      exit 1
    }
  fi

  ssh -p "$PORT" ${KEY:+-i "$KEY"} "$USER_@$HOST" \
    "$SUDO_C bash /tmp/zlb-bootstrap.sh --files-in /tmp; rm -f /tmp/zlb-bootstrap.sh /tmp/zlb.service /tmp/zlb.env.example"
  exit $?
fi

[[ "$(id -u)" -eq 0 ]] || die "需要 root（用 sudo bash bootstrap.sh）"
FILES_IN="/tmp"
[[ "${1:-}" == "--files-in" ]] && FILES_IN="${2:-/tmp}"

# ---------- JDK ----------
info "检查 JDK 17"
JAVA_BIN="$(command -v java 2>/dev/null || true)"
if [[ -n "$JAVA_BIN" ]] && "$JAVA_BIN" -version 2>&1 | grep -qE '"(1[7-9]|2[0-9])'; then
  info "已有 JDK 17+：$JAVA_BIN（unit 用 /usr/bin/java，无需另装）"
else
  info "安装 openjdk-17-jre-headless"
  if command -v apt-get >/dev/null; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jre-headless
  elif command -v dnf >/dev/null; then
    dnf install -y java-17-openjdk-headless
  elif command -v yum >/dev/null; then
    yum install -y java-17-openjdk-headless
  else
    die "无法识别包管理器，请手动安装 JDK 17"
  fi
fi

# ---------- 目录 ----------
# 运行用户直接用 ubuntu（服务器上已存在，且旧进程本来就是它跑的）
info "建立目录结构 $REMOTE_DIR"
mkdir -p "$REMOTE_DIR"/{releases,uploads}
chown -R ubuntu:ubuntu "$REMOTE_DIR"
chmod 755 "$REMOTE_DIR"

# ---------- 从旧的「裸 jar 直放」结构迁移 ----------
# 旧结构：/opt/zouliangbu-server/zouliangbu-server-0.1.0.jar 直接跑
# 新结构：/opt/zouliangbu-server/{releases/<版本>/app.jar, current -> releases/<版本>}
OLD_JAR="$REMOTE_DIR/zouliangbu-server-0.1.0.jar"
if [[ -f "$OLD_JAR" && ! -L "$REMOTE_DIR/current" ]]; then
  info "检测到旧版直放 jar，迁移到 releases/ 结构"
  SEED_DIR="$REMOTE_DIR/releases/legacy-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$SEED_DIR"
  mv "$OLD_JAR" "$SEED_DIR/app.jar"
  # 不用 ln -sfn：目标 current 不存在时 -n 不生效，会把链接建到目录内部
  rm -f "$REMOTE_DIR/.current.next"
  ln -s "${SEED_DIR#$REMOTE_DIR/}" "$REMOTE_DIR/.current.next"
  mv -Tf "$REMOTE_DIR/.current.next" "$REMOTE_DIR/current"
  chown -R ubuntu:ubuntu "$REMOTE_DIR/releases"
  info "旧 jar 保留为回滚基线: ${SEED_DIR#$REMOTE_DIR/}"
fi

# ---------- env ----------
if [[ -f "$REMOTE_DIR/zlb.env" ]]; then
  warn "$REMOTE_DIR/zlb.env 已存在，保留不动"
else
  if [[ -f "$FILES_IN/zlb.env.example" ]]; then
    cp "$FILES_IN/zlb.env.example" "$REMOTE_DIR/zlb.env"
    chown ubuntu:ubuntu "$REMOTE_DIR/zlb.env"
    chmod 600 "$REMOTE_DIR/zlb.env"
    warn "已生成 $REMOTE_DIR/zlb.env —— 记得改 DATABASE_PASSWORD 和 ADMIN_PASSWORD"
  else
    die "缺 zlb.env.example，无法生成配置"
  fi
fi

# ---------- systemd ----------
info "安装 systemd 单元 $SERVICE"
if [[ -f "$FILES_IN/zlb.service" ]]; then
  install -m 644 "$FILES_IN/zlb.service" "/etc/systemd/system/$SERVICE.service"
  systemctl daemon-reload
  systemctl enable "$SERVICE" >/dev/null
  info "已 enable $SERVICE（jar 就位后 systemctl start 即可）"
else
  die "缺 zouliangbu-server.service"
fi

echo
info "bootstrap 完成。下一步："
echo "  1. 编辑 $REMOTE_DIR/zlb.env 填数据库密码 / 后台密码"
echo "  2. 回本机执行 ./deploy/release.sh"
