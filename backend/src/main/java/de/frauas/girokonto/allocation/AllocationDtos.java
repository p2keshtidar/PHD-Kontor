package de.frauas.girokonto.allocation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Anfragen und Antworten für Sparregeln und Geldzuweisungen. */
public final class AllocationDtos {

    private AllocationDtos() {
    }

    /**
     * Neue Sparregel.
     *
     * @param sourceType PROJEKTBANK (eigenes Girokonto, echte Umbuchung in der Simulation) oder EXTERNAL
     *                   (verbundenes Bankkonto, nur Vormerkung)
     * @param sourceId   bei EXTERNAL die Kennung des verbundenen Kontos; bei PROJEKTBANK leer (eigenes Girokonto)
     * @param interval   WEEKLY, BIWEEKLY, MONTHLY (Standard) oder QUARTERLY
     * @param startDate  erste Ausführung (heute oder später; Standard: heute)
     */
    public record CreateRuleRequest(@NotNull Long targetAccountId, String sourceType, Long sourceId,
                                    @NotNull @Positive Long amountCents, String interval, LocalDate startDate,
                                    LocalDate endDate) {}

    /** Ändern: nur gesetzte Felder werden übernommen. */
    public record UpdateRuleRequest(@Positive Long amountCents, String interval, LocalDate endDate,
                                    Boolean removeEndDate) {}

    /** Zusätzliche Vormerkung aus einem verbundenen Bankkonto. */
    public record EarmarkRequest(@NotNull Long targetAccountId, @NotNull Long externalAccountId,
                                 @NotNull @Positive Long amountCents) {}

    /** Vormerkung aufheben; ohne Betrag wird alles von diesem Bankkonto für dieses digitale Konto aufgehoben. */
    public record ReleaseRequest(@NotNull Long targetAccountId, @NotNull Long externalAccountId,
                                 @Positive Long amountCents) {}

    /**
     * Mögliche Finanzierungsquelle.
     *
     * @param movesMoney     true = Geld wird (in der Simulation) wirklich umgebucht; false = nur Vormerkung
     * @param availableCents Kontostand minus bereits vorgemerkter Beträge
     * @param coverageWarning Vormerkungen sind höher als der letzte Kontostand
     */
    public record SourceDto(String type, Long id, String sourceName, String bankName, String ibanMasked,
                            Long balanceCents, long earmarkedCents, Long availableCents, Instant balanceAt,
                            boolean usable, String unusableReason, boolean movesMoney, String sourceExplanation,
                            boolean sandbox, boolean coverageWarning, String coverageText) {}

    public record RunDto(Long id, Long ruleId, Long targetAccountId, String targetName, String sourceType,
                         String sourceName, String kind, String kindLabel, LocalDate dueDate, long amountCents,
                         String status, String statusLabel, int attempts, LocalDate nextAttempt, String failureReason,
                         Long bookingId, boolean manual, Instant updatedAt) {}

    public record RuleDto(Long id, Long targetAccountId, String targetName, String sourceType, Long sourceId,
                          String sourceName, String bankName, boolean movesMoney, String sourceExplanation,
                          long amountCents, String interval, String intervalLabel, LocalDate startDate,
                          LocalDate endDate, LocalDate nextDate, String status, String statusLabel,
                          String statusReason, RunDto lastRun, Instant createdAt) {}

    /**
     * Vormerkung je Quelle. Bei Vormerkungen anderer Mitglieder nur der Name der Person (keine Bankdaten).
     *
     * @param active Bankverbindung ist aktiv; sonst ist die Deckung nicht mehr geprüft
     */
    public record EarmarkDto(Long externalAccountId, String sourceName, String bankName, long amountCents,
                             boolean own, boolean active) {}

    public record UpcomingDto(LocalDate date, long amountCents, Long ruleId, String sourceName, boolean movesMoney) {}

    /**
     * Alles zu Sparregeln eines digitalen Kontos.
     *
     * @param rules        eigene Sparregeln für dieses Konto
     * @param otherRules   Sparregeln anderer Mitglieder (bei geteilten Konten; nur die Anzahl)
     * @param runs         letzte Vorgänge (ausgeführt, vorgemerkt, Wiederholung geplant, fehlgeschlagen)
     * @param upcoming     nächste geplante Termine der eigenen Regeln ("ausstehend")
     * @param projectedGoalDate wann das Ziel voraussichtlich erreicht ist (alle aktiven Regeln, ohne Gewähr)
     */
    public record AccountAllocations(Long accountId, String targetName, boolean canAllocate, boolean canRelease,
                                     long balanceCents, long earmarkedCents, List<EarmarkDto> earmarks,
                                     List<RuleDto> rules, int otherRules, List<RunDto> runs,
                                     List<UpcomingDto> upcoming, LocalDate projectedGoalDate, String projectionText,
                                     String earmarkExplanation) {}
}
