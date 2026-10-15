-- Finanz-Marktplatz (nur simulierte Anbieter): Favoriten und simulierte Anträge mit Unterlagen.
CREATE TABLE market_favorite (
    user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    product_id  VARCHAR(60)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);

CREATE TABLE market_application (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_user (id),
    product_id     VARCHAR(60)  NOT NULL,
    product_name   VARCHAR(120) NOT NULL,
    provider_name  VARCHAR(120) NOT NULL,
    category       VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,   -- DOCUMENTS_MISSING, SUBMITTED, APPROVED, DECLINED, WITHDRAWN
    status_reason  VARCHAR(300),
    note           VARCHAR(500),            -- eigene Entscheidung/Notiz
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at     TIMESTAMPTZ,
    CONSTRAINT ck_market_application_status
        CHECK (status IN ('DOCUMENTS_MISSING', 'SUBMITTED', 'APPROVED', 'DECLINED', 'WITHDRAWN'))
);
CREATE INDEX idx_market_application_user ON market_application (user_id);

CREATE TABLE market_application_document (
    application_id  BIGINT       NOT NULL REFERENCES market_application (id) ON DELETE CASCADE,
    doc_key         VARCHAR(40)  NOT NULL,
    label           VARCHAR(120) NOT NULL,
    provided_at     TIMESTAMPTZ,
    PRIMARY KEY (application_id, doc_key)
);
