CREATE TABLE admin_users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) UNIQUE NOT NULL,
    name       VARCHAR(255)        NOT NULL,
    password_hash VARCHAR(255)     NOT NULL,
    created_at TIMESTAMP NOT NULL  DEFAULT NOW()
);

CREATE INDEX idx_admin_users_email ON admin_users(email);
