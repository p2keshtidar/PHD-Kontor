package de.frauas.girokonto.assistant;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.InsightsService;
import de.frauas.girokonto.insights.MonthSummaryDto;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.planning.BudgetService;
import de.frauas.girokonto.planning.ForecastService;
import de.frauas.girokonto.planning.PlanningDtos.BudgetItem;
import de.frauas.girokonto.planning.PlanningDtos.BudgetOverview;
import de.frauas.girokonto.planning.PlanningDtos.Forecast;
import de.frauas.girokonto.planning.PlanningDtos.MonthEnd;
import de.frauas.girokonto.savings.SavingsPlanService;
import java.text.Normalizer;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persönlicher Finanzassistent – regelbasiert und nur mit den Daten der App (keine externe KI, keine erfundenen Zahlen).
 *
 * Beantwortet Fragen zu Ausgaben und Einnahmen, zeigt Sparpotenziale, erstellt einen Monatsbericht und rechnet
 * einfache Szenarien („Was wäre, wenn …“) aus den eigenen Durchschnittswerten.
 */
@Service
public class AssistantService {

    public static final String BASIS = "Grundlage: deine Umsätze in der App (Projektbank, verbundene Banken, CSV-Import).";
    public static final String DISCLAIMER = "Automatische Auswertung deiner App-Daten – keine Finanzberatung.";
    public static final String SCENARIO_DISCLAIMER = "Rechenbeispiel aus deinen Durchschnittswerten – keine Prognose, keine Beratung.";

