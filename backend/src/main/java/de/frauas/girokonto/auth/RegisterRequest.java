package de.frauas.girokonto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Eingaben der Registrierung (/F010/). Das Mindestalter 18 prüft der AuthService. */
public record RegisterRequest(
        @NotBlank(message = "Vorname fehlt.") @Size(max = 100) String firstName,
        @NotBlank(message = "Nachname fehlt.") @Size(max = 100) String lastName,
        @NotBlank(message = "E-Mail fehlt.") @Email(message = "E-Mail ist ungültig.") @Size(max = 255) String email,
        @NotBlank(message = "Telefonnummer fehlt.")
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "Telefonnummer ist ungültig.") String phone,
        @NotNull(message = "Geburtsdatum fehlt.") @Past(message = "Geburtsdatum muss in der Vergangenheit liegen.")
        LocalDate birthDate,
        @NotBlank(message = "Passwort fehlt.")
        @Size(min = 10, max = 100, message = "Das Passwort muss mindestens 10 Zeichen haben.") String password) {
}
