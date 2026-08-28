package br.com.nutriplan.food.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the Brazilian slice of Open Food Facts: processed products with a
 * barcode, which cover what TACO does not reach.
 *
 * Data under the Open Database License (ODbL) — free use with attribution. The
 * attribution travels with the food in DataSource.OPEN_FOOD_FACTS and appears
 * in the prescriptions and reports generated from it.
 *
 * It runs last (@Order 3) so as not to delay the boot with the largest file,
 * and it is idempotent: if a food from this source exists, it does nothing.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class OpenFoodFactsImporter implements ApplicationRunner {

    private static final String FILE = "dados/openfoodfacts-br.csv";
    private static final int BATCH_SIZE = 500;

    private final FoodRepository foodRepository;
    private final FoodsTableReader reader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (foodRepository.existsBySource(DataSource.OPEN_FOOD_FACTS)) {
            log.debug("Produtos do Open Food Facts já importados; nada a fazer.");
            return;
        }
        var resource = new ClassPathResource(FILE);
        if (!resource.exists()) {
            log.info("Arquivo {} ausente; a base seguira sem produtos industrializados.", FILE);
            return;
        }

        try (var input = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            var result = reader.read(input, DataSource.OPEN_FOOD_FACTS, ',');

            result.warnings().forEach(warning -> log.warn("Open Food Facts: {}", warning));
            if (result.empty()) {
                log.warn("Nenhum produto do Open Food Facts pode ser importado.");
                return;
            }

            List<Food> withoutDuplicates = removeDuplicates(result.foods());
            storeAtBatches(withoutDuplicates);

            log.info("Open Food Facts importado: {} produtos brasileiros ({} linhas ignoradas).",
                    withoutDuplicates.size(), result.rowsIgnored());
        } catch (IOException e) {
            log.error("Falha ao importar {}", FILE, e);
        }
    }

    /**
     * The same barcode appears more than once in the dump (duplicate records
     * from the collaborative base). Without pruning here, the unique constraint
     * on (source, barcode) would abort the whole import.
     */
    private List<Food> removeDuplicates(List<Food> foods) {
        Set<String> seen = new HashSet<>();
        List<Food> unique = new ArrayList<>(foods.size());
        int duplicated = 0;

        for (Food food : foods) {
            String key = food.getCodeBarcode() != null
                    ? food.getCodeBarcode()
                    : food.getCodeSource();
            if (key == null || seen.add(key)) {
                unique.add(food);
            } else {
                duplicated++;
            }
        }
        if (duplicated > 0) {
            log.info("{} produtos repetidos descartados na importação.", duplicated);
        }
        return unique;
    }

    /** It saves in batches so as not to hold tens of thousands of entities in the context. */
    private void storeAtBatches(List<Food> foods) {
        for (int start = 0; start < foods.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, foods.size());
            foodRepository.saveAll(foods.subList(start, end));
            foodRepository.flush();
        }
    }
}
