-- V7（SQLite 方言）：社区共维护提案机制
-- ① content_proposal：所有"社区贡献"的统一入口（外部导入 / 自有轨迹上传 / 线路勘误）
-- ② content_vote：提案投票（一人一票，可改票）
-- ③ route_detail_revision：线路版本快照（可回滚，替代"审核防错"）
-- ④ route_detail 补 version / curator_name / updated_at
-- 与 mysql/V7__content_proposal.sql 结构一致，仅自增主键写法不同。

-- ① 提案
CREATE TABLE content_proposal (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    type             VARCHAR(16)  NOT NULL,      -- IMPORT 外部导入 / UPLOAD 自有轨迹 / EDIT 线路勘误
    category         VARCHAR(16)  NOT NULL DEFAULT 'OBJECTIVE',  -- OBJECTIVE 客观修正 / SUBJECTIVE 主观 / PRIVACY 隐私
    target_route_id  BIGINT,
    source_url       VARCHAR(512),
    payload_json     TEXT,
    base_version     INT          NOT NULL DEFAULT 1,   -- 提案基于哪个线路版本，冲突检测用
    reason           VARCHAR(500),
    proposer_uuid    VARCHAR(64)  NOT NULL,
    proposer_name    VARCHAR(64),
    proposer_user_id BIGINT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED/MERGED/CONFLICT
    votes_up         INT          NOT NULL DEFAULT 0,
    votes_down       INT          NOT NULL DEFAULT 0,
    threshold        INT          NOT NULL DEFAULT 2,
    route_id         BIGINT,                            -- 提案落地后的线路 id
    merged_version   INT,
    reject_reason    VARCHAR(200),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at     TIMESTAMP
);
CREATE INDEX idx_proposal_status ON content_proposal (status, id);
CREATE INDEX idx_proposal_route  ON content_proposal (target_route_id, status);
CREATE INDEX idx_proposal_uuid   ON content_proposal (proposer_uuid, created_at);

-- ② 投票
CREATE TABLE content_vote (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    proposal_id BIGINT      NOT NULL,
    voter_key   VARCHAR(64) NOT NULL,
    value       INT         NOT NULL,             -- 1 认可 / -1 反对
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_vote ON content_vote (proposal_id, voter_key);

-- ③ 线路版本快照（每次合并/导入存一份完整状态，回滚 = 把旧快照写回）
CREATE TABLE route_detail_revision (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id      BIGINT      NOT NULL,
    version       INT         NOT NULL,
    snapshot_json TEXT        NOT NULL,
    proposer_name VARCHAR(64),
    reason        VARCHAR(500),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_revision ON route_detail_revision (route_id, version);

-- ④ 线路详情加版本与维护者
ALTER TABLE route_detail ADD COLUMN version      INT NOT NULL DEFAULT 1;
ALTER TABLE route_detail ADD COLUMN curator_name VARCHAR(64);
ALTER TABLE route_detail ADD COLUMN updated_at   TIMESTAMP;

-- ⑤ 历史提交单平滑迁移为 IMPORT 提案（route_submission 保留，旧后台页面不受影响）
INSERT INTO content_proposal (
    type, category, source_url, payload_json, reason, proposer_uuid,
    proposer_name, status, route_id, created_at, processed_at, threshold, base_version
)
SELECT
    'IMPORT', 'OBJECTIVE', source_url, source_route_id, submitter_note, submitter_uuid,
    NULL, status, route_id, created_at, processed_at, 2, 1
FROM route_submission;
