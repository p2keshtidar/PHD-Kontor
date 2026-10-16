package de.frauas.girokonto.planner;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.loan.LoanDtos.Offer;
import de.frauas.girokonto.loan.LoanDtos.OfferResult;
import de.frauas.girokonto.loan.LoanPurpose;
import de.frauas.girokonto.loan.LoanService;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.savings.SavingsPlanService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Zentraler Planer für größere Vorhaben (Reise, Auto, Immobilie): Ansparen, Finanzieren oder Anzahlung ansparen und
 * den Rest finanzieren – nebeneinander, mit den Rechnern, die es schon gibt (Sparplan-Prüfung und Kreditvergleich
 * mit fiktiven Banken). Es wird nichts angelegt oder beantragt.
 */
@Service
public class PlannerService {

    public static final String DISCLAIMER = "Rechenbeispiele mit fiktiven Banken und deinen Durchschnittswerten – "
            + "kein Angebot, keine Beratung. Angelegt oder beantragt wird erst in den nächsten Schritten.";

    /** Zieldatum oder Monate ab heute (Simulationszeit). */
    public record PlannerRequest(String goal, Long priceCents, LocalDate targetDate, Integer months, Long equityCents) {}

    /** {@code loanAmountCents}/{@code loanEquityCents}: Werte für den Kreditvergleich (nur bei Finanzierung). */
    public record Option(String kind, String title, boolean possible, Long monthlyCents, Long savingMonthlyCents,
                         Long extraCostCents, Long totalCostCents, String feasibility, String bankName,
                         Integer termMonths, String text, Long loanAmountCents, Long loanEquityCents) {}

    public record PlannerResult(String goal, String goalLabel, long priceCents, long equityCents, int months,
                                long availableCents, boolean hasData, List<Option> options, String recommendation,
                                String loanPurpose, String disclaimer) {}

    private final SavingsPlanService savings;
    private final LoanService loans;
    private final Clock clock;

    public PlannerService(SavingsPlanService savings, LoanService loans, Clock clock) {
        this.savings = savings;
        this.loans = loans;
        this.clock = clock;
    }

    record Goal(String key, String label, LoanPurpose purpose, int term) {}

    static Goal goal(String key) {
        return switch (key == null ? "" : key.trim().toUpperCase(Locale.ROOT)) {
            case "REISE" -> new Goal("REISE", "Reise", LoanPurpose.SONSTIGES, 24);
            case "AUTO" -> new Goal("AUTO", "Auto", LoanPurpose.AUTO, 48);
            case "IMMOBILIE" -> new Goal("IMMOBILIE", "Immobilie", LoanPurpose.IMMOBILIE, 240);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GOAL", "Vorhaben: Reise, Auto oder Immobilie.");
        };
    }

