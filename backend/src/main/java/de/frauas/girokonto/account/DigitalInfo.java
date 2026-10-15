package de.frauas.girokonto.account;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Angaben zu einem digitalen Konto (Topf innerhalb des Projektbank-Kontos).
 *
 * @param role           OWNER, FULL (einzahlen + auszahlen), DEPOSIT (nur einzahlen), VIEW (nur ansehen)
 * @param custody        wie das Geld gehalten wird: INTERNAL = umgebucht innerhalb der Projektbank (Simulation)
 * @param custodyText    Erklärung für die App – ausdrücklich: kein eigenes Bankkonto, keine eigene Einlagensicherung
 * @param monthlyNeededCents nötige Rate pro Monat bis zum Zieldatum (nur mit Ziel und Datum)
 */
public record DigitalInfo(String purpose, String purposeLabel, Long targetCents, LocalDate targetDate,
                          Double progressPercent, Long remainingCents, Long monthlyNeededCents, boolean goalReached,
                          String role, String roleLabel, boolean canDeposit, boolean canWithdraw, boolean canManage,
                          String custody, String custodyLabel, String custodyText) {

    public static final String CUSTODY_LABEL = "Umgebucht innerhalb der Projektbank (Simulation)";
    public static final String CUSTODY_TEXT = "Das Geld wurde von einem Girokonto der Projektbank hierher umgebucht. "
            + "Ein digitales Konto ist ein Topf in deinem Konto – kein eigenes Bankkonto und ohne eigene Einlagensicherung.";

    public static DigitalInfo of(Account a, long balanceCents, String role, LocalDate today) {
        DigitalPurpose purpose = DigitalPurpose.of(a.getPurpose());
        Long target = a.getTargetCents();
        Double progress = null;
        Long remaining = null;
        Long monthly = null;
        boolean reached = false;
        if (target != null && target > 0) {
            progress = Math.min(100.0, Math.round(Math.max(balanceCents, 0) * 1000.0 / target) / 10.0);
            remaining = Math.max(0, target - balanceCents);
            reached = balanceCents >= target;
            LocalDate date = a.getTargetDate();
            if (!reached && date != null && date.isAfter(today)) {
                long months = Math.max(1, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), date.withDayOfMonth(1)));
                monthly = (remaining + months - 1) / months;
            }
        }
        String r = role == null ? "VIEW" : role;
        boolean owner = r.equals("OWNER");
        return new DigitalInfo(purpose.name(), purpose.label(), target, a.getTargetDate(), progress, remaining, monthly,
                reached, r, roleLabel(r), !r.equals("VIEW"), owner || r.equals("FULL"), owner,
                "INTERNAL", CUSTODY_LABEL, CUSTODY_TEXT);
    }

    public static String roleLabel(String role) {
        return switch (role) {
            case "OWNER" -> "Besitzer";
            case "FULL" -> "Einzahlen und auszahlen";
            case "DEPOSIT" -> "Nur einzahlen";
            default -> "Nur ansehen";
        };
    }
}
