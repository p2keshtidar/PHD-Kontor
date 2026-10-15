-- Digitale Konten (bisher "Unterkonten"): Zweck, Zielbetrag, Zieldatum.
-- Ein digitales Konto ist ein Topf innerhalb des Projektbank-Kontos (eigene Zeile im Hauptbuch),
-- kein eigenes Bankkonto und ohne eigene Einlagensicherung.
ALTER TABLE account ADD COLUMN purpose VARCHAR(20);
ALTER TABLE account ADD COLUMN target_cents BIGINT;
ALTER TABLE account ADD COLUMN target_date DATE;
ALTER TABLE account ADD COLUMN goal_reached_at TIMESTAMPTZ;   -- "Ziel erreicht" nur einmal melden
ALTER TABLE account ADD CONSTRAINT ck_account_target CHECK (target_cents IS NULL OR target_cents > 0);
UPDATE account SET purpose = 'SONSTIGES' WHERE type = 'SUB';

-- Rollen in geteilten digitalen Konten: FULL = einzahlen + auszahlen, DEPOSIT = nur einzahlen, VIEW = nur ansehen
ALTER TABLE account_member ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'FULL';
ALTER TABLE account_member ADD CONSTRAINT ck_account_member_role CHECK (role IN ('FULL', 'DEPOSIT', 'VIEW'));
