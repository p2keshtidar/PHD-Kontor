package de.frauas.girokonto.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Bitte Eingaben prüfen.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", "Ungültige Anfrage."));
    }

    /** Falscher Typ in URL oder Parameter, z. B. /api/plans/abc oder ?direction=SEITWAERTS */
    @ExceptionHandler(org.springframework.beans.TypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(org.springframework.beans.TypeMismatchException e) {
        String name = e.getPropertyName() == null ? "Wert" : e.getPropertyName();
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", "Ungültiger Wert für „" + name + "“."));
    }

    /** Prüfungen an Methodenparametern (@Min, @Max …) */
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> handleConstraint(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", "Bitte Eingaben prüfen."));
    }

    /** Verstoß gegen eine Datenbank-Regel (z. B. eindeutiger Schlüssel bei gleichzeitigen Anfragen) */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Datenbank-Regel verletzt: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", "Die Aktion passt nicht zum aktuellen Stand. Bitte erneut versuchen."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", "Keine Berechtigung."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e) {
        // Fehler, die Spring selbst mit Status kennt (z. B. 404 unbekannter Pfad, 405 falsche Methode)
        if (e instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode())
                    .body(ApiError.of("HTTP_" + er.getStatusCode().value(), "Anfrage nicht möglich."));
        }
        log.error("Unerwarteter Fehler", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Interner Fehler. Bitte später erneut versuchen."));
    }
}
