-- Phase 1: Benutzer, Anmeldung, Konten und Buchungen (Pflichtenheft Kap. 6)

CREATE TABLE app_user (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(30)  NOT NULL UNIQUE,
    birth_date     DATE         NOT NULL,
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    -- wird bei Abmeldung erhöht; alte Access-Tokens werden dadurch ungültig (/F020/)
    token_version  INT          NOT NULL DEFAULT 0,
    failed_logins  INT          NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256, das Token selbst wird nie gespeichert
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- 10-stellige Kontonummern für die IBAN
CREATE SEQUENCE account_number_seq START WITH 1000000001;

CREATE TABLE account (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT       REFERENCES app_user (id),   -- NULL bei System-Konten
    type        VARCHAR(20)  NOT NULL,                   -- MAIN, SUB, SAVINGS, SYSTEM
    name        VARCHAR(100) NOT NULL,
    icon        VARCHAR(50),
    iban        VARCHAR(34)  UNIQUE,                     -- nur Hauptkonten
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_account_owner ON account (owner_id);

CREATE TABLE booking (
    id               BIGSERIAL PRIMARY KEY,
    type             VARCHAR(30)  NOT NULL,
    description      VARCHAR(140) NOT NULL,
    booked_at        TIMESTAMPTZ  NOT NULL,
    idempotency_key  VARCHAR(100) UNIQUE,
    created_by       BIGINT       REFERENCES app_user (id)
);

-- Doppelte Buchführung: die Summe aller Zeilen einer Buchung ist immer 0 (Kap. 6.3)
CREATE TABLE booking_line (
    id            BIGSERIAL PRIMARY KEY,
    booking_id    BIGINT NOT NULL REFERENCES booking (id),
    account_id    BIGINT NOT NULL REFERENCES account (id),
    amount_cents  BIGINT NOT NULL CHECK (amount_cents <> 0)
);
CREATE INDEX idx_booking_line_account ON booking_line (account_id);
CREATE INDEX idx_booking_line_booking ON booking_line (booking_id);

-- System-Konto, aus dem das Startguthaben kommt
INSERT INTO account (owner_id, type, name) VALUES (NULL, 'SYSTEM', 'BANK');
