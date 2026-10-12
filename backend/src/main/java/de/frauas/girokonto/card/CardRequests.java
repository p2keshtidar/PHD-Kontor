package de.frauas.girokonto.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Eingaben rund um die Karte. */
public final class CardRequests {

    private CardRequests() {
    }

    public record Details(@NotBlank(message = "Passwort fehlt.") String password) {}

    public record Limits(
            @Min(value = 0, message = "Das Limit darf nicht negativ sein.")
            @Max(value = 200000, message = "Das Limit darf höchstens 2.000 € betragen.") long dailyLimitCents,
            boolean onlineEnabled) {}

    /** Kartenzahlung beim Händler-Simulator (/F340/). */
    public record Payment(
            @NotBlank(message = "Händler fehlt.") @Size(max = 60, message = "Händlername zu lang.") String merchant,
            @Positive(message = "Der Betrag muss größer als 0 sein.") long amountCents,
            boolean online) {}
}
