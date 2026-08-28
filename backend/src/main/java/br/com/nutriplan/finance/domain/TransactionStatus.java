package br.com.nutriplan.finance.domain;

/**
 * Status of a transaction.
 *
 * The distinction between pending and paid is what separates cash-basis
 * settlement from accrual-basis settlement: adding up what has not come in yet
 * would make the practice look like it has money it does not.
 */
public enum TransactionStatus {

    PENDING("Pendente"),
    PAID("Pago"),
    CANCELED("Cancelado");

    private final String description;

    TransactionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
