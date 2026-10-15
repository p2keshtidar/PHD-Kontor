package de.frauas.girokonto.subaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class SubaccountRequests {

    private SubaccountRequests() {
    }

    /**
     * Digitales Konto anlegen oder ändern (/F410/). icon: Schlüssel wie "flight", "home", "gift".
     * purpose: Zweck (siehe DigitalPurpose), targetCents/targetDate: optionales Ziel.
     */
    public record Save(
            @NotBlank(message = "Name fehlt.") @Size(max = 40, message = "Der Name darf höchstens 40 Zeichen haben.") String name,
            @Size(max = 30) String icon,
            @Size(max = 20) String purpose,
            @Positive(message = "Der Zielbetrag muss größer als 0 sein.")
            @jakarta.validation.constraints.Max(value = 100_000_000, message = "Zielbetrag höchstens 1.000.000,00 €.")
            Long targetCents,
            java.time.LocalDate targetDate) {

        public Save(String name, String icon) {
            this(name, icon, null, null, null);
        }
    }

    /** Geld zwischen eigenen Konten verschieben (/F420/). */
    public record Move(
            @NotNull(message = "Quellkonto fehlt.") Long fromAccountId,
            @NotNull(message = "Zielkonto fehlt.") Long toAccountId,
            @Positive(message = "Der Betrag muss größer als 0 sein.") long amountCents) {}
}
