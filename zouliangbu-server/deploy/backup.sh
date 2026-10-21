#!/usr/bin/env bash
# SQLite + 上传目录在线备份：sqlite3 .backup 保证一致性（不锁写、不拷到写一半的状态）。
#
# crontab 示例（每天 03:10 备份，保留 7 份）：
#   10 3 * * * /opt/zouliangbu-server/deploy/backup.sh >> /opt/zouliangbu-server/backups/backup.log 2>&1
#
# 变量（可从 zlb.env source 或环境变量覆盖）：
#   SQLITE_PATH  SQLite 库文件路径   默认 ./zlb-dev.db
#   UPLOAD_DIR   上传图片目录        默认 ./uploads
#   BACKUP_DIR   备份输出目录        默认 ./backups

set -euo pipefail

SQLITE_PATH="${SQLITE_PATH:-./zlb-dev.db}"
UPLOAD_DIR="${UPLOAD_DIR:-./uploads}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP=7

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_off=$'\033[0m'
die() { printf '%s[x]%s %s\n' "$c_red" "$c_off" "$*" >&2; exit 1; }
info() { printf '%s==>%s %s\n' "$c_grn" "$c_off" "$*"; }

[[ -f "$SQLITE_PATH" ]] || die "数据库文件不存在: $SQLITE_PATH"
command -v sqlite3 >/dev/null || die "找不到 sqlite3（apt install sqlite3 / yum install sqlite）"

TS="$(date +%Y%m%d-%H%M%S)"
HOST_TAG="$(hostname -s 2>/dev/null || echo host)"
STAMP="${HOST_TAG}-${TS}"
mkdir -p "$BACKUP_DIR"

# 1) 数据库：.backup 命令在线一致性快照
DB_DEST="$BACKUP_DIR/db-$STAMP.db"
sqlite3 "$SQLITE_PATH" ".backup '$DB_DEST'" || die "sqlite3 .backup 失败"
info "数据库已备份: $DB_DEST ($(du -h "$DB_DEST" | cut -f1))"

# 2) 上传目录：tar 打包（目录不存在则跳过）
if [[ -d "$UPLOAD_DIR" ]]; then
  UP_DEST="$BACKUP_DIR/uploads-$STAMP.tar.gz"
  tar -czf "$UP_DEST" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
  info "上传目录已备份: $UP_DEST ($(du -h "$UP_DEST" | cut -f1))"
else
  info "上传目录 $UPLOAD_DIR 不存在，跳过"
fi

# 3) 只保留最近 KEEP 份（按时间排序，删掉更旧的；db 与 uploads 成对清理用同一 STAMP）
ls -1t "$BACKUP_DIR"/db-*.db 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
  rm -f "$old"
  old_up="${old/db-/uploads-}"; old_up="${old_up%.db}.tar.gz"
  rm -f "$old_up"
done

info "备份完成，当前保留 $(ls -1 "$BACKUP_DIR"/db-*.db 2>/dev/null | wc -l) 份"
