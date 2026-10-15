package de.frauas.girokonto.market;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ein Angebot im Marktplatz. Alle Anbieter sind fiktiv (Simulation), die Konditionen sind Beispielwerte zum Üben des
 * Vergleichs – keine echten Angebote.
 *
 * @param category         GIRO, CREDIT_CARD, CALL (Tagesgeld), FIXED (Festgeld), DEPOT, LOAN
 * @param monthlyFeeCents  Kontoführung/Grundgebühr pro Monat
 * @param annualFeeCents   Jahresgebühr (z. B. Kreditkarte)
 * @param interestPercent  Guthabenzins (Tagesgeld, Festgeld) bzw. Sollzins (Kreditkarte, Kredit) in % p. a.
 * @param termMonths       Laufzeit (Festgeld) oder null
 * @param minAmountCents   Mindestanlage/-betrag oder null
 * @param orderFeeCents    Gebühr je Wertpapierkauf (Depot) oder null
 * @param documents        Unterlagen für einen (simulierten) Antrag
 * @param minIncomeCents   Voraussetzung: durchschnittliche Einnahmen pro Monat (laut Finanzüberblick) oder null
 */
public record MarketProduct(String id, String category, String provider, String name, long monthlyFeeCents,
                            long annualFeeCents, BigDecimal interestPercent, Integer termMonths, Long minAmountCents,
                            Long orderFeeCents, List<String> requirements, List<String> pros, List<String> cons,
                            List<String> documents, Long minIncomeCents) {

    public long yearlyFeeCents() {
        return monthlyFeeCents * 12 + annualFeeCents;
    }
}
