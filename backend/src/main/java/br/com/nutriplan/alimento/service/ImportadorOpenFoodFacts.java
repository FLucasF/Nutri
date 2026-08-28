package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.Alimento;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Carrega o recorte brasileiro do Open Food Facts: produtos industrializados
 * com codigo de barras, que cobrem o que a TACO nao alcanca.
 *
 * Dados sob Open Database License (ODbL) — uso livre mediante atribuicao. A
 * atribuicao viaja com o alimento em FonteDeDados.OPEN_FOOD_FACTS e aparece
 * nas prescricoes e relatorios gerados a partir dele.
 *
 * Roda por ultimo (@Order 3) para nao atrasar o boot com o arquivo maior, e e
 * idempotente: havendo alimento desta fonte, nao faz nada.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class ImportadorOpenFoodFacts implements ApplicationRunner {

    private static final String ARQUIVO = "dados/openfoodfacts-br.csv";
    private static final int TAMANHO_DO_LOTE = 500;

    private final AlimentoRepository alimentoRepository;
    private final LeitorDeTabelaDeAlimentos leitor;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (alimentoRepository.existsByFonte(FonteDeDados.OPEN_FOOD_FACTS)) {
            log.debug("Produtos do Open Food Facts já importados; nada a fazer.");
            return;
        }
        var recurso = new ClassPathResource(ARQUIVO);
        if (!recurso.exists()) {
            log.info("Arquivo {} ausente; a base seguira sem produtos industrializados.", ARQUIVO);
            return;
        }

        try (var entrada = new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8)) {
            var resultado = leitor.ler(entrada, FonteDeDados.OPEN_FOOD_FACTS, ',');

            resultado.avisos().forEach(aviso -> log.warn("Open Food Facts: {}", aviso));
            if (resultado.vazio()) {
                log.warn("Nenhum produto do Open Food Facts pode ser importado.");
                return;
            }

            List<Alimento> semDuplicatas = removerDuplicatas(resultado.alimentos());
            gravarEmLotes(semDuplicatas);

            log.info("Open Food Facts importado: {} produtos brasileiros ({} linhas ignoradas).",
                    semDuplicatas.size(), resultado.linhasIgnoradas());
        } catch (IOException e) {
            log.error("Falha ao importar {}", ARQUIVO, e);
        }
    }

    /**
     * O mesmo codigo de barras aparece mais de uma vez no dump (registros
     * duplicados da base colaborativa). Sem podar aqui, a unique constraint de
     * (fonte, codigo_barras) abortaria a importacao inteira.
     */
    private List<Alimento> removerDuplicatas(List<Alimento> alimentos) {
        Set<String> vistos = new HashSet<>();
        List<Alimento> unicos = new ArrayList<>(alimentos.size());
        int repetidos = 0;

        for (Alimento alimento : alimentos) {
            String chave = alimento.getCodigoBarras() != null
                    ? alimento.getCodigoBarras()
                    : alimento.getCodigoFonte();
            if (chave == null || vistos.add(chave)) {
                unicos.add(alimento);
            } else {
                repetidos++;
            }
        }
        if (repetidos > 0) {
            log.info("{} produtos repetidos descartados na importação.", repetidos);
        }
        return unicos;
    }

    /** Grava em lotes para nao segurar dezenas de milhares de entidades no contexto. */
    private void gravarEmLotes(List<Alimento> alimentos) {
        for (int inicio = 0; inicio < alimentos.size(); inicio += TAMANHO_DO_LOTE) {
            int fim = Math.min(inicio + TAMANHO_DO_LOTE, alimentos.size());
            alimentoRepository.saveAll(alimentos.subList(inicio, fim));
            alimentoRepository.flush();
        }
    }
}
