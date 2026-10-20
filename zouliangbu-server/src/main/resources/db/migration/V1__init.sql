-- V1: 内容层四张表（首页动态 / 线路 / 线路分组 / 装备分类）
-- 注意：DDL 必须同时能在 MySQL 8 与 H2(MODE=MySQL) 上执行，因此不写 ENGINE/CHARSET 子句
-- （字符集在库级设置：CREATE DATABASE ... DEFAULT CHARSET utf8mb4）。

CREATE TABLE feed_post (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    title              VARCHAR(120) NOT NULL,
    author             VARCHAR(40)  NOT NULL,
    like_count         INT          NOT NULL DEFAULT 0,
    city               VARCHAR(32)  NOT NULL,
    cover_url          VARCHAR(512),
    cover_aspect_ratio DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    status             INT          NOT NULL DEFAULT 1,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_feed_city_created ON feed_post (city, created_at);
CREATE INDEX idx_feed_status_id ON feed_post (status, id);

CREATE TABLE route_section (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(40) NOT NULL,
    subtitle    VARCHAR(80),
    sort_weight INT NOT NULL DEFAULT 0
);

CREATE TABLE route (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    region        VARCHAR(32)  NOT NULL,
    distance_km   DECIMAL(6,1) NOT NULL,
    ascent_meters INT          NOT NULL DEFAULT 0,
    cover_url     VARCHAR(512),
    tags          VARCHAR(200),
    section_id    BIGINT,
    is_recommend  BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_weight   INT          NOT NULL DEFAULT 0,
    status        INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_route_section ON route (section_id, sort_weight);
CREATE INDEX idx_route_region ON route (region);
CREATE INDEX idx_route_recommend ON route (is_recommend, sort_weight);

CREATE TABLE gear_category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(16) NOT NULL,
    name        VARCHAR(32) NOT NULL,
    note        VARCHAR(64),
    icon_key    VARCHAR(32),
    sort_weight INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_gear_code ON gear_category (code);
