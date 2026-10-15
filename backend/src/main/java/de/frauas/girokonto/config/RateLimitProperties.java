package de.frauas.girokonto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Begrenzung der Anfragen pro Minute (Schutz vor Passwort-Raten und Überlastung).
 *
 * @param authPerMinute Anmelden, Registrieren, Token erneuern, Zwei-Faktor – je IP-Adresse
 * @param apiPerMinute  alle übrigen /api-Aufrufe – je angemeldetem Token bzw. IP-Adresse
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, int authPerMinute, int apiPerMinute) {
}
