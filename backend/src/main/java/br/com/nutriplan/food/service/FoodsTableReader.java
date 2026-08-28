package br.com.nutriplan.food.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.NutritionalComposition;
import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.domain.HouseholdMeasure;
import br.com.nutriplan.food.domain.Nutrient;
import br.com.nutriplan.shared.util.CsvReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a food table in CSV and returns entities ready to store.
 *
 * It exists so that the system is not tied to a specific source: the same
 * routine imports TACO, a slice of Open Food Facts or a spreadsheet the
 * nutritionist put together, simply by reporting the source. The nutrient
 * columns are matched against the catalog in {@link Nutrient}, so adding a
 * nutrient to the catalog makes it importable without touching this file.
 *
 * The column matching is tolerant: it ignores accents, uppercase and the usual
 * separators, so that "Energia (kcal)", "energia_kcal" and "energiaKcal" all
 * reach the same field.
 */
@Component
@Slf4j
public class FoodsTableReader {

    /**
     * Portuguese name of the nutrient column, accepted on import.
     *
     * The catalog key became English, but the files from TACO, IBGE and Open
     * Food Facts go on naming the column in Portuguese — and there is no reason
     * to demand that they change.
     */
    private static final Map<String, String> PORTUGUESE_ALIAS = Map.ofEntries(
            Map.entry("energiaKcal", "energyKcal"),
            Map.entry("energiaKj", "energyKj"),
            Map.entry("proteinaG", "proteinG"),
            Map.entry("carboidratoG", "carbohydrateG"),
            Map.entry("acucaresG", "sugarsG"),
            Map.entry("acucaresAdicionadosG", "sugarsAddedG"),
            Map.entry("fibraG", "fiberG"),
            Map.entry("lipideosG", "fatG"),
            Map.entry("gordurasSaturadasG", "fatSaturatedG"),
            Map.entry("gordurasTransG", "fatTransG"),
            Map.entry("gordurasMonoinsaturadasG", "fatMonounsaturatedG"),
            Map.entry("gordurasPoliinsaturadasG", "fatPolyunsaturatedG"),
            Map.entry("colesterolMg", "cholesterolMg"),
            Map.entry("sodioMg", "sodiumMg"),
            Map.entry("calcioMg", "calciumMg"),
            Map.entry("ferroMg", "ironMg"),
            Map.entry("magnesioMg", "magnesiumMg"),
            Map.entry("fosforoMg", "phosphorusMg"),
            Map.entry("potassioMg", "potassiumMg"),
            Map.entry("zincoMg", "zincMg"),
            Map.entry("cobreMg", "copperMg"),
            Map.entry("manganesMg", "manganeseMg"),
            Map.entry("selenioMcg", "seleniumMcg"),
            Map.entry("vitaminaCMg", "vitaminCMg"),
            Map.entry("tiaminaMg", "thiaminMg"),
            Map.entry("riboflavinaMg", "riboflavinMg"),
            Map.entry("niacinaMg", "niacinMg"),
            Map.entry("piridoxinaMg", "pyridoxineMg"),
            Map.entry("vitaminaB12Mcg", "vitaminB12Mcg"),
            Map.entry("folatoMcg", "folateMcg"),
            Map.entry("vitaminaDMcg", "vitaminDMcg"),
            Map.entry("vitaminaEMg", "vitaminEMg"),
            Map.entry("umidadePct", "moisturePct"),
            Map.entry("cinzasG", "ashG")
    );

    /** Alternative names accepted for the descriptive columns. */
    private static final Map<String, List<String>> ALIAS_DESCRIPTIVE = Map.of(
            "description",   List.of("descricao", "descricaodoalimento", "descricaodosalimentos",
                                     "description", "nome", "name", "alimento", "food",
                                     "produto", "product", "productname"),
            "codigofonte",   List.of("codigo", "codigofonte", "code", "id",
                                     "codigotaco", "codigoibge"),
            "codigobarras",  List.of("codigobarras", "ean", "gtin", "barcode"),
            "brand",         List.of("marca", "marcas", "brand", "brands", "manufacturer"),
            "group",         List.of("grupo", "group", "categoria", "category",
                                     "categories", "grupodealimentos"),
            "quantity",      List.of("quantidade", "quantity", "embalagem", "packaging")
    );

