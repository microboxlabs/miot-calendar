package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;

/**
 * Standard error response
 */
@Schema(description = "Standard error response returned for all non-2xx status codes")
public record ErrorResponse(
    @Schema(required = true, description = "Error category", examples = {"Bad Request"})
    String error,

    @Schema(required = true, description = "Human-readable error message", examples = {"Calendar code is required"})
    String message,

    @Schema(required = true, description = "HTTP status code", examples = {"400"})
    Integer status,

    @Schema(required = true, description = "Timestamp when the error occurred", format = "date-time")
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
