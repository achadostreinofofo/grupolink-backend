CREATE TABLE scheduled_messages (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    structure_id  UUID REFERENCES structures(id) ON DELETE SET NULL,
    title         VARCHAR(255) NOT NULL,
    content       TEXT NOT NULL,
    media_url     TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at  TIMESTAMP NOT NULL,
    executed_at   TIMESTAMP,
    error_message TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_messages_owner       ON scheduled_messages(owner_id);
CREATE INDEX idx_scheduled_messages_status      ON scheduled_messages(status);
CREATE INDEX idx_scheduled_messages_scheduled_at ON scheduled_messages(scheduled_at);
