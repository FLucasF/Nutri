package br.com.nutriplan.anthropometry.domain;

import java.math.BigDecimal;

/** Position of the pregnant patient's weight gain against the band expected for the week. */
public enum GainStatus {

    BELOW("Abaixo do esperado"),
    ADEQUATE("Dentro do esperado"),
    ABOVE("Acima do esperado");

    private final String description;

    GainStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static GainStatus from(BigDecimal gain, BigDecimal minimum, BigDecimal maximum) {
        if (gain == null || minimum == null || maximum == null) {
            return null;
        }
        if (gain.compareTo(minimum) < 0) {
            return BELOW;
        }
        if (gain.compareTo(maximum) > 0) {
            return ABOVE;
        }
        return ADEQUATE;
    }
}
