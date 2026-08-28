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
 * A usual portion of a food — "colher de sopa cheia", "fatia media", "unidade".
 *
 * It is what makes the prescription usable by the patient: nobody weighs 23 g
 * of rice, but everybody understands "2 colheres de sopa". The weight in grams
 * is what the calculation uses; the description is what appears in the printed
 * plan.
 */
@Entity
@Table(name = "household_measure", indexes = @Index(name = "ix_measure_food", columnList = "food_id"))
@Getter
@Setter
@NoArgsConstructor
public class HouseholdMeasure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_medida_alimento"))
    private Food food;

    /**
     * Practice that owns the portion. Null identifies a portion of the base
     * catalog, shared by everyone; filled in, only the owning practice sees it.
     */
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(name = "grams", nullable = false, precision = 10, scale = 3)
    private BigDecimal grams;

    /** Marks the portion shown by default when adding the food to the plan. */
    @Column(name = "standard", nullable = false)
    private boolean standard = false;

    public HouseholdMeasure(String description, BigDecimal grams) {
        this.description = description;
        this.grams = grams;
    }

    /** Total weight of a quantity of this measure (e.g. 2.5 spoons). */
    public BigDecimal gramsTo(BigDecimal quantity) {
        return grams.multiply(quantity);
    }

    public boolean isForCatalogBase() {
        return accountId == null;
    }

    /** A portion of the base catalog cannot be changed by any practice. */
    public boolean editableBy(Long account) {
        return accountId != null && accountId.equals(account);
    }
}