    /** The result of the reading, with the warnings accumulated to show whoever imported. */
    public record Result(List<Food> foods, List<String> warnings, int rowsIgnored) {
        public boolean empty() {
            return foods.isEmpty();
        }
    }

    public Result read(Reader input, DataSource source, char separator) throws IOException {
        List<Food> foods = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int ignored = 0;
        int withoutNutrients = 0;

        try (var reader = new BufferedReader(input)) {
            String header = reader.readLine();
            if (header == null) {
                warnings.add("O arquivo esta vazio.");
                return new Result(foods, warnings, 0);
            }

            Map<String, Integer> columns = mapColumns(CsvReader.divide(header, separator));
            Map<String, Integer> nutrients = mapNutrients(CsvReader.divide(header, separator));

            if (!columns.containsKey("description")) {
                warnings.add("Não encontrei a coluna de descrição do alimento. "
                        + "Nomeie a coluna como \"descricao\", \"nome\" ou \"alimento\".");
                return new Result(foods, warnings, 0);
            }
            if (nutrients.isEmpty()) {
                warnings.add("Nenhuma coluna de nutriente reconhecida. "
                        + "Use nomes como \"energiaKcal\", \"proteinaG\" ou \"Energia (kcal)\".");
                return new Result(foods, warnings, 0);
            }

            log.debug("Colunas reconhecidas: descritivas={}, nutrientes={}",
                    columns.keySet(), nutrients.keySet());

            String row;
            int number = 1;
            while ((row = reader.readLine()) != null) {
                number++;
                if (row.isBlank()) {
                    continue;
                }
                String[] fields = CsvReader.divide(row, separator);
                Food food = build(fields, columns, nutrients, source);
                if (food == null) {
                    ignored++;
                    if (warnings.size() < 20) {
                        warnings.add("Linha %d ignorada: sem descrição do alimento.".formatted(number));
                    }
                    continue;
                }
                if (food.getComposition().empty()) {
                    withoutNutrients++;
                }
                foods.add(food);
            }
        }

        if (ignored > 20) {
            warnings.add("... e mais %d linhas ignoradas pelo mesmo motivo.".formatted(ignored - 20));
        }
        if (withoutNutrients > 0) {
            warnings.add(("%d alimentos entraram sem nenhum nutriente preenchido e aparecerao como "
                    + "\"nao informado\".").formatted(withoutNutrients));
        }
        return new Result(foods, warnings, ignored);
    }

    private Food build(String[] fields, Map<String, Integer> columns,
                            Map<String, Integer> nutrients, DataSource source) {

        String description = value(fields, columns.get("description"));
        if (description == null) {
            return null;
        }

        var composition = new NutritionalComposition();
        nutrients.forEach((key, index) -> {
            BigDecimal value = number(value(fields, index));
            if (value != null) {
                composition.define(key, value);
            }
        });
        // An empty composition does not invalidate the row: official tables carry
        // items whose nutrients were not determined (TACO has one), and making
        // them disappear in silence would falsify the base. They enter the
        // catalog showing "não informado", and whoever imported is told how
        // many there were.
        var food = new Food(trim(description, 250), source);
        food.setComposition(composition);
        food.setCodeSource(trim(value(fields, columns.get("codigofonte")), 30));
        food.setCodeBarcode(trim(value(fields, columns.get("codigobarras")), 20));
        food.setBrand(trim(value(fields, columns.get("brand")), 100));
        food.setGroup(trim(value(fields, columns.get("group")), 100));

        // Processed products usually carry only the barcode; using it as the
        // source code too keeps the deduplication key filled in.
        if (food.getCodeSource() == null && food.getCodeBarcode() != null) {
            food.setCodeSource(food.getCodeBarcode());
        }

        packageServing(value(fields, columns.get("quantity")))
                .ifPresent(food::addMeasure);

        return food;
    }

