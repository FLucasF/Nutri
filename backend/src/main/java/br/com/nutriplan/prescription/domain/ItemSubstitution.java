package br.com.nutriplan.prescription.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import br.com.nutriplan.shared.util.PluralMeasure;
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
 * A substitution option for an item of the meal.
 *
 * It is what gives the plan variety without taking the patient out of the
 * prescription: instead of "2 slices of whole-grain bread", they can eat "1
 * medium tapioca". The substitution does not enter the totals — what defines
 * the plan's numbers is the main item, and adding up the alternatives would
 * count meals that do not happen.
 */
@Entity
@Table(name = "item_substitution",
        indexes = @Index(name = "ix_substitution_item", columnList = "item_id"))
@Getter
@Setter
@NoArgsConstructor
public class ItemSubstitution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_equivalente_item"))
    private MealItem item;

    @Column(name = "food_id")
    private Long foodId;

    @Column(name = "measure_id")
    private Long measureId;

    @Column(nullable = false, length = 250)
    private String description;

    @Column(name = "measure_description", length = 120)
    private String descriptionMeasure;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(precision = 10, scale = 3)
    private BigDecimal grams;

    public ItemSubstitution(String description) {
        this.description = description;
    }

    /** Ready-made portion text, the way the patient reads it. */
    public String servingFormatted() {
        if (quantity == null) {
            return descriptionMeasure != null ? descriptionMeasure : "a vontade";
        }
        String number = quantity.stripTrailingZeros().toPlainString();
        if (descriptionMeasure != null && !descriptionMeasure.isBlank()) {
            return "%s %s".formatted(number,
                    PluralMeasure.agree(quantity, descriptionMeasure));
        }
        return "%s g".formatted(number);
    }
}
