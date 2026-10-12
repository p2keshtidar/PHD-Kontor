-- /K050/ Zwei-Faktor-Anmeldung mit Authenticator-App (TOTP, RFC 6238)
ALTER TABLE app_user ADD COLUMN totp_secret VARCHAR(200);              -- verschlüsselt (AES-GCM)
ALTER TABLE app_user ADD COLUMN totp_pending_secret VARCHAR(200);      -- während der Einrichtung
ALTER TABLE app_user ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN totp_last_step BIGINT;                 -- verhindert doppelte Nutzung eines Codes
