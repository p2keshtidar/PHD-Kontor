package de.frauas.girokonto.common;

import java.util.Map;

/** Einheitliches Fehlerformat aller Endpunkte. */
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
