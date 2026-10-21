#!/usr/bin/env bash
# 一键发布：本地打包 → 上传 → 远端切换 → 健康检查（失败自动回滚）
#
#   ./deploy/release.sh              # 完整发布
#   ./deploy/release.sh --skip-tests # 跳过 mvn test
#   ./deploy/release.sh --rollback   # 回滚到上一个版本
#
# 配置全部走环境变量，或用 scripts/deploy.env 文件（自动 source）：
#   ZLB_HOST       服务器 IP/域名        默认 82.156.84.127
#   ZLB_USER       ssh 用户              默认 root
#   ZLB_PORT       ssh 端口              默认 22
#   ZLB_REMOTE_DIR 远端目录              默认 /opt/zouliangbu-server
#   ZLB_SERVICE    systemd 单元名        默认 zouliangbu-server
#   ZLB_KEY        私钥路径              默认 ~/.ssh/id_ed25519
#
# 设计要点：
# - 脚本**不碰 git**（提交/推送由人决定），只管把当前代码变成线上服务
# - 远端用「版本目录 + 软链」切换：新版本起不来就切回旧软链，jar 不覆盖
# - 健康检查连不上视为失败并自动回滚

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$SERVER_DIR/.." && pwd)"

# 本地部署配置（不入库，含服务器地址等个人环境信息）
if [[ -f "$SCRIPT_DIR/deploy.env" ]]; then
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/deploy.env"
fi

ZLB_HOST="${ZLB_HOST:-82.156.84.127}"
ZLB_USER="${ZLB_USER:-root}"
ZLB_PORT="${ZLB_PORT:-22}"
ZLB_REMOTE_DIR="${ZLB_REMOTE_DIR:-/opt/zouliangbu-server}"
ZLB_SERVICE="${ZLB_SERVICE:-zouliangbu-server}"
ZLB_KEY="${ZLB_KEY:-$HOME/.ssh/id_ed25519}"

# 远端提权前缀。ubuntu 用户不是 root：写 /opt、改软链、systemctl 都要 sudo。
# 如果直接用 root 登录，把 ZLB_SUDO 设为空。
if [[ "${ZLB_USER:-root}" == "root" ]]; then
  ZLB_SUDO="${ZLB_SUDO-}"
else
  ZLB_SUDO="${ZLB_SUDO-sudo}"
fi
SUDO="${ZLB_SUDO:+$ZLB_SUDO }"

JAR_NAME="zouliangbu-server-0.1.0.jar"
SKIP_TESTS=0
DO_ROLLBACK=0

for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=1 ;;
    --rollback)   DO_ROLLBACK=1 ;;
    -h|--help)    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数: $arg（--help 看用法）" >&2; exit 2 ;;
  esac
done

# ---------- 输出 ----------
c_red=$'\033[31m'; c_grn=$'\033[32m'; c_ylw=$'\033[33m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
info() { printf '%s==>%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s[!]%s %s\n' "$c_ylw" "$c_off" "$*"; }
die()  { printf '%s[x]%s %s\n' "$c_red" "$c_off" "$*" >&2; exit 1; }
step() { printf '\n%s── %s %s\n' "$c_dim" "$*" "$c_off"; }

SSH_OPTS=(-p "$ZLB_PORT" -o ConnectTimeout=10 -o ServerAliveInterval=15 -o StrictHostKeyChecking=accept-new)
if [[ -f "$ZLB_KEY" ]]; then
  SSH_OPTS+=(-i "$ZLB_KEY")
fi
TARGET="$ZLB_USER@$ZLB_HOST"

rsh() { ssh "${SSH_OPTS[@]}" "$TARGET" "$@"; }

# ---------- 前置检查 ----------
step "0/5 前置检查"

command -v java >/dev/null || die "找不到 java"
command -v mvn  >/dev/null || die "找不到 mvn"
command -v ssh  >/dev/null || die "找不到 ssh"
command -v scp  >/dev/null || die "找不到 scp"

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
[[ "${JAVA_MAJOR:-0}" -ge 17 ]] || die "本地 JDK 需 ≥17，当前 $JAVA_MAJOR"

info "本地 JDK $JAVA_MAJOR / 目标 $TARGET:$ZLB_REMOTE_DIR ($ZLB_SERVICE)"

rsh true 2>/dev/null || die "ssh 连不上 $TARGET。
  先确认公钥已授权：把 ~/.ssh/id_ed25519.pub 内容追加到该用户 ~/.ssh/authorized_keys
  或在本机 ~/.ssh/config 配好 Host 别名，再用 ZLB_USER/ZLB_PORT 覆盖。"
info "ssh 连接正常"

