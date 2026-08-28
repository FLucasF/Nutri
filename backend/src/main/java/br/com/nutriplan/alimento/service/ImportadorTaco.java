package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
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
 * Carrega a TACO (NEPA/Unicamp, 4a edicao) na base de alimentos publica
 * durante o primeiro boot.
 *
 * Os dados de composicao sao medicoes publicadas pelo NEPA/Unicamp; a fonte
 * fica registrada em cada alimento (FonteDeDados.TACO) para que apareca nas
 * prescricoes e relatorios gerados pelo sistema.
 *
 * A leitura do arquivo fica em {@link LeitorDeTabelaDeAlimentos}, compartilhada
 * com as demais fontes: as colunas do CSV da TACO ("energia_kcal", "proteina_g")
 * casam com o catalogo de nutrientes pela normalizacao de nomes.
 *
 * A importacao e idempotente: se ja existir alimento com fonte TACO, nao faz
 * nada. Para reimportar, apague as linhas de fonte TACO antes.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class ImportadorTaco implements ApplicationRunner {

    private static final String ARQUIVO = "dados/taco.csv";

    private final AlimentoRepository alimentoRepository;
    private final LeitorDeTabelaDeAlimentos leitor;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (alimentoRepository.existsByFonte(FonteDeDados.TACO)) {
            log.debug("Base TACO já importada; nada a fazer.");
            return;
        }
        var recurso = new ClassPathResource(ARQUIVO);
        if (!recurso.exists()) {
            log.warn("Arquivo {} não encontrado no classpath. A base de alimentos ficará vazia.", ARQUIVO);
            return;
        }

        try (var entrada = new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8)) {
            var resultado = leitor.ler(entrada, FonteDeDados.TACO, ',');
            resultado.avisos().forEach(aviso -> log.warn("TACO: {}", aviso));

            if (resultado.vazio()) {
                log.warn("Nenhum alimento da TACO pode ser importado.");
                return;
            }
            alimentoRepository.saveAll(resultado.alimentos());
            log.info("Base TACO importada: {} alimentos ({} linhas ignoradas).",
                    resultado.alimentos().size(), resultado.linhasIgnoradas());
        } catch (IOException e) {
            // Nao derruba a aplicacao: sem a base publica o sistema ainda opera
            // com alimentos cadastrados pelo proprio nutricionista.
            log.error("Falha ao importar a base TACO de {}", ARQUIVO, e);
        }
    }
}
