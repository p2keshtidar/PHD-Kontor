package de.frauas.girokonto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-Mail fehlt.") String email,
        @NotBlank(message = "Passwort fehlt.") String password) {
}
