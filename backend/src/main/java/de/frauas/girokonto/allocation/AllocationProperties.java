package de.frauas.girokonto.allocation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen für Sparregeln (Präfix app.allocations).
 *
 * @param maxRulesPerUser     offene Sparregeln je Nutzer (gegen Missbrauch)
 * @param maxAttempts         Versuche je Fälligkeit (1 + Wiederholungen an den Folgetagen)
 * @param balanceMaxAgeHours  so alt darf der Kontostand eines verbundenen Bankkontos für eine Vormerkung sein
 */
@ConfigurationProperties(prefix = "app.allocations")
public record AllocationProperties(int maxRulesPerUser, int maxAttempts, int balanceMaxAgeHours) {

    public AllocationProperties {
        maxRulesPerUser = maxRulesPerUser <= 0 ? 20 : maxRulesPerUser;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        balanceMaxAgeHours = balanceMaxAgeHours <= 0 ? 48 : balanceMaxAgeHours;
    }
}
