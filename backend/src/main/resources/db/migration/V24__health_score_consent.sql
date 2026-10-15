-- Finanz-Check nur mit Einwilligung (jederzeit widerrufbar)
ALTER TABLE app_user ADD COLUMN health_score_consent_at TIMESTAMPTZ;
