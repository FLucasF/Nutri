package br.com.nutriplan.prescription.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.prescription.domain.AdequacyBand;
import br.com.nutriplan.food.domain.NutritionalComposition;
import br.com.nutriplan.food.domain.Nutrient;
import br.com.nutriplan.prescription.domain.MealItem;
import br.com.nutriplan.prescription.domain.Meal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Totals the nutritional composition of meals and of the plan.
 *
 * The delicate point of this calculation is not the arithmetic, it is the
 * honesty of the result. Composition tables carry nutrients that were not
 * determined, and summing "present + absent" while treating absent as zero
 * would produce a total that looks exact and is not — the professional would
 * read 4 mg of iron in a plan where half the items never had iron analyzed at
 * all.
 *
 * The output is therefore not just a number: each nutrient comes with how many
 * items contributed to it. When there is a gap, the total is a floor, and the
 * interface has to say so.
 */
@Component
public class NutritionalCalculator {

    /** Energy percentage per gram, used in the macronutrient distribution. */
    private static final BigDecimal KCAL_BY_G_PROTEIN = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_BY_G_CARBOHYDRATE = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_BY_G_LIPID = BigDecimal.valueOf(9);

    /**
     * Total of a set of items.
     *
     * @param itemsInCalculation      how many items had a known weight and food
     * @param itemsOutsideCalculation items with no weight — qualitative or purely textual
     * @param coverage                per nutrient, how many items had the data
     */
    public record Total(
            NutritionalComposition composition,
            int itemsInCalculation,
            int itemsOutsideCalculation,
            Map<String, Integer> coverage
    ) {
        /** Nutrients that no item reported. */
        public Set<String> nutrientsWithoutDatum() {
            Set<String> missing = new TreeSet<>();
            for (Nutrient nutrient : Nutrient.ALL) {
                if (coverage.getOrDefault(nutrient.key(), 0) == 0) {
                    missing.add(nutrient.key());
                }
            }
            return missing;
        }

        /**
         * Nutrients reported by some of the items, but not by all: the total
         * exists, but it underestimates the real value.
         */
        public Set<String> nutrientsIncomplete() {
            Set<String> incomplete = new TreeSet<>();
            coverage.forEach((key, howMany) -> {
                if (howMany > 0 && howMany < itemsInCalculation) {
                    incomplete.add(key);
                }
            });
            return incomplete;
        }

        public boolean reliable() {
            return itemsInCalculation > 0 && nutrientsIncomplete().isEmpty();
        }
    }

    /**
     * Sums the items using the composition of the foods provided.
     *
     * @param foodsById foods already loaded, to avoid one query per item
     */
    public Total total(List<MealItem> items, Map<Long, Food> foodsById) {
        var accumulated = new NutritionalComposition();
        var coverage = new java.util.HashMap<String, Integer>();
        int inside = 0;
        int outside = 0;

        for (MealItem item : items) {
            if (!item.entersCalculation()) {
                outside++;
                continue;
            }
            Food food = foodsById.get(item.getFoodId());
            if (food == null) {
                // A food removed from the catalog after the prescription. The item
                // stays in the plan, but there is nothing to add up.
                outside++;
                continue;
            }

            NutritionalComposition forServing = food.compositionTo(item.getGrams());
            accumulated = accumulated.sum(forServing);
            inside++;

            for (Nutrient nutrient : Nutrient.ALL) {
                if (nutrient.read().apply(forServing) != null) {
                    coverage.merge(nutrient.key(), 1, Integer::sum);
                }
            }
        }

        return new Total(accumulated, inside, outside, Map.copyOf(coverage));
    }

    /**
     * Total of the whole plan, adding up every meal that counts.
     *
     * A meal switched out of the calculation is skipped here and nowhere else:
     * it keeps its own total, which is what makes it useful as a substitute —
     * the professional prescribes two options for lunch and sees what each one
     * is worth, while the day adds up only once.
     */
    public Total totalMeals(List<Meal> meals, Map<Long, Food> foodsById) {
        List<MealItem> all = new ArrayList<>();
        meals.stream().filter(Meal::isInCalculation).forEach(r -> all.addAll(r.getItems()));
        return total(all, foodsById);
    }

    /**
     * Percentage distribution of energy across the macronutrients.
     *
     * Calculated from the grams of each macro through the Atwater factors, and
     * not from the declared energy, so that the three percentages add up to 100
     * even when the source rounds the energy independently of the macros.
     *
     * It returns null if any one of the three is missing: a distribution with
     * two macronutrients is not a distribution, and showing it would lead the
     * professional to a wrong reading.
     */
    public DistributionMacros macrosDistribution(NutritionalComposition composition) {
        BigDecimal protein = composition.getProteinG();
        BigDecimal carbohydrate = composition.getCarbohydrateG();
        BigDecimal lipid = composition.getFatG();

        if (protein == null || carbohydrate == null || lipid == null) {
            return null;
        }

        BigDecimal kcalProtein = protein.multiply(KCAL_BY_G_PROTEIN);
        BigDecimal kcalCarbohydrate = carbohydrate.multiply(KCAL_BY_G_CARBOHYDRATE);
        BigDecimal kcalLipid = lipid.multiply(KCAL_BY_G_LIPID);
        BigDecimal totalKcal = kcalProtein.add(kcalCarbohydrate).add(kcalLipid);

        if (totalKcal.signum() <= 0) {
            return null;
        }

        return new DistributionMacros(
                percentage(kcalProtein, totalKcal),
                percentage(kcalCarbohydrate, totalKcal),
                percentage(kcalLipid, totalKcal),
                totalKcal.setScale(1, RoundingMode.HALF_UP));
    }

