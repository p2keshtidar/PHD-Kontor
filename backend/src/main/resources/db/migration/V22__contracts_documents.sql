-- Verträge (Laufzeiten, Kündigungsfristen, Kosten, Erinnerungen) und verschlüsselte Dokumente.

CREATE TABLE contract (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user (id),
    name             VARCHAR(80)  NOT NULL,
    provider         VARCHAR(80),
    kind             VARCHAR(20)  NOT NULL,
    cost_cents       BIGINT       NOT NULL CHECK (cost_cents >= 0),
    billing          VARCHAR(10)  NOT NULL CHECK (billing IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    start_date       DATE         NOT NULL,
    min_term_months  INT          NOT NULL CHECK (min_term_months BETWEEN 0 AND 120),
    renewal_months   INT          NOT NULL CHECK (renewal_months BETWEEN 1 AND 60),
    notice_value     INT          NOT NULL CHECK (notice_value BETWEEN 0 AND 24),
    notice_unit      VARCHAR(6)   NOT NULL CHECK (notice_unit IN ('DAYS', 'WEEKS', 'MONTHS')),
    reminder_days    INT          NOT NULL CHECK (reminder_days BETWEEN 0 AND 90),
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    ends_on          DATE,
    note             VARCHAR(300),
    reminded_for     DATE,
    created_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_contract_user ON contract (user_id);

-- Inhalt AES-256-GCM-verschlüsselt (Schlüssel aus CARD_KEY abgeleitet), nur Metadaten im Klartext
CREATE TABLE document (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user (id),
    contract_id  BIGINT REFERENCES contract (id) ON DELETE SET NULL,
    title        VARCHAR(100) NOT NULL,
    file_name    VARCHAR(120) NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    size_bytes   INT          NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    content      BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_document_user ON document (user_id, created_at DESC);