# 提权探测：ubuntu 用户跑 systemctl 需要 sudo，且必须免密（否则脚本会卡在输密码）
if [[ -n "$ZLB_SUDO" ]]; then
  if ! rsh "sudo -n true" 2>/dev/null; then
    die "$ZLB_USER 用户无法免密 sudo。
  在服务器上执行一次：
    echo '$ZLB_USER ALL=(ALL) NOPASSWD:ALL' | sudo tee /etc/sudoers.d/zlb-deploy
    sudo chmod 440 /etc/sudoers.d/zlb-deploy
  然后重试。"
  fi
  info "sudo 免密可用"
fi

# 远端必备环境
rsh "test -d '$ZLB_REMOTE_DIR'" || die "远端目录 $ZLB_REMOTE_DIR 不存在。首次部署请先跑 deploy/bootstrap.sh"
rsh "systemctl list-unit-files 2>/dev/null | grep -q '^${ZLB_SERVICE}.service'" \
  || die "远端没有 systemd 单元 ${ZLB_SERVICE}.service。首次部署请先跑 deploy/bootstrap.sh"
rsh "test -f '$ZLB_REMOTE_DIR/zlb.env'" \
  || die "远端缺 $ZLB_REMOTE_DIR/zlb.env（数据库密码/后台密码）。参考 deploy/zlb.env.example"
info "远端目录与 systemd 单元就绪"

