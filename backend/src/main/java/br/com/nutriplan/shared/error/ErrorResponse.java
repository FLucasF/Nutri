package br.com.nutriplan.shared.error;

import java.time.Instant;
import java.util.List;

/** Standard error body of the API. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<InvalidField> fields
) {
    public record InvalidField(String field, String message) {}

    public static ErrorResponse from(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }
}
