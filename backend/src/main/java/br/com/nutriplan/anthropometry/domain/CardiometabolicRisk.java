package br.com.nutriplan.anthropometry.domain;

import br.com.nutriplan.patient.domain.Sex;

import java.math.BigDecimal;

/**
 * Risk associated with the distribution of body fat, read through the
 * waist-to-hip ratio.
 *
 * The cutoff points are sex-specific. With no sex reported the system
 * calculates the ratio but does not classify: applying the male cutoff to a
 * female patient, or the reverse, would produce wrong clinical guidance.
 */
public enum CardiometabolicRisk {

    LOW("Baixo"),
    MODERATE("Moderado"),
    HIGH("Alto");

    private final String description;

    CardiometabolicRisk(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Classifies by the waist-to-hip ratio.
     *
     * @return null when the ratio could not be calculated or the sex is unknown
     */
    public static CardiometabolicRisk byRatioWaistHip(BigDecimal ratio, Sex sex) {
        if (ratio == null || sex == null) {
            return null;
        }
        double value = ratio.doubleValue();
        return switch (sex) {
            case FEMALE -> value < 0.80 ? LOW : value < 0.85 ? MODERATE : HIGH;
            case MALE -> value < 0.90 ? LOW : value < 1.00 ? MODERATE : HIGH;
        };
    }
}
