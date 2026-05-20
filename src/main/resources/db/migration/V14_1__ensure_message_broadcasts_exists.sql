-- Idempotent: creates message_broadcasts tables if V14 was applied with different SQL
-- This handles checksum mismatch scenarios where V14 was modified after application

CREATE TABLE IF NOT EXISTS message_broadcasts (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id),
    structure_id        UUID        NOT NULL REFERENCES structures(id),
    message_type        VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    content             TEXT        NOT NULL,
    media_url           TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    total_groups        INT         NOT NULL DEFAULT 0,
    groups_processed    INT         NOT NULL DEFAULT 0,
    groups_successful   INT         NOT NULL DEFAULT 0,
    groups_failed       INT         NOT NULL DEFAULT 0,
    error_message       TEXT,
    scheduled_at        TIMESTAMP,
    executed_at         TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS broadcast_group_results (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcast_id        UUID        NOT NULL REFERENCES message_broadcasts(id),
    group_id            UUID        NOT NULL REFERENCES whatsapp_groups(id),
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    whatsapp_message_id VARCHAR(128),
    error_message       TEXT,
    processed_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_broadcasts_user      ON message_broadcasts(user_id);
CREATE INDEX IF NOT EXISTS idx_message_broadcasts_structure ON message_broadcasts(structure_id);
CREATE INDEX IF NOT EXISTS idx_message_broadcasts_status    ON message_broadcasts(status);
CREATE INDEX IF NOT EXISTS idx_broadcast_results_broadcast  ON broadcast_group_results(broadcast_id);
