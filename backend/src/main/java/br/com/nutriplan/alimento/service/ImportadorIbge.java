package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.domain.MedidaCaseira;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
import br.com.nutriplan.alimento.repository.MedidaCaseiraRepository;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carrega a tabela do IBGE (POF 2008-2009) e as medidas caseiras que a
 * acompanham.
 *
 * A tabela preenche a lacuna entre a TACO e os industrializados: são alimentos
 * **como consumidos** no Brasil, com a preparação como dimensão própria — o
 * mesmo alimento aparece cru, cozido, frito e empanado, cada um com composição
 * distinta, porque fritar muda o alimento.
 *
 * Traz ainda cinco nutrientes que a TACO não determina: selênio, cobalamina,
 * folato, vitamina D e vitamina E.
 *
 * As medidas que vêm com ela têm procedência melhor que qualquer estimativa:
 * foram registradas em campo pelo entrevistador da pesquisa, com o utensílio
 * que a família de fato usou.
 *
 * Fonte: IBGE, Pesquisa de Orçamentos Familiares 2008-2009.
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class ImportadorIbge implements ApplicationRunner {

    private static final String ARQUIVO_COMPOSICAO = "dados/ibge.csv";
    private static final String ARQUIVO_MEDIDAS = "dados/ibge-medidas.csv";

    private final AlimentoRepository alimentoRepository;
    private final MedidaCaseiraRepository medidaCaseiraRepository;
    private final LeitorDeTabelaDeAlimentos leitor;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (alimentoRepository.existsByFonte(FonteDeDados.IBGE)) {
            log.debug("Tabela do IBGE já importada; nada a fazer.");
            return;
        }
        var recurso = new ClassPathResource(ARQUIVO_COMPOSICAO);
        if (!recurso.exists()) {
            log.info("Arquivo {} ausente; a base seguirá sem a tabela do IBGE.", ARQUIVO_COMPOSICAO);
            return;
        }

        try (var entrada = new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8)) {
            var resultado = leitor.ler(entrada, FonteDeDados.IBGE, ',');
            resultado.avisos().forEach(aviso -> log.warn("IBGE: {}", aviso));

            if (resultado.vazio()) {
                log.warn("Nenhum alimento do IBGE pôde ser importado.");
                return;
            }
            alimentoRepository.saveAll(resultado.alimentos());
            alimentoRepository.flush();
            log.info("Tabela do IBGE importada: {} alimentos.", resultado.alimentos().size());

            importarMedidas();
        } catch (IOException e) {
            log.error("Falha ao importar {}", ARQUIVO_COMPOSICAO, e);
        }
    }

    /**
     * Liga as medidas aos alimentos recém-gravados pelo código da POF.
     *
     * As porções entram com contaId nulo — pertencem ao acervo comum, como as
     * demais que acompanham o sistema.
     */
    private void importarMedidas() throws IOException {
        var recurso = new ClassPathResource(ARQUIVO_MEDIDAS);
        if (!recurso.exists()) {
            log.warn("Arquivo {} ausente; os alimentos do IBGE ficarão sem porções.", ARQUIVO_MEDIDAS);
            return;
        }

        Map<String, Alimento> porCodigo = alimentoRepository.findByFonte(FonteDeDados.IBGE).stream()
                .filter(a -> a.getCodigoFonte() != null)
                .collect(Collectors.toMap(Alimento::getCodigoFonte, Function.identity(), (a, b) -> a));

        List<MedidaCaseira> medidas = new ArrayList<>();
        int semAlimento = 0;

        try (var reader = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // cabeçalho
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                // Divisor que respeita aspas: 11 utensilios trazem virgula
                // decimal no nome, como "garrafa (1,5 l)".
                String[] campos = LeitorCsv.dividir(linha);
                if (campos.length < 4) {
                    continue;
                }
                Alimento alimento = porCodigo.get(campos[0].trim());
                if (alimento == null) {
                    semAlimento++;
                    continue;
                }
                try {
                    var medida = new MedidaCaseira(campos[1].trim(), new BigDecimal(campos[2].trim()));
                    medida.setPadrao(Boolean.parseBoolean(campos[3].trim()));
                    medida.setAlimento(alimento);
                    medidas.add(medida);
                } catch (NumberFormatException e) {
                    log.debug("Peso inválido na medida do IBGE: {}", campos[2]);
                }
            }
        }

        if (semAlimento > 0) {
            log.warn("{} medidas do IBGE sem alimento correspondente.", semAlimento);
        }
        medidaCaseiraRepository.saveAll(medidas);
        log.info("Medidas caseiras do IBGE carregadas: {} porções.", medidas.size());
    }
}
