package de.frauas.girokonto.subaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class SubaccountRequests {

    private SubaccountRequests() {
    }

    /** Unterkonto anlegen oder umbenennen (/F410/). icon: Schlüssel wie "flight", "home", "gift". */
    public record Save(
            @NotBlank(message = "Name fehlt.") @Size(max = 40, message = "Der Name darf höchstens 40 Zeichen haben.") String name,
            @Size(max = 30) String icon) {}

    /** Geld zwischen eigenen Konten verschieben (/F420/). */
    public record Move(
            @NotNull(message = "Quellkonto fehlt.") Long fromAccountId,
            @NotNull(message = "Zielkonto fehlt.") Long toAccountId,
            @Positive(message = "Der Betrag muss größer als 0 sein.") long amountCents) {}
}
