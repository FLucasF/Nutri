package br.com.nutriplan.anthropometry.domain;

import java.math.BigDecimal;

/**
 * Body mass index bands for adults, according to the World Health
 * Organization.
 *
 * The classification only applies to adults. For children and adolescents the
 * correct reading is by percentile of age and sex, and applying the adult band
 * would produce a wrong conclusion — which is why {@link #toAdult} returns null
 * below 20 years, and it is up to the caller to explain why.
 */
public enum BmiClassification {

    LOW_WEIGHT("Baixo peso", null, 18.5),
    NORMAL("Eutrofia", 18.5, 25.0),
    OVERWEIGHT("Sobrepeso", 25.0, 30.0),
    OBESITY_I("Obesidade grau I", 30.0, 35.0),
    OBESITY_II("Obesidade grau II", 35.0, 40.0),
    OBESITY_III("Obesidade grau III", 40.0, null);

    /** Below this age the adult band does not apply. */
    public static final int MINIMUM_AGE_ADULT = 20;

    private final String description;
    private final Double limitInferior;
    private final Double limitSuperior;

    BmiClassification(String description, Double limitInferior, Double limitSuperior) {
        this.description = description;
        this.limitInferior = limitInferior;
        this.limitSuperior = limitSuperior;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Classifies an adult's BMI.
     *
     * @return null if the BMI was not calculated, or if the age is known and
     *         below {@value #MINIMUM_AGE_ADULT} years
     */
    public static BmiClassification toAdult(BigDecimal bmi, Integer age) {
        if (bmi == null) {
            return null;
        }
        if (age != null && age < MINIMUM_AGE_ADULT) {
            return null;
        }
        double value = bmi.doubleValue();
        for (BmiClassification range : values()) {
            boolean floorAbove = range.limitInferior == null || value >= range.limitInferior;
            boolean ceilingBelow = range.limitSuperior == null || value < range.limitSuperior;
            if (floorAbove && ceilingBelow) {
                return range;
            }
        }
        return null;
    }
}
