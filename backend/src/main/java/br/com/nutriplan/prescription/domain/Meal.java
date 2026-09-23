package br.com.nutriplan.prescription.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One meal of the plan — breakfast, lunch, supper.
 *
 * The order is explicit and not derived from the time: the professional may
 * want the pre-workout right after lunch in the list, even if the clock says
 * otherwise, and there are plans with no time defined.
 */
@Entity
@Table(name = "meal", indexes = @Index(name = "ix_meal_plan", columnList = "plan_id"))
@Getter
@Setter
@NoArgsConstructor
public class Meal extends BaseEntity {

    /**
     * The plan this meal belongs to, or null when it is a saved favourite.
     *
     * A favourite meal is a meal with no plan: same structure, same items, same
     * substitutions, and the copy routine that already exists works in both
     * directions. What makes it safe is that nothing outside {@link MealPlan}
     * ever reads this field.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id",
            foreignKey = @ForeignKey(name = "fk_refeicao_plano"))
    private MealPlan plan;

    /** The practice that owns the favourite. Null for a meal inside a plan. */
    @Column(name = "account_id")
    private Long accountId;

    /** The name the favourite was saved under. Null for a meal inside a plan. */
    @Column(name = "favorite_name", length = 150)
    private String favoriteName;

    public boolean isFavorite() {
        return plan == null;
    }

    /**
     * Nome da refeição, que o cliente usa como frase.
     *
     * O exemplo dele: "Café da Manhã – Mamão c/ Farelo de Aveia, Pão c/ Ovos
     * Fritos e Café c/ Açúcar". Por isso 250 e não 100.
     */
    @Column(nullable = false, length = 250)
    private String name;

    /**
     * Whether this meal counts towards the day.
     *
     * Turning it off is how a substitute meal is prescribed: two options for
     * lunch should not add up as two lunches. The meal's own total is still
     * calculated — the professional needs to know what the option not being
     * counted is worth.
     */
    @Column(name = "in_calculation", nullable = false)
    private boolean inCalculation = true;

    /** Name and type here; the bytes in meal_photo. */
    @Column(name = "photo_name", length = 200)
    private String photoName;

    @Column(name = "photo_type", length = 100)
    private String photoType;

    public boolean hasPhoto() {
        return photoName != null;
    }

    @Column
    private LocalTime time;

    @Column(name = "sort_order", nullable = false)
    private Integer order = 0;

    /** Guidance specific to the meal, shown to the patient. */
    /** Observações da refeição, no formato do editor. Ver RichTextDocument. */
    @Column(length = 8000)
    private String notes;

    // Loaded in a batch: without this, reading a plan with six meals would fire
    // six item queries.
    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order asc")
    @BatchSize(size = 50)
    private List<MealItem> items = new ArrayList<>();

    public Meal(String name, LocalTime time) {
        this.name = name;
        this.time = time;
    }

    public void addItem(MealItem item) {
        item.setMeal(this);
        if (item.getOrder() == null) {
            item.setOrder(nextOrder());
        }
        items.add(item);
    }

    public void removeItem(MealItem item) {
        items.remove(item);
        item.setMeal(null);
    }

    private int nextOrder() {
        return items.stream()
                .map(MealItem::getOrder)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    public void renumberItems() {
        List<MealItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(
                MealItem::getOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setOrder(i + 1);
        }
    }
}
