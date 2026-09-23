package br.com.nutriplan.anthropometry.domain;

/**
 * Where a circumference is measured.
 *
 * The list is the client's, from pages 22 and 23 of the document. Seven of the
 * thirteen sites are measured on both sides, and that is the whole reason
 * circumferences left the flat columns: a column per side would be twenty
 * columns on a table that already had seventeen, and adding the fourteenth
 * site would mean another migration and another pair of fields in every layer.
 *
 * Skinfolds stayed as columns on purpose. They are a closed set of ten, none
 * of them sided, and the composition protocols read them by name.
 */
public enum CircumferenceSite {

    NECK("Pescoço", false),
    SHOULDER("Ombro", false),
    CHEST("Tórax", false),
    WAIST("Cintura", false),
    ABDOMEN("Abdômen", false),
    HIP("Quadril", false),

    ARM_RELAXED("Braço relaxado", true),
    ARM_CONTRACTED("Braço contraído", true),
    FOREARM("Antebraço", true),
    THIGH_PROXIMAL("Coxa proximal", true),
    THIGH_MEDIAL("Coxa medial", true),
    THIGH_DISTAL("Coxa distal", true),
    CALF("Panturrilha", true);

    private final String description;
    private final boolean bilateral;

    CircumferenceSite(String description, boolean bilateral) {
        this.description = description;
        this.bilateral = bilateral;
    }

    public String getDescription() {
        return description;
    }

    /** Whether the site is measured on the right and on the left. */
    public boolean isBilateral() {
        return bilateral;
    }
}