    /**
     * O que foi prescrito contra o que foi planejado, macro a macro.
     *
     * É a aba de distribuição da página 32: uma coluna do prescrito, uma do
     * teórico, uma da diferença, e a faixa que decide a cor do rótulo.
     *
     * O teórico sai da meta energética repartida pelas porcentagens
     * planejadas, convertida em gramas pelos mesmos fatores de Atwater que o
     * resto da classe usa. Usar outro fator aqui faria as duas colunas
     * responderem perguntas ligeiramente diferentes.
     *
     * @param weightKg peso programado, para o g/kg. Nulo omite a coluna.
     */
    public List<MacroComparison> comparison(NutritionalComposition prescribed,
                                            BigDecimal targetKcal,
                                            BigDecimal proteinPct,
                                            BigDecimal carbohydratePct,
                                            BigDecimal fatPct,
                                            BigDecimal weightKg) {
        if (targetKcal == null || targetKcal.signum() <= 0) {
            return List.of();
        }
        var rows = new ArrayList<MacroComparison>();
        rows.add(row("protein", "Proteínas", prescribed.getProteinG(),
                targetKcal, proteinPct, KCAL_BY_G_PROTEIN, weightKg));
        rows.add(row("carbohydrate", "Carboidratos", prescribed.getCarbohydrateG(),
                targetKcal, carbohydratePct, KCAL_BY_G_CARBOHYDRATE, weightKg));
        rows.add(row("fat", "Gorduras", prescribed.getFatG(),
                targetKcal, fatPct, KCAL_BY_G_LIPID, weightKg));
        return List.copyOf(rows);
    }

    private MacroComparison row(String macro, String description,
                                BigDecimal prescribedG, BigDecimal targetKcal,
                                BigDecimal pct, BigDecimal kcalByG, BigDecimal weightKg) {
        BigDecimal theoreticalKcal = pct == null ? null
                : targetKcal.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal theoreticalG = theoreticalKcal == null ? null
                : theoreticalKcal.divide(kcalByG, 1, RoundingMode.HALF_UP);

        BigDecimal prescribed = prescribedG == null ? null
                : prescribedG.setScale(1, RoundingMode.HALF_UP);
        BigDecimal difference = (prescribed == null || theoreticalG == null) ? null
                : prescribed.subtract(theoreticalG);

        BigDecimal adequacy = null;
        if (prescribed != null && theoreticalG != null && theoreticalG.signum() > 0) {
            adequacy = prescribed.multiply(BigDecimal.valueOf(100))
                    .divide(theoreticalG, 1, RoundingMode.HALF_UP);
        }

        return new MacroComparison(macro, description,
                prescribed, theoreticalG, difference,
                prescribed == null ? null : prescribed.multiply(kcalByG),
                theoreticalKcal,
                adequacy, AdequacyBand.of(adequacy),
                perKg(prescribed, weightKg), perKg(theoreticalG, weightKg));
    }

    /** Gramas por quilo do peso programado. Nulo quando não há peso. */
    private BigDecimal perKg(BigDecimal grams, BigDecimal weightKg) {
        if (grams == null || weightKg == null || weightKg.signum() <= 0) {
            return null;
        }
        return grams.divide(weightKg, 2, RoundingMode.HALF_UP);
    }

    public record MacroComparison(
            String macro,
            String description,
            BigDecimal prescribedG,
            BigDecimal theoreticalG,
            BigDecimal differenceG,
            BigDecimal prescribedKcal,
            BigDecimal theoreticalKcal,
            BigDecimal adequacyPct,
            AdequacyBand band,
            /** g/kg sobre o peso programado. Ele não usa na tela, mas quer no relatório. */
            BigDecimal prescribedPerKg,
            BigDecimal theoreticalPerKg
    ) {}

    public record DistributionMacros(
            BigDecimal proteinPct,
            BigDecimal carbohydratePct,
            BigDecimal lipidPct,
            BigDecimal calculatedEnergyKcal
    ) {}

    private BigDecimal percentage(BigDecimal part, BigDecimal total) {
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }

    /**
     * Adequacy of what was prescribed against the energy goal, as a percentage.
     * Null when there is no goal defined or no energy calculated.
     */
    public BigDecimal adequacyEnergy(NutritionalComposition composition, BigDecimal targetKcal) {
        BigDecimal energy = composition.getEnergyKcal();
        if (energy == null || targetKcal == null || targetKcal.signum() <= 0) {
            return null;
        }
        return energy.multiply(BigDecimal.valueOf(100))
                .divide(targetKcal, 1, RoundingMode.HALF_UP);
    }
}
