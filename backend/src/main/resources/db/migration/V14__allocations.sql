-- Sparregeln (Geldzuweisungen) für digitale Konten: regelmäßig (wöchentlich, alle 2 Wochen, monatlich,
-- vierteljährlich) einen festen Betrag zuweisen.
--
-- Finanzierungsquelle PROJEKTBANK: echte Umbuchung im Hauptbuch der Projektbank (Simulation)
--   vom eigenen Girokonto in das digitale Konto (Buchungsart INTERNAL, keine Überweisung).
-- Finanzierungsquelle EXTERNAL: ein verbundenes Bankkonto (Open Banking, nur Lesezugriff).
--   Es wird KEIN Geld bewegt und KEINE Zahlung ausgelöst. Der Betrag wird nur vorgemerkt:
--   Das Geld bleibt bei der Bank, die App rechnet es dem Ziel zu (Deckung wird vorher geprüft).

CREATE TABLE allocation_rule (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id),      -- wer die Regel angelegt hat
    target_account_id   BIGINT       NOT NULL REFERENCES account (id),       -- digitales Konto
    source_type         VARCHAR(12)  NOT NULL,
    source_account_id   BIGINT       REFERENCES account (id),                -- Girokonto (PROJEKTBANK)
    source_external_id  BIGINT       REFERENCES external_account (id) ON DELETE SET NULL,   -- (EXTERNAL)
    amount_cents        BIGINT       NOT NULL CHECK (amount_cents > 0),
    interval_unit       VARCHAR(10)  NOT NULL,
    start_date          DATE         NOT NULL,           -- erste Ausführung, zugleich Bezug für das Intervall
    end_date            DATE,                            -- letzte mögliche Ausführung (optional)
    next_date           DATE,                            -- nächste Fälligkeit; NULL = keine mehr
    status              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    status_reason       VARCHAR(200),                    -- z. B. warum automatisch pausiert
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alloc_rule_source CHECK (source_type IN ('PROJEKTBANK', 'EXTERNAL')),
    CONSTRAINT ck_alloc_rule_interval CHECK (interval_unit IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY')),
    CONSTRAINT ck_alloc_rule_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    CONSTRAINT ck_alloc_rule_end CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_alloc_rule_user ON allocation_rule (user_id);
CREATE INDEX idx_alloc_rule_target ON allocation_rule (target_account_id);
CREATE INDEX idx_alloc_rule_due ON allocation_rule (status, next_date);

-- Jeder Vorgang (geplant, manuell, Vormerkung aufheben) mit Status. Eine Fälligkeit einer Regel gibt es
-- genau einmal (uq_alloc_run) – zusammen mit dem Idempotenz-Schlüssel der Buchung verhindert das doppelte
-- Abbuchungen, auch bei Wiederholungen nach einem Fehler.
CREATE TABLE allocation_run (
    id                  BIGSERIAL PRIMARY KEY,
    rule_id             BIGINT       REFERENCES allocation_rule (id),        -- NULL = manuell
    user_id             BIGINT       NOT NULL REFERENCES app_user (id),
    target_account_id   BIGINT       NOT NULL REFERENCES account (id),
    source_type         VARCHAR(12)  NOT NULL,
    source_external_id  BIGINT       REFERENCES external_account (id) ON DELETE SET NULL,
    kind                VARCHAR(10)  NOT NULL,           -- TRANSFER, EARMARK, RELEASE
    due_date            DATE         NOT NULL,
    amount_cents        BIGINT       NOT NULL CHECK (amount_cents > 0),
    status              VARCHAR(10)  NOT NULL,           -- PENDING, COMPLETED, RETRYING, FAILED, CANCELLED
    attempts            INT          NOT NULL DEFAULT 0,
    next_attempt        DATE,                            -- nur bei PENDING und RETRYING
    reason              VARCHAR(200),                    -- Grund für Fehler (ohne Bankdaten)
    booking_id          BIGINT       REFERENCES booking (id),
    manual              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_alloc_run UNIQUE (rule_id, due_date),
    CONSTRAINT ck_alloc_run_kind CHECK (kind IN ('TRANSFER', 'EARMARK', 'RELEASE')),
    CONSTRAINT ck_alloc_run_status CHECK (status IN ('PENDING', 'COMPLETED', 'RETRYING', 'FAILED', 'CANCELLED'))
);
CREATE INDEX idx_alloc_run_target ON allocation_run (target_account_id);
CREATE INDEX idx_alloc_run_user ON allocation_run (user_id);
CREATE INDEX idx_alloc_run_retry ON allocation_run (status, next_attempt);
CREATE INDEX idx_alloc_run_source ON allocation_run (source_external_id);
