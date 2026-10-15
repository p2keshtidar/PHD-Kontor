-- Multi-Banking in der Projektbank (Simulation): weitere Girokonten (Typ CURRENT) und Tagesgeld (Typ CALL).
-- Der Zinssatz des Tagesgelds ist fiktiv und wird beim Eröffnen festgehalten.
ALTER TABLE account ADD COLUMN interest_rate NUMERIC(6, 3);
