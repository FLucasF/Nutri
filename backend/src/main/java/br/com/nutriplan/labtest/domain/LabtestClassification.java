package br.com.nutriplan.labtest.domain;

import java.math.BigDecimal;

/**
 * Position of the result against the reference range.
 *
 * Decided once, at entry, and stored with the lab test. Recalculating on read
 * would make an old result change classification when the registered range was
 * corrected — the past does not change because the laboratory switched methods.
 */
public enum LabtestClassification {

    BELOW("Abaixo da referência"),
    NORMAL("Dentro da referência"),
    ABOVE("Acima da referência");

    private final String description;

    LabtestClassification(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Classifies the value against the range.
     *
     * @return null when there is no value or no range — and null here means
     *         "not classified", which is different from "normal"
     */
    public static LabtestClassification from(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value == null || (minimum == null && maximum == null)) {
            return null;
        }
        if (minimum != null && value.compareTo(minimum) < 0) {
            return BELOW;
        }
        if (maximum != null && value.compareTo(maximum) > 0) {
            return ABOVE;
        }
        return NORMAL;
    }
}
