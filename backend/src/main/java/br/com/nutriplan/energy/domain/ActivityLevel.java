package br.com.nutriplan.energy.domain;

/**
 * How much the patient moves, as the energy equations ask for it.
 *
 * This is an enum and not a free multiplier because the two families of
 * equation want the same answer in different shapes. A basal equation wants a
 * number to multiply by; the EER equations of 2005 want a coefficient that
 * changes with sex, and the ones of 2023 want a whole different row of
 * coefficients. A single {@code BigDecimal} in the form could only serve the
 * first, and the professional would have to know which was which.
 *
 * The four levels are the ones the DRI uses. The names on screen follow the
 * report: inactive is not "lazy", it is the reference level against which the
 * others are described.
 */
public enum ActivityLevel {

    /** PAL 1.0–1.4: the daily life of someone who does not exercise. */
    INACTIVE("Sedentário", 1.2, 1.00, 1.00),

    /** PAL 1.4–1.6: light activity, or exercise a couple of times a week. */
    LOW_ACTIVE("Pouco ativo", 1.375, 1.11, 1.12),

    /** PAL 1.6–1.9: regular exercise, or work on one's feet. */
    ACTIVE("Ativo", 1.55, 1.25, 1.27),

    /** PAL 1.9–2.5: heavy training or heavy manual work. */
    VERY_ACTIVE("Muito ativo", 1.725, 1.48, 1.45);

    private final String description;
    private final double basalFactor;
    private final double paMale;
    private final double paFemale;

    ActivityLevel(String description, double basalFactor, double paMale, double paFemale) {
        this.description = description;
        this.basalFactor = basalFactor;
        this.paMale = paMale;
        this.paFemale = paFemale;
    }

    public String getDescription() {
        return description;
    }

    /**
     * The multiplier applied to a basal rate — Harris-Benedict, Mifflin,
     * Schofield. It is the classic table, not part of any of those papers:
     * the equations give basal expenditure and say nothing about activity.
     */
    public double basalFactor() {
        return basalFactor;
    }

    /** The PA coefficient of the 2005 DRI, which differs between the sexes. */
    public double physicalActivityCoefficient(boolean male) {
        return male ? paMale : paFemale;
    }
}
