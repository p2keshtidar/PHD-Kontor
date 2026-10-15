package de.frauas.girokonto.banking.provider;

/** Fehler beim Anbieter. Die Meldung darf keine Bank- oder Zugangsdaten enthalten. */
public class ProviderException extends RuntimeException {

    public enum Kind {
        /** vorübergehend (Zeitüberschreitung, 5xx) – erneut versuchen */
        TRANSIENT,
        /** Freigabe abgelaufen oder widerrufen – Nutzer muss neu verbinden */
        AUTH_EXPIRED,
        /** Abruf-Grenze der Bank erreicht – später erneut */
        RATE_LIMITED,
        /** dauerhaft (falsche Anfrage, Konfiguration) */
        PERMANENT
    }

    private final Kind kind;

    public ProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean transientError() {
        return kind == Kind.TRANSIENT;
    }
}
