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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_refeicao_plano"))
    private MealPlan plan;

    @Column(nullable = false, length = 100)
    private String name;

    @Column
    private LocalTime time;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    /** Guidance specific to the meal, shown to the patient. */
    @Column(length = 1000)
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