    private static final java.util.regex.Pattern QUANTITY = java.util.regex.Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(kg|g|gr|gramas?|ml|l|lt|litros?)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Derives the "1 package" portion from the weight declared on the label.
     *
     * It is the portion that makes sense for a processed product: the patient
     * understands "1 can" or "1 pack", not "395 g".
     *
     * Milliliter is treated as gram on purpose. Labels for liquids declare the
     * composition per 100 ml, and that is the same basis on which it was
     * imported — reading 1 L as 1000 g keeps the calculation consistent with
     * the source data, without needing the density of each product.
     */
    private java.util.Optional<HouseholdMeasure> packageServing(String quantity) {
        if (quantity == null) {
            return java.util.Optional.empty();
        }
        var m = QUANTITY.matcher(quantity);
        if (!m.find()) {
            return java.util.Optional.empty();
        }
        BigDecimal value = number(m.group(1));
        if (value == null || value.signum() <= 0) {
            return java.util.Optional.empty();
        }
        String unit = m.group(2).toLowerCase();
        BigDecimal grams = switch (unit) {
            case "kg", "l", "lt", "litro", "litros" -> value.multiply(BigDecimal.valueOf(1000));
            default -> value;
        };
        // Absurd packages (a typo) do not become a portion.
        if (grams.compareTo(BigDecimal.valueOf(20000)) > 0) {
            return java.util.Optional.empty();
        }

        var measure = new HouseholdMeasure("embalagem (%s)".formatted(quantity.trim()),
                grams.stripTrailingZeros());
        measure.setStandard(true);
        return java.util.Optional.of(measure);
    }

    private Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> encontradas = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String normalized = normalize(header[i]);
            ALIAS_DESCRIPTIVE.forEach((field, aliases) -> {
                if (aliases.contains(normalized)) {
                    encontradas.putIfAbsent(field, indexDe(header, normalized));
                }
            });
        }
        return encontradas;
    }

    /**
     * Matches columns against the nutrient catalog. Besides the catalog key, it
     * accepts the underscore form ("energia_kcal") and the form with the unit
     * in parentheses ("Energia (kcal)"), which is how published tables tend to
     * name them.
     */
    private Map<String, Integer> mapNutrients(String[] header) {
        Map<String, String> byNameNormalized = new HashMap<>();
        for (Nutrient nutrient : Nutrient.ALL) {
            byNameNormalized.put(normalize(nutrient.key()), nutrient.key());
            byNameNormalized.put(
                    normalize(nutrient.label() + nutrient.unit()), nutrient.key());
        }
        PORTUGUESE_ALIAS.forEach(
                (inPortuguese, key) -> byNameNormalized.put(normalize(inPortuguese), key));

        Map<String, Integer> encontradas = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = byNameNormalized.get(normalize(header[i]));
            if (key != null) {
                encontradas.putIfAbsent(key, i);
            }
        }
        return encontradas;
    }

    private int indexDe(String[] header, String normalized) {
        for (int i = 0; i < header.length; i++) {
            if (normalize(header[i]).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    /** Removes accents, spaces, underscores, hyphens and parentheses; returns lowercase. */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccent = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccent.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String value(String[] fields, Integer index) {
        if (index == null || index < 0 || index >= fields.length) {
            return null;
        }
        String raw = fields[index].trim();
        return raw.isEmpty() ? null : raw;
    }

    private String trim(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    /** It accepts a decimal comma, common in Brazilian spreadsheets. */
    private BigDecimal number(String text) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
