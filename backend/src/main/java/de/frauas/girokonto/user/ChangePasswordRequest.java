package de.frauas.girokonto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Altes Passwort fehlt.") String oldPassword,
        @NotBlank @Size(min = 10, max = 100, message = "Das Passwort muss mindestens 10 Zeichen haben.")
        String newPassword) {
}
