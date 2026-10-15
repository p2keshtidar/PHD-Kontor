package de.frauas.girokonto.health;

import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.loan.Loan;
import de.frauas.girokonto.loan.LoanService;
import de.frauas.girokonto.planning.BudgetService;
import de.frauas.girokonto.planning.PlanningDtos.BudgetItem;
import de.frauas.girokonto.savings.SavingsPlanService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interner Finanz-Check (0–100 Punkte) aus den eigenen Daten der App: Sparquote, Notgroschen, Verschuldungsquote,
 * Budgets. Ausdrücklich keine SCHUFA-Auskunft und keine Bonitätsbewertung – nur eine Orientierung.
 */
@Service
public class HealthScoreService {

    static final String DISCLAIMER = "Interner Finanz-Check der App aus deinen eigenen Daten – keine SCHUFA-Auskunft, "
            + "keine Bonitätsbewertung, keine Finanzberatung.";

    /** Ein Bestandteil des Scores (je 0–25 Punkte). */
    public record Component(String key, String label, int points, int max, String value, String text) {}

    /** @param level GUT (ab 70), MITTEL (40–69), KRITISCH (unter 40), OHNE_DATEN */
    public record Score(int score, String level, String levelLabel, List<Component> components, List<String> tips,
                        String disclaimer, boolean consented, String consentText) {}

    static final String CONSENT_TEXT = "Der Finanz-Check wertet deine Umsätze, Konten, Kredite und Budgets in der App aus "
            + "und berechnet daraus eine Punktzahl nur für dich. Er wird nicht weitergegeben und ist jederzeit abschaltbar.";

    private final SavingsPlanService plans;
    private final AccountService accounts;
    private final LoanService loans;
    private final BudgetService budgets;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final java.time.Clock clock;

