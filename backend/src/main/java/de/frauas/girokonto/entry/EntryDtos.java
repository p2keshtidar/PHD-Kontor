package de.frauas.girokonto.entry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class EntryDtos {

    private EntryDtos() {
    }

    /**
     * @param kind           INCOME oder EXPENSE
     * @param category       z. B. GEHALT, WOHNEN, FREIZEIT (siehe GET /api/entries/categories)
     * @param intervalMonths 0 = einmalig, 1 = monatlich, 3 = vierteljährlich, 6 = halbjährlich, 12 = jährlich
     * @param date           einmalig: das Datum; regelmäßig: erste Buchung (Tag 1 bis 28).
     *                       Bis zu 12 Monate zurück: vergangene Termine werden sofort nachgebucht.
     */
    public record CreateRequest(
            @NotNull(message = "Bitte Einnahme oder Ausgabe wählen.") Entry.Kind kind,
            @NotBlank(message = "Bitte eine Bezeichnung eingeben.")
            @Size(max = 60, message = "Die Bezeichnung darf höchstens 60 Zeichen haben.") String title,
            String category,
            @NotNull(message = "Betrag fehlt.") @Positive(message = "Der Betrag muss größer als 0 sein.") Long amountCents,
            Integer intervalMonths,
            @NotNull(message = "Datum fehlt.") LocalDate date,
            LocalDate endDate) {}

    public record UpdateRequest(
            @Positive(message = "Der Betrag muss größer als 0 sein.") Long amountCents,
            @Size(max = 60, message = "Die Bezeichnung darf höchstens 60 Zeichen haben.") String title,
            String category,
            Boolean paused,
            LocalDate endDate,
            Boolean removeEndDate) {}

    public record EntryDto(
            Long id,
            Entry.Kind kind,
            String title,
            String category,
            String categoryLabel,
            long amountCents,
            int intervalMonths,
            String intervalLabel,
            LocalDate startDate,
            LocalDate endDate,
            Entry.Status status,
            LocalDate nextDate,
            LocalDate lastBookedDate,
            int bookedCount,
            /** Betrag umgerechnet auf einen Monat (+ Einnahme, − Ausgabe); einmalig: 0 */
            long monthlyCents) {}

    /** Antwort beim Anlegen: der Eintrag und wie viele Termine sofort gebucht wurden. */
    public record CreateResult(EntryDto entry, int bookedNow, long bookedNowCents) {}

    public record Summary(long monthlyIncomeCents, long monthlyExpenseCents, long monthlyBalanceCents,
                          List<EntryDto> entries) {}

    public record CategoryOption(String key, String label, String kind) {}
}
