package de.frauas.girokonto.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/**
 * Kauf und Bewertung der fiktiven Fonds – an einer Stelle, damit Sparrate, Depotwert und Vermögensverlauf
 * dieselben Regeln nutzen.
 */
public final class DepotMath {

    private DepotMath() {
    }

    /** Aufteilung eines Depot-Betrags nach Risikostufe: Anteile je Fonds zum Preis des Monats (6 Nachkommastellen). */
    public static Map<Fund, BigDecimal> unitsFor(RiskLevel risk, long depotCents, YearMonth ym) {
        Map<Fund, BigDecimal> units = new EnumMap<>(Fund.class);
        if (depotCents <= 0) {
            return units;
        }
        long equity = Math.round(depotCents * risk.equityWeight());
        put(units, Fund.WELT_AKTIEN, equity, ym);
        put(units, Fund.EURO_ANLEIHEN, depotCents - equity, ym);
        return units;
    }

    private static void put(Map<Fund, BigDecimal> units, Fund fund, long cents, YearMonth ym) {
        if (cents > 0) {
            BigDecimal priceCents = BigDecimal.valueOf(fund.price(ym) * 100);
            units.put(fund, BigDecimal.valueOf(cents).divide(priceCents, 6, RoundingMode.HALF_UP));
        }
    }

    /** Wert von Fondsanteilen zum Preis des Monats in Cent (kaufmännisch gerundet). */
    public static long value(Fund fund, BigDecimal units, YearMonth ym) {
        return units.multiply(BigDecimal.valueOf(fund.price(ym) * 100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
