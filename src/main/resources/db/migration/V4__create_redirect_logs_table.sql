CREATE TABLE redirect_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    structure_id  UUID NOT NULL REFERENCES structures(id) ON DELETE CASCADE,
    group_id      UUID NOT NULL REFERENCES whatsapp_groups(id) ON DELETE CASCADE,
    cookie_id     VARCHAR(36)  NOT NULL,
    ip_address    VARCHAR(45),
    user_agent    TEXT,
    utm_source    VARCHAR(255),
    utm_medium    VARCHAR(255),
    utm_campaign  VARCHAR(255),
    utm_content   VARCHAR(255),
    utm_term      VARCHAR(255),
    redirected_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_redirect_logs_cookie        ON redirect_logs(cookie_id);
CREATE INDEX idx_redirect_logs_structure     ON redirect_logs(structure_id);
CREATE INDEX idx_redirect_logs_group         ON redirect_logs(group_id);
CREATE INDEX idx_redirect_logs_redirected_at ON redirect_logs(redirected_at);
