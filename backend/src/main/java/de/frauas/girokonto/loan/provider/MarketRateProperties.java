package de.frauas.girokonto.loan.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Referenzzinsen der Deutschen Bundesbank (Präfix app.loans.market-rates).
 *
 * @param enabled       Abruf einschalten (öffentliche Schnittstelle, keine Zugangsdaten nötig)
 * @param baseUrl       SDMX-Webservice der Bundesbank
 * @param refreshHours  Abstand zwischen zwei Abrufen
 * @param staleDays     älter als so viele Tage seit dem letzten erfolgreichen Abruf = veraltet
 */
@ConfigurationProperties(prefix = "app.loans.market-rates")
public record MarketRateProperties(boolean enabled, String baseUrl, int refreshHours, int staleDays) {

    public MarketRateProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.statistiken.bundesbank.de/rest/data" : baseUrl;
        refreshHours = refreshHours <= 0 ? 24 : refreshHours;
        staleDays = staleDays <= 0 ? 7 : staleDays;
    }
}
