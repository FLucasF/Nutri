package br.com.nutriplan.anthropometry.service;

import br.com.nutriplan.anthropometry.domain.GrowthChart;
import br.com.nutriplan.anthropometry.domain.GrowthIndicator;
import br.com.nutriplan.anthropometry.repository.GrowthChartRepository;
import br.com.nutriplan.patient.domain.Sex;
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

/**
 * Loads the WHO growth curves.
 *
 * The file brings the LMS parameters of two distinct references — the 2006
 * standards for 0 to 5 years and the 2007 reference for 5 to 19 — normalized to
 * age in months, which is the clinical unit and the one SISVAN uses.
 *
 * There are 916 rows: four combinations of indicator and sex, with 229 months
 * each.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class GrowthChartImporter implements ApplicationRunner {

    private static final String FILE = "dados/curvas-oms.csv";

    private final GrowthChartRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (repository.existsByIndicator(GrowthIndicator.BMI_TO_AGE)) {
            log.debug("Curvas de crescimento já carregadas; nada a fazer.");
            return;
        }

        var resource = new ClassPathResource(FILE);
        if (!resource.exists()) {
            log.warn("Arquivo {} não encontrado: a avaliação infantil ficará indisponível.",
                    FILE);
            return;
        }

        List<GrowthChart> charts = new ArrayList<>();
        int ignored = 0;

        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.isBlank()) {
                    continue;
                }
                String[] fields = CsvReader.divide(row);
                if (fields.length < 6) {
                    ignored++;
                    continue;
                }
                try {
                    charts.add(new GrowthChart(
                            GrowthIndicator.valueOf(fields[0].trim()),
                            Sex.valueOf(fields[1].trim()),
                            Integer.parseInt(fields[2].trim()),
                            new BigDecimal(fields[3].trim()),
                            new BigDecimal(fields[4].trim()),
                            new BigDecimal(fields[5].trim())));
                } catch (IllegalArgumentException e) {
                    // A crooked row does not bring the file down, but it does not
                    // disappear in silence either: with no curve, a whole age
                    // band is left without a classification, and that has to
                    // show up in the log.
                    ignored++;
                }
            }
        }

        repository.saveAll(charts);
        log.info("Curvas de crescimento carregadas: {} pontos{}", charts.size(),
                ignored > 0 ? " (%d linhas ignoradas)".formatted(ignored) : "");
    }
}
