package br.com.nutriplan.prescription.domain;

/**
 * How the plan expresses what the patient should eat.
 *
 * The choice changes what the system requires and what it calculates: a
 * qualitative plan has no quantity to add up, and demanding that from the
 * professional would be paperwork with no clinical value.
 */
public enum PrescriptionMethod {

    /** Each item carries a defined food and quantity. It totals nutrients. */
    FOODS("Por alimentos", true),

    /**
     * Each item carries interchangeable options of nearby nutritional value.
     * It totals by the main option, which is the reference for the calculation.
     */
    SUBSTITUTIONS("Por equivalentes", true),

    /**
     * Guidance without quantifying — "salad as desired", "one fruit".
     * It does not total: there is no quantity to add up.
     */
    QUALITATIVE("Qualitativo", false);

    private final String description;
    private final boolean quantified;

    PrescriptionMethod(String description, boolean quantified) {
        this.description = description;
        this.quantified = quantified;
    }

    public String getDescription() {
        return description;
    }

    /** Says whether the items require a quantity and enter the totals. */
    public boolean isQuantified() {
        return quantified;
    }

    /**
     * Says whether an item may carry alternatives to the main option.
     *
     * A substitution swaps one portion for another, so it only means something
     * where there is a portion: it follows quantification, not the method. Tying
     * it to {@link #SUBSTITUTIONS} would be reading the name instead of the
     * rule — a food plan offers "papaya or banana" for the same slot without
     * ever touching an exchange table.
     */
    public boolean admitsSubstitutions() {
        return quantified;
    }
}
