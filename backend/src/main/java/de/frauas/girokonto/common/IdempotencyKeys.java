package de.frauas.girokonto.common;

import org.springframework.http.HttpStatus;

/** Prüft den Header {@code Idempotency-Key} einheitlich (gleiche Anfrage zweimal → nur einmal gebucht). */
public final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    /** @return den Schlüssel, wenn er gesetzt und höchstens {@code maxLength} Zeichen lang ist */
    public static String require(String key, int maxLength) {
        if (key == null || key.isBlank() || key.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_MISSING",
                    "Header Idempotency-Key fehlt oder ist zu lang.");
        }
        return key;
    }
}
