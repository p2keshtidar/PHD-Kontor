package de.frauas.girokonto.common;

import org.springframework.http.HttpStatus;

/** Fachlicher Fehler mit HTTP-Status, maschinenlesbarem Code und deutscher Meldung für die App. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
