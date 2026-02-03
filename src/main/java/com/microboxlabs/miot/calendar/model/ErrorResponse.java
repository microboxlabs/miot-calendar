package com.microboxlabs.miot.calendar.model;

import java.time.ZonedDateTime;

/**
 * Standard error response
 */
public record ErrorResponse(
    String error,
    String message,
    Integer status,
    ZonedDateTime timestamp
) {
    public static ErrorResponse of(String error, String message, int status) {
        return new ErrorResponse(error, message, status, ZonedDateTime.now());
    }
    
    public static ErrorResponse badRequest(String message) {
        return of("Bad Request", message, 400);
    }
    
    public static ErrorResponse notFound(String message) {
        return of("Not Found", message, 404);
    }
    
    public static ErrorResponse conflict(String message) {
        return of("Conflict", message, 409);
    }
    
    public static ErrorResponse internalError(String message) {
        return of("Internal Server Error", message, 500);
    }
}
