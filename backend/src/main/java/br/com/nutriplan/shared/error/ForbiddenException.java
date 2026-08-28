package br.com.nutriplan.shared.error;

/**
 * Raised when a nutritionist tries to access another one's data.
 * Deliberately distinct from NotFoundException for the log, but answered as a
 * 404 so as not to leak the existence of somebody else's records.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String resource, Object id) {
        super("Acesso negado a %s: %s".formatted(resource, id));
    }
}
