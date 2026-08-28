package br.com.nutriplan.food.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalog of the nutrients the system tracks.
 *
 * It exists so that scaling, summing and displaying a composition are generic
 * operations over this list, instead of 30 repeated lines per operation. Before
 * this, the composition was assembled by a positional constructor of dozens of
 * arguments, where swapping two nutrients would go unnoticed — a silent and
 * serious error in a system that calculates prescriptions.
 *
 * Adding a new nutrient becomes one line here, plus the column in the migration
 * and the getter/setter pair on the entity.
 *
 * @param group used by the interface to group the display
 */
public record Nutrient(
        String key,
        String label,
        String unit,
        Group group,
        Function<NutritionalComposition, BigDecimal> read,
        BiConsumer<NutritionalComposition, BigDecimal> store
) {

    public enum Group { ENERGY, MACRONUTRIENT, LIPIDIO, MINERAL, VITAMIN, OTHER }

    private static Nutrient n(String key, String label, String unit, Group group,
                               Function<NutritionalComposition, BigDecimal> read,
                               BiConsumer<NutritionalComposition, BigDecimal> store) {
        return new Nutrient(key, label, unit, group, read, store);
    }

    /** The order of this list is the display order in the interface and in the reports. */
    public static final List<Nutrient> ALL = List.of(
            n("energyKcal", "Energia", "kcal", Group.ENERGY,
                    NutritionalComposition::getEnergyKcal, NutritionalComposition::setEnergyKcal),
            n("energyKj", "Energia", "kJ", Group.ENERGY,
                    NutritionalComposition::getEnergyKj, NutritionalComposition::setEnergyKj),

            n("proteinG", "Proteínas", "g", Group.MACRONUTRIENT,
                    NutritionalComposition::getProteinG, NutritionalComposition::setProteinG),
            n("carbohydrateG", "Carboidratos", "g", Group.MACRONUTRIENT,
                    NutritionalComposition::getCarbohydrateG, NutritionalComposition::setCarbohydrateG),
            n("sugarsG", "Açúcares totais", "g", Group.MACRONUTRIENT,
                    NutritionalComposition::getSugarsG, NutritionalComposition::setSugarsG),
            n("sugarsAddedG", "Açúcares adicionados", "g", Group.MACRONUTRIENT,
                    NutritionalComposition::getSugarsAddedG, NutritionalComposition::setSugarsAddedG),
            n("fiberG", "Fibra alimentar", "g", Group.MACRONUTRIENT,
                    NutritionalComposition::getFiberG, NutritionalComposition::setFiberG),

            n("fatG", "Gorduras totais", "g", Group.LIPIDIO,
                    NutritionalComposition::getFatG, NutritionalComposition::setFatG),
            n("fatSaturatedG", "Gorduras saturadas", "g", Group.LIPIDIO,
                    NutritionalComposition::getFatSaturatedG, NutritionalComposition::setFatSaturatedG),
            n("fatTransG", "Gorduras trans", "g", Group.LIPIDIO,
                    NutritionalComposition::getFatTransG, NutritionalComposition::setFatTransG),
            n("fatMonounsaturatedG", "Gorduras monoinsaturadas", "g", Group.LIPIDIO,
                    NutritionalComposition::getFatMonounsaturatedG, NutritionalComposition::setFatMonounsaturatedG),
            n("fatPolyunsaturatedG", "Gorduras poli-insaturadas", "g", Group.LIPIDIO,
                    NutritionalComposition::getFatPolyunsaturatedG, NutritionalComposition::setFatPolyunsaturatedG),
            n("cholesterolMg", "Colesterol", "mg", Group.LIPIDIO,
                    NutritionalComposition::getCholesterolMg, NutritionalComposition::setCholesterolMg),

            n("sodiumMg", "Sódio", "mg", Group.MINERAL,
                    NutritionalComposition::getSodiumMg, NutritionalComposition::setSodiumMg),
            n("calciumMg", "Cálcio", "mg", Group.MINERAL,
                    NutritionalComposition::getCalciumMg, NutritionalComposition::setCalciumMg),
            n("ironMg", "Ferro", "mg", Group.MINERAL,
                    NutritionalComposition::getIronMg, NutritionalComposition::setIronMg),
            n("magnesiumMg", "Magnésio", "mg", Group.MINERAL,
                    NutritionalComposition::getMagnesiumMg, NutritionalComposition::setMagnesiumMg),
            n("phosphorusMg", "Fósforo", "mg", Group.MINERAL,
                    NutritionalComposition::getPhosphorusMg, NutritionalComposition::setPhosphorusMg),
            n("potassiumMg", "Potássio", "mg", Group.MINERAL,
                    NutritionalComposition::getPotassiumMg, NutritionalComposition::setPotassiumMg),
            n("zincMg", "Zinco", "mg", Group.MINERAL,
                    NutritionalComposition::getZincMg, NutritionalComposition::setZincMg),
            n("copperMg", "Cobre", "mg", Group.MINERAL,
                    NutritionalComposition::getCopperMg, NutritionalComposition::setCopperMg),
            n("manganeseMg", "Manganês", "mg", Group.MINERAL,
                    NutritionalComposition::getManganeseMg, NutritionalComposition::setManganeseMg),
            n("seleniumMcg", "Selênio", "mcg", Group.MINERAL,
                    NutritionalComposition::getSeleniumMcg, NutritionalComposition::setSeleniumMcg),

            n("vitaminCMg", "Vitamina C", "mg", Group.VITAMIN,
                    NutritionalComposition::getVitaminCMg, NutritionalComposition::setVitaminCMg),
            n("thiaminMg", "Tiamina (B1)", "mg", Group.VITAMIN,
                    NutritionalComposition::getThiaminMg, NutritionalComposition::setThiaminMg),
            n("riboflavinMg", "Riboflavina (B2)", "mg", Group.VITAMIN,
                    NutritionalComposition::getRiboflavinMg, NutritionalComposition::setRiboflavinMg),
            n("niacinMg", "Niacina (B3)", "mg", Group.VITAMIN,
                    NutritionalComposition::getNiacinMg, NutritionalComposition::setNiacinMg),
            n("pyridoxineMg", "Piridoxina (B6)", "mg", Group.VITAMIN,
                    NutritionalComposition::getPyridoxineMg, NutritionalComposition::setPyridoxineMg),
            n("retinolMcg", "Retinol", "mcg", Group.VITAMIN,
                    NutritionalComposition::getRetinolMcg, NutritionalComposition::setRetinolMcg),
            n("reMcg", "Equivalente de retinol (RE)", "mcg", Group.VITAMIN,
                    NutritionalComposition::getReMcg, NutritionalComposition::setReMcg),
            n("raeMcg", "Equivalente de atividade de retinol (RAE)", "mcg", Group.VITAMIN,
                    NutritionalComposition::getRaeMcg, NutritionalComposition::setRaeMcg),
            n("vitaminB12Mcg", "Cobalamina (B12)", "mcg", Group.VITAMIN,
                    NutritionalComposition::getVitaminB12Mcg, NutritionalComposition::setVitaminB12Mcg),
            n("folateMcg", "Folato", "mcg", Group.VITAMIN,
                    NutritionalComposition::getFolateMcg, NutritionalComposition::setFolateMcg),
            n("vitaminDMcg", "Vitamina D", "mcg", Group.VITAMIN,
                    NutritionalComposition::getVitaminDMcg, NutritionalComposition::setVitaminDMcg),
            n("vitaminEMg", "Vitamina E", "mg", Group.VITAMIN,
                    NutritionalComposition::getVitaminEMg, NutritionalComposition::setVitaminEMg),

            n("moisturePct", "Umidade", "%", Group.OTHER,
                    NutritionalComposition::getMoisturePct, NutritionalComposition::setMoisturePct),
            n("ashG", "Cinzas", "g", Group.OTHER,
                    NutritionalComposition::getAshG, NutritionalComposition::setAshG)
    );

    public static final Map<String, Nutrient> BY_KEY =
            ALL.stream().collect(Collectors.toMap(Nutrient::key, x -> x));

    /**
     * Nutrients whose total cannot simply be summed across foods. Moisture is a
     * percentage and energy in kJ is redundant with the kcal; both go on being
     * scaled per portion, but they do not enter meal totals.
     */
    public static final List<Nutrient> SUMMABLE = ALL.stream()
            .filter(x -> !x.key().equals("moisturePct"))
            .toList();
}
