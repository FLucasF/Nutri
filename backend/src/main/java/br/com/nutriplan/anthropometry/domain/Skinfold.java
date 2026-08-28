package br.com.nutriplan.anthropometry.domain;

/**
 * Skinfolds measured with a caliper, in millimeters.
 *
 * The enum exists so that the protocols declare which skinfolds they require,
 * and the system can tell the professional exactly which one is missing —
 * instead of refusing the estimate without explaining.
 */
public enum Skinfold {

    TRICEPS("Tricipital"),
    BICEPS("Bicipital"),
    SUBSCAPULAR("Subescapular"),
    SUPRAILIAC("Supra-ilíaca"),
    ABDOMINAL("Abdominal"),
    CHEST("Peitoral"),
    THIGH("Coxa"),
    CALF("Panturrilha medial"),
    MEAN_AXILLARY("Axilar média");

    private final String description;

    Skinfold(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
