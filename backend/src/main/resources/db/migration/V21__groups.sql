-- Gemeinsame Ausgaben (WG, Reisen, Paare): Gruppen, Ausgaben mit Aufteilung, Ausgleiche.
-- Beträge in Cent. Ein Ausgleich per Überweisung ist eine normale Überweisung der Projektbank (Spielgeld).

CREATE TABLE expense_group (
    id         BIGSERIAL PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES app_user (id),
    name       VARCHAR(60)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE expense_group_member (
    group_id  BIGINT      NOT NULL REFERENCES expense_group (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES app_user (id),
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_expense_group_member_user ON expense_group_member (user_id);

CREATE TABLE group_expense (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT       NOT NULL REFERENCES expense_group (id) ON DELETE CASCADE,
    payer_id     BIGINT       NOT NULL REFERENCES app_user (id),
    amount_cents BIGINT       NOT NULL CHECK (amount_cents > 0),
    description  VARCHAR(100) NOT NULL,
    expense_date DATE         NOT NULL,
    created_by   BIGINT       NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_group_expense_group ON group_expense (group_id, expense_date DESC);

CREATE TABLE group_expense_share (
    expense_id  BIGINT NOT NULL REFERENCES group_expense (id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES app_user (id),
    share_cents BIGINT NOT NULL CHECK (share_cents >= 0),
    PRIMARY KEY (expense_id, user_id)
);

CREATE TABLE group_settlement (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT      NOT NULL REFERENCES expense_group (id) ON DELETE CASCADE,
    from_user    BIGINT      NOT NULL REFERENCES app_user (id),
    to_user      BIGINT      NOT NULL REFERENCES app_user (id),
    amount_cents BIGINT      NOT NULL CHECK (amount_cents > 0),
    method       VARCHAR(10) NOT NULL CHECK (method IN ('TRANSFER', 'CASH')),
    booking_id   BIGINT REFERENCES booking (id),
    created_by   BIGINT      NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_group_settlement_group ON group_settlement (group_id);
