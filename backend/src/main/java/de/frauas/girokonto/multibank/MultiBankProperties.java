package de.frauas.girokonto.multibank;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Einstellungen für weitere Konten der Projektbank (Präfix app.accounts).
 *
 * @param maxExtraPerUser     weitere Girokonten und Tagesgeldkonten je Nutzer zusammen
 * @param callInterestPercent fiktiver Zinssatz der Simulation für neue Tagesgeldkonten (Prozent pro Jahr)
 */
@ConfigurationProperties(prefix = "app.accounts")
public record MultiBankProperties(int maxExtraPerUser, BigDecimal callInterestPercent) {

    public MultiBankProperties {
        maxExtraPerUser = maxExtraPerUser <= 0 ? 3 : maxExtraPerUser;
        callInterestPercent = callInterestPercent == null || callInterestPercent.signum() < 0
                ? new BigDecimal("2.00") : callInterestPercent;
    }
}
