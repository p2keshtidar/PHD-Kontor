-- Phase 4: Finanzüberblick, Spar- und Investpläne, Kredite, Simulationszeit (Pflichtenheft 0.2)

-- Kategorie je Buchungszeile (/F560/); jede Zeile ist die Sicht eines Kontos
ALTER TABLE booking_line ADD COLUMN category VARCHAR(30);
UPDATE booking_line l SET category = CASE
        WHEN b.type = 'INITIAL'  THEN 'STARTGUTHABEN'
        WHEN b.type = 'INTERNAL' THEN 'UMBUCHUNG'
        WHEN b.type = 'TRANSFER' AND l.amount_cents > 0 THEN 'SONSTIGE_EINNAHMEN'
        ELSE 'SONSTIGES' END
  FROM booking b WHERE b.id = l.booking_id;

-- Verschiebbare Uhr für den Demo-Modus (/F950/)
CREATE TABLE sim_clock (
    id           INT PRIMARY KEY,
    offset_days  INT NOT NULL DEFAULT 0
);
INSERT INTO sim_clock (id, offset_days) VALUES (1, 0);

-- Jeder Tageslauf wird genau einmal ausgeführt
CREATE TABLE job_run (
    run_date     DATE PRIMARY KEY,
    finished_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE demo_profile (
    user_id     BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    profile     VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE savings_plan (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES app_user (id),
    account_id           BIGINT       REFERENCES account (id),      -- Unterkonto (nur Sparplan)
    type                 VARCHAR(10)  NOT NULL,                     -- GOAL, INVEST
    name                 VARCHAR(60)  NOT NULL,
    icon                 VARCHAR(30),
    target_cents         BIGINT,
    target_date          DATE,
    monthly_rate_cents   BIGINT       NOT NULL,
    execution_day        INT          NOT NULL,
    risk_level           VARCHAR(12)  NOT NULL,
    status               VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE, PAUSED, COMPLETED, CLOSED
    deposited_cents      BIGINT       NOT NULL DEFAULT 0,
    last_executed_month  VARCHAR(7),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_savings_plan_user ON savings_plan (user_id);

CREATE TABLE depot_position (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT        NOT NULL REFERENCES savings_plan (id),
    fund_code       VARCHAR(20)   NOT NULL,
    units           NUMERIC(18,6) NOT NULL DEFAULT 0,
    invested_cents  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_depot_position UNIQUE (plan_id, fund_code)
);

CREATE TABLE plan_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    plan_id          BIGINT NOT NULL REFERENCES savings_plan (id),
    snapshot_date    DATE   NOT NULL,
    value_cents      BIGINT NOT NULL,
    deposited_cents  BIGINT NOT NULL
);
CREATE INDEX idx_plan_snapshot_plan ON plan_snapshot (plan_id, snapshot_date);

CREATE TABLE loan (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id),
    account_id          BIGINT       NOT NULL REFERENCES account (id),   -- Kreditkonto (Typ LOAN)
    bank_code           VARCHAR(20)  NOT NULL,
    bank_name           VARCHAR(60)  NOT NULL,
    purpose             VARCHAR(20)  NOT NULL,
    principal_cents     BIGINT       NOT NULL,
    equity_cents        BIGINT       NOT NULL DEFAULT 0,
    interest_rate       NUMERIC(6,4) NOT NULL,                           -- Prozent p. a., z. B. 5.5900
    term_months         INT          NOT NULL,
    monthly_rate_cents  BIGINT       NOT NULL,
    start_date          DATE         NOT NULL,
    next_due_date       DATE         NOT NULL,
    paid_installments   INT          NOT NULL DEFAULT 0,
    status              VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE, PAID_OFF
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_loan_user ON loan (user_id);

-- weitere System-Konten
INSERT INTO account (owner_id, type, name) VALUES
    (NULL, 'SYSTEM', 'DEPOT'),
    (NULL, 'SYSTEM', 'ZINSEN'),
    (NULL, 'SYSTEM', 'ARBEITGEBER'),
    (NULL, 'SYSTEM', 'LASTSCHRIFT');
