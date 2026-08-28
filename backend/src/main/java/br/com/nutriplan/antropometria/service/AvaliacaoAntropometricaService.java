package br.com.nutriplan.antropometria.service;

import br.com.nutriplan.antropometria.domain.AvaliacaoAntropometrica;
import br.com.nutriplan.antropometria.domain.ClassificacaoImc;
import br.com.nutriplan.antropometria.domain.Dobra;
import br.com.nutriplan.antropometria.domain.EquacaoGastoEnergetico;
import br.com.nutriplan.antropometria.domain.ProtocoloComposicao;
import br.com.nutriplan.antropometria.domain.RiscoCardiometabolico;
import br.com.nutriplan.antropometria.domain.ClassificacaoInfantil;
import br.com.nutriplan.antropometria.domain.GanhoGestacional;
import br.com.nutriplan.antropometria.domain.IndicadorDeCrescimento;
import br.com.nutriplan.antropometria.domain.SituacaoDoGanho;
import br.com.nutriplan.antropometria.repository.CurvaDeCrescimentoRepository;
import br.com.nutriplan.paciente.domain.Sexo;
import br.com.nutriplan.antropometria.dto.AntropometriaDtos;
import br.com.nutriplan.antropometria.repository.AvaliacaoAntropometricaRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.repository.PacienteRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvaliacaoAntropometricaService {

    private static final int ESCALA = 2;

    /** Circunferências aceitas, mapeando a chave da API ao campo da entidade. */
    private static final List<String> CIRCUNFERENCIAS = List.of(
            "cintura", "quadril", "abdomen", "braco", "antebraco", "coxa", "panturrilha", "torax");

    private final AvaliacaoAntropometricaRepository avaliacaoRepository;
    private final PacienteRepository pacienteRepository;
    private final CurvaDeCrescimentoRepository curvaRepository;
    private final CalculadoraDeEscoreZ escoreZ;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<AntropometriaDtos.AvaliacaoResponse> listarDoPaciente(Long pacienteId) {
        Long contaId = contextoAtual.contaId();
        Paciente paciente = exigirPaciente(pacienteId, contaId);

        return avaliacaoRepository
                .findByContaIdAndPacienteIdOrderByDataAscIdAsc(contaId, pacienteId)
                .stream()
                .map(a -> montarResposta(a, paciente))
                .toList();
    }

    @Transactional(readOnly = true)
    public AntropometriaDtos.AvaliacaoResponse detalhar(Long id) {
        AvaliacaoAntropometrica avaliacao = exigirDaConta(id);
        return montarResposta(avaliacao, exigirPaciente(avaliacao.getPacienteId(), avaliacao.getContaId()));
    }

    @Transactional(readOnly = true)
    public AvaliacaoAntropometrica exigirDaConta(Long id) {
        return avaliacaoRepository.findByIdAndContaId(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação antropométrica", id));
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public AntropometriaDtos.AvaliacaoResponse criar(Long pacienteId,
                                                     AntropometriaDtos.AvaliacaoRequest req) {
        Long contaId = contextoAtual.contaId();
        Paciente paciente = exigirPaciente(pacienteId, contaId);

        var avaliacao = new AvaliacaoAntropometrica(contaId, pacienteId, req.data());
        aplicar(req, avaliacao, paciente);
        avaliacaoRepository.save(avaliacao);

        log.info("Avaliação registrada: id={} paciente={} conta={}",
                avaliacao.getId(), pacienteId, contaId);
        return montarResposta(avaliacao, paciente);
    }

    @Transactional
    public AntropometriaDtos.AvaliacaoResponse atualizar(Long id,
                                                         AntropometriaDtos.AvaliacaoRequest req) {
        AvaliacaoAntropometrica avaliacao = exigirDaConta(id);
        Paciente paciente = exigirPaciente(avaliacao.getPacienteId(), avaliacao.getContaId());

        avaliacao.setData(req.data());
        aplicar(req, avaliacao, paciente);
        return montarResposta(avaliacao, paciente);
    }

    @Transactional
    public void remover(Long id) {
        avaliacaoRepository.delete(exigirDaConta(id));
        log.info("Avaliação removida: id={}", id);
    }

    // ------------------------------------------------------------------ cálculo

    private void aplicar(AntropometriaDtos.AvaliacaoRequest req,
                         AvaliacaoAntropometrica avaliacao,
                         Paciente paciente) {

        avaliacao.setPesoKg(req.pesoKg());
        avaliacao.setAlturaCm(req.alturaCm());
        avaliacao.setObservacoes(req.observacoes());
        avaliacao.setSemanaGestacional(req.semanaGestacional());
        avaliacao.setPesoPreGestacionalKg(req.pesoPreGestacionalKg());

        aplicarDobras(req.dobras(), avaliacao);
        aplicarCircunferencias(req.circunferencias(), avaliacao);

        // Recalcula do zero: editar uma dobra sem recalcular deixaria o
        // percentual gravado incoerente com as medidas ao lado dele.
        avaliacao.limparComposicao();
        avaliacao.limparGastoEnergetico();

        if (req.protocoloComposicao() != null) {
            estimarComposicao(req.protocoloComposicao(), avaliacao, paciente);
        }
        if (req.equacaoGasto() != null) {
            estimarGastoEnergetico(req.equacaoGasto(), req.fatorAtividade(), avaliacao, paciente);
        }
    }

    /**
     * Estima a composição corporal pelo protocolo escolhido.
     *
     * Recusa quando falta qualquer dobra exigida, nomeando quais — completar a
     * conta com dobra ausente inventaria composição corporal.
     */
    private void estimarComposicao(ProtocoloComposicao protocolo,
                                   AvaliacaoAntropometrica avaliacao,
                                   Paciente paciente) {

        if (protocolo.exigeSexo() && paciente.getSexo() == null) {
            throw new RegraDeNegocioException(
                    "O protocolo %s depende do sexo do paciente, que não está informado no cadastro."
                            .formatted(protocolo.getDescricao()));
        }
        Integer idade = paciente.idadeEm(avaliacao.getData());
        if (protocolo.exigeIdade() && idade == null) {
            throw new RegraDeNegocioException(
                    "O protocolo %s depende da idade, e o paciente não tem data de nascimento cadastrada."
                            .formatted(protocolo.getDescricao()));
        }

        Map<Dobra, Double> dobras = avaliacao.dobrasMedidas();
        List<Dobra> faltantes = protocolo.dobrasFaltantes(dobras, paciente.getSexo());
        if (!faltantes.isEmpty()) {
            throw new RegraDeNegocioException(
                    "O protocolo %s exige as dobras que faltam: %s.".formatted(
                            protocolo.getDescricao(),
                            faltantes.stream().map(Dobra::getDescricao).collect(
                                    java.util.stream.Collectors.joining(", "))));
        }

        double percentual = protocolo.percentualDeGordura(dobras, paciente.getSexo(), idade);
        if (percentual <= 0 || percentual >= 100) {
            throw new RegraDeNegocioException(
                    "As dobras informadas produzem um percentual de gordura fora da faixa possível. "
                    + "Confira as medidas.");
        }

        BigDecimal percentualArredondado = arredondar(percentual);
        avaliacao.setProtocoloComposicao(protocolo);
        avaliacao.setPercentualGordura(percentualArredondado);

        // Massa gorda e magra dependem do peso; sem ele fica só o percentual.
        if (avaliacao.getPesoKg() != null) {
            BigDecimal massaGorda = avaliacao.getPesoKg()
                    .multiply(percentualArredondado)
                    .divide(BigDecimal.valueOf(100), ESCALA, RoundingMode.HALF_UP);
            avaliacao.setMassaGordaKg(massaGorda);
            avaliacao.setMassaMagraKg(
                    avaliacao.getPesoKg().subtract(massaGorda).setScale(ESCALA, RoundingMode.HALF_UP));
        }
    }

    private void estimarGastoEnergetico(EquacaoGastoEnergetico equacao,
                                        BigDecimal fatorAtividade,
                                        AvaliacaoAntropometrica avaliacao,
                                        Paciente paciente) {

        if (avaliacao.getPesoKg() == null || avaliacao.getAlturaCm() == null) {
            throw new RegraDeNegocioException(
                    "A estimativa de gasto energético depende de peso e altura.");
        }
        if (paciente.getSexo() == null) {
            throw new RegraDeNegocioException(
                    "A estimativa de gasto energético depende do sexo do paciente.");
        }
        Integer idade = paciente.idadeEm(avaliacao.getData());
        if (idade == null) {
            throw new RegraDeNegocioException(
                    "A equação de gasto energético depende da idade, e o paciente não tem "
                    + "data de nascimento cadastrada.");
        }

        double basal = equacao.basal(
                avaliacao.getPesoKg().doubleValue(),
                avaliacao.getAlturaCm().doubleValue(),
                paciente.getSexo(),
                idade);

        avaliacao.setEquacaoGasto(equacao);
        avaliacao.setGastoBasalKcal(arredondar(basal));

        if (fatorAtividade != null) {
            avaliacao.setFatorAtividade(fatorAtividade);
            avaliacao.setGastoTotalKcal(
                    arredondar(basal * fatorAtividade.doubleValue()));
        }
    }

    // ---------------------------------------------------------------- evolução

    /**
     * Série do paciente com as variações entre avaliações.
     *
     * Duas regras de honestidade governam a comparação:
     * medida ausente numa das pontas não gera variação — ausência não é
     * redução; e percentual de gordura estimado por protocolos diferentes não é
     * comparado, porque cada protocolo tem erro-padrão próprio e a diferença
     * entre eles seria lida como mudança do paciente.
     */
    @Transactional(readOnly = true)
    public AntropometriaDtos.EvolucaoResponse evolucao(Long pacienteId) {
        Long contaId = contextoAtual.contaId();
        Paciente paciente = exigirPaciente(pacienteId, contaId);

        List<AvaliacaoAntropometrica> serie =
                avaliacaoRepository.findByContaIdAndPacienteIdOrderByDataAscIdAsc(contaId, pacienteId);

        List<AntropometriaDtos.PontoDaEvolucao> pontos = new ArrayList<>();
        for (int i = 0; i < serie.size(); i++) {
            AvaliacaoAntropometrica atual = serie.get(i);
            AvaliacaoAntropometrica anterior = i > 0 ? serie.get(i - 1) : null;
            AvaliacaoAntropometrica primeira = serie.get(0);

            pontos.add(new AntropometriaDtos.PontoDaEvolucao(
                    atual.getId(),
                    atual.getData(),
                    atual.getPesoKg(),
                    atual.getImc(),
                    atual.getPercentualGordura(),
                    atual.getProtocoloComposicao(),
                    anterior == null ? List.of() : compararCom(atual, anterior),
                    i == 0 ? List.of() : compararCom(atual, primeira)));
        }

        return new AntropometriaDtos.EvolucaoResponse(
                pacienteId, paciente.getNome(), serie.size(), pontos);
    }

    private List<AntropometriaDtos.Variacao> compararCom(AvaliacaoAntropometrica atual,
                                                          AvaliacaoAntropometrica referencia) {
        List<AntropometriaDtos.Variacao> variacoes = new ArrayList<>();

        variacoes.add(variacaoSimples("pesoKg", "Peso (kg)",
                atual.getPesoKg(), referencia.getPesoKg()));
        variacoes.add(variacaoSimples("imc", "IMC",
                atual.getImc(), referencia.getImc()));
        variacoes.add(variacaoSimples("circCintura", "Cintura (cm)",
                atual.getCircCintura(), referencia.getCircCintura()));
        variacoes.add(variacaoSimples("circQuadril", "Quadril (cm)",
                atual.getCircQuadril(), referencia.getCircQuadril()));
        variacoes.add(variacaoSimples("massaMagraKg", "Massa magra (kg)",
                atual.getMassaMagraKg(), referencia.getMassaMagraKg()));

        variacoes.add(variacaoDeComposicao(atual, referencia));

        return variacoes;
    }

    private AntropometriaDtos.Variacao variacaoSimples(String medida, String rotulo,
                                                        BigDecimal atual, BigDecimal referencia) {
        if (atual == null || referencia == null) {
            return new AntropometriaDtos.Variacao(medida, rotulo, atual, referencia, null, false,
                    "Medida ausente em uma das avaliações.");
        }
        return new AntropometriaDtos.Variacao(medida, rotulo, atual, referencia,
                atual.subtract(referencia).setScale(ESCALA, RoundingMode.HALF_UP), true, null);
    }

    private AntropometriaDtos.Variacao variacaoDeComposicao(AvaliacaoAntropometrica atual,
                                                             AvaliacaoAntropometrica referencia) {
        final String medida = "percentualGordura";
        final String rotulo = "Gordura corporal (%)";

        if (!atual.temComposicaoEstimada() || !referencia.temComposicaoEstimada()) {
            return new AntropometriaDtos.Variacao(medida, rotulo,
                    atual.getPercentualGordura(), referencia.getPercentualGordura(), null, false,
                    "Composição não estimada em uma das avaliações.");
        }
        if (atual.getProtocoloComposicao() != referencia.getProtocoloComposicao()) {
            return new AntropometriaDtos.Variacao(medida, rotulo,
                    atual.getPercentualGordura(), referencia.getPercentualGordura(), null, false,
                    "Protocolos diferentes (%s e %s). Cada protocolo tem erro-padrão próprio, "
                            .formatted(referencia.getProtocoloComposicao().getDescricao(),
                                    atual.getProtocoloComposicao().getDescricao())
                            + "e a diferença entre eles não representa mudança do paciente.");
        }
        return new AntropometriaDtos.Variacao(medida, rotulo,
                atual.getPercentualGordura(), referencia.getPercentualGordura(),
                atual.getPercentualGordura().subtract(referencia.getPercentualGordura())
                        .setScale(ESCALA, RoundingMode.HALF_UP),
                true, null);
    }

    // ------------------------------------------------------------------ apoio

    private AntropometriaDtos.AvaliacaoResponse montarResposta(AvaliacaoAntropometrica a,
                                                                Paciente paciente) {
        Integer idade = paciente.idadeEm(a.getData());
        BigDecimal imc = a.getImc();
        BigDecimal rcq = a.getRelacaoCinturaQuadril();

        return new AntropometriaDtos.AvaliacaoResponse(
                a.getId(), a.getPacienteId(), paciente.getNome(), a.getData(),
                a.getPesoKg(), a.getAlturaCm(),
                dobrasComoMapa(a), circunferenciasComoMapa(a),
                imc, classificar(imc, idade),
                rcq, classificarRisco(rcq, paciente),
                a.getProtocoloComposicao() == null ? null
                        : new AntropometriaDtos.ComposicaoCorporalResponse(
                                a.getProtocoloComposicao(),
                                a.getProtocoloComposicao().getDescricao(),
                                a.getPercentualGordura(), a.getMassaGordaKg(), a.getMassaMagraKg()),
                a.getEquacaoGasto() == null ? null
                        : new AntropometriaDtos.GastoEnergeticoResponse(
                                a.getEquacaoGasto(), a.getEquacaoGasto().getDescricao(),
                                a.getFatorAtividade(), a.getGastoBasalKcal(), a.getGastoTotalKcal()),
                crescimentoInfantil(a, paciente),
                gestacao(a),
                a.getObservacoes(), a.getCriadoEm());
    }

    /**
     * Leitura infantil pelas curvas da OMS.
     *
     * Ate 19 anos o IMC nao se le pela faixa adulta: a mesma medida significa
     * coisas diferentes conforme a idade, e a comparacao correta e contra a
     * distribuicao daquela idade. Acima disso a resposta vem vazia, com o
     * motivo — e a classificacao adulta que vale.
     */
    private AntropometriaDtos.Derivado<AntropometriaDtos.CrescimentoInfantilResponse>
            crescimentoInfantil(AvaliacaoAntropometrica a, Paciente paciente) {

        if (paciente.getDataNascimento() == null) {
            return AntropometriaDtos.Derivado.ausente(
                    "A curva depende da idade em meses. Cadastre a data de nascimento.");
        }
        if (paciente.getSexo() == null) {
            return AntropometriaDtos.Derivado.ausente(
                    "As curvas da OMS são especificas por sexo. Informe o sexo do paciente.");
        }

        int meses = (int) java.time.temporal.ChronoUnit.MONTHS.between(
                paciente.getDataNascimento(), a.getData());
        if (!CalculadoraDeEscoreZ.idadeTemCurva(meses)) {
            return AntropometriaDtos.Derivado.ausente(
                    "As curvas da OMS vao até 19 anos. Acima disso vale a classificação adulta.");
        }

        var indicadores = new java.util.ArrayList<AntropometriaDtos.IndicadorInfantilResponse>();
        adicionarIndicador(indicadores, IndicadorDeCrescimento.IMC_PARA_IDADE,
                paciente.getSexo(), meses, a.getImc());
        adicionarIndicador(indicadores, IndicadorDeCrescimento.ESTATURA_PARA_IDADE,
                paciente.getSexo(), meses, a.getAlturaCm());

        if (indicadores.isEmpty()) {
            return AntropometriaDtos.Derivado.ausente(
                    "As curvas de crescimento não estao carregadas nesta instalacao.");
        }
        return AntropometriaDtos.Derivado.de(
                new AntropometriaDtos.CrescimentoInfantilResponse(meses, indicadores));
    }

    private void adicionarIndicador(
            java.util.List<AntropometriaDtos.IndicadorInfantilResponse> destino,
            IndicadorDeCrescimento indicador, Sexo sexo, int meses, BigDecimal valor) {

        if (valor == null) {
            return;
        }
        var curva = curvaRepository.findByIndicadorAndSexoAndMes(indicador, sexo, meses)
                .orElse(null);
        if (curva == null) {
            return;
        }
        BigDecimal z = escoreZ.calcular(curva, valor);
        var classificacao = ClassificacaoInfantil.de(indicador, z, meses);
        destino.add(new AntropometriaDtos.IndicadorInfantilResponse(
                indicador, indicador.getDescricao(), z, classificacao,
                classificacao == null ? null : classificacao.getDescricao(),
                classificacao != null && classificacao.exigeAtencao(),
                curva.referencia()));
    }

    /**
     * Ganho de peso na gestacao, pelas faixas do IOM 2009.
     *
     * Sem o peso pre-gestacional o sistema nao classifica. Poderia estimar a
     * partir do IMC atual, mas o IMC atual ja inclui o ganho que se quer
     * avaliar — a conta se morderia.
     */
    private AntropometriaDtos.Derivado<AntropometriaDtos.GestacaoResponse> gestacao(
            AvaliacaoAntropometrica a) {

        if (!a.ehGestacional()) {
            return null;
        }
        if (a.getPesoPreGestacionalKg() == null) {
            return AntropometriaDtos.Derivado.ausente(
                    "A faixa de ganho depende do IMC anterior a gestação. "
                            + "Informe o peso pre-gestacional.");
        }
        if (a.getAlturaCm() == null || a.getAlturaCm().signum() <= 0) {
            return AntropometriaDtos.Derivado.ausente(
                    "O IMC pre-gestacional depende da altura.");
        }

        BigDecimal alturaM = a.getAlturaCm().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal imcPre = a.getPesoPreGestacionalKg()
                .divide(alturaM.multiply(alturaM), ESCALA, RoundingMode.HALF_UP);

        var faixa = GanhoGestacional.porImcPreGestacional(imcPre);
        if (faixa == null) {
            return AntropometriaDtos.Derivado.ausente(
                    "Não foi possível enquadrar o IMC pre-gestacional numa faixa.");
        }

        int semana = a.getSemanaGestacional();
        BigDecimal ganho = a.getPesoKg().subtract(a.getPesoPreGestacionalKg())
                .setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal min = faixa.esperadoMin(semana);
        BigDecimal max = faixa.esperadoMax(semana);
        var situacao = SituacaoDoGanho.de(ganho, min, max);

        return AntropometriaDtos.Derivado.de(new AntropometriaDtos.GestacaoResponse(
                semana, a.getPesoPreGestacionalKg(), imcPre,
                faixa, faixa.getDescricao(), ganho, min, max,
                situacao, situacao == null ? null : situacao.getDescricao(),
                faixa.getGanhoTotalMin(), faixa.getGanhoTotalMax()));
    }

    private AntropometriaDtos.Derivado<ClassificacaoImc> classificar(BigDecimal imc, Integer idade) {
        if (imc == null) {
            return AntropometriaDtos.Derivado.ausente("Informe peso e altura para calcular o IMC.");
        }
        if (idade != null && idade < ClassificacaoImc.IDADE_MINIMA_ADULTO) {
            return AntropometriaDtos.Derivado.ausente(
                    "As faixas da OMS valem para adultos. Abaixo de %d anos a leitura correta é "
                            .formatted(ClassificacaoImc.IDADE_MINIMA_ADULTO)
                            + "por percentil de idade e sexo.");
        }
        return AntropometriaDtos.Derivado.de(ClassificacaoImc.paraAdulto(imc, idade));
    }

    private AntropometriaDtos.Derivado<RiscoCardiometabolico> classificarRisco(BigDecimal rcq,
                                                                                Paciente paciente) {
        if (rcq == null) {
            return AntropometriaDtos.Derivado.ausente(
                    "Informe cintura e quadril para calcular a relação.");
        }
        if (paciente.getSexo() == null) {
            return AntropometriaDtos.Derivado.ausente(
                    "Os pontos de corte são específicos por sexo, que não está informado no cadastro.");
        }
        return AntropometriaDtos.Derivado.de(
                RiscoCardiometabolico.porRelacaoCinturaQuadril(rcq, paciente.getSexo()));
    }

    private void aplicarDobras(Map<String, BigDecimal> dobras, AvaliacaoAntropometrica avaliacao) {
        for (Dobra dobra : Dobra.values()) {
            avaliacao.definirDobra(dobra, null);
        }
        if (dobras == null) {
            return;
        }
        dobras.forEach((chave, valor) -> {
            Dobra dobra = dobraPorChave(chave);
            if (dobra != null && valor != null && valor.signum() > 0) {
                avaliacao.definirDobra(dobra, valor);
            }
        });
    }

    private Dobra dobraPorChave(String chave) {
        for (Dobra dobra : Dobra.values()) {
            if (dobra.name().equalsIgnoreCase(chave)) {
                return dobra;
            }
        }
        return null;
    }

    private void aplicarCircunferencias(Map<String, BigDecimal> medidas,
                                        AvaliacaoAntropometrica a) {
        a.setCircCintura(null);
        a.setCircQuadril(null);
        a.setCircAbdomen(null);
        a.setCircBraco(null);
        a.setCircAntebraco(null);
        a.setCircCoxa(null);
        a.setCircPanturrilha(null);
        a.setCircTorax(null);
        if (medidas == null) {
            return;
        }
        medidas.forEach((chave, valor) -> {
            if (valor == null || valor.signum() <= 0) {
                return;
            }
            switch (chave.toLowerCase()) {
                case "cintura" -> a.setCircCintura(valor);
                case "quadril" -> a.setCircQuadril(valor);
                case "abdomen" -> a.setCircAbdomen(valor);
                case "braco" -> a.setCircBraco(valor);
                case "antebraco" -> a.setCircAntebraco(valor);
                case "coxa" -> a.setCircCoxa(valor);
                case "panturrilha" -> a.setCircPanturrilha(valor);
                case "torax" -> a.setCircTorax(valor);
                default -> log.debug("Circunferência desconhecida ignorada: {}", chave);
            }
        });
    }

    private Map<String, BigDecimal> dobrasComoMapa(AvaliacaoAntropometrica a) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        a.dobrasMedidas().forEach((dobra, valor) ->
                mapa.put(dobra.name(), BigDecimal.valueOf(valor).setScale(ESCALA, RoundingMode.HALF_UP)));
        return mapa;
    }

    /** Só as circunferências efetivamente medidas entram na resposta. */
    private Map<String, BigDecimal> circunferenciasComoMapa(AvaliacaoAntropometrica a) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        BigDecimal[] valores = {
                a.getCircCintura(), a.getCircQuadril(), a.getCircAbdomen(), a.getCircBraco(),
                a.getCircAntebraco(), a.getCircCoxa(), a.getCircPanturrilha(), a.getCircTorax()
        };
        for (int i = 0; i < CIRCUNFERENCIAS.size(); i++) {
            if (valores[i] != null) {
                mapa.put(CIRCUNFERENCIAS.get(i), valores[i]);
            }
        }
        return mapa;
    }

    private Paciente exigirPaciente(Long pacienteId, Long contaId) {
        return pacienteRepository.findByIdAndContaId(pacienteId, contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
    }

    private BigDecimal arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /** Data máxima aceita para uma avaliação. Existe para o teste ser explícito. */
    public static LocalDate dataMaximaPermitida() {
        return LocalDate.now();
    }
}
