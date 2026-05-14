CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan                VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    mercado_pago_id     VARCHAR(100) UNIQUE,
    payer_email         VARCHAR(255),
    amount              NUMERIC(10, 2),
    period_start_date   TIMESTAMP,
    period_end_date     TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_owner  ON subscriptions(owner_id);
CREATE INDEX idx_subscriptions_mp_id  ON subscriptions(mercado_pago_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
