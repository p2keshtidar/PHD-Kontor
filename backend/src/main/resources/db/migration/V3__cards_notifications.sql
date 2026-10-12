-- Phase 3: Virtuelle Karte (/F310/ bis /F340/) und Benachrichtigungen (/F510/, /F520/)

CREATE TABLE card (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL UNIQUE REFERENCES app_user (id),
    account_id         BIGINT       NOT NULL REFERENCES account (id),
    pan_encrypted      VARCHAR(200) NOT NULL,   -- volle Kartennummer, AES-GCM verschlüsselt
    pan_last4          VARCHAR(4)   NOT NULL,
    cvc_encrypted      VARCHAR(200) NOT NULL,
    expiry_month       INT          NOT NULL,
    expiry_year        INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, LOCKED
    daily_limit_cents  BIGINT       NOT NULL DEFAULT 100000,
    online_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        VARCHAR(300) NOT NULL,
    booking_id  BIGINT       REFERENCES booking (id),
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);

-- System-Konto, auf das Kartenzahlungen gebucht werden
INSERT INTO account (owner_id, type, name) VALUES (NULL, 'SYSTEM', 'HAENDLER');
