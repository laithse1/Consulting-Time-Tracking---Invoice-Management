package io.consulting.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(
        org.springframework.web.bind.MethodArgumentNotValidException exception
    ) {
        var fields = new java.util.LinkedHashMap<String, String>();
        exception
            .getBindingResult()
            .getFieldErrors()
            .forEach(error ->
                fields.putIfAbsent(
                    error.getField(),
                    java.util.Objects.toString(error.getDefaultMessage(), "Invalid value")
                )
            );
        return ResponseEntity.badRequest().body(
            new io.consulting.dto.ApiError(400, "Please correct the invalid request fields", fields)
        );
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable() {
        return ResponseEntity.badRequest().body(
            new io.consulting.dto.ApiError(400, "Request body contains invalid JSON or field types")
        );
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        java.time.DateTimeException.class,
        ClassCastException.class,
    })
    public ResponseEntity<?> invalid(RuntimeException e) {
        return ResponseEntity.badRequest().body(
            Map.of(
                "message",
                e instanceof ClassCastException
                    ? "Invalid action format"
                    : java.util.Objects.toString(e.getMessage(), "Invalid request")
            )
        );
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(
            Map.of(
                "message",
                "A duplicate or conflicting record was detected; no actions were applied."
            )
        );
    }
}
