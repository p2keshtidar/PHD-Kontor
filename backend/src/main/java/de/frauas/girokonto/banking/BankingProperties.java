package de.frauas.girokonto.banking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen für Bankverbindungen (Präfix app.banking).
 *
 * @param sandboxEnabled      Sandbox-Banken mit Testdaten anbieten
 * @param autoSync            Konten regelmäßig im Hintergrund abrufen
 * @param syncIntervalMinutes Abstand des Hintergrund-Abrufs
 * @param retryAttempts       Versuche je Abruf bei vorübergehenden Fehlern
 * @param retryBackoffMs      Wartezeit vor dem 2. Versuch (verdoppelt sich)
 * @param initialHistoryDays  Umsätze beim ersten Abruf (Tage zurück)
 * @param redirectUrl         Rücksprung nach der Freigabe bei der Bank
 * @param gocardless          Zugangsdaten GoCardless Bank Account Data (nur über Umgebungsvariablen)
 */
@ConfigurationProperties(prefix = "app.banking")
public record BankingProperties(boolean sandboxEnabled, boolean autoSync, int syncIntervalMinutes, int retryAttempts,
                                long retryBackoffMs, int initialHistoryDays, String redirectUrl, GoCardless gocardless) {

    public record GoCardless(String baseUrl, String secretId, String secretKey) {

        public boolean configured() {
            return secretId != null && !secretId.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }
}
