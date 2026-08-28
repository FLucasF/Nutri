package br.com.nutriplan.anthropometry.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * One anthropometric assessment of the patient on a date.
 *
 * The entity keeps the **raw measurements** and also the **derived results**
 * (fat percentage, energy expenditure), together with the protocol and the
 * equation that produced them.
 *
 * Storing the derived value is deliberate. Recalculating on read would make an
 * assessment from two years ago change value because the implementation evolved
 * — and the patient's history would stop being comparable with itself. The raw
 * measurements sit alongside precisely so that the calculation can be checked
 * again.
 */
@Entity
@Table(name = "anthropometric_assessment", indexes = {
        @Index(name = "ix_assessment_account", columnList = "account_id"),
        @Index(name = "ix_assessment_patient", columnList = "patient_id, date")
})
@Getter
@Setter
@NoArgsConstructor
public class AnthropometricAssessment extends AccountEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 6, scale = 2)
    private BigDecimal heightCm;

    // --- skinfolds, in millimeters ----------------------------------------
    @Column(name = "triceps_skinfold",   precision = 6, scale = 2) private BigDecimal skinfoldTriceps;
    @Column(name = "biceps_skinfold",    precision = 6, scale = 2) private BigDecimal skinfoldBiceps;
    @Column(name = "subscapular_skinfold", precision = 6, scale = 2) private BigDecimal skinfoldSubscapular;
    @Column(name = "suprailiac_skinfold",  precision = 6, scale = 2) private BigDecimal skinfoldSuprailiac;
    @Column(name = "abdominal_skinfold",    precision = 6, scale = 2) private BigDecimal skinfoldAbdominal;
    @Column(name = "chest_skinfold",     precision = 6, scale = 2) private BigDecimal skinfoldChest;
    @Column(name = "thigh_skinfold",         precision = 6, scale = 2) private BigDecimal skinfoldThigh;
    @Column(name = "calf_skinfold",  precision = 6, scale = 2) private BigDecimal skinfoldCalf;
    @Column(name = "skinfold_mean_axillary", precision = 6, scale = 2) private BigDecimal skinfoldMeanAxillary;

    // --- circumferences, in centimeters ------------------------------------
    @Column(name = "waist_circumference",     precision = 6, scale = 2) private BigDecimal circumferenceWaist;
    @Column(name = "hip_circumference",     precision = 6, scale = 2) private BigDecimal circumferenceHip;
    @Column(name = "abdomen_circumference",     precision = 6, scale = 2) private BigDecimal circumferenceAbdomen;
    @Column(name = "arm_circumference",       precision = 6, scale = 2) private BigDecimal circumferenceArm;
    @Column(name = "forearm_circumference",   precision = 6, scale = 2) private BigDecimal circumferenceForearm;
    @Column(name = "thigh_circumference",        precision = 6, scale = 2) private BigDecimal circumferenceThigh;
    @Column(name = "calf_circumference", precision = 6, scale = 2) private BigDecimal circumferenceCalf;
    @Column(name = "chest_circumference",       precision = 6, scale = 2) private BigDecimal circumferenceChest;

    // ---- body composition, derived and stored -----------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "composition_protocol", length = 30)
    private CompositionProtocol protocolComposition;

    @Column(name = "fat_percentage", precision = 5, scale = 2)
    private BigDecimal percentageFat;

    @Column(name = "mass_fat_kg", precision = 6, scale = 2)
    private BigDecimal massFatKg;

    @Column(name = "mass_lean_kg", precision = 6, scale = 2)
    private BigDecimal massLeanKg;

    // ---- energy expenditure, derived and stored ---------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "expenditure_equation", length = 30)
    private EnergyExpenditureEquation equationExpenditure;

    @Column(name = "activity_factor", precision = 4, scale = 2)
    private BigDecimal factorActivity;

    @Column(name = "basal_expenditure_kcal", precision = 8, scale = 2)
    private BigDecimal basalExpenditureKcal;

    @Column(name = "total_expenditure_kcal", precision = 8, scale = 2)
    private BigDecimal totalExpenditureKcal;

    @Column(length = 2000)
    private String notes;

    // --------------------------------------------------------------- pregnancy
    // They stay on the assessment itself: they are the same measurements, read
    // against another reference. A separate table would duplicate weight and
    // date.

    @Column(name = "gestational_week")
    private Integer gestationalWeek;

    /**
     * Weight before pregnancy. It is what defines the expected gain band — the
     * current BMI already embeds the gain being assessed.
     */
    @Column(name = "weight_gestational_pre_kg", precision = 6, scale = 2)
    private BigDecimal weightGestationalPreKg;

    public boolean isGestational() {
        return gestationalWeek != null;
    }

    public AnthropometricAssessment(Long accountId, Long patientId, LocalDate date) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.date = date;
    }

    /**
     * Body mass index — weight divided by the square of the height in meters.
     * Null if weight or height is missing: there is no way to estimate one from
     * the other.
     */
    public BigDecimal getBmi() {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightMeters = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightMeters.multiply(heightMeters), 2, RoundingMode.HALF_UP);
    }

    /**
     * Waist-to-hip ratio. It requires both measurements — one alone does not
     * allow inferring the distribution of fat.
     */
    public BigDecimal getRatioWaistHip() {
        if (circumferenceWaist == null || circumferenceHip == null || circumferenceHip.signum() <= 0) {
            return null;
        }
        return circumferenceWaist.divide(circumferenceHip, 2, RoundingMode.HALF_UP);
    }

    /** Skinfolds measured, indexed for use by the protocols. */
    public Map<Skinfold, Double> skinfoldsMeasures() {
        Map<Skinfold, Double> map = new EnumMap<>(Skinfold.class);
        add(map, Skinfold.TRICEPS, skinfoldTriceps);
        add(map, Skinfold.BICEPS, skinfoldBiceps);
        add(map, Skinfold.SUBSCAPULAR, skinfoldSubscapular);
        add(map, Skinfold.SUPRAILIAC, skinfoldSuprailiac);
        add(map, Skinfold.ABDOMINAL, skinfoldAbdominal);
        add(map, Skinfold.CHEST, skinfoldChest);
        add(map, Skinfold.THIGH, skinfoldThigh);
        add(map, Skinfold.CALF, skinfoldCalf);
        add(map, Skinfold.MEAN_AXILLARY, skinfoldMeanAxillary);
        return map;
    }

    public void defineSkinfold(Skinfold skinfold, BigDecimal value) {
        switch (skinfold) {
            case TRICEPS -> skinfoldTriceps = value;
            case BICEPS -> skinfoldBiceps = value;
            case SUBSCAPULAR -> skinfoldSubscapular = value;
            case SUPRAILIAC -> skinfoldSuprailiac = value;
            case ABDOMINAL -> skinfoldAbdominal = value;
            case CHEST -> skinfoldChest = value;
            case THIGH -> skinfoldThigh = value;
            case CALF -> skinfoldCalf = value;
            case MEAN_AXILLARY -> skinfoldMeanAxillary = value;
        }
    }

    /** Clears the estimated composition — used when the assessment is edited again. */
    public void clearComposition() {
        protocolComposition = null;
        percentageFat = null;
        massFatKg = null;
        massLeanKg = null;
    }

    public void clearExpenditureEnergy() {
        equationExpenditure = null;
        factorActivity = null;
        basalExpenditureKcal = null;
        totalExpenditureKcal = null;
    }

    public boolean hasEstimatedComposition() {
        return protocolComposition != null && percentageFat != null;
    }

    private static void add(Map<Skinfold, Double> map, Skinfold skinfold, BigDecimal value) {
        if (value != null && value.signum() > 0) {
            map.put(skinfold, value.doubleValue());
        }
    }
}