# ---------- 回滚 ----------
if [[ "$DO_ROLLBACK" -eq 1 ]]; then
  step "回滚"
  rsh "set -e
    cd '$ZLB_REMOTE_DIR'
    [[ -L current ]] || { echo 'NO_CURRENT'; exit 1; }
    prev=\$(ls -1dt releases/*/ 2>/dev/null | sed -n '2p')
    [[ -n \"\$prev\" ]] || { echo 'NO_PREV'; exit 1; }
    ${SUDO}rm -f .current.next
    ${SUDO}ln -s \"\${prev%/}\" .current.next
    ${SUDO}mv -Tf .current.next current
    echo \"ROLLED_BACK_TO=\${prev%/}\"
    ${SUDO}systemctl restart '$ZLB_SERVICE'"
  for i in $(seq 1 30); do
    if rsh "curl -fsS -m 3 http://127.0.0.1:8080/api/v1/healthz >/dev/null 2>&1"; then
      info "回滚完成，服务已恢复"; exit 0
    fi
    sleep 2
  done
  die "回滚后健康检查仍失败，请 ssh 上机看 journalctl -u $ZLB_SERVICE -n 80"
fi

# ---------- 构建 ----------
step "1/5 构建"
cd "$SERVER_DIR"

if [[ "$SKIP_TESTS" -eq 1 ]]; then
  warn "跳过测试（--skip-tests）"
  mvn -s maven-settings.xml -B -q -DskipTests clean package
else
  mvn -s maven-settings.xml -B -q clean package
fi

[[ -f "target/$JAR_NAME" ]] || die "构建产物不存在: target/$JAR_NAME"
JAR_SIZE="$(du -h "target/$JAR_NAME" | cut -f1)"
info "构建完成 target/$JAR_NAME ($JAR_SIZE)"

# 版本号：时间戳，便于回滚时辨认
BUILD_TS="$(date +%Y%m%d-%H%M%S)"
GIT_SHA="$(cd "$REPO_ROOT" && git rev-parse --short HEAD 2>/dev/null || echo 'nogit')"
GIT_DIRTY="$(cd "$REPO_ROOT" && [[ -n "$(git status --porcelain -- zouliangbu-server)" ]] && echo '-dirty' || echo '')"
RELEASE_ID="${BUILD_TS}-${GIT_SHA}${GIT_DIRTY}"
info "版本 $RELEASE_ID"

# ---------- 上传 ----------
step "2/5 上传"
REMOTE_REL="releases/$RELEASE_ID"
rsh "${SUDO}mkdir -p '$ZLB_REMOTE_DIR/$REMOTE_REL' '$ZLB_REMOTE_DIR/uploads' && ${SUDO}chown -R \$(id -u):\$(id -g) '$ZLB_REMOTE_DIR/$REMOTE_REL' 2>/dev/null || true"

scp -P "$ZLB_PORT" ${ZLB_KEY:+-i "$ZLB_KEY"} -o StrictHostKeyChecking=accept-new -q \
  "target/$JAR_NAME" "$TARGET:$ZLB_REMOTE_DIR/$REMOTE_REL/app.jar"

# 校验传输完整性
LOCAL_SUM="$(md5sum "target/$JAR_NAME" | cut -d' ' -f1)"
REMOTE_SUM="$(rsh "md5sum '$ZLB_REMOTE_DIR/$REMOTE_REL/app.jar'" | cut -d' ' -f1)"
[[ "$LOCAL_SUM" == "$REMOTE_SUM" ]] || die "传输校验失败（本地 $LOCAL_SUM / 远端 $REMOTE_SUM）"
info "上传完成并校验一致 ($LOCAL_SUM)"

# ---------- 切换 ----------
step "3/5 切换软链并重启"
# 软链切换必须是原子的：mv -T 才能覆盖「指向目录的软链」，
# 且 ln 不能直接以 current 为目标（目标不存在时 -n 不生效，会把链接建到目录里）。
# 因此统一用：先建临时软链 → mv -T 覆盖。
# service 以 zlb 用户运行，jar 必须对它可读。
rsh "set -e
  cd '$ZLB_REMOTE_DIR'
  ${SUDO}rm -f .current.next
  ${SUDO}ln -s '$REMOTE_REL' .current.next
  ${SUDO}mv -Tf .current.next current
  ${SUDO}chown -R zlb:zlb '$ZLB_REMOTE_DIR/releases/$RELEASE_ID' 2>/dev/null || true
  test -f current/app.jar

  # 历史遗留：早期是 nohup java -jar 手搓的进程，不归 systemd 管。
  # 不先杀掉，它会一直占着 8080，新服务起不来（日志报 Address already in use）。
  ${SUDO}systemctl stop '$ZLB_SERVICE' 2>/dev/null || true
  # 只匹配 java 进程，且排除本脚本自身（否则 pgrep 会把当前 shell 也匹上）
  stray=\$(pgrep -u ubuntu -f '^/usr/bin/java .*zouliangbu-server/.*\.jar' || true)
  if [[ -n \"\$stray\" ]]; then
    echo \"停止遗留进程: \$stray\"
    ${SUDO}kill \$stray 2>/dev/null || true
    for i in \$(seq 1 15); do
      pgrep -u ubuntu -f '^/usr/bin/java .*zouliangbu-server/.*\.jar' >/dev/null || break
      sleep 1
    done
    left=\$(pgrep -u ubuntu -f '^/usr/bin/java .*zouliangbu-server/.*\.jar' || true)
    [[ -n \"\$left\" ]] && ${SUDO}kill -9 \$left 2>/dev/null || true
  fi

  ${SUDO}systemctl restart '$ZLB_SERVICE'"
info "已切到 $REMOTE_REL，服务重启中"

# ---------- 健康检查 ----------
step "4/5 健康检查"
HEALTH_OK=0
for i in $(seq 1 45); do
  if rsh "curl -fsS -m 3 http://127.0.0.1:8080/api/v1/healthz 2>/dev/null | grep -q '\"status\"'" ; then
    HEALTH_OK=1
    info "健康检查通过（第 ${i} 次探测）"
    break
  fi
  sleep 2
done

if [[ "$HEALTH_OK" -ne 1 ]]; then
  warn "健康检查超时，开始自动回滚"
  PREV="$(rsh "ls -1dt '$ZLB_REMOTE_DIR'/releases/*/ 2>/dev/null | sed -n '2p'")"
  if [[ -n "$PREV" ]]; then
    rsh "cd '$ZLB_REMOTE_DIR' && ${SUDO}rm -f .current.next && ${SUDO}ln -s '${PREV%/}' .current.next && ${SUDO}mv -Tf .current.next current && ${SUDO}systemctl restart '$ZLB_SERVICE'"
    warn "已回滚到 ${PREV%/}"
  else
    warn "没有更早的版本可回滚"
  fi
  echo
  rsh "${SUDO}journalctl -u '$ZLB_SERVICE' -n 60 --no-pager" || true
  die "发布失败（已尝试回滚）"
fi

# 外部可达性（从本机探，绕过代理）
step "5/5 外部可达性"
if command -v curl >/dev/null; then
  if curl --noproxy '*' -fsS -m 8 "http://$ZLB_HOST:8080/api/v1/healthz" 2>/dev/null | grep -q ok; then
    info "公网 http://$ZLB_HOST:8080 可访问"
  else
    warn "本机访问公网 8080 失败（可能是本机代理/防火墙），服务本身已在远端检查通过"
  fi
fi

# 保留最近 5 个版本
rsh "cd '$ZLB_REMOTE_DIR/releases' && ls -1dt */ 2>/dev/null | tail -n +6 | xargs -r ${SUDO}rm -rf" || true

printf '\n%s✔ 发布完成%s: %s  →  http://%s:8080\n' "$c_grn" "$c_off" "$RELEASE_ID" "$ZLB_HOST"
printf '%s  后台 http://%s:8080/admin/login.html  日志 ssh %s "journalctl -u %s -f"%s\n\n' \
  "$c_dim" "$ZLB_HOST" "$TARGET" "$ZLB_SERVICE" "$c_off"
