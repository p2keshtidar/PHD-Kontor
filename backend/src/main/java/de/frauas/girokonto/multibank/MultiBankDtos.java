package de.frauas.girokonto.multibank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Weitere Konten der Projektbank und konsolidierte Umsätze (alles Simulation mit Spielgeld). */
public final class MultiBankDtos {

    private MultiBankDtos() {
    }

    /**
     * Ein Kontoangebot der Projektbank.
     *
     * @param type            CURRENT (weiteres Girokonto) oder CALL (Tagesgeld)
     * @param interestPercent fiktiver Zinssatz der Simulation (nur Tagesgeld)
     */
    public record Product(String type, String label, String description, BigDecimal interestPercent,
                          boolean simulation) {}

    public record Products(List<Product> products, int openCount, int maxCount, boolean canOpen, String disclaimer) {}

    public record OpenRequest(@NotBlank(message = "Bitte eine Kontoart wählen.") String type,
                              @Size(max = 40, message = "Der Name darf höchstens 40 Zeichen haben.") String name) {}

    public record RenameRequest(@NotBlank(message = "Bitte einen Namen eingeben.")
                                @Size(max = 40, message = "Der Name darf höchstens 40 Zeichen haben.") String name) {}

    /**
     * Ergebnis beim Auflösen.
     *
     * @param interestCents Zinsen bis gestern (nur Tagesgeld), vor dem Übertrag gutgeschrieben
     * @param movedCents    Restguthaben, das aufs Girokonto gebucht wurde
     */
    public record CloseResult(long interestCents, long movedCents, String message) {}

    /**
     * Ein Umsatz in der konsolidierten Übersicht.
     *
     * @param source    PROJEKTBANK (Simulation) oder EXTERNAL (verbundene Bank, nur lesend)
     * @param id        Buchung (Projektbank) bzw. Umsatz der verbundenen Bank
     * @param bookedAt  Zeitpunkt (nur Projektbank; Banken liefern nur das Buchungsdatum)
     * @param status    BOOKED oder PENDING (vorgemerkt, nur verbundene Banken)
     * @param internal  Umbuchung zwischen eigenen Konten – zählt nicht als Einnahme oder Ausgabe
     * @param excluded  vom Nutzer aus der Auswertung genommen (nur verbundene Banken)
     */
    public record TxItem(String source, Long id, Long accountId, String accountName, String institution,
                         LocalDate date, Instant bookedAt, long amountCents, String currency, String counterpartyName,
                         String description, String category, String categoryLabel, String status, boolean internal,
                         boolean excluded) {}

    /**
     * Eine Seite der konsolidierten Umsätze.
     *
     * @param incomeCents  Summe der Einnahmen im Filter (gebucht, in Euro, ohne Umbuchungen und ausgenommene Umsätze)
     * @param expenseCents Summe der Ausgaben im Filter (positiv)
     */
    public record TxPage(List<TxItem> items, int page, int size, boolean hasMore, long incomeCents, long expenseCents,
                         List<String> notes) {}
}
