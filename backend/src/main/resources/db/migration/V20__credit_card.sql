-- Virtuelle Kreditkarte (Simulation mit Spielgeld): Umsätze sammeln sich im Monat und werden am Monatsersten
-- vom Girokonto eingezogen. Es gibt keine echte Karte und keine Kreditprüfung.
CREATE TABLE credit_card (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL REFERENCES app_user (id),
    masked_number        VARCHAR(19)   NOT NULL,
    limit_cents          BIGINT        NOT NULL CHECK (limit_cents > 0),
    foreign_fee_percent  NUMERIC(4, 2) NOT NULL,
    status               VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, BLOCKED, CLOSED
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_credit_card_open ON credit_card (user_id) WHERE status <> 'CLOSED';

CREATE TABLE credit_card_statement (
    id           BIGSERIAL PRIMARY KEY,
    card_id      BIGINT       NOT NULL REFERENCES credit_card (id),
    period       VARCHAR(7)   NOT NULL,
    total_cents  BIGINT       NOT NULL,
    fee_cents    BIGINT       NOT NULL,
    status       VARCHAR(10)  NOT NULL,          -- OPEN (noch nicht eingezogen), PAID
    booking_id   BIGINT REFERENCES booking (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at      TIMESTAMPTZ,
    CONSTRAINT uq_credit_card_statement UNIQUE (card_id, period)
);

CREATE TABLE credit_card_tx (
    id            BIGSERIAL PRIMARY KEY,
    card_id       BIGINT       NOT NULL REFERENCES credit_card (id),
    booked_at     TIMESTAMPTZ  NOT NULL,
    merchant      VARCHAR(100) NOT NULL,
    amount_cents  BIGINT       NOT NULL CHECK (amount_cents > 0),
    fee_cents     BIGINT       NOT NULL DEFAULT 0,
    foreign_currency BOOLEAN   NOT NULL DEFAULT FALSE,
    statement_id  BIGINT REFERENCES credit_card_statement (id)
);
CREATE INDEX idx_credit_card_tx_card ON credit_card_tx (card_id, statement_id);
