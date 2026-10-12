package de.frauas.girokonto.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param recipient IBAN, E-Mail oder Telefonnummer */
public record AddContactRequest(
        @NotBlank(message = "Empfänger fehlt.") String recipient,
        @Size(max = 100, message = "Der Spitzname darf höchstens 100 Zeichen haben.") String nickname) {
}
