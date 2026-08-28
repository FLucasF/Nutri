package br.com.nutriplan.food.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.domain.HouseholdMeasure;
import br.com.nutriplan.food.repository.FoodRepository;
import br.com.nutriplan.food.repository.HouseholdMeasureRepository;
import br.com.nutriplan.shared.util.CsvReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the base catalog of household measures.
 *
 * It runs after TacoImporter (@Order) because it needs the foods already stored
 * in order to match the table code with the generated id.
 *
 * TACO publishes composition per 100 g, but it does not publish usual portions.
 * The weights in this catalog are estimates of current Brazilian portions and
 * exist so that the meal plan comes out readable for the patient — nobody
 * serves 5 g of salt, they serve a pinch. Each practice can register its own
 * version of any measure, and its own version takes precedence over the
 * catalog's.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class MeasuresImporter implements ApplicationRunner {

    private static final String FILE = "dados/medidas.csv";

    private final FoodRepository foodRepository;
    private final HouseholdMeasureRepository householdMeasureRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (householdMeasureRepository.countByAccountIdIsNull() > 0) {
            log.debug("Acervo de medidas caseiras já carregado; nada a fazer.");
            return;
        }
        var resource = new ClassPathResource(FILE);
        if (!resource.exists()) {
            log.warn("Arquivo {} não encontrado. Os alimentos ficarão sem porções usuais.", FILE);
            return;
        }

        Map<String, Food> byCode = foodRepository.findBySource(DataSource.TACO).stream()
                .filter(a -> a.getCodeSource() != null)
                .collect(Collectors.toMap(Food::getCodeSource, Function.identity(), (a, b) -> a));

        if (byCode.isEmpty()) {
            log.warn("Nenhum alimento TACO encontrado; medidas caseiras não importadas.");
            return;
        }

        try {
            List<HouseholdMeasure> measures = read(resource, byCode);
            householdMeasureRepository.saveAll(measures);
            log.info("Acervo de medidas caseiras carregado: {} porções para {} alimentos.",
                    measures.size(), byCode.size());
        } catch (IOException e) {
            log.error("Falha ao carregar medidas caseiras de {}", FILE, e);
        }
    }

    private List<HouseholdMeasure> read(ClassPathResource resource, Map<String, Food> byCode)
            throws IOException {

        List<HouseholdMeasure> measures = new ArrayList<>();
        Map<String, Integer> standardsByCode = new HashMap<>();
        int withoutMatch = 0;

        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // header
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.isBlank()) {
                    continue;
                }
                String[] fields = CsvReader.divide(row);
                if (fields.length < 4) {
                    continue;
                }
                String code = fields[0].trim();
                Food food = byCode.get(code);
                if (food == null) {
                    withoutMatch++;
                    continue;
                }

                var measure = new HouseholdMeasure(fields[1].trim(), new BigDecimal(fields[2].trim()));
                boolean standard = Boolean.parseBoolean(fields[3].trim());
                if (standard) {
                    standardsByCode.merge(code, 1, Integer::sum);
                }
                measure.setStandard(standard);
                measure.setFood(food);
                // null accountId: a base catalog portion, visible to everyone.
                measures.add(measure);
            }
        }

        if (withoutMatch > 0) {
            log.warn("{} linhas de medidas ignoradas por não casarem com nenhum alimento.",
                    withoutMatch);
        }
        standardsByCode.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> log.warn("Alimento {} tem {} medidas marcadas como padrão.",
                        e.getKey(), e.getValue()));

        return measures;
    }
}