    static final String[] MONTHS_DE = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
            "September", "Oktober", "November", "Dezember"};
    private static final String[] MONTHS_EN = {"january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december"};

    /** Suchwörter je Kategorie (klein, ohne Umlaute). */
    static final Map<Category, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Category.LEBENSMITTEL, List.of("lebensmittel", "essen", "supermarkt", "einkauf", "grocer", "food"));
        KEYWORDS.put(Category.WOHNEN, List.of("wohnen", "miete", "nebenkosten", "strom", "rent", "housing"));
        KEYWORDS.put(Category.VERTRAEGE, List.of("vertrag", "vertraege", "abo", "handy", "streaming", "subscription", "contract"));
        KEYWORDS.put(Category.MOBILITAET, List.of("mobilitaet", "mobility", "auto", "tanken", "bahn", "bus", "ticket", "transport", "fuel"));
        KEYWORDS.put(Category.FREIZEIT, List.of("freizeit", "restaurant", "kino", "urlaub", "hobby", "leisure"));
        KEYWORDS.put(Category.SHOPPING, List.of("shopping", "kleidung", "klamotten", "clothes"));
        KEYWORDS.put(Category.KREDIT, List.of("kredit", "loan"));
        KEYWORDS.put(Category.SONSTIGES, List.of("sonstig", "other"));
        KEYWORDS.put(Category.GEHALT, List.of("gehalt", "lohn", "salary"));
    }

    public record Fact(String label, Long amountCents, String value) {}
    public record Answer(String intent, String question, String text, List<Fact> facts, List<String> notes,
                         String note, String disclaimer) {}
    public record Suggestion(String text) {}
    public record CategoryLine(String category, String label, long cents, long previousCents, Integer changePercent) {}
    public record Report(String month, String monthLabel, boolean complete, boolean hasData, long incomeCents,
                         long expenseCents, long freeCents, Integer savingsRatePercent, long previousExpenseCents,
                         List<CategoryLine> categories, List<String> notes, String note, String disclaimer) {}
    public record ScenarioRequest(String type, String category, Long amountCents, Integer percent, Long targetCents) {}
    public record Scenario(String type, String text, long monthlyEffectCents, long yearlyEffectCents,
                           long currentFreeCents, long newFreeCents, Integer currentSavingsRatePercent,
                           Integer newSavingsRatePercent, Integer monthsToTarget, List<String> warnings,
                           List<String> assumptions, String disclaimer) {}

    private final InsightsService insights;
    private final SavingsPlanService savings;
    private final BudgetService budgets;
    private final ForecastService forecasts;
    private final Clock clock;

    public AssistantService(InsightsService insights, SavingsPlanService savings, BudgetService budgets,
                            ForecastService forecasts, Clock clock) {
        this.insights = insights;
        this.savings = savings;
        this.budgets = budgets;
        this.forecasts = forecasts;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- Fragen

    /** Vorschläge passend zu den vorhandenen Daten. */
    @Transactional(readOnly = true)
    public List<Suggestion> questions(Long userId) {
        List<String> q = new ArrayList<>();
        q.add("Wofür gebe ich am meisten aus?");
        List<MonthSummaryDto> months = insights.months(userId, 2);
        String cat = months.stream().flatMap(m -> m.categories().stream())
                .filter(c -> c.category().isExpense()).map(MonthSummaryDto.CategoryAmount::label).findFirst()
                .orElse(Category.LEBENSMITTEL.label());
        q.add("Wie viel habe ich diesen Monat für " + cat + " ausgegeben?");
        q.add("Wo kann ich sparen?");
        q.add("Reicht mein Geld bis zum Monatsende?");
        q.add("Zeig mir den Monatsbericht für letzten Monat.");
        q.add("Wie stehen meine Budgets?");
        return q.stream().map(Suggestion::new).toList();
    }

    @Transactional(readOnly = true)
    public Answer ask(Long userId, String question) {
        String raw = question == null ? "" : question.trim();
        if (raw.isEmpty() || raw.length() > 300) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUESTION", "Bitte eine Frage mit bis zu 300 Zeichen stellen.");
        }
        String q = normalize(raw);
        List<MonthSummaryDto> months = insights.months(userId, 13);
        YearMonth month = monthIn(q);
        Category cat = categoryIn(q);

        if (has(q, "bericht", "zusammenfassung", "report", "summary")) {
            Report r = report(userId, month == null ? YearMonth.now(clock).minusMonths(1) : month);
            List<Fact> facts = List.of(new Fact("Einnahmen", r.incomeCents(), null),
                    new Fact("Ausgaben", r.expenseCents(), null), new Fact("Übrig", r.freeCents(), null));
            String text = r.hasData() ? "Monatsbericht für " + r.monthLabel() + "."
                    : "Für " + r.monthLabel() + " liegen keine Umsätze vor.";
            return answer("REPORT", raw, text, facts, r.notes());
        }
        if (has(q, "sparpotenzial", "einsparen", "sparen", "spare", "save", "saving")) {
            return savingsAnswer(raw, months);
        }
        if (has(q, "monatsende", "reicht", "prognose", "forecast", "end of the month", "end of month", "enough")) {
            return forecastAnswer(userId, raw);
        }
        if (has(q, "budget")) {
            return budgetAnswer(userId, raw);
        }
        if (has(q, "am meisten", "wofuer", "groesste", "most", "biggest", "top")) {
            MonthSummaryDto m = pick(months, month);
            List<MonthSummaryDto.CategoryAmount> top = m.categories().stream().filter(c -> c.category().isExpense())
                    .limit(3).toList();
            if (top.isEmpty()) {
                return answer("TOP_CATEGORIES", raw, "Für " + label(m) + " liegen keine Ausgaben vor.", List.of());
            }
            List<Fact> facts = top.stream().map(c -> new Fact(c.label(), c.cents(), null)).toList();
            return answer("TOP_CATEGORIES", raw, "Die meisten Ausgaben " + period(m) + ": " + top.get(0).label() + " mit "
                    + Money.format(top.get(0).cents()) + " (" + percent(top.get(0).cents(), m.expenseCents())
                    + " % aller Ausgaben).", facts);
        }
        if (cat != null) {
            MonthSummaryDto m = pick(months, month);
            long spent = m.categories().stream().filter(c -> c.category() == cat)
                    .mapToLong(MonthSummaryDto.CategoryAmount::cents).sum();
            long avg = average(months, cat);
            String text = cat.isIncome()
                    ? "Durch " + cat.label() + " hast du " + period(m) + " insgesamt " + Money.format(spent) + " eingenommen."
                    : "Für " + cat.label() + " hast du " + period(m) + " insgesamt " + Money.format(spent) + " ausgegeben.";
            return answer("CATEGORY", raw, text, List.of(new Fact(cat.label(), spent, null),
                    new Fact("Schnitt der letzten 3 vollen Monate", avg, null)));
        }
        if (has(q, "einnahmen", "verdient", "income", "earned")) {
            MonthSummaryDto m = pick(months, month);
            return answer("INCOME", raw, "Deine Einnahmen " + period(m) + ": " + Money.format(m.incomeCents()) + ".",
                    List.of(new Fact("Einnahmen", m.incomeCents(), null)));
        }
        if (has(q, "ausgegeben", "ausgaben", "kosten", "spent", "spend", "expenses")) {
            MonthSummaryDto m = pick(months, month);
            return answer("TOTAL", raw, "Deine Ausgaben " + period(m) + ": " + Money.format(m.expenseCents())
                    + ", davon fest " + Money.format(m.fixedCents()) + ".", List.of(new Fact("Ausgaben", m.expenseCents(), null),
                    new Fact("Fixkosten", m.fixedCents(), null)));
        }
        return answer("UNKNOWN", raw, "Das kann ich aus deinen Daten nicht beantworten. Frag zum Beispiel nach Ausgaben "
                + "einer Kategorie, Sparpotenzialen, dem Monatsende, Budgets oder dem Monatsbericht.", List.of());
    }

    private Answer savingsAnswer(String raw, List<MonthSummaryDto> months) {
        List<MonthSummaryDto> full = fullMonths(months, 3);
        if (full.isEmpty()) {
            return answer("SAVINGS", raw, "Für Sparpotenziale brauche ich mindestens einen vollen Monat mit Ausgaben.", List.of());
        }
        MonthSummaryDto last = full.get(full.size() - 1);
        List<Fact> facts = new ArrayList<>();
        long potential = 0;
        for (Category c : Category.values()) {
            if (c.kind() != Category.Kind.VARIABLE) {
                continue;
            }
            long avg = average(months, c);
            long lastSpent = last.categories().stream().filter(x -> x.category() == c)
                    .mapToLong(MonthSummaryDto.CategoryAmount::cents).sum();
            long tenPercent = Math.round(avg * 0.10);
            long over = Math.max(0, lastSpent - avg);
            long p = Math.max(tenPercent, over);
            if (p >= 500) {
                facts.add(new Fact(c.label(), p, null));
                potential += p;
            }
        }
        facts.sort((a, b) -> Long.compare(b.amountCents(), a.amountCents()));
        if (potential == 0) {
            return answer("SAVINGS", raw, "Bei deinen variablen Ausgaben sehe ich gerade kein nennenswertes Sparpotenzial.", List.of());
        }
        return answer("SAVINGS", raw, "Etwa " + Money.format(potential) + " pro Monat (" + Money.format(potential * 12)
                + " im Jahr) sind drin, wenn du variable Ausgaben um 10 % senkst bzw. auf deinen Schnitt zurückführst. "
                + "Am meisten bei " + facts.get(0).label() + ".", facts);
    }

    private Answer forecastAnswer(Long userId, String raw) {
        Forecast f = forecasts.forecast(userId, 31);
        if (f.monthEnds().isEmpty()) {
            return answer("FORECAST", raw, "Für eine Prognose fehlen noch Daten.", List.of());
        }
        MonthEnd end = f.monthEnds().get(0);
        String text = end.expectedCents() >= 0
                ? "Ja, voraussichtlich bleiben am Monatsende " + Money.format(end.expectedCents()) + " (ungünstig: "
                        + Money.format(end.worstCents()) + ")."
                : "Eher nicht: am Monatsende wären es voraussichtlich " + Money.format(end.expectedCents()) + ".";
        List<String> notes = f.lowest() != null && f.lowest().expectedCents() < 0
                ? List.of("Achtung: der Kontostand würde zwischendurch unter 0 € fallen.") : List.of();
        return answer("FORECAST", raw, text, List.of(new Fact("Erwartet am Monatsende", end.expectedCents(), null),
                new Fact("Ungünstiger Fall", end.worstCents(), null), new Fact("Günstiger Fall", end.bestCents(), null)),
                notes);
    }

    private Answer budgetAnswer(Long userId, String raw) {
        BudgetOverview o = budgets.overview(userId);
        if (o.items().isEmpty()) {
            return answer("BUDGET", raw, "Du hast noch keine Budgets. Lege sie unter „Budgets & Prognose“ an.", List.of());
        }
        long exceeded = o.items().stream().filter(i -> "OVER".equals(i.status())).count();
        List<Fact> facts = new ArrayList<>();
        for (BudgetItem i : o.items()) {
            facts.add(new Fact(i.label(), i.remainingCents(), null));
        }
        String text = exceeded == 0
                ? "Alle " + o.items().size() + " Budgets sind eingehalten. Noch " + o.daysLeft() + " Tage im Monat."
                : exceeded + " von " + o.items().size() + " Budgets sind überschritten. Noch " + o.daysLeft() + " Tage im Monat.";
        return answer("BUDGET", raw, text, facts);
    }

    private Answer answer(String intent, String question, String text, List<Fact> facts) {
        return answer(intent, question, text, facts, List.of());
    }

    private Answer answer(String intent, String question, String text, List<Fact> facts, List<String> notes) {
        return new Answer(intent, question, text, facts, notes, BASIS, DISCLAIMER);
    }

    // ---------------------------------------------------------------- Monatsbericht

    @Transactional(readOnly = true)
    public Report report(Long userId, YearMonth month) {
        YearMonth now = YearMonth.now(clock);
        if (month.isAfter(now) || month.isBefore(now.minusMonths(12))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Monatsberichte gibt es für die letzten 12 Monate.");
        }
        List<MonthSummaryDto> months = insights.months(userId, 14);
        MonthSummaryDto m = byMonth(months, month);
        MonthSummaryDto prev = byMonth(months, month.minusMonths(1));
        List<CategoryLine> lines = new ArrayList<>();
        for (MonthSummaryDto.CategoryAmount c : m.categories()) {
            if (!c.category().isExpense()) {
                continue;
            }
            long before = prev.categories().stream().filter(x -> x.category() == c.category())
                    .mapToLong(MonthSummaryDto.CategoryAmount::cents).sum();
            Integer change = before > 0 ? (int) Math.round((c.cents() - before) * 100.0 / before) : null;
            lines.add(new CategoryLine(c.category().name(), c.label(), c.cents(), before, change));
        }
        Integer rate = m.incomeCents() > 0 ? (int) Math.round(m.freeCents() * 100.0 / m.incomeCents()) : null;
        List<String> highlights = new ArrayList<>();
        if (m.hasData()) {
            highlights.add("Einnahmen " + Money.format(m.incomeCents()) + ", Ausgaben " + Money.format(m.expenseCents())
                    + ", übrig " + Money.format(m.freeCents()) + ".");
            if (rate != null) {
                highlights.add("Sparquote " + rate + " %.");
            }
            if (prev.hasData() && prev.expenseCents() > 0) {
                long diff = m.expenseCents() - prev.expenseCents();
                highlights.add(diff > 0 ? "Du hast " + Money.format(diff) + " mehr ausgegeben als im Vormonat."
                        : diff < 0 ? "Du hast " + Money.format(-diff) + " weniger ausgegeben als im Vormonat."
                        : "Gleich viel ausgegeben wie im Vormonat.");
            }
            lines.stream().filter(l -> l.changePercent() != null && l.changePercent() >= 30 && l.cents() - l.previousCents() >= 2000)
                    .findFirst().ifPresent(l -> highlights.add(l.label() + " ist um " + l.changePercent()
                            + " % gestiegen (" + Money.format(l.previousCents()) + " → " + Money.format(l.cents()) + ")."));
            if (!lines.isEmpty()) {
                highlights.add("Größter Posten: " + lines.get(0).label() + " mit " + Money.format(lines.get(0).cents()) + ".");
            }
        }
        return new Report(month.toString(), monthLabel(month), m.complete(), m.hasData(), m.incomeCents(),
                m.expenseCents(), m.freeCents(), rate, prev.expenseCents(), lines, highlights, BASIS, DISCLAIMER);
    }

    // ---------------------------------------------------------------- Szenarien

    @Transactional(readOnly = true)
    public Scenario scenario(Long userId, ScenarioRequest req) {
        OverviewDto o = savings.overview(userId);
        if (!o.hasData()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_DATA",
                    "Für Szenarien brauche ich mindestens einen vollen Monat mit Einnahmen und Ausgaben.");
        }
        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        long free = o.avgFreeCents();
        long income = o.avgIncomeCents();
        List<String> warnings = new ArrayList<>();
        List<String> assumptions = new ArrayList<>(List.of(
                "Durchschnitt der letzten " + o.monthsUsed() + " vollen Monate: Einnahmen " + Money.format(income)
                        + ", Ausgaben " + Money.format(o.avgExpenseCents()) + "."));
        long monthly;
        String text;
        Integer monthsToTarget = null;
        switch (type) {
            case "CUT_CATEGORY" -> {
                Category c = parseCategory(req.category());
                long amount = positive(req.amountCents());
                long avg = average(insights.months(userId, 4), c);
                if (amount > avg) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SCENARIO_TOO_HIGH",
                            "Das ist mehr, als du im Schnitt für " + c.label() + " ausgibst (" + Money.format(avg) + ").");
                }
                monthly = amount;
                text = "Wenn du " + Money.format(amount) + " pro Monat weniger für " + c.label() + " ausgibst, hast du "
                        + Money.format(amount * 12) + " im Jahr mehr übrig.";
            }
            case "INCOME_CHANGE" -> {
                int p = req.percent() == null ? 0 : req.percent();
                if (p == 0 || p < -50 || p > 50) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERCENT", "Bitte eine Änderung zwischen −50 % und +50 % angeben.");
                }
                monthly = Math.round(income * p / 100.0);
                text = (p > 0 ? "Mit " + p + " % mehr Einnahmen" : "Mit " + (-p) + " % weniger Einnahmen")
                        + " bleiben dir pro Monat " + Money.format(free + monthly) + " statt " + Money.format(free) + ".";
                assumptions.add("Ausgaben bleiben gleich.");
            }
            case "EXTRA_SAVING" -> {
                long amount = positive(req.amountCents());
                long target = positive(req.targetCents());
                monthly = -amount;
                monthsToTarget = (int) ((target + amount - 1) / amount);
                text = "Mit " + Money.format(amount) + " pro Monat erreichst du " + Money.format(target) + " in "
                        + monthsToTarget + " Monaten.";
                if (amount > free) {
                    warnings.add("Das ist mehr, als dir im Schnitt pro Monat übrig bleibt (" + Money.format(free) + ").");
                }
                assumptions.add("Ohne Zinsen gerechnet.");
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCENARIO", "Unbekanntes Szenario.");
        }
        long newFree = free + monthly;
        if (newFree < 0) {
            warnings.add("Damit gibst du mehr aus, als du einnimmst.");
        }
        long newIncome = "INCOME_CHANGE".equals(type) ? income + monthly : income;
        return new Scenario(type, text, monthly, monthly * 12, free, newFree, rate(free, income), rate(newFree, newIncome),
                monthsToTarget, warnings, assumptions, SCENARIO_DISCLAIMER);
    }

    // ---------------------------------------------------------------- Hilfen

    static String normalize(String s) {
        String n = s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return Normalizer.normalize(n, Normalizer.Form.NFKC);
    }

    private static boolean has(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) {
                return true;
            }
        }
        return false;
    }

    static Category categoryIn(String q) {
        for (var e : KEYWORDS.entrySet()) {
            for (String w : e.getValue()) {
                if (q.matches("(?s).*\\b" + w + ".*")) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** Monat aus der Frage: „letzten Monat“, „diesen Monat“ oder ein Monatsname (der letzte vergangene). */
    YearMonth monthIn(String q) {
        YearMonth now = YearMonth.now(clock);
        if (has(q, "letzten monat", "letzter monat", "vormonat", "last month", "previous month")) {
            return now.minusMonths(1);
        }
        if (has(q, "diesen monat", "dieser monat", "this month", "aktuell")) {
            return now;
        }
        for (int i = 0; i < 12; i++) {
            String de = normalize(MONTHS_DE[i]);
            if (q.matches("(?s).*\\b(" + de + "|" + MONTHS_EN[i] + ")\\b.*")) {
                YearMonth ym = YearMonth.of(now.getYear(), i + 1);
                return ym.isAfter(now) ? ym.minusYears(1) : ym;
            }
        }
        return null;
    }

    private MonthSummaryDto pick(List<MonthSummaryDto> months, YearMonth month) {
        return byMonth(months, month == null ? YearMonth.now(clock) : month);
    }

    private static MonthSummaryDto byMonth(List<MonthSummaryDto> months, YearMonth ym) {
        return months.stream().filter(m -> m.month().equals(ym.toString())).findFirst()
                .orElse(new MonthSummaryDto(ym.toString(), true, false, 0, 0, 0, 0, List.of()));
    }

    private static List<MonthSummaryDto> fullMonths(List<MonthSummaryDto> months, int n) {
        List<MonthSummaryDto> full = months.stream().filter(MonthSummaryDto::complete).filter(MonthSummaryDto::hasData).toList();
        return full.subList(Math.max(0, full.size() - n), full.size());
    }

    /** Schnitt der letzten drei vollen Monate mit Daten in einer Kategorie. */
    private static long average(List<MonthSummaryDto> months, Category c) {
        List<MonthSummaryDto> full = fullMonths(months, 3);
        if (full.isEmpty()) {
            return 0;
        }
        long sum = full.stream().flatMap(m -> m.categories().stream()).filter(x -> x.category() == c)
                .mapToLong(MonthSummaryDto.CategoryAmount::cents).sum();
        return sum / full.size();
    }

    private String period(MonthSummaryDto m) {
        return m.month().equals(YearMonth.now(clock).toString()) ? "in diesem Monat (bisher)" : "im " + label(m);
    }

    private static String label(MonthSummaryDto m) {
        return monthLabel(YearMonth.parse(m.month()));
    }

    static String monthLabel(YearMonth ym) {
        return MONTHS_DE[ym.getMonthValue() - 1] + " " + ym.getYear();
    }

    private static long percent(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 100.0 / total);
    }

    private static Integer rate(long free, long income) {
        return income > 0 ? (int) Math.round(free * 100.0 / income) : null;
    }

    private static long positive(Long v) {
        if (v == null || v <= 0 || v > 10_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Der Betrag muss zwischen 0,01 € und 100.000 € liegen.");
        }
        return v;
    }

    private static Category parseCategory(String c) {
        try {
            Category cat = Category.valueOf(c == null ? "" : c.trim().toUpperCase(Locale.ROOT));
            if (cat.kind() == Category.Kind.VARIABLE || cat.kind() == Category.Kind.FIXED) {
                return cat;
            }
        } catch (IllegalArgumentException ignored) {
            // unten
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Unbekannte Kategorie.");
    }
}
