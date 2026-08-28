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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carrega o acervo base de medidas caseiras.
 *
 * Roda depois do ImportadorTaco (@Order) porque precisa dos alimentos ja
 * gravados para casar o codigo da tabela com o id gerado.
 *
 * A TACO publica composicao por 100 g, mas nao publica porcoes usuais. Os pesos
 * deste acervo sao estimativas de porcoes brasileiras correntes e existem para
 * que o plano alimentar saia legivel para o paciente — ninguem serve 5 g de sal,
 * serve uma pitada. Cada consultorio pode cadastrar a propria versao de qualquer
 * medida, e a versao propria tem precedencia sobre a do acervo.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ImportadorMedidas implements ApplicationRunner {

    private static final String ARQUIVO = "dados/medidas.csv";

    private final AlimentoRepository alimentoRepository;
    private final MedidaCaseiraRepository medidaCaseiraRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (medidaCaseiraRepository.countByContaIdIsNull() > 0) {
            log.debug("Acervo de medidas caseiras já carregado; nada a fazer.");
            return;
        }
        var recurso = new ClassPathResource(ARQUIVO);
        if (!recurso.exists()) {
            log.warn("Arquivo {} não encontrado. Os alimentos ficarão sem porções usuais.", ARQUIVO);
            return;
        }

        Map<String, Alimento> porCodigo = alimentoRepository.findByFonte(FonteDeDados.TACO).stream()
                .filter(a -> a.getCodigoFonte() != null)
                .collect(Collectors.toMap(Alimento::getCodigoFonte, Function.identity(), (a, b) -> a));

        if (porCodigo.isEmpty()) {
            log.warn("Nenhum alimento TACO encontrado; medidas caseiras não importadas.");
            return;
        }

        try {
            List<MedidaCaseira> medidas = ler(recurso, porCodigo);
            medidaCaseiraRepository.saveAll(medidas);
            log.info("Acervo de medidas caseiras carregado: {} porções para {} alimentos.",
                    medidas.size(), porCodigo.size());
        } catch (IOException e) {
            log.error("Falha ao carregar medidas caseiras de {}", ARQUIVO, e);
        }
    }

    private List<MedidaCaseira> ler(ClassPathResource recurso, Map<String, Alimento> porCodigo)
            throws IOException {

        List<MedidaCaseira> medidas = new ArrayList<>();
        Map<String, Integer> padroesPorCodigo = new HashMap<>();
        int semCorrespondencia = 0;

        try (var reader = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // cabecalho
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                String[] campos = LeitorCsv.dividir(linha);
                if (campos.length < 4) {
                    continue;
                }
                String codigo = campos[0].trim();
                Alimento alimento = porCodigo.get(codigo);
                if (alimento == null) {
                    semCorrespondencia++;
                    continue;
                }

                var medida = new MedidaCaseira(campos[1].trim(), new BigDecimal(campos[2].trim()));
                boolean padrao = Boolean.parseBoolean(campos[3].trim());
                if (padrao) {
                    padroesPorCodigo.merge(codigo, 1, Integer::sum);
                }
                medida.setPadrao(padrao);
                medida.setAlimento(alimento);
                // contaId nulo: porcao do acervo base, visivel para todos.
                medidas.add(medida);
            }
        }

        if (semCorrespondencia > 0) {
            log.warn("{} linhas de medidas ignoradas por não casarem com nenhum alimento.",
                    semCorrespondencia);
        }
        padroesPorCodigo.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> log.warn("Alimento {} tem {} medidas marcadas como padrão.",
                        e.getKey(), e.getValue()));

        return medidas;
    }
}
