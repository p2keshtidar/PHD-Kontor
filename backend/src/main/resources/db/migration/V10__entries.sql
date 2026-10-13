-- Eigene Einnahmen und Ausgaben (Gehalt, Miete, Urlaub …): einmalig oder regelmäßig.
-- Gebucht wird auf das Girokonto gegen das System-Konto EXTERN ("Geld von/nach außerhalb der Bank").
CREATE TABLE entry (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user (id),
    kind             VARCHAR(10)  NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    title            VARCHAR(60)  NOT NULL,
    category         VARCHAR(30)  NOT NULL,
    amount_cents     BIGINT       NOT NULL CHECK (amount_cents > 0),
    interval_months  INT          NOT NULL CHECK (interval_months IN (0, 1, 3, 6, 12)),  -- 0 = einmalig
    start_date       DATE         NOT NULL,          -- einmalig: das Datum; regelmäßig: erste Buchung
    end_date         DATE,                           -- regelmäßig: letzte mögliche Buchung (optional)
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, PAUSED, DONE, DELETED
    last_booked_date DATE,
    booked_count     INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_entry_user ON entry (user_id);
CREATE INDEX idx_entry_status ON entry (status);

INSERT INTO account (owner_id, type, name) VALUES (NULL, 'SYSTEM', 'EXTERN');
