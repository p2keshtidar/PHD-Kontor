-- Virtuelle Demo-Karten für digitale Konten (ohne Kartennummer: nur die letzten 4 Ziffern zur Anzeige)
CREATE TABLE subaccount_card (
    id                BIGSERIAL PRIMARY KEY,
    account_id        BIGINT      NOT NULL UNIQUE REFERENCES account (id) ON DELETE CASCADE,
    created_by        BIGINT      NOT NULL REFERENCES app_user (id),
    last4             VARCHAR(4)  NOT NULL,
    status            VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'LOCKED')),
    daily_limit_cents BIGINT      NOT NULL CHECK (daily_limit_cents BETWEEN 100 AND 200000),
    created_at        TIMESTAMPTZ NOT NULL
);
