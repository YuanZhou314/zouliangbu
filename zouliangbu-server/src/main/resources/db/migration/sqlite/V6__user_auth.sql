-- V6（SQLite 方言）：用户表（账号体系第一步，用户名/邮箱 + 密码登录）
-- 与 mysql/V6__user_auth.sql 结构一致，仅自增主键写法不同。
CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    email         VARCHAR(120) UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
