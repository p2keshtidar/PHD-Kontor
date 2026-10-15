-- Sprache der Oberfläche je Nutzer ('de' oder 'en'); NULL = noch nicht gewählt (App nimmt Gerätesprache)
ALTER TABLE app_user ADD COLUMN language VARCHAR(2);
ALTER TABLE app_user ADD CONSTRAINT app_user_language_check CHECK (language IS NULL OR language IN ('de', 'en'));
