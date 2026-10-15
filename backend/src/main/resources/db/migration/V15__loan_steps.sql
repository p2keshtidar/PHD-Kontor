-- Kreditvergleich mit mehreren Anbietern und getrennten Schritten:
--   1. Vergleich (unverbindliche Rechenbeispiele, nichts wird gespeichert)
--   2. Kreditanfrage  → Tabelle loan_application (Zusage oder Ablehnung, Zusage befristet)
--   3. Kreditabschluss → loan mit Status CONTRACTED (Vertrag, noch KEIN Geld geflossen)
--   4. Auszahlung     → Buchung LOAN_PAYOUT, Status ACTIVE
-- Alle Banken sind fiktiv (Simulation der Projektbank); echte Kreditangebote sind nicht angebunden.

CREATE TABLE loan_application (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id),
    provider            VARCHAR(20)  NOT NULL,              -- z. B. SIMULATION
    bank_code           VARCHAR(20)  NOT NULL,
    bank_name           VARCHAR(60)  NOT NULL,
    purpose             VARCHAR(20)  NOT NULL,
    principal_cents     BIGINT       NOT NULL CHECK (principal_cents > 0),
    equity_cents        BIGINT       NOT NULL DEFAULT 0,
    term_months         INT          NOT NULL CHECK (term_months > 0),
    credit_class        VARCHAR(10)  NOT NULL,              -- Selbsteinschätzung: SEHR_GUT, GUT, MITTEL, SCHWACH
    interest_rate       NUMERIC(6,4),                       -- gebundener Sollzins p. a. (nur bei Zusage)
    effective_rate      NUMERIC(6,4),                       -- effektiver Jahreszins
    monthly_rate_cents  BIGINT,
    total_cents         BIGINT,                             -- Gesamtbetrag (Kredit + Zinsen + Kosten)
    status              VARCHAR(12)  NOT NULL,              -- APPROVED, DECLINED, ACCEPTED, WITHDRAWN, EXPIRED
    decision_reason     VARCHAR(300),
    valid_until         DATE,                               -- Zusage gilt bis (einschließlich)
    loan_id             BIGINT       REFERENCES loan (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at          TIMESTAMPTZ,
    accepted_at         TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_loan_application_status
        CHECK (status IN ('APPROVED', 'DECLINED', 'ACCEPTED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT ck_loan_application_class CHECK (credit_class IN ('SEHR_GUT', 'GUT', 'MITTEL', 'SCHWACH'))
);
CREATE INDEX idx_loan_application_user ON loan_application (user_id);
CREATE INDEX idx_loan_application_status ON loan_application (status, valid_until);

-- Vertrag und Auszahlung getrennt: next_due_date erst ab der Auszahlung
ALTER TABLE loan ADD COLUMN application_id BIGINT REFERENCES loan_application (id);
ALTER TABLE loan ADD COLUMN effective_rate NUMERIC(6,4);
ALTER TABLE loan ADD COLUMN contracted_at TIMESTAMPTZ;
ALTER TABLE loan ADD COLUMN paid_out_at TIMESTAMPTZ;
ALTER TABLE loan ALTER COLUMN next_due_date DROP NOT NULL;
UPDATE loan SET contracted_at = created_at, paid_out_at = created_at;

-- Durchschnittszinsen der Deutschen Bundesbank (MFI-Zinsstatistik) als Referenz – keine Angebote.
-- Letzter erfolgreicher Abruf je Zeitreihe; bleibt bei Fehlern erhalten und wird dann als veraltet markiert.
CREATE TABLE market_rate (
    series_key    VARCHAR(40)  PRIMARY KEY,
    period        VARCHAR(7)   NOT NULL,                    -- Berichtsmonat, z. B. 2026-08
    rate_percent  NUMERIC(6,3) NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL
);
