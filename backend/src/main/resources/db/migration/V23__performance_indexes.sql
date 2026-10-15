-- Indizes nach Prüfung der häufigen Abfragen (siehe docs/technik/architektur.md, Abschnitt Performance)

-- Vertragsübersicht zählt Dokumente je Vertrag (Unterabfrage je Zeile)
CREATE INDEX idx_document_contract ON document (contract_id) WHERE contract_id IS NOT NULL;

-- Tageslauf Nr. 65: nur laufende Verträge mit Erinnerung
CREATE INDEX idx_contract_reminder ON contract (status) WHERE status = 'ACTIVE' AND reminder_days > 0;

-- Tageslauf Nr. 60: offene Kreditkartenabrechnungen
CREATE INDEX idx_credit_card_statement_open ON credit_card_statement (card_id) WHERE status = 'OPEN';

-- Zähler ungelesener Benachrichtigungen (bei jeder Seite der App)
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE NOT is_read;
