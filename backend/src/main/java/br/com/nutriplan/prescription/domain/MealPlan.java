package br.com.nutriplan.prescription.domain;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A meal plan prescribed for a patient.
 *
 * The plan is a single entity, seen both by the nutritionist and by the patient
 * — there is no copy per audience. What changes is the access path: the
 * professional comes in authenticated, the patient opens it through the
 * {@link #publicIdentifier}, a UUID that works as the plan's address.
 *
 * Using a UUID and not the sequential id is deliberate: a predictable
 * identifier would allow opening another patient's plan by adding 1 to the
 * number in the link.
 */
@Entity
@Table(name = "meal_plan", indexes = {
        @Index(name = "ix_plan_account", columnList = "account_id"),
        @Index(name = "ix_plan_patient", columnList = "patient_id"),
        @Index(name = "ix_public_plan", columnList = "public_identifier")
})
@Getter
@Setter
@NoArgsConstructor
public class MealPlan extends AccountEntity {

    /** Null in a template plan, which exists to be reused and belongs to nobody. */
    @Column(name = "patient_id")
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionMethod method = PrescriptionMethod.FOODS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "public_identifier", nullable = false, length = 36, unique = true)
    private String publicIdentifier = UUID.randomUUID().toString();

    @Column(name = "start_validity")
    private LocalDate validityStart;

    @Column(name = "end_validity")
    private LocalDate validityEnd;

    /** General guidance, shown to the patient before the meals. */
    @Column(length = 4000)
    private String handouts;

    /** The professional's internal note. It never goes out in the patient's link. */
    @Column(name = "internal_notes", length = 4000)
    private String internalNotes;

    /** A plan kept as a starting point for others, with no patient attached. */
    @Column(nullable = false)
    private boolean template = false;

    /** Daily energy goal, to compare with what was actually prescribed. */
    @Column(name = "target_energy_kcal", precision = 10, scale = 2)
    private java.math.BigDecimal targetEnergyKcal;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order asc")
    private List<Meal> meals = new ArrayList<>();

    public MealPlan(Long accountId, String title) {
        setAccountId(accountId);
        this.title = title;
    }

    public void addMeal(Meal meal) {
        meal.setPlan(this);
        if (meal.getOrder() == null) {
            meal.setOrder(nextOrder());
        }
        meals.add(meal);
    }

    public void removeMeal(Meal meal) {
        meals.remove(meal);
        meal.setPlan(null);
    }

    private int nextOrder() {
        return meals.stream()
                .map(Meal::getOrder)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    /**
     * Renumbers the meals into a continuous sequence. Called after a removal so
     * that the order is not left with holes, which would confuse reordering.
     */
    public void renumberMeals() {
        List<Meal> sorted = new ArrayList<>(meals);
        sorted.sort(Comparator.comparing(
                Meal::getOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setOrder(i + 1);
        }
    }

    public boolean isVisibleByLink() {
        return status.isVisibleAoPatient();
    }

    public boolean podeSerEdited() {
        return status.allowsEdit();
    }

    /** In force at the reference date, treating the interval as open at both ends. */
    public boolean currentAt(LocalDate reference) {
        if (status != PlanStatus.ACTIVE) {
            return false;
        }
        boolean started = validityStart == null || !reference.isBefore(validityStart);
        boolean notEnded = validityEnd == null || !reference.isAfter(validityEnd);
        return started && notEnded;
    }

    /** Generates a new address, invalidating the link handed out earlier. */
    public void regeneratePublicIdentifier() {
        this.publicIdentifier = UUID.randomUUID().toString();
    }

    public int itemsTotal() {
        return meals.stream().mapToInt(r -> r.getItems().size()).sum();
    }
}
