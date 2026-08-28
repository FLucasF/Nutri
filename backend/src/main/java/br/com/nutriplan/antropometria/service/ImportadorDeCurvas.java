package br.com.nutriplan.antropometria.service;

import br.com.nutriplan.antropometria.domain.CurvaDeCrescimento;
import br.com.nutriplan.antropometria.domain.IndicadorDeCrescimento;
import br.com.nutriplan.antropometria.repository.CurvaDeCrescimentoRepository;
import br.com.nutriplan.paciente.domain.Sexo;
import br.com.nutriplan.shared.util.LeitorCsv;
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
 * Carrega as curvas de crescimento da OMS.
 *
 * O arquivo traz os parâmetros LMS de duas referências distintas — os padrões
 * de 2006 para 0 a 5 anos e a referência de 2007 para 5 a 19 — normalizados
 * para idade em meses, que é a unidade clínica e a que o SISVAN usa.
 *
 * São 916 linhas: quatro combinações de indicador e sexo, com 229 meses cada.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class ImportadorDeCurvas implements ApplicationRunner {

    private static final String ARQUIVO = "dados/curvas-oms.csv";

    private final CurvaDeCrescimentoRepository repositorio;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (repositorio.existsByIndicador(IndicadorDeCrescimento.IMC_PARA_IDADE)) {
            log.debug("Curvas de crescimento já carregadas; nada a fazer.");
            return;
        }

        var recurso = new ClassPathResource(ARQUIVO);
        if (!recurso.exists()) {
            log.warn("Arquivo {} não encontrado: a avaliação infantil ficará indisponível.",
                    ARQUIVO);
            return;
        }

        List<CurvaDeCrescimento> curvas = new ArrayList<>();
        int ignoradas = 0;

        try (var leitor = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            leitor.readLine(); // cabeçalho
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                String[] campos = LeitorCsv.dividir(linha);
                if (campos.length < 6) {
                    ignoradas++;
                    continue;
                }
                try {
                    curvas.add(new CurvaDeCrescimento(
                            IndicadorDeCrescimento.valueOf(campos[0].trim()),
                            Sexo.valueOf(campos[1].trim()),
                            Integer.parseInt(campos[2].trim()),
                            new BigDecimal(campos[3].trim()),
                            new BigDecimal(campos[4].trim()),
                            new BigDecimal(campos[5].trim())));
                } catch (IllegalArgumentException e) {
                    // Linha torta não derruba o arquivo, mas também não some em
                    // silêncio: sem curva, uma faixa etária inteira fica sem
                    // classificação, e isso precisa aparecer no log.
                    ignoradas++;
                }
            }
        }

        repositorio.saveAll(curvas);
        log.info("Curvas de crescimento carregadas: {} pontos{}", curvas.size(),
                ignoradas > 0 ? " (%d linhas ignoradas)".formatted(ignoradas) : "");
    }
}
