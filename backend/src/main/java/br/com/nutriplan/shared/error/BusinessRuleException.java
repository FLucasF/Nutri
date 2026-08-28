package br.com.nutriplan.shared.error;

/** Violation of a domain invariant — it becomes a 422 at the HTTP edge. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
