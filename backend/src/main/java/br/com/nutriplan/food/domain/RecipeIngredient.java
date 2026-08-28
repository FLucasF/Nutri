package br.com.nutriplan.food.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * An ingredient inside a recipe.
 *
 * It points at two foods: the recipe it belongs to and the food used. Both are
 * {@link Food} because one recipe can go into another — a sofrito inside a pie
 * — and nothing in the model needs to know that in advance.
 */
@Entity
@Table(name = "recipe_ingredient", indexes = {
        @Index(name = "ix_ingredient_recipe", columnList = "recipe_id"),
        @Index(name = "ix_ingredient_food", columnList = "food_id")
})
@Getter
@Setter
@NoArgsConstructor
public class RecipeIngredient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ingrediente_receita"))
    private Food recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ingrediente_alimento"))
    private Food food;

    /** Household measure chosen. Null when the ingredient was weighed directly. */
    @Column(name = "measure_id")
    private Long measureId;

    /**
     * Label of the measure at the moment the recipe was assembled. It repeats
     * what is in {@link HouseholdMeasure} for the same reason as the meal item:
     * the measure can be corrected or removed later, and the recipe still has
     * to say "2 tablespoons" to whoever reads it.
     */
    @Column(name = "measure_description", length = 120)
    private String descriptionMeasure;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /**
     * Weight of the ingredient, resolved on save.
     *
     * Stored, and not derived on every read: correcting the weight of a
     * tablespoon later must not silently change the composition of a recipe the
     * nutritionist has already checked. It is the same rule as the prescribed
     * weight.
     */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal grams;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    public RecipeIngredient(Food recipe, Food food, BigDecimal grams, int order) {
        this.recipe = recipe;
        this.food = food;
        this.grams = grams;
        this.order = order;
    }

    /** Ready-made text of the quantity, the way it appears in the ingredient list. */
    public String quantityFormatted() {
        if (quantity == null) {
            return "%s g".formatted(grams.stripTrailingZeros().toPlainString());
        }
        String number = quantity.stripTrailingZeros().toPlainString();
        if (descriptionMeasure != null && !descriptionMeasure.isBlank()) {
            return "%s %s".formatted(number,
                    br.com.nutriplan.shared.util.PluralMeasure.agree(quantity, descriptionMeasure));
        }
        return "%s g".formatted(number);
    }
}
