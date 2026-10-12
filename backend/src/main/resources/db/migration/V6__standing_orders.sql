-- /K030/ Daueraufträge: regelmäßige Überweisungen an andere Nutzer
CREATE TABLE standing_order (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES app_user (id),
    recipient_iban       VARCHAR(34)  NOT NULL,
    recipient_name       VARCHAR(120) NOT NULL,
    amount_cents         BIGINT       NOT NULL CHECK (amount_cents > 0),
    reference            VARCHAR(140) NOT NULL,
    execution_day        INT          NOT NULL CHECK (execution_day BETWEEN 1 AND 28),
    interval_months      INT          NOT NULL CHECK (interval_months IN (1, 3, 6, 12)),
    start_date           DATE         NOT NULL,          -- erste Ausführung
    end_date             DATE,                           -- letzte mögliche Ausführung (optional)
    status               VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, PAUSED, ENDED
    last_executed_month  VARCHAR(7),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_standing_order_user ON standing_order (user_id);
CREATE INDEX idx_standing_order_day ON standing_order (status, execution_day);
