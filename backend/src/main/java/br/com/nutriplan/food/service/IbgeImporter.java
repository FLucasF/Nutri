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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the IBGE table (POF 2008-2009) and the household measures that come
 * with it.
 *
 * The table fills the gap between TACO and the processed products: these are
 * foods **as consumed** in Brazil, with the preparation as a dimension of its
 * own — the same food appears raw, boiled, fried and breaded, each with a
 * distinct composition, because frying changes the food.
 *
 * It also brings five nutrients TACO does not determine: selenium, cobalamin,
 * folate, vitamin D and vitamin E.
 *
 * The measures that come with it have better provenance than any estimate:
 * they were recorded in the field by the survey's interviewer, with the
 * utensil the family actually used.
 *
 * Source: IBGE, Pesquisa de Orçamentos Familiares 2008-2009.
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class IbgeImporter implements ApplicationRunner {

    private static final String FILE_COMPOSITION = "dados/ibge.csv";
    private static final String FILE_MEASURES = "dados/ibge-medidas.csv";

    private final FoodRepository foodRepository;
    private final HouseholdMeasureRepository householdMeasureRepository;
    private final FoodsTableReader reader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (foodRepository.existsBySource(DataSource.IBGE)) {
            log.debug("Tabela do IBGE já importada; nada a fazer.");
            return;
        }
        var resource = new ClassPathResource(FILE_COMPOSITION);
        if (!resource.exists()) {
            log.info("Arquivo {} ausente; a base seguirá sem a tabela do IBGE.", FILE_COMPOSITION);
            return;
        }

        try (var input = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            var result = reader.read(input, DataSource.IBGE, ',');
            result.warnings().forEach(warning -> log.warn("IBGE: {}", warning));

            if (result.empty()) {
                log.warn("Nenhum alimento do IBGE pôde ser importado.");
                return;
            }
            foodRepository.saveAll(result.foods());
            foodRepository.flush();
            log.info("Tabela do IBGE importada: {} alimentos.", result.foods().size());

            importMeasures();
        } catch (IOException e) {
            log.error("Falha ao importar {}", FILE_COMPOSITION, e);
        }
    }

    /**
     * Links the measures to the just-saved foods by the POF code.
     *
     * The portions come in with a null accountId — they belong to the shared
     * catalog, like the others that ship with the system.
     */
    private void importMeasures() throws IOException {
        var resource = new ClassPathResource(FILE_MEASURES);
        if (!resource.exists()) {
            log.warn("Arquivo {} ausente; os alimentos do IBGE ficarão sem porções.", FILE_MEASURES);
            return;
        }

        Map<String, Food> byCode = foodRepository.findBySource(DataSource.IBGE).stream()
                .filter(a -> a.getCodeSource() != null)
                .collect(Collectors.toMap(Food::getCodeSource, Function.identity(), (a, b) -> a));

        List<HouseholdMeasure> measures = new ArrayList<>();
        int withoutFood = 0;

        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // header
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.isBlank()) {
                    continue;
                }
                // A splitter that respects quotes: 11 utensils carry a decimal
                // comma in their name, like "garrafa (1,5 l)".
                String[] fields = CsvReader.divide(row);
                if (fields.length < 4) {
                    continue;
                }
                Food food = byCode.get(fields[0].trim());
                if (food == null) {
                    withoutFood++;
                    continue;
                }
                try {
                    var measure = new HouseholdMeasure(fields[1].trim(), new BigDecimal(fields[2].trim()));
                    measure.setStandard(Boolean.parseBoolean(fields[3].trim()));
                    measure.setFood(food);
                    measures.add(measure);
                } catch (NumberFormatException e) {
                    log.debug("Peso inválido na medida do IBGE: {}", fields[2]);
                }
            }
        }

        if (withoutFood > 0) {
            log.warn("{} medidas do IBGE sem alimento correspondente.", withoutFood);
        }
        householdMeasureRepository.saveAll(measures);
        log.info("Medidas caseiras do IBGE carregadas: {} porções.", measures.size());
    }
}
