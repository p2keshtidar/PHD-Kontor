-- Phase 2: Adressbuch (/F220/) und Indizes für die Umsatzsuche (/F130/)

CREATE TABLE contact (
    id               BIGSERIAL PRIMARY KEY,
    owner_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    contact_user_id  BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    nickname         VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_contact UNIQUE (owner_id, contact_user_id)
);

CREATE INDEX idx_booking_booked_at ON booking (booked_at);
CREATE INDEX idx_booking_type ON booking (type);
