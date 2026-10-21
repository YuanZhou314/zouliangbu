-- V7（MySQL 方言）：社区共维护提案机制
-- 结构与 sqlite/V7__content_proposal.sql 一致，仅主键与引擎写法不同。

CREATE TABLE content_proposal (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    type             VARCHAR(16)  NOT NULL,
    category         VARCHAR(16)  NOT NULL DEFAULT 'OBJECTIVE',
    target_route_id  BIGINT,
    source_url       VARCHAR(512),
    payload_json     TEXT,
    base_version     INT          NOT NULL DEFAULT 1,
    reason           VARCHAR(500),
    proposer_uuid    VARCHAR(64)  NOT NULL,
    proposer_name    VARCHAR(64),
    proposer_user_id BIGINT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    votes_up         INT          NOT NULL DEFAULT 0,
    votes_down       INT          NOT NULL DEFAULT 0,
    threshold        INT          NOT NULL DEFAULT 2,
    route_id         BIGINT,
    merged_version   INT,
    reject_reason    VARCHAR(200),
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at     DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_proposal_status ON content_proposal (status, id);
CREATE INDEX idx_proposal_route  ON content_proposal (target_route_id, status);
CREATE INDEX idx_proposal_uuid   ON content_proposal (proposer_uuid, created_at);

CREATE TABLE content_vote (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_id BIGINT      NOT NULL,
    voter_key   VARCHAR(64) NOT NULL,
    value       INT         NOT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_vote ON content_vote (proposal_id, voter_key);

CREATE TABLE route_detail_revision (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id      BIGINT      NOT NULL,
    version       INT         NOT NULL,
    snapshot_json TEXT        NOT NULL,
    proposer_name VARCHAR(64),
    reason        VARCHAR(500),
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE UNIQUE INDEX uk_revision ON route_detail_revision (route_id, version);

ALTER TABLE route_detail ADD COLUMN version      INT NOT NULL DEFAULT 1;
ALTER TABLE route_detail ADD COLUMN curator_name VARCHAR(64);
ALTER TABLE route_detail ADD COLUMN updated_at   DATETIME;

INSERT INTO content_proposal (
    type, category, source_url, payload_json, reason, proposer_uuid,
    proposer_name, status, route_id, created_at, processed_at, threshold, base_version
)
SELECT
    'IMPORT', 'OBJECTIVE', source_url, source_route_id, submitter_note, submitter_uuid,
    NULL, status, route_id, created_at, processed_at, 2, 1
FROM route_submission;