    public HealthScoreService(SavingsPlanService plans, AccountService accounts, LoanService loans,
                              BudgetService budgets, org.springframework.jdbc.core.JdbcTemplate jdbc, java.time.Clock clock) {
        this.plans = plans;
        this.accounts = accounts;
        this.loans = loans;
        this.budgets = budgets;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Einwilligung erteilen (true) oder widerrufen (false). */
    @Transactional
    public Score consent(Long userId, boolean on) {
        jdbc.update("UPDATE app_user SET health_score_consent_at = ? WHERE id = ?",
                on ? java.sql.Timestamp.from(java.time.Instant.now(clock)) : null, userId);
        return score(userId);
    }

    @Transactional(readOnly = true)
    public Score score(Long userId) {
        Boolean consented = jdbc.queryForObject("SELECT health_score_consent_at IS NOT NULL FROM app_user WHERE id = ?",
                Boolean.class, userId);
        if (!Boolean.TRUE.equals(consented)) {
            return new Score(0, "NICHT_AKTIVIERT", "Nicht aktiviert", List.of(), List.of(), DISCLAIMER, false, CONSENT_TEXT);
        }
        OverviewDto o = plans.overview(userId);
        boolean data = o.hasData() && o.avgIncomeCents() > 0;
        long income = data ? o.avgIncomeCents() : 0;
        long expense = data ? o.avgExpenseCents() : 0;
        List<Component> c = new ArrayList<>();
        List<String> tips = new ArrayList<>();

        // 1. Sparquote
        if (!data) {
            c.add(new Component("SAVINGS_RATE", "Sparquote", 0, 25, "–",
                    "Noch keine vollständigen Monate mit Einnahmen – trag Einnahmen und Ausgaben ein."));
            tips.add("Trag dein Gehalt und feste Ausgaben unter „Einnahmen & Ausgaben“ ein.");
        } else {
            double rate = (income - expense) * 100.0 / income;
            int p = rate >= 20 ? 25 : rate >= 10 ? 18 : rate >= 0 ? 10 : 0;
            c.add(new Component("SAVINGS_RATE", "Sparquote", p, 25, Math.round(rate) + " %",
                    "Von deinen Einnahmen bleiben im Schnitt " + Math.round(rate) + " % übrig (Ziel: 20 %)."));
            if (p < 25) {
                tips.add("Prüfe die größten Ausgaben im Finanzüberblick und lege Budgets fest.");
            }
        }

        // 2. Notgroschen: frei verfügbares Geld in Monatsausgaben
        long liquid = 0;
        for (AccountDto a : accounts.listForUser(userId)) {
            if (a.type() == AccountType.MAIN || a.type() == AccountType.CURRENT || a.type() == AccountType.CALL
                    || a.type() == AccountType.SUB) {
                liquid += a.balanceCents();
            }
        }
        if (!data || expense <= 0) {
            c.add(new Component("EMERGENCY_FUND", "Notgroschen", 0, 25, Money.format(liquid),
                    "Ohne Ausgaben aus Vormonaten lässt sich der Notgroschen nicht einschätzen."));
        } else {
            double months = liquid / (double) expense;
            int p = months >= 3 ? 25 : months >= 1 ? 15 : 5;
            String value = String.format(java.util.Locale.GERMANY, "%.1f Monate", months);
            c.add(new Component("EMERGENCY_FUND", "Notgroschen", p, 25, value,
                    "Dein verfügbares Geld reicht für etwa " + value + " Ausgaben (Ziel: 3 bis 6 Monate)."));
            if (p < 25) {
                tips.add("Baue einen Notgroschen von drei Monatsausgaben auf, z. B. mit einem digitalen Konto und einer Sparregel.");
            }
        }

        // 3. Verschuldungsquote
        long rates = loans.list(userId).stream().filter(l -> l.status() == Loan.Status.ACTIVE)
                .mapToLong(l -> l.monthlyRateCents()).sum();
        if (rates == 0) {
            c.add(new Component("DEBT_RATIO", "Verschuldung", 25, 25, "0 %", "Keine laufenden Kredite."));
        } else if (!data) {
            c.add(new Component("DEBT_RATIO", "Verschuldung", 0, 25, Money.format(rates) + " pro Monat",
                    "Kreditraten ohne bekannte Einnahmen."));
            tips.add("Trag deine Einnahmen ein, damit die Kreditraten eingeordnet werden können.");
        } else {
            double ratio = rates * 100.0 / income;
            int p = ratio <= 20 ? 18 : ratio <= 35 ? 10 : 0;
            c.add(new Component("DEBT_RATIO", "Verschuldung", p, 25, Math.round(ratio) + " %",
                    "Kreditraten sind " + Math.round(ratio) + " % deiner Einnahmen (unter 20 % gilt als gut tragbar)."));
            if (ratio > 20) {
                tips.add("Prüfe eine Sondertilgung oder eine längere Laufzeit, um die Raten zu senken.");
            }
        }

        // 4. Budgets
        List<BudgetItem> items = budgets.overview(userId).items();
        long over = items.stream().filter(i -> i.status().equals("OVER")).count();
        if (items.isEmpty()) {
            c.add(new Component("BUDGETS", "Budgets", 12, 25, "keine", "Noch keine Budgets festgelegt."));
            tips.add("Lege Budgets für Kategorien wie Lebensmittel oder Freizeit fest.");
        } else {
            int p = over == 0 ? 25 : over == 1 ? 15 : 5;
            c.add(new Component("BUDGETS", "Budgets", p, 25, (items.size() - over) + "/" + items.size(),
                    over == 0 ? "Alle Budgets eingehalten." : over + " Budget(s) in diesem Monat überschritten."));
        }

        int score = c.stream().mapToInt(Component::points).sum();
        String level = !data ? "OHNE_DATEN" : score >= 70 ? "GUT" : score >= 40 ? "MITTEL" : "KRITISCH";
        String label = switch (level) {
            case "GUT" -> "Gut aufgestellt";
            case "MITTEL" -> "Ausbaufähig";
            case "KRITISCH" -> "Achtung";
            default -> "Noch zu wenige Daten";
        };
        return new Score(score, level, label, c, tips, DISCLAIMER, true, CONSENT_TEXT);
    }
}
