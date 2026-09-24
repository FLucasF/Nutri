package br.com.nutriplan.prescription.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import br.com.nutriplan.shared.util.PluralMeasure;
import jakarta.persistence.CascadeType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * An item prescribed inside a meal.
 *
 * The item keeps three things that look redundant and are not:
 *
 *  - the portion chosen and the quantity ("3 tablespoons"), which is how the
 *    patient reads the prescription;
 *  - the resulting weight in grams, which is what the nutritional calculation
 *    runs on;
 *  - the description of the food at the moment of prescription.
 *
 * The weight is stored, and not derived on every read, because the portion can
 * be corrected later: if the practice adjusts its "tablespoon" from 25 g to
 * 28 g, a plan already delivered to the patient must not rewrite itself. What
 * was prescribed that day goes on being what was prescribed.
 *
 * In a qualitative plan the quantity and the weight are null: "salad as
 * desired" has no number, and forcing one would be inventing clinical data.
 */
@Entity
@Table(name = "meal_item", indexes = {
        @Index(name = "ix_item_meal", columnList = "meal_id"),
        @Index(name = "ix_item_food", columnList = "food_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MealItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_refeicao"))
    private Meal meal;

    /** Null in a purely textual item, used in the qualitative method. */
    /** Food line or separator. See {@link MealItemKind}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealItemKind kind = MealItemKind.FOOD;

    @Column(name = "food_id")
    private Long foodId;

    /** Portion chosen. Null when the prescription was made straight in grams. */
    @Column(name = "measure_id")
    private Long measureId;

    /**
     * How the food appears in the plan. Copied from the food at prescription
     * time, and editable: the professional can write "brown rice (from lunch)"
     * for the patient without changing the food's registration.
     */
    @Column(nullable = false, length = 250)
    private String description;

    /** Label of the portion at the moment of prescription, e.g. "colher de sopa cheia". */
    @Column(name = "measure_description", length = 120)
    private String descriptionMeasure;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    /** Resulting weight, frozen at editing time. The basis of the nutritional calculation. */
    @Column(precision = 10, scale = 3)
    private BigDecimal grams;

    /**
     * "À vontade": prescrito sem quantidade, fora do somatório.
     *
     * Vale em qualquer alimento do plano por alimentos — é o pedido do cliente
     * para folhas, salada, chá. O item continua no cardápio com a palavra "à
     * vontade" no lugar da porção; só não entra na conta do dia.
     */
    @Column(name = "ad_libitum", nullable = false)
    private boolean adLibitum = false;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    @Column(length = 8000)
    private String notes;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ItemSubstitution> substitutions = new ArrayList<>();

    public MealItem(String description) {
        this.description = description;
    }

    public void addSubstitution(ItemSubstitution substitution) {
        substitution.setItem(this);
        substitutions.add(substitution);
    }

    /**
     * Whether the line reaches the totals.
     *
     * A separator has nothing to add up, and an item without a known weight
     * has no number to add — "salada a vontade" is a prescription, not a
     * quantity.
     */
    public boolean entersCalculation() {
        return kind == MealItemKind.FOOD && !adLibitum
                && foodId != null && grams != null && grams.signum() > 0;
    }

    public boolean isSeparator() {
        return kind == MealItemKind.SEPARATOR;
    }

    /** Ready-made portion text, the way the patient reads it. */
    public String servingFormatted() {
        if (adLibitum) {
            return "à vontade";
        }
        if (quantity == null) {
            return descriptionMeasure != null ? descriptionMeasure : "à vontade";
        }
        String number = quantity.stripTrailingZeros().toPlainString();
        if (descriptionMeasure != null && !descriptionMeasure.isBlank()) {
            return "%s %s".formatted(number,
                    PluralMeasure.agree(quantity, descriptionMeasure));
        }
        return "%s g".formatted(number);
    }
}
