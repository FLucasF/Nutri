package br.com.nutriplan.food.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A food with a known nutritional composition.
 *
 * Unlike the other entities in the system, a food does not extend
 * AccountEntity: the items of the public bases (TACO, TBCA) are shared by every
 * practice and have a null accountId. Foods registered by a nutritionist carry
 * that account and appear only to it — the query combines the two cases with
 * "account_id is null or account_id = :account".
 */
@Entity
@Table(name = "food", indexes = {
        @Index(name = "ix_food_account", columnList = "account_id"),
        @Index(name = "ix_food_description", columnList = "description"),
        @Index(name = "ix_food_source_code", columnList = "source, source_code"),
        @Index(name = "ix_food_code_barcode", columnList = "barcode")
})
@Getter
@Setter
@NoArgsConstructor
public class Food extends BaseEntity {

    /** Null for foods from the public base; filled in for an own registration. */
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 250)
    private String description;

    /**
     * Version of the description without accents and in lowercase, stored on
     * write. It allows searching for "acucar" and finding "Açúcar" without
     * depending on the database collation, which differs between H2 and
     * PostgreSQL.
     */
    @Column(name = "search_description", nullable = false, length = 250)
    private String descriptionSearch;

    @Column(name = "group_name", length = 100)
    private String group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    /** The food's code in the source table, when there is one. */
    @Column(name = "source_code", length = 30)
    private String codeSource;

    @Column(length = 100)
    private String brand;

    /** EAN/GTIN of the processed product. Null for unprocessed foods. */
    @Column(name = "barcode", length = 20)
    private String codeBarcode;

    @Embedded
    private NutritionalComposition composition = new NutritionalComposition();

    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("description asc")
    private List<HouseholdMeasure> measures = new ArrayList<>();

    // ----------------------------------------------------------- recipe fields
    // Sparse: null in the 23,945 records of the reference tables. They live
    // here, and not in a separate table, because a recipe is a food — and
    // treating the two as one is what makes a recipe enter the search, accept
    // a household measure and be prescribed with no new code.

    @Column(name = "instructions_mode", length = 20_000)
    private String modeInstructions;

    /**
     * Weight of the finished preparation. It is not the sum of the ingredients:
     * cooking loses or gains water. Null means not reported — and the sum of
     * the ingredients serves as an estimate, which the interface says instead
     * of hiding.
     */
    @Column(name = "grams_yield", precision = 12, scale = 3)
    private BigDecimal yieldGrams;

    /** How many servings the preparation yields, when the nutritionist reports it. */
    @Column(name = "servings")
    private Integer servings;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order asc")
    @BatchSize(size = 50)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    public boolean isRecipe() {
        return source == DataSource.RECIPE;
    }

    @Column(nullable = false)
    private boolean active = true;

    public Food(String description, DataSource source) {
        setDescription(description);
        this.source = source;
    }

    /** Keeps descriptionSearch always in sync with description. */
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSearch = normalizeToSearch(description);
    }

    public void addMeasure(HouseholdMeasure measure) {
        measure.setFood(this);
        measures.add(measure);
    }

    public boolean isPublicBase() {
        return accountId == null;
    }

    /**
     * Composition corresponding to a quantity in grams.
     */
    public NutritionalComposition compositionTo(BigDecimal grams) {
        return composition.toGrams(grams);
    }

    public static String normalizeToSearch(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccent = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccent.toLowerCase().trim();
    }
}
