-- Mehrere echte Bankkonten über Open Banking (PSD2, Kontoinformationsdienst) verbinden.
-- Es werden NIE Bank-Passwörter gespeichert: Die Anmeldung erfolgt bei der Bank (Weiterleitung),
-- gespeichert wird nur die Kennung der Freigabe beim Anbieter – verschlüsselt (AES-256-GCM).

CREATE TABLE bank_connection (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id),
    provider            VARCHAR(30)  NOT NULL,              -- z. B. SANDBOX, GOCARDLESS
    institution_id      VARCHAR(100) NOT NULL,
    institution_name    VARCHAR(150) NOT NULL,
    status              VARCHAR(20)  NOT NULL,              -- PENDING, ACTIVE, EXPIRED, REVOKED, REJECTED, ERROR
    consent_ref_enc     TEXT,                               -- verschlüsselt; nach Widerruf gelöscht (NULL)
    consent_expires_at  TIMESTAMPTZ,
    expiry_notified     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_sync_at        TIMESTAMPTZ,
    last_sync_status    VARCHAR(20),                        -- OK, FAILED
    last_error          VARCHAR(200),                       -- kurze, unkritische Meldung (keine Bankdaten)
    failed_syncs        INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ,
    CONSTRAINT ck_bank_connection_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED', 'REJECTED', 'ERROR'))
);
CREATE INDEX idx_bank_connection_user ON bank_connection (user_id);
CREATE INDEX idx_bank_connection_status ON bank_connection (status);

CREATE TABLE external_account (
    id              BIGSERIAL PRIMARY KEY,
    connection_id   BIGINT       NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id),
    external_id     VARCHAR(100) NOT NULL,                  -- Kennung des Kontos beim Anbieter
    iban_masked     VARCHAR(34),                            -- nur gekürzt, z. B. DE89 •••• 3000
    iban_hash       VARCHAR(64),                            -- HMAC-SHA-256: eigenes Konto wiedererkennen
    name            VARCHAR(150) NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    balance_cents   BIGINT,
    balance_type    VARCHAR(40),
    balance_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_account UNIQUE (connection_id, external_id)
);
CREATE INDEX idx_external_account_user ON external_account (user_id);

-- Originaldaten der Bank: werden nur vom Abgleich mit der Bank geändert, nie vom Nutzer.
CREATE TABLE external_transaction (
    id                   BIGSERIAL PRIMARY KEY,
    external_account_id  BIGINT       NOT NULL REFERENCES external_account (id) ON DELETE CASCADE,
    user_id              BIGINT       NOT NULL REFERENCES app_user (id),
    external_id          VARCHAR(160) NOT NULL,              -- stabile Kennung: keine doppelten Umsätze
    status               VARCHAR(20)  NOT NULL,              -- PENDING, BOOKED, CANCELLED, SUPERSEDED, REVERSED
    booking_date         DATE         NOT NULL,
    value_date           DATE,
    amount_cents         BIGINT       NOT NULL,
    currency             VARCHAR(3)   NOT NULL,
    counterparty         VARCHAR(200),
    remittance           VARCHAR(500),
    bank_code            VARCHAR(60),                        -- Buchungsart der Bank (ISO 20022), falls geliefert
    is_reversal          BOOLEAN      NOT NULL DEFAULT FALSE,  -- ist selbst eine Rückbuchung
    related_id           BIGINT REFERENCES external_transaction (id) ON DELETE SET NULL,
    auto_category        VARCHAR(30)  NOT NULL,
    raw_enc              TEXT         NOT NULL,              -- Originaldaten unverändert, verschlüsselt
    first_seen_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_transaction UNIQUE (external_account_id, external_id),
    CONSTRAINT ck_external_transaction_status
        CHECK (status IN ('PENDING', 'BOOKED', 'CANCELLED', 'SUPERSEDED', 'REVERSED'))
);
CREATE INDEX idx_external_transaction_user_date ON external_transaction (user_id, booking_date);
CREATE INDEX idx_external_transaction_account_status ON external_transaction (external_account_id, status);

-- Eigene Bearbeitungen des Nutzers – getrennt von den Originaldaten.
CREATE TABLE external_transaction_edit (
    transaction_id  BIGINT       PRIMARY KEY REFERENCES external_transaction (id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id),
    category        VARCHAR(30),
    note            VARCHAR(500),
    tags            VARCHAR(300),                           -- kommagetrennt, klein geschrieben
    excluded        BOOLEAN      NOT NULL DEFAULT FALSE,    -- nicht im Finanzüberblick mitzählen
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Prüfprotokoll für sicherheitsrelevante Aktionen (ohne Bankdaten, ohne Geheimnisse).
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(60)  NOT NULL,
    target      VARCHAR(100),
    details     VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_user ON audit_log (user_id, created_at);
