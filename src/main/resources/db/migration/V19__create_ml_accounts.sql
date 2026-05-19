CREATE TABLE ml_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ml_user_id VARCHAR(64) NOT NULL,
    ml_nickname VARCHAR(128),
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id)
);

CREATE INDEX idx_ml_accounts_owner ON ml_accounts(owner_id);
