-- V4（MySQL 方言）：管理后台扩展
-- 与 sqlite/V4__admin_console.sql 结构一致，仅自增主键与布尔类型写法不同。

-- ① 首页动态补字段
ALTER TABLE feed_post ADD COLUMN content TEXT NULL;
ALTER TABLE feed_post ADD COLUMN cover_author VARCHAR(40) NULL;

-- ② 系统配置（KV）
CREATE TABLE app_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value TEXT,
    note         VARCHAR(200),
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO app_config (config_key, config_value, note) VALUES
('force_update.enabled',      'false', '是否开启强更弹窗'),
('force_update.min_version',  '0',     '低于此 versionCode 强制更新'),
('force_update.url',          '',      'APK 下载地址'),
('force_update.message',      '',      '强更弹窗文案'),
('notice.enabled',            'false', '是否展示首页公告条'),
('notice.text',               '',      '公告文案'),
('notice.link',               '',      '公告跳转链接，可空');

-- ③ 操作审计
CREATE TABLE admin_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
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
