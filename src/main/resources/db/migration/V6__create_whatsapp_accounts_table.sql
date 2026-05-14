CREATE TABLE whatsapp_accounts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_number_id       VARCHAR(100) NOT NULL,
    access_token          TEXT NOT NULL,
    display_name          VARCHAR(255),
    business_account_id   VARCHAR(100),
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_whatsapp_accounts_owner  ON whatsapp_accounts(owner_id);
CREATE INDEX idx_whatsapp_accounts_phone  ON whatsapp_accounts(phone_number_id);
