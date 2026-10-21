package io.consulting.dto;

import java.util.Map;

/** Stable error envelope consumed by the browser API client. */
public record ApiError(int status, String message, Map<String, String> fields) {
    public ApiError(int status, String message) {
        this(status, message, Map.of());
    }
}
