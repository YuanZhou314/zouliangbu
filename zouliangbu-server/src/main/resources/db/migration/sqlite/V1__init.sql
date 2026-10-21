-- V1（SQLite 方言）：内容层四张表
-- 与 mysql/V1__init.sql 结构一致，仅主键写法不同：
--   MySQL  BIGINT AUTO_INCREMENT PRIMARY KEY
--   SQLite INTEGER PRIMARY KEY AUTOINCREMENT（必须 INTEGER，BIGINT 不会自增）

CREATE TABLE feed_post (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
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
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    title       VARCHAR(40) NOT NULL,
    subtitle    VARCHAR(80),
    sort_weight INT NOT NULL DEFAULT 0
);

CREATE TABLE route (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
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
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        VARCHAR(16) NOT NULL,
    name        VARCHAR(32) NOT NULL,
    note        VARCHAR(64),
    icon_key    VARCHAR(32),
    sort_weight INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_gear_code ON gear_category (code);
