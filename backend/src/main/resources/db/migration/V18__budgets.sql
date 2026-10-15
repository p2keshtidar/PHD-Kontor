-- Budgets je Ausgabenkategorie (monatlich) und bereits verschickte Warnungen (80 % / 100 %) je Monat.
CREATE TABLE budget (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    category       VARCHAR(30)  NOT NULL,
    monthly_cents  BIGINT       NOT NULL CHECK (monthly_cents > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget UNIQUE (user_id, category)
);

CREATE TABLE budget_alert (
    user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    category    VARCHAR(30)  NOT NULL,
    month       VARCHAR(7)   NOT NULL,
    level       INT          NOT NULL,          -- 80 oder 100
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, category, month, level)
);
