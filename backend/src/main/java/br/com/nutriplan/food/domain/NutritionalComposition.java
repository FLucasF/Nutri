package br.com.nutriplan.food.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nutritional composition always referred to 100 g of the edible part — the
 * same basis used by TACO and by Brazilian labels.
 *
 * Null fields mean a nutrient not determined in the source. Null is absence of
 * information, not zero: in clinical software showing "não informado" is
 * correct, showing 0 mg of sodium for a food that was not analyzed is false.
 *
 * The values use BigDecimal because a meal plan adds up hundreds of terms, and
 * the accumulated floating-point error would show in the total.
 *
 * The scaling and summing operations walk the catalog in {@link Nutrient}, so
 * that including a new nutrient does not require touching this logic.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class NutritionalComposition {

    /** Rounding scale for the results of a calculation. */
    public static final int SCALE = 3;

    // --- energy ------------------------------------------------------------
    @Column(name = "energy_kcal",   precision = 10, scale = 4) private BigDecimal energyKcal;
    @Column(name = "kj_energy",     precision = 10, scale = 4) private BigDecimal energyKj;

    // --- macronutrientes ---------------------------------------------------
    @Column(name = "protein_g",              precision = 10, scale = 4) private BigDecimal proteinG;
    @Column(name = "carbohydrate_g",           precision = 10, scale = 4) private BigDecimal carbohydrateG;
    @Column(name = "sugars_g",              precision = 10, scale = 4) private BigDecimal sugarsG;
    @Column(name = "sugars_added_g",  precision = 10, scale = 4) private BigDecimal sugarsAddedG;
    @Column(name = "fiber_g",                 precision = 10, scale = 4) private BigDecimal fiberG;

    // --- lipidios ----------------------------------------------------------
    @Column(name = "fat_g",                  precision = 10, scale = 4) private BigDecimal fatG;
    @Column(name = "fat_saturated_g",        precision = 10, scale = 4) private BigDecimal fatSaturatedG;
    @Column(name = "fat_trans_g",            precision = 10, scale = 4) private BigDecimal fatTransG;
    @Column(name = "fat_monounsaturated_g",  precision = 10, scale = 4) private BigDecimal fatMonounsaturatedG;
    @Column(name = "fat_polyunsaturated_g",  precision = 10, scale = 4) private BigDecimal fatPolyunsaturatedG;
    @Column(name = "cholesterol_mg",               precision = 10, scale = 4) private BigDecimal cholesterolMg;

    // --- minerais ----------------------------------------------------------
    @Column(name = "sodium_mg",    precision = 10, scale = 4) private BigDecimal sodiumMg;
    @Column(name = "calcium_mg",   precision = 10, scale = 4) private BigDecimal calciumMg;
    @Column(name = "iron_mg",    precision = 10, scale = 4) private BigDecimal ironMg;
    @Column(name = "magnesium_mg", precision = 10, scale = 4) private BigDecimal magnesiumMg;
    @Column(name = "phosphorus_mg",  precision = 10, scale = 4) private BigDecimal phosphorusMg;
    @Column(name = "potassium_mg", precision = 10, scale = 4) private BigDecimal potassiumMg;
    @Column(name = "zinc_mg",    precision = 10, scale = 4) private BigDecimal zincMg;
    @Column(name = "copper_mg",    precision = 10, scale = 4) private BigDecimal copperMg;
    @Column(name = "manganese_mg", precision = 10, scale = 4) private BigDecimal manganeseMg;
    @Column(name = "selenium_mcg", precision = 10, scale = 4) private BigDecimal seleniumMcg;

    // --- vitaminas ---------------------------------------------------------
    @Column(name = "vitamin_c_mg",  precision = 10, scale = 4) private BigDecimal vitaminCMg;
    @Column(name = "thiamin_mg",     precision = 10, scale = 4) private BigDecimal thiaminMg;
    @Column(name = "riboflavin_mg", precision = 10, scale = 4) private BigDecimal riboflavinMg;
    @Column(name = "niacin_mg",     precision = 10, scale = 4) private BigDecimal niacinMg;
    @Column(name = "pyridoxine_mg",  precision = 10, scale = 4) private BigDecimal pyridoxineMg;
    @Column(name = "retinol_mcg",    precision = 10, scale = 4) private BigDecimal retinolMcg;
    @Column(name = "re_mcg",         precision = 10, scale = 4) private BigDecimal reMcg;
    @Column(name = "rae_mcg",        precision = 10, scale = 4) private BigDecimal raeMcg;
    @Column(name = "vitamin_b12_mcg", precision = 10, scale = 4) private BigDecimal vitaminB12Mcg;
    @Column(name = "folate_mcg",       precision = 10, scale = 4) private BigDecimal folateMcg;
    @Column(name = "vitamin_d_mcg",   precision = 10, scale = 4) private BigDecimal vitaminDMcg;
    @Column(name = "vitamin_e_mg",    precision = 10, scale = 4) private BigDecimal vitaminEMg;

    // --- other -------------------------------------------------------------
    @Column(name = "moisture_pct", precision = 10, scale = 4) private BigDecimal moisturePct;
    @Column(name = "ash_g",    precision = 10, scale = 4) private BigDecimal ashG;

    /**
     * Scales this composition (100 g basis) to the quantity given in grams.
     * Nutrients absent from the source stay absent: there is no way to infer
     * them.
     */
    public NutritionalComposition toGrams(BigDecimal grams) {
        BigDecimal factor = grams.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        var result = new NutritionalComposition();
        for (Nutrient nutrient : Nutrient.ALL) {
            BigDecimal value = nutrient.read().apply(this);
            if (value != null) {
                nutrient.store().accept(result,
                        value.multiply(factor).setScale(SCALE, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    /**
     * Adds another composition to this one, returning a new instance.
     *
     * Absent added to present returns the present: when totalling a meal, a
     * food with no zinc data must not zero out the zinc of the others. The side
     * effect is that the total becomes a floor, not an exact value — which is
     * why the meal calculation reports separately, in
     * {@link #nutrientsMissing()}, which nutrients were left incomplete.
     */
    public NutritionalComposition sum(NutritionalComposition other) {
        if (other == null) {
            return this;
        }
        var result = new NutritionalComposition();
        for (Nutrient nutrient : Nutrient.SUMMABLE) {
            BigDecimal a = nutrient.read().apply(this);
            BigDecimal b = nutrient.read().apply(other);
            BigDecimal sums;
            if (a == null) {
                sums = b;
            } else if (b == null) {
                sums = a;
            } else {
                sums = a.add(b).setScale(SCALE, RoundingMode.HALF_UP);
            }
            if (sums != null) {
                nutrient.store().accept(result, sums);
            }
        }
        return result;
    }

    /** Keys of the nutrients with no value in this composition. */
    public java.util.List<String> nutrientsMissing() {
        return Nutrient.ALL.stream()
                .filter(x -> x.read().apply(this) == null)
                .map(Nutrient::key)
                .toList();
    }

    public BigDecimal valueDe(String nutrientKey) {
        Nutrient nutrient = Nutrient.BY_KEY.get(nutrientKey);
        return nutrient == null ? null : nutrient.read().apply(this);
    }

    public void define(String nutrientKey, BigDecimal value) {
        Nutrient nutrient = Nutrient.BY_KEY.get(nutrientKey);
        if (nutrient != null) {
            nutrient.store().accept(this, value);
        }
    }

    /** Key-value representation, omitting nutrients that were not determined. */
    public Map<String, BigDecimal> asMap() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Nutrient nutrient : Nutrient.ALL) {
            BigDecimal value = nutrient.read().apply(this);
            if (value != null) {
                map.put(nutrient.key(), value);
            }
        }
        return map;
    }

    public boolean empty() {
        return Nutrient.ALL.stream().allMatch(x -> x.read().apply(this) == null);
    }
}
