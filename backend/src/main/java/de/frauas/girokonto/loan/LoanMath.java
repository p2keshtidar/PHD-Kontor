package de.frauas.girokonto.loan;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Annuitätenrechnung (Pflichtenheft Kap. 8.5). Beträge in Cent, Zinssatz in Prozent p. a. */
public final class LoanMath {

    private static final MathContext MC = MathContext.DECIMAL64;

    private LoanMath() {
    }

    /** Eine Zeile des Tilgungsplans. */
    public record Row(int month, long rateCents, long interestCents, long principalCents, long remainingCents) {}

    /** Monatszins j = Zinssatz / 100 / 12 */
    public static BigDecimal monthlyInterest(BigDecimal ratePercent) {
        return ratePercent.divide(BigDecimal.valueOf(1200), MC);
    }

    /** Rate = K · j / (1 − (1 + j)^(−n)), auf den Cent aufgerundet. */
    public static long annuity(long principalCents, BigDecimal ratePercent, int months) {
        BigDecimal j = monthlyInterest(ratePercent);
        if (j.signum() == 0) {
            return (principalCents + months - 1) / months;
        }
        BigDecimal factor = BigDecimal.ONE.add(j).pow(months, MC);
        BigDecimal rate = BigDecimal.valueOf(principalCents).multiply(j).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), MC);
        return rate.setScale(0, RoundingMode.CEILING).longValue();
    }

    /** Zins eines Monats = Restschuld · j, kaufmännisch gerundet. */
    public static long interest(long remainingCents, BigDecimal ratePercent) {
        return BigDecimal.valueOf(remainingCents).multiply(monthlyInterest(ratePercent))
                .setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Vollständiger Tilgungsplan; die letzte Rate wird so angepasst, dass die Restschuld genau 0 ist. */
    public static List<Row> schedule(long principalCents, BigDecimal ratePercent, int months) {
        long rate = annuity(principalCents, ratePercent, months);
        List<Row> rows = new ArrayList<>(months);
        long remaining = principalCents;
        for (int m = 1; m <= months && remaining > 0; m++) {
            long interest = interest(remaining, ratePercent);
            long principal = rate - interest;
            long thisRate = rate;
            if (m == months || principal >= remaining) {
                principal = remaining;
                thisRate = interest + remaining;
            }
            remaining -= principal;
            rows.add(new Row(m, thisRate, interest, principal, remaining));
        }
        return rows;
    }

    /** Höchster Kreditbetrag, dessen Rate höchstens maxRate ist: K = R · (1 − (1 + j)^(−n)) / j */
    public static long maxPrincipal(long maxRateCents, BigDecimal ratePercent, int months) {
        if (maxRateCents <= 0) {
            return 0;
        }
        BigDecimal j = monthlyInterest(ratePercent);
        if (j.signum() == 0) {
            return maxRateCents * months;
        }
        BigDecimal factor = BigDecimal.ONE.add(j).pow(months, MC);
        BigDecimal pv = BigDecimal.valueOf(maxRateCents).multiply(BigDecimal.ONE.subtract(BigDecimal.ONE.divide(factor, MC)))
                .divide(j, MC);
        return pv.setScale(0, RoundingMode.FLOOR).longValue();
    }
}
