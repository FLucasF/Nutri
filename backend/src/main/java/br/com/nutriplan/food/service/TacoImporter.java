package br.com.nutriplan.food.service;

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

/**
 * Loads TACO (NEPA/Unicamp, 4th edition) into the public food base during the
 * first boot.
 *
 * The composition data are measurements published by NEPA/Unicamp; the source
 * is recorded on each food (DataSource.TACO) so that it appears in the
 * prescriptions and reports the system generates.
 *
 * Reading the file lives in {@link FoodsTableReader}, shared with the other
 * sources: the columns of the TACO CSV ("energia_kcal", "proteina_g") match the
 * nutrient catalog through name normalization.
 *
 * The import is idempotent: if a food with source TACO already exists, it does
 * nothing. To reimport, delete the rows with source TACO first.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TacoImporter implements ApplicationRunner {

    private static final String FILE = "dados/taco.csv";

    private final FoodRepository foodRepository;
    private final FoodsTableReader reader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (foodRepository.existsBySource(DataSource.TACO)) {
            log.debug("Base TACO já importada; nada a fazer.");
            return;
        }
        var resource = new ClassPathResource(FILE);
        if (!resource.exists()) {
            log.warn("Arquivo {} não encontrado no classpath. A base de alimentos ficará vazia.", FILE);
            return;
        }

        try (var input = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            var result = reader.read(input, DataSource.TACO, ',');
            result.warnings().forEach(warning -> log.warn("TACO: {}", warning));

            if (result.empty()) {
                log.warn("Nenhum alimento da TACO pode ser importado.");
                return;
            }
            foodRepository.saveAll(result.foods());
            log.info("Base TACO importada: {} alimentos ({} linhas ignoradas).",
                    result.foods().size(), result.rowsIgnored());
        } catch (IOException e) {
            // It does not bring the application down: without the public base the
            // system still operates with foods registered by the nutritionist
            // themselves.
            log.error("Falha ao importar a base TACO de {}", FILE, e);
        }
    }
}
