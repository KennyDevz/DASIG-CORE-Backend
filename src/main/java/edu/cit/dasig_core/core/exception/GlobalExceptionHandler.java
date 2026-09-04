package edu.cit.dasig_core.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Services throughout the app throw this for validation/business-rule failures
    // (duplicate email, account not found, wrong password, etc). Without this handler
    // it bubbles up as an unhandled exception and Spring Boot returns a bare 500 with
    // no message, instead of the 400 the caller actually needs to act on.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    // Triggered by @Valid on a @RequestBody DTO. Surfaces the first field error's
    // message so callers get the same actionable, single-string "message" shape
    // as every other error response instead of Spring's default (which is
    // suppressed by server.error.include-message unless explicitly enabled).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Validation failed.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
