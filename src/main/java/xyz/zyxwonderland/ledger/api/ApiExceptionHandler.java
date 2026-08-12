package xyz.zyxwonderland.ledger.api;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(String error, String message, Instant timestamp) {
        static ErrorResponse of(String error, String message) {
            return new ErrorResponse(error, message, Instant.now());
        }
    }

    @ExceptionHandler(UnbalancedTransactionException.class)
    public ResponseEntity<ErrorResponse> handleUnbalanced(UnbalancedTransactionException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("unbalanced_transaction", e.getMessage()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("account_not_found", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("invalid_request", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ErrorResponse.of("validation_failed", message));
    }

    /**
     * TransactionService only catches DataIntegrityViolationException for
     * the idempotency-key-collision case (ADR-003) and re-throws anything
     * else it doesn't recognize as that — a genuine constraint violation
     * (e.g. a bug that bypasses the service layer) lands here instead of
     * silently falling through to Spring's default, unstructured error
     * response. Deliberately 409, not 500: the client's request conflicted
     * with existing state, which is closer to the truth than "the server
     * is broken."
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("unhandled data integrity violation", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("conflict", "The request conflicts with existing data."));
    }

    /**
     * Catch-all so an unexpected failure still returns this API's
     * consistent ErrorResponse shape instead of Spring Boot's default
     * whitelabel/error-attribute response — found missing in a
     * post-implementation code survey (see docs/RISKS.md). The exception
     * itself is logged server-side with full detail; the response body
     * intentionally omits it, so an internal failure never leaks
     * implementation details (stack traces, exception class names, SQL) to
     * a client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("internal_error", "An unexpected error occurred."));
    }
}
