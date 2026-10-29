package com.example.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns every error into the same small JSON shape: {"error": "message"}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Errors thrown by the service (404 not found, 400 amount too big or blank text)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleServiceError(ResponseStatusException e) {
        log.warn("Request rejected: {} - {}", e.getStatusCode().value(), e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
    }

    // A @NotBlank / @Positive / ... check failed
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFields(MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + " " + f.getDefaultMessage()).toList());
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid resource point fields."));
    }

    // The body is not valid JSON, has a wrong type, or contains an unknown field
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(HttpMessageNotReadableException e) {
        log.warn("Malformed JSON body: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON body or field type."));
    }
}
