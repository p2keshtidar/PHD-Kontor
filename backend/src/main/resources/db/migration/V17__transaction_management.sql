-- Transaktionsmanagement: eigene Notizen zu Umsätzen der Projektbank (privat je Nutzer)
-- und als „kein Duplikat“ markierte Paare bei der Duplikat-Erkennung.
CREATE TABLE booking_note (
    booking_id  BIGINT       NOT NULL REFERENCES booking (id),
    account_id  BIGINT       NOT NULL REFERENCES account (id),
    user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    note        VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (booking_id, account_id, user_id)
);

CREATE TABLE duplicate_dismissal (
    user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    pair_key    VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, pair_key)
);
