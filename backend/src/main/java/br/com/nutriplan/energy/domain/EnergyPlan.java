package br.com.nutriplan.energy.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.nutriplan.patient.domain.Sex;
import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An energy calculation, as its own record.
 *
 * It used to be a handful of columns inside the anthropometric assessment,
 * which made it impossible to keep a history of calculations for one
 * measurement, or to calculate without measuring. The client asked for an area
 * with its own list of past calculations, an injury factor, calories by MET
 * and weight programming — none of which fits as a field on something else.
 *
 * Every input is copied into the record: weight, height, age, activity. They
 * come from the anthropometry, and the anthropometry keeps changing. A plan
 * that read them live would answer a different number next month and still
 * look like the same prescription.
 */
@Entity
@Table(name = "energy_plan", indexes = {
        @Index(name = "ix_energy_plan_patient", columnList = "patient_id"),
        @Index(name = "ix_energy_plan_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
public class EnergyPlan extends AccountEntity {

    /** Adipose tissue holds about 7 700 kcal per kilogram. */
    public static final BigDecimal KCAL_PER_KG_ADIPOSE = BigDecimal.valueOf(7700);

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    /** The assessment the measurements were taken from, when there was one. */
    @Column(name = "assessment_id")
    private Long assessmentId;

    // ------------------------------------------------------------- as entradas

    @Column(name = "weight_kg", precision = 6, scale = 2, nullable = false)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 6, scale = 2, nullable = false)
    private BigDecimal heightCm;

    @Column(name = "age_years", nullable = false)
    private Integer ageYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 20)
    private ActivityLevel activityLevel = ActivityLevel.INACTIVE;

    /**
     * Injury factor, for a patient under metabolic stress.
     *
     * It is 1.00 outside the hospital, which is why it defaults to that
     * instead of being required.
     */
    @Column(name = "injury_factor", precision = 4, scale = 2, nullable = false)
    private BigDecimal injuryFactor = BigDecimal.ONE;

    /** Extra calories declared by MET, for training the equations do not see. */
    @Column(name = "met_kcal", precision = 8, scale = 2)
    private BigDecimal metKcal;

    // ---------------------------------------------------- a programação de peso

    @Column(name = "target_weight_kg", precision = 6, scale = 2)
    private BigDecimal targetWeightKg;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /**
     * What the programming takes off, or adds to, the daily total.
     *
     * Negative for weight loss. It comes from the adipose tissue method, and
     * is frozen here because the target date passes.
     */
    @Column(name = "adjustment_kcal", precision = 8, scale = 2)
    private BigDecimal adjustmentKcal;

    // ------------------------------------------------------------ os resultados

    /** The average of the selected equations' daily totals. */
    @Column(name = "average_kcal", precision = 8, scale = 2, nullable = false)
    private BigDecimal averageKcal = BigDecimal.ZERO;

    /** The number that goes to the meal plan, after injury, MET and programming. */
    @Column(name = "prescribed_kcal", precision = 8, scale = 2, nullable = false)
    private BigDecimal prescribedKcal = BigDecimal.ZERO;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order")
    private List<EnergyPlanEquation> equations = new ArrayList<>();

    @Column(length = 8000)
    private String notes;

    public EnergyPlan(Long accountId, Long patientId, String name, LocalDate date) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.name = name;
        this.date = date;
    }

    public void clearEquations() {
        equations.clear();
    }

    public void add(EnergyPlanEquation equation) {
        equation.setPlan(this);
        equations.add(equation);
    }
}
