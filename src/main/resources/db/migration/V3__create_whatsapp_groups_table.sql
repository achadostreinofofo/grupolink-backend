CREATE TABLE whatsapp_groups (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    structure_id        UUID NOT NULL REFERENCES structures(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    whatsapp_group_id   VARCHAR(255),
    invite_link         TEXT,
    max_members         INT  NOT NULL DEFAULT 256,
    member_count        INT  NOT NULL DEFAULT 0,
    click_count         BIGINT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order          INT  NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_whatsapp_groups_structure ON whatsapp_groups(structure_id);
CREATE INDEX idx_whatsapp_groups_status    ON whatsapp_groups(status);
