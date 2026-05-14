CREATE TABLE structures (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                  VARCHAR(255) NOT NULL,
    slug                  VARCHAR(100) NOT NULL UNIQUE,
    description           TEXT,
    max_members_per_group INT  NOT NULL DEFAULT 256,
    fill_threshold        DOUBLE PRECISION NOT NULL DEFAULT 0.80,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_structures_owner ON structures(owner_id);
CREATE INDEX idx_structures_slug  ON structures(slug);
