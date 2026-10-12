-- /K040/ CSV-Import: Umsätze einer anderen (echten) Bank für den Finanzüberblick.
-- Die importierten Umsätze sind KEINE Buchungen: Sie verändern keine Kontostände,
-- fließen aber in Einnahmen, Ausgaben und Kategorien ein.
CREATE TABLE csv_import (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    file_name      VARCHAR(200) NOT NULL,
    format         VARCHAR(60)  NOT NULL,
    rows_imported  INT          NOT NULL,
    rows_skipped   INT          NOT NULL,
    first_date     DATE,
    last_date      DATE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_csv_import_user ON csv_import (user_id);

CREATE TABLE imported_transaction (
    id             BIGSERIAL PRIMARY KEY,
    import_id      BIGINT       NOT NULL REFERENCES csv_import (id) ON DELETE CASCADE,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    booked_on      DATE         NOT NULL,
    amount_cents   BIGINT       NOT NULL,
    counterparty   VARCHAR(200),
    description    VARCHAR(400),
    category       VARCHAR(30)  NOT NULL,
    dedup_key      VARCHAR(64)  NOT NULL,          -- SHA-256: gleiche Zeile nicht doppelt importieren
    CONSTRAINT uq_imported_transaction UNIQUE (user_id, dedup_key)
);
CREATE INDEX idx_imported_transaction_user_date ON imported_transaction (user_id, booked_on);
