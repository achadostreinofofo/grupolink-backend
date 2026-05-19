CREATE TABLE whatsapp_web_sessions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID        NOT NULL REFERENCES users(id),
    session_id  VARCHAR(64) NOT NULL UNIQUE,
    status      VARCHAR(30) NOT NULL DEFAULT 'WAITING_SCAN',
    phone       VARCHAR(20),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_whatsapp_web_sessions_owner ON whatsapp_web_sessions(owner_id);
CREATE INDEX idx_whatsapp_web_sessions_session_id ON whatsapp_web_sessions(session_id);
