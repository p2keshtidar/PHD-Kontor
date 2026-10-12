package de.frauas.girokonto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Vorname fehlt.") @Size(max = 100) String firstName,
        @NotBlank(message = "Nachname fehlt.") @Size(max = 100) String lastName,
        @NotBlank(message = "Telefonnummer fehlt.")
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "Telefonnummer ist ungültig.") String phone) {
}
