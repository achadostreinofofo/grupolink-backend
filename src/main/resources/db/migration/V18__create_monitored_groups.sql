-- Configuração de grupos WhatsApp monitorados.
-- Mensagens recebidas que contiverem um link "https://meli.la/..." (filtradas no
-- whatsapp-service via Baileys) são redistribuídas para os grupos da estrutura
-- vinculada. Mensagens não são armazenadas — apenas reencaminhadas.

CREATE TABLE monitored_groups (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id              UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    web_session_id        UUID         NOT NULL REFERENCES whatsapp_web_sessions(id) ON DELETE CASCADE,
    structure_id          UUID         NOT NULL REFERENCES structures(id) ON DELETE CASCADE,

    -- JID do grupo monitorado no WhatsApp (ex: "120363019483@g.us")
    whatsapp_group_id     VARCHAR(255) NOT NULL,
    -- Nome amigável (cache do nome do grupo na hora da configuração)
    whatsapp_group_name   VARCHAR(255),

    -- Prefixo opcional adicionado no início de cada mensagem reencaminhada
    -- Ex: "🔥 PROMO " resulta em "🔥 PROMO {mensagem original}"
    message_prefix        TEXT,

    active                BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Garante que o mesmo grupo WhatsApp não é monitorado duas vezes na mesma sessão
    CONSTRAINT uk_monitored_group UNIQUE (web_session_id, whatsapp_group_id)
);

CREATE INDEX idx_monitored_groups_owner       ON monitored_groups(owner_id);
CREATE INDEX idx_monitored_groups_session     ON monitored_groups(web_session_id);
CREATE INDEX idx_monitored_groups_lookup      ON monitored_groups(web_session_id, whatsapp_group_id) WHERE active = TRUE;
