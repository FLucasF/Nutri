package br.com.nutriplan.food.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.NutritionalComposition;
import br.com.nutriplan.food.domain.RecipeIngredient;
import br.com.nutriplan.food.domain.Nutrient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates the composition of a preparation from its ingredients.
 *
 * The arithmetic itself is simple — sum what each ingredient contributes and
 * divide by the final weight. What takes care is two things the sum hides.
 *
 * <p><b>The final weight is not the sum of the ingredients.</b> Cooking loses
 * or gains water: 100 g of raw rice becomes about 250 g cooked, and 100 g of
 * meat becomes about 70 g grilled. Dividing by the sum of the raw ingredients
 * would produce a per-100 g composition wrong by a large factor — in the case
 * of rice, two and a half times more concentrated than reality. That is why the
 * yield is the nutritionist's field, and when they do not report it, the result
 * says the sum was used as an estimate instead of presenting the number as
 * measured.
 *
 * <p><b>A nutrient absent from some of the ingredients becomes a floor, not a
 * total.</b> If the flour has declared fiber and the yeast does not, the summed
 * fiber is at least that — it may be more. Summing while treating the absent as
 * zero would produce a number that looks exact. That is why the result carries
 * the list of incomplete nutrients, and a nutrient no ingredient determined
 * stays null.
 */
@Component
public class RecipeCalculator {

    /**
     * @param composition          per-100 g composition of the finished dish
     * @param ingredientsWeight    sum of the ingredient weights
     * @param yieldUsed            final weight used in the calculation
     * @param estimatedYield       true when the yield was not reported and the
     *                             sum of the ingredients was used instead
     * @param nutrientsIncomplete  nutrients summed from only part of the
     *                             ingredients — the value is a floor
     */
    public record Result(
            NutritionalComposition composition,
            BigDecimal ingredientsWeight,
            BigDecimal yieldUsed,
            boolean estimatedYield,
            Set<String> nutrientsIncomplete
    ) {}

    public Result calculate(List<RecipeIngredient> ingredients, BigDecimal yieldReported) {
        BigDecimal ingredientsWeight = BigDecimal.ZERO;
        var total = new NutritionalComposition();
        Set<String> presentAtSome = new LinkedHashSet<>();
        Set<String> missingAtSome = new LinkedHashSet<>();

        for (RecipeIngredient ingredient : ingredients) {
            Food food = ingredient.getFood();
            BigDecimal grams = ingredient.getGrams();
            ingredientsWeight = ingredientsWeight.add(grams);

            NutritionalComposition contribution = food.getComposition().toGrams(grams);
            total = total.sum(contribution);

            for (Nutrient nutrient : Nutrient.SUMMABLE) {
                if (nutrient.read().apply(contribution) != null) {
                    presentAtSome.add(nutrient.key());
                } else {
                    missingAtSome.add(nutrient.key());
                }
            }
        }

        boolean estimated = yieldReported == null;
        BigDecimal yieldGrams = estimated ? ingredientsWeight : yieldReported;

        // A recipe with no ingredient: there is nothing to calculate, and dividing
        // by zero would be the only way to get this wrong.
        if (yieldGrams.signum() <= 0) {
            return new Result(new NutritionalComposition(), BigDecimal.ZERO,
                    BigDecimal.ZERO, estimated, Set.of());
        }

        // A food's composition is always per 100 g. The accumulated total
        // corresponds to the whole yield, so it goes back to the 100 basis.
        BigDecimal factor = BigDecimal.valueOf(100)
                .divide(yieldGrams, 10, RoundingMode.HALF_UP);
        var por100g = new NutritionalComposition();
        for (Nutrient nutrient : Nutrient.ALL) {
            BigDecimal value = nutrient.read().apply(total);
            if (value != null) {
                nutrient.store().accept(por100g, value.multiply(factor)
                        .setScale(NutritionalComposition.SCALE, RoundingMode.HALF_UP));
            }
        }

        // Incomplete is what showed up in some ingredients and was missing in
        // others. What was missing in all of them does not even enter: it stays
        // null, and null already says "not determined" without needing a
        // warning.
        Set<String> incomplete = new LinkedHashSet<>(presentAtSome);
        incomplete.retainAll(missingAtSome);

        return new Result(por100g, ingredientsWeight, yieldGrams, estimated, incomplete);
    }
}
