-- V3: 路线内容平台（提交/审批 + 抓取解析的轨迹与标注）
-- 关联 route 表（原有 12 条种子数据不动）。
-- 坐标系：轨迹/标注点存 GCJ-02（抓取后已转换），与 App 全链路一致。

-- 1. 线路详情扩展（正文 + 交通 + 分类 + 来源）
CREATE TABLE route_detail (
    route_id           BIGINT PRIMARY KEY,
    intro_html         TEXT,
    content_html       TEXT,
    traffic_html       TEXT,
    difficulty         VARCHAR(16),
    risk               VARCHAR(16),
    suitable_months    VARCHAR(32),
    road_type          VARCHAR(32),
    view_type          VARCHAR(32),
    duration_name      VARCHAR(16),
    track_source_id    BIGINT,
    source_url         VARCHAR(512) NOT NULL,
    source_name        VARCHAR(32) NOT NULL DEFAULT '两步路',
    source_author      VARCHAR(64),
    parsed_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 轨迹点（GCJ-02，已转换）
CREATE TABLE route_track_point (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id           BIGINT NOT NULL,
    seq                INT    NOT NULL,
    lat                DOUBLE NOT NULL,
    lng                DOUBLE NOT NULL,
    altitude           DOUBLE,
    speed              DOUBLE,
    track_time         BIGINT
);
CREATE INDEX idx_track_point_route ON route_track_point (route_id, seq);

-- 3. 标注点（GCJ-02，已转换）
CREATE TABLE route_marker (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id           BIGINT NOT NULL,
    seq                INT    NOT NULL,
    name               VARCHAR(64),
    lat                DOUBLE NOT NULL,
    lng                DOUBLE NOT NULL,
    altitude           DOUBLE,
    marker_time        BIGINT
);
CREATE INDEX idx_route_marker_route ON route_marker (route_id, seq);

-- 4. 用户提交的路线链接 + 审批状态
CREATE TABLE route_submission (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_url         VARCHAR(512) NOT NULL,
    source_route_id    VARCHAR(32),
    submitter_uuid     VARCHAR(64)  NOT NULL,
    submitter_note     VARCHAR(200),
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reject_reason      VARCHAR(200),
    route_id           BIGINT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at       TIMESTAMP
);
CREATE INDEX idx_submission_status ON route_submission (status, id);
CREATE INDEX idx_submission_uuid   ON route_submission (submitter_uuid);
