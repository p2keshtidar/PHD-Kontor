-- /K060/ Geteilte Unterkonten: Mitglieder dürfen einzahlen, auszahlen und die Umsätze sehen
CREATE TABLE account_member (
    account_id  BIGINT      NOT NULL REFERENCES account (id),
    user_id     BIGINT      NOT NULL REFERENCES app_user (id),
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, user_id)
);
CREATE INDEX idx_account_member_user ON account_member (user_id);
