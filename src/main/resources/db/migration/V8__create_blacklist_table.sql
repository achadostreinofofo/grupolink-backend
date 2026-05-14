CREATE TABLE blacklist (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_number VARCHAR(20) NOT NULL,
    reason       TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blacklist_owner ON blacklist(owner_id);
CREATE INDEX idx_blacklist_phone ON blacklist(phone_number);
CREATE UNIQUE INDEX idx_blacklist_owner_phone ON blacklist(owner_id, phone_number);
