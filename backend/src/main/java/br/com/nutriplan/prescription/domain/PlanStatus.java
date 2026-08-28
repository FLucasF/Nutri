package br.com.nutriplan.prescription.domain;

/**
 * Life cycle of the meal plan.
 *
 * The patient only sees a published plan. A draft is the professional's work in
 * progress, and showing it half-done would be worse than showing nothing.
 */
public enum PlanStatus {

    /** Being drafted. Invisible to the patient. */
    DRAFT("Rascunho", false),

    /** Published and in force. Visible through the patient's link. */
    ACTIVE("Ativo", true),

    /**
     * Replaced or expired. It stays visible for consultation, marked as closed
     * — the patient needs to know that plan no longer holds, and the history
     * cannot disappear from the chart.
     */
    CLOSED("Encerrado", true);

    private final String description;
    private final boolean visibleAoPatient;

    PlanStatus(String description, boolean visibleAoPatient) {
        this.description = description;
        this.visibleAoPatient = visibleAoPatient;
    }

    public String getDescription() {
        return description;
    }

    public boolean isVisibleAoPatient() {
        return visibleAoPatient;
    }

    public boolean allowsEdit() {
        return this != CLOSED;
    }
}