    /** Ohne eigene Transaktion: ein abgelehnter Kreditvergleich (Ausnahme) darf den Rest nicht zurückrollen. */
    public PlannerResult compare(Long userId, PlannerRequest req) {
        Goal g = goal(req.goal());
        long price = req.priceCents() == null ? 0 : req.priceCents();
        long equity = req.equityCents() == null ? 0 : req.equityCents();
        if (price < 10_000 || price > 200_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Der Preis muss zwischen 100 € und 2.000.000 € liegen.");
        }
        if (equity < 0 || equity >= price) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EQUITY", "Das Eigenkapital muss kleiner als der Preis sein.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = req.months() != null ? today.plusMonths(req.months()) : req.targetDate();
        if (date == null || !date.isAfter(today.plusMonths(1)) || date.isAfter(today.plusYears(30))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Das Zieldatum muss zwischen einem Monat und 30 Jahren in der Zukunft liegen.");
        }
        int months = (int) Math.max(1, ChronoUnit.MONTHS.between(today, date));
        long need = price - equity;

        List<Option> options = new ArrayList<>();
        // 1) Ansparen (ohne Zinsen gerechnet; Budget = verfügbarer Betrag aus dem Finanzüberblick)
        OverviewDto ov = savings.overview(userId);
        long rate = (need + months - 1) / months;
        String feasibility = !ov.hasData() ? "UNKNOWN" : rate <= ov.availableCents() ? "GREEN" : "RED";
        options.add(new Option("SAVE", "Ansparen", "GREEN".equals(feasibility), rate, rate, 0L, need, feasibility, null,
                months, "Bis " + Dates.de(date) + " jeden Monat " + Money.format(rate) + " zurücklegen – keine Zinsen.",
                null, null));

        // 2) Finanzieren (sofort, mit Laufzeit des Vorhabens)
        Optional<Offer> finance = cheapest(userId, g.purpose(), need, equity, g.term());
        options.add(financeOption("FINANCE", "Sofort finanzieren", finance, null, g.term(),
                "Kredit über " + Money.format(need) + " für " + g.term() + " Monate", need, equity));

        // 3) Anzahlung ansparen (20 % des Preises), Rest finanzieren
        long down = Math.max(0, Math.round(price * 0.20) - equity);
        if (down > 0 && need - down > 0) {
            long downRate = (down + months - 1) / months;
            Optional<Offer> mix = cheapest(userId, g.purpose(), need - down, equity + down, g.term());
            options.add(financeOption("MIX", "Anzahlung ansparen, Rest finanzieren", mix, downRate, g.term(),
                    Money.format(down) + " Anzahlung in " + months + " Monaten ansparen, danach Kredit über "
                            + Money.format(need - down), need - down, equity + down));
        }

        String recommendation = recommend(options, ov.hasData());
        return new PlannerResult(g.key(), g.label(), price, equity, months, ov.availableCents(), ov.hasData(), options,
                recommendation, g.purpose().name(), DISCLAIMER);
    }

    private Optional<Offer> cheapest(Long userId, LoanPurpose purpose, long principal, long equity, int term) {
        try {
            OfferResult r = loans.offers(userId, purpose, principal, equity, term);
            return r.offers().stream().filter(o -> !o.declined())
                    .min(Comparator.comparingLong(Offer::totalCostCents));
        } catch (ApiException e) {
            return Optional.empty();          // z. B. Betrag außerhalb der Grenzen dieses Kreditzwecks
        }
    }

    private static Option financeOption(String kind, String title, Optional<Offer> offer, Long savingRate, int term,
                                        String what, long loanAmount, long loanEquity) {
        if (offer.isEmpty()) {
            return new Option(kind, title, false, null, savingRate, null, null, "RED", null, term,
                    what + ": keine passende Bank (Betrag, Laufzeit oder Tragfähigkeit).", loanAmount, loanEquity);
        }
        Offer o = offer.get();
        return new Option(kind, title, o.affordable(), o.monthlyRateCents(), savingRate, o.totalInterestCents(),
                o.totalCostCents(), o.affordable() ? "GREEN" : "RED", o.bankName(), term,
                what + " bei " + o.bankName() + ": " + Money.format(o.monthlyRateCents()) + " im Monat, Zinsen "
                        + Money.format(o.totalInterestCents()) + ".", loanAmount, loanEquity);
    }

    static String recommend(List<Option> options, boolean hasData) {
        if (!hasData) {
            return "Für eine Empfehlung fehlen Einnahmen und Ausgaben – erzeuge Beispielmonate oder trag sie ein.";
        }
        Option save = options.get(0);
        if (save.possible()) {
            return "Ansparen ist am günstigsten: keine Zinsen und das Ziel passt in dein Budget.";
        }
        Optional<Option> best = options.stream().skip(1).filter(Option::possible)
                .min(Comparator.comparingLong(o -> o.extraCostCents() == null ? Long.MAX_VALUE : o.extraCostCents()));
        return best.map(o -> "Ansparen bis zum Termin passt nicht in dein Budget. Günstigste tragbare Variante: "
                        + o.title() + " (Zinsen " + Money.format(o.extraCostCents()) + ").")
                .orElse("Keine Variante passt in dein Budget – später, günstiger oder mit mehr Eigenkapital planen.");
    }
}
