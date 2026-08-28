package br.com.nutriplan.anthropometry.domain;

/**
 * Child anthropometric indicator, read against the WHO curves.
 *
 * Each one answers a different question, and that is why both exist: BMI for
 * age says how the weight is for the height the child has now; height for age
 * says whether they grew as they should — and short stature is the consequence
 * of a prolonged deficit, which BMI does not show.
 */
public enum GrowthIndicator {

    BMI_TO_AGE("IMC para idade"),
    HEIGHT_TO_AGE("Estatura para idade");

    private final String description;

    GrowthIndicator(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * The WHO correction for an extreme z-score applies to a weight-based
     * indicator, and not to height.
     *
     * In weight indicators the distribution has a long tail, and the LMS
     * formula produces absurd values beyond three deviations. In those cases
     * the WHO prescribes extrapolating linearly from the interval between the
     * second and the third deviation. Height does not have that problem: the
     * distribution is close to normal, and the formula holds across the whole
     * range.
     */
    public boolean requiresTailsCorrection() {
        return this == BMI_TO_AGE;
    }
}
