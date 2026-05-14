CREATE TABLE short_links (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code        VARCHAR(100) NOT NULL UNIQUE,
    target_url  TEXT NOT NULL,
    title       VARCHAR(255),
    clicks      BIGINT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_short_links_code  ON short_links(code);
CREATE INDEX idx_short_links_owner ON short_links(owner_id);
