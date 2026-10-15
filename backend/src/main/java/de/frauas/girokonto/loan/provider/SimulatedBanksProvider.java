package de.frauas.girokonto.loan.provider;

import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.loan.Bank;
import de.frauas.girokonto.loan.CreditClass;
import de.frauas.girokonto.loan.LoanMath;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Fiktive Banken der Projektbank (Simulation). Zins = Basiszins des Zwecks (mit Laufzeit- und Eigenkapitalregeln)
 * + Aufschlag der Bank + Aufschlag nach Bonitätsklasse. Keine Bearbeitungsgebühr, Sondertilgungen kostenlos –
 * so rechnet auch die App beim simulierten Kredit. Alle Werte sind unverbindliche Rechenbeispiele.
 */
@Component
public class SimulatedBanksProvider implements LoanOfferProvider {

    public static final String ID = "SIMULATION";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Fiktive Banken (Simulation)";
    }

    @Override
    public boolean simulation() {
        return true;
    }

    @Override
    public boolean supportsApplications() {
        return true;
    }

    @Override
    public Status status() {
        return new Status("OK", "Rechenbeispiele fiktiver Banken – keine echten Angebote.", null);
    }

    @Override
    public List<Quote> quotes(Query q) {
        List<Quote> list = new ArrayList<>();
        BigDecimal base = q.purpose().rateFor(q.termMonths(), q.principalCents(), q.equityCents())
                .add(q.creditClass().addOn(q.purpose()));
        for (Bank bank : Bank.values()) {
            list.add(quote(bank, base.add(bank.adjustment()), q));
        }
        return list;
    }

    /** Sollzins höchstens 0,01 % (kein negativer Zins in der Simulation). */
    Quote quote(Bank bank, BigDecimal rate, Query q) {
        BigDecimal r = rate.max(new BigDecimal("0.01"));
        long monthly = LoanMath.annuity(q.principalCents(), r, q.termMonths());
        long interest = LoanMath.schedule(q.principalCents(), r, q.termMonths()).stream()
                .mapToLong(LoanMath.Row::interestCents).sum();
        BigDecimal effective = effectiveRate(r);
        boolean eligible = q.creditClass().atLeast(bank.minClass());
        String reason = eligible ? null : "Die " + bank.label() + " bietet erst ab Bonität „" + bank.minClass().label()
                + "“ an.";
        long total = q.principalCents() + interest;
        return new Quote(ID, bank.name(), bank.label(), r, effective, monthly, interest, 0, total, bank.minClass(),
                eligible, reason, bank.tolerance(), bank.requirements(), conditions(),
                example(q, r, effective, monthly, total));
    }

    static List<String> conditions() {
        return List.of("Keine Bearbeitungsgebühr", "Sondertilgungen jederzeit kostenlos",
                "Zins fest für die ganze Laufzeit", "Widerruf innerhalb von 14 Tagen nach Vertragsschluss");
    }

    /** Effektiver Jahreszins bei monatlicher Zahlung ohne weitere Kosten: (1 + Sollzins/12)^12 − 1. */
    public static BigDecimal effectiveRate(BigDecimal ratePercent) {
        BigDecimal j = ratePercent.divide(BigDecimal.valueOf(1200), MathContext.DECIMAL64);
        BigDecimal eff = BigDecimal.ONE.add(j).pow(12, MathContext.DECIMAL64).subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));
        return eff.setScale(2, RoundingMode.HALF_UP);
    }

    static String example(Query q, BigDecimal rate, BigDecimal effective, long monthly, long total) {
        return "Unverbindliches Rechenbeispiel (Simulation, fiktive Bank): Nettodarlehensbetrag "
                + Money.format(q.principalCents()) + ", Laufzeit " + q.termMonths() + " Monate, gebundener Sollzins "
                + percent(rate) + " p. a., effektiver Jahreszins " + percent(effective) + ", monatliche Rate "
                + Money.format(monthly) + ", Gesamtbetrag " + Money.format(total) + ".";
    }

    static String percent(BigDecimal p) {
        return String.format(Locale.GERMANY, "%.2f %%", p);
    }
}
