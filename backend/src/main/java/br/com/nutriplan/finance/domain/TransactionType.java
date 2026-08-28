package br.com.nutriplan.finance.domain;

/**
 * Direction of the transaction in the cash flow.
 *
 * The stored amount is always positive; it is this type that says whether it
 * adds or subtracts. Storing an expense as a negative number would spread the
 * rule across every sum in the system.
 */
public enum TransactionType {

    INCOME("Receita", 1),
    EXPENSE("Despesa", -1);

    private final String description;
    private final int sign;

    TransactionType(String description, int sign) {
        this.description = description;
        this.sign = sign;
    }

    public String getDescription() {
        return description;
    }

    /** +1 for income, -1 for expense. Used when settling the result. */
    public int getSign() {
        return sign;
    }

    public boolean isIncome() {
        return this == INCOME;
    }
}
