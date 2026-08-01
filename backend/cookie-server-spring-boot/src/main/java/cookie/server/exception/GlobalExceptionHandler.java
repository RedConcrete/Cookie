package cookie.server.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Zentrale Fehlerbehandlung. Ohne das faellt jede Validierungs-Exception (z.B.
 * "Markt-Stock leer", "Nicht genug Cookies") auf Springs generischen 500 durch und
 * der Client bekommt einen rohen Stacktrace-artigen Fehler statt einer sauberen Meldung.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Erwartete Validierungsfehler (ungueltige Eingabe, nicht genug Bestand/Cookies, ...)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // Nicht gefundene Ressourcen (User, Upgrade, ...)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    // Alles andere: echter Serverfehler -- geloggt, aber ohne Stacktrace an den Client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        logger.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unerwarteter Serverfehler."));
    }
}
