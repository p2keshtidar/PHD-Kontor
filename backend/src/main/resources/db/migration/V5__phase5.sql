-- Phase 5: Kannkriterien

-- /K010/ Erinnerung an die Sparrate: X Tage vor der Abbuchung (0 = aus)
ALTER TABLE savings_plan ADD COLUMN reminder_days INT NOT NULL DEFAULT 3;
ALTER TABLE savings_plan ADD COLUMN last_reminded_month VARCHAR(7);

-- /K020/ Sondertilgung: zusätzliche Rückzahlungen eines Kredits
CREATE TABLE loan_extra_payment (
    id                 BIGSERIAL PRIMARY KEY,
    loan_id            BIGINT      NOT NULL REFERENCES loan (id),
    booking_id         BIGINT      NOT NULL REFERENCES booking (id),
    amount_cents       BIGINT      NOT NULL CHECK (amount_cents > 0),
    after_installment  INT         NOT NULL,   -- gezahlt nach Rate Nr. X (0 = vor der ersten Rate)
    paid_on            DATE        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_loan_extra_payment_loan ON loan_extra_payment (loan_id);
