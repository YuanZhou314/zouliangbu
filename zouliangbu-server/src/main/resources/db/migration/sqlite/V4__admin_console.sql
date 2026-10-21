-- V4（SQLite 方言）：管理后台扩展
-- 内容：① feed_post 补正文/封面作者（首页动态管理需要） ② 系统配置表 ③ 操作审计表
-- 与 mysql/V4__admin_console.sql 结构一致，仅自增主键与布尔类型写法不同。

-- ① 首页动态补字段：后台要能编辑正文与封面图作者（署名不等于发帖人）
ALTER TABLE feed_post ADD COLUMN content TEXT;
ALTER TABLE feed_post ADD COLUMN cover_author VARCHAR(40);

-- ② 系统配置：强更 / 公告这类"后台改、App 读"的开关，用 KV 表避免每加一个开关就加一列
CREATE TABLE app_config (
    config_key  VARCHAR(64) PRIMARY KEY,
    config_value TEXT,
    note        VARCHAR(200),
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_config (config_key, config_value, note) VALUES
('force_update.enabled',      'false', '是否开启强更弹窗'),
('force_update.min_version',  '0',     '低于此 versionCode 强制更新'),
('force_update.url',          '',      'APK 下载地址'),
('force_update.message',      '',      '强更弹窗文案'),
('notice.enabled',            'false', '是否展示首页公告条'),
('notice.text',               '',      '公告文案'),
('notice.link',               '',      '公告跳转链接，可空');

-- ③ 操作审计：谁在什么时候改了什么。detail 存参数摘要（正文类截断），防止审计表被正文撑爆
CREATE TABLE admin_audit (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    operator     VARCHAR(64)  NOT NULL,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(32),
    target_id    VARCHAR(64),
    detail       VARCHAR(500),
    ip           VARCHAR(64),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_created ON admin_audit (created_at);
CREATE INDEX idx_audit_action  ON admin_audit (action, id);
