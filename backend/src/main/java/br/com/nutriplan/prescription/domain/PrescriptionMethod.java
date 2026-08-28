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
    private final boolean quantificado;

    PrescriptionMethod(String description, boolean quantificado) {
        this.description = description;
        this.quantificado = quantificado;
    }

    public String getDescription() {
        return description;
    }

    /** Says whether the items require a quantity and enter the totals. */
    public boolean isQuantificado() {
        return quantificado;
    }

    public boolean admitsSubstitutions() {
        return this == SUBSTITUTIONS;
    }
}
