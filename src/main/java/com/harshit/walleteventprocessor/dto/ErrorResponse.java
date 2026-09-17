package com.harshit.walleteventprocessor.dto;

import java.time.LocalDateTime;

/**
 * Standard error response format for all API errors
 */
public record ErrorResponse(
        String error,
        String message,
        int status,
        String path,
        LocalDateTime timestamp
) {
}
