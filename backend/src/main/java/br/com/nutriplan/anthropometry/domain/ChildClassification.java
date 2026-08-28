package br.com.nutriplan.anthropometry.domain;

import java.math.BigDecimal;

/**
 * Nutritional status of children and adolescents, by the WHO z-score.
 *
 * The bands change at five years and it is not a table detail: up to five, a
 * z-score above +1 is **risk of overweight**, because at that age the child can
 * still catch up with the curve; from five to nineteen, the same value is
 * already overweight. Classifying an adolescent by the child band would
 * underestimate the picture by a whole grade.
 *
 * These are the bands adopted by SISVAN, of the Ministry of Health.
 */
public enum ChildClassification {

    THINNESS_SEVERE("Magreza acentuada"),
    THINNESS("Magreza"),
    NORMAL("Eutrofia"),
    OVERWEIGHT_RISK("Risco de sobrepeso"),
    OVERWEIGHT("Sobrepeso"),
    OBESITY("Obesidade"),
    OBESITY_SEVERE("Obesidade grave"),

    VERY_LOW_HEIGHT("Muito baixa estatura para a idade"),
    LOW_HEIGHT("Baixa estatura para a idade"),
    HEIGHT_ADEQUATE("Estatura adequada para a idade");

    private final String description;

    ChildClassification(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Flags a picture that calls for action, so the interface can highlight it. */
    public boolean requiresAttention() {
        return this != NORMAL && this != HEIGHT_ADEQUATE;
    }

    /**
     * @param indicator which curve produced the score
     * @param months    age in completed months — it decides the cutoff band
     */
    public static ChildClassification from(GrowthIndicator indicator,
                                           BigDecimal scoreZ, int months) {
        if (scoreZ == null) {
            return null;
        }
        double z = scoreZ.doubleValue();

        if (indicator == GrowthIndicator.HEIGHT_TO_AGE) {
            if (z < -3) return VERY_LOW_HEIGHT;
            if (z < -2) return LOW_HEIGHT;
            return HEIGHT_ADEQUATE;
        }

        if (z < -3) return THINNESS_SEVERE;
        if (z < -2) return THINNESS;
        if (z <= 1) return NORMAL;

        // From here on the band depends on the age.
        if (months <= 60) {
            if (z <= 2) return OVERWEIGHT_RISK;
            if (z <= 3) return OVERWEIGHT;
            return OBESITY;
        }
        if (z <= 2) return OVERWEIGHT;
        if (z <= 3) return OBESITY;
        return OBESITY_SEVERE;
    }
}
