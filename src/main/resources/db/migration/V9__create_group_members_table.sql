CREATE TABLE group_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id     UUID NOT NULL REFERENCES whatsapp_groups(id) ON DELETE CASCADE,
    phone_number VARCHAR(20),
    name         VARCHAR(255),
    cookie_id    VARCHAR(36),
    source       VARCHAR(20) NOT NULL DEFAULT 'REDIRECT',
    joined_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    left_at      TIMESTAMP
);

CREATE INDEX idx_group_members_group  ON group_members(group_id);
CREATE INDEX idx_group_members_phone  ON group_members(phone_number);
CREATE INDEX idx_group_members_active ON group_members(group_id, left_at);
CREATE INDEX idx_group_members_cookie ON group_members(cookie_id);
