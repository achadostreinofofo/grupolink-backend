ALTER TABLE users ADD COLUMN email_verified       BOOLEAN   NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verify_token  VARCHAR(36);
ALTER TABLE users ADD COLUMN email_verify_exp    TIMESTAMP;

CREATE INDEX idx_users_email_verify_token ON users (email_verify_token) WHERE email_verify_token IS NOT NULL;
