package br.com.nutriplan.exame.service;

import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.exame.domain.ClassificacaoDoExame;
import br.com.nutriplan.exame.domain.Exame;
import br.com.nutriplan.exame.domain.FaixaDeReferencia;
import br.com.nutriplan.exame.domain.LaudoDoExame;
import br.com.nutriplan.exame.domain.ParametroExame;
import br.com.nutriplan.exame.domain.ParametroSolicitado;
import br.com.nutriplan.exame.domain.SolicitacaoDeExame;
import br.com.nutriplan.exame.dto.ExameDtos;
import br.com.nutriplan.exame.repository.ExameRepository;
import br.com.nutriplan.exame.repository.LaudoDoExameRepository;
import br.com.nutriplan.exame.repository.ParametroExameRepository;
import br.com.nutriplan.exame.repository.SolicitacaoDeExameRepository;
import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.service.PacienteService;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exames laboratoriais do paciente.
 *
 * A regra que estrutura este servico e a de sempre neste sistema: o registro
 * guarda o que se sabia no momento em que foi feito. Aqui isso significa gravar
 * a faixa de referencia junto do valor.
 *
 * Faixa de referencia nao e constante universal — depende do metodo do
 * laboratorio e muda com o tempo. Se a classificacao fosse calculada na
 * leitura, corrigir uma faixa no cadastro reclassificaria exames antigos, e um
 * resultado que era normal apareceria alterado sem que nada tivesse acontecido
 * com o paciente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExameService {

    private final ExameRepository exameRepository;
    private final LaudoDoExameRepository laudoRepository;
    private final ParametroExameRepository parametroRepository;
    private final SolicitacaoDeExameRepository solicitacaoRepository;
    private final PacienteService pacienteService;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------- parametros

    @Transactional(readOnly = true)
    public List<ExameDtos.ParametroResponse> parametros() {
        return parametroRepository.visiveisPara(contextoAtual.contaId()).stream()
                .map(ExameDtos.ParametroResponse::de)
                .toList();
    }

    /**
     * Cadastra um parametro proprio do consultorio.
     *
     * O catalogo do sistema cobre o exame comum; o consultorio precisa poder
     * acrescentar o que a sua pratica pede sem esperar por atualizacao.
     */
    @Transactional
    public ExameDtos.ParametroResponse criarParametro(ExameDtos.ParametroRequest req) {
        var parametro = new ParametroExame(contextoAtual.contaId(), req.nome(),
                req.unidadePadrao(), req.grupo());

        if (req.minimo() != null || req.maximo() != null) {
            var faixa = new FaixaDeReferencia();
            faixa.setParametro(parametro);
            faixa.setMinimo(req.minimo());
            faixa.setMaximo(req.maximo());
            parametro.getFaixas().add(faixa);
        }
        parametroRepository.save(parametro);
        return ExameDtos.ParametroResponse.de(parametro);
    }

    // ----------------------------------------------------------------- exames

    @Transactional(readOnly = true)
    public List<ExameDtos.ExameResponse> doPaciente(Long pacienteId) {
        pacienteService.exigirDaConta(pacienteId);
        return exameRepository.doPaciente(pacienteId, contextoAtual.contaId()).stream()
                .map(ExameDtos.ExameResponse::de)
                .toList();
    }

    @Transactional
    public ExameDtos.ExameResponse registrar(Long pacienteId, ExameDtos.ExameRequest req) {
        Paciente paciente = pacienteService.exigirDaConta(pacienteId);
        ParametroExame parametro = exigirParametro(req.parametroId());

        var exame = new Exame(contextoAtual.contaId(), pacienteId, parametro, req.dataColeta());
        aplicar(exame, paciente, parametro, req);
        exameRepository.save(exame);

        log.info("Exame registrado: paciente={} parâmetro={} classificação={}",
                pacienteId, parametro.getNome(), exame.getClassificacao());
        return ExameDtos.ExameResponse.de(exame);
    }

    @Transactional
    public ExameDtos.ExameResponse atualizar(Long id, ExameDtos.ExameRequest req) {
        Exame exame = exigirExame(id);
        Paciente paciente = pacienteService.exigirDaConta(exame.getPacienteId());
        ParametroExame parametro = exigirParametro(req.parametroId());

        exame.setParametro(parametro);
        exame.setDataColeta(req.dataColeta());
        aplicar(exame, paciente, parametro, req);
        return ExameDtos.ExameResponse.de(exame);
    }

    @Transactional
    public void remover(Long id) {
        exameRepository.delete(exigirExame(id));
    }

    /**
     * Preenche valor, unidade e a classificacao contra a faixa vigente agora.
     *
     * A faixa e resolvida aqui, uma vez, e gravada. Toda leitura posterior usa
     * o que ficou gravado.
     */
    private void aplicar(Exame exame, Paciente paciente, ParametroExame parametro,
                         ExameDtos.ExameRequest req) {
        exame.setValor(req.valor());
        exame.setUnidade(StringUtils.hasText(req.unidade())
                ? req.unidade() : parametro.getUnidadePadrao());
        exame.setObservacao(req.observacao());

        FaixaDeReferencia faixa = faixaPara(parametro, paciente);
        if (faixa != null) {
            exame.setReferenciaMin(faixa.getMinimo());
            exame.setReferenciaMax(faixa.getMaximo());
        } else {
            exame.setReferenciaMin(null);
            exame.setReferenciaMax(null);
        }

        // Valor em unidade diferente da do parametro nao e classificado: os
        // limites da faixa estao na unidade padrao, e comparar numeros de
        // escalas diferentes produziria classificacao errada com aparencia de
        // certa.
        boolean unidadeCompativel = exame.getUnidade().equalsIgnoreCase(parametro.getUnidadePadrao());
        exame.setClassificacao(unidadeCompativel
                ? ClassificacaoDoExame.de(req.valor(), exame.getReferenciaMin(), exame.getReferenciaMax())
                : null);
    }

    /** A faixa mais especifica que serve ao paciente. */
    private FaixaDeReferencia faixaPara(ParametroExame parametro, Paciente paciente) {
        Integer idade = paciente.getIdade();
        return parametro.getFaixas().stream()
                .filter(f -> f.serve(paciente.getSexo(), idade))
                .max(Comparator.comparingInt(FaixaDeReferencia::especificidade))
                .orElse(null);
    }

    // ------------------------------------------------------------------ serie

    @Transactional(readOnly = true)
    public ExameDtos.SerieResponse serie(Long pacienteId, Long parametroId) {
        pacienteService.exigirDaConta(pacienteId);
        ParametroExame parametro = exigirParametro(parametroId);

        List<Exame> exames = exameRepository.serie(pacienteId, parametroId, contextoAtual.contaId());
        List<ExameDtos.PontoDaSerie> pontos = new ArrayList<>();
        BigDecimal anterior = null;
        String primeiraUnidade = null;
        boolean misturadas = false;

        for (Exame e : exames) {
            if (primeiraUnidade == null) {
                primeiraUnidade = e.getUnidade();
            } else if (!primeiraUnidade.equalsIgnoreCase(e.getUnidade())) {
                misturadas = true;
            }
            // Variacao so entre pontos na mesma unidade: subtrair mg/dL de
            // mmol/L daria um numero sem significado nenhum.
            BigDecimal variacao = (anterior != null && e.getValor() != null
                    && e.getUnidade().equalsIgnoreCase(primeiraUnidade))
                    ? e.getValor().subtract(anterior) : null;
            pontos.add(new ExameDtos.PontoDaSerie(e.getDataColeta(), e.getValor(),
                    e.getUnidade(), e.getClassificacao(), variacao));
            if (e.getValor() != null) {
                anterior = e.getValor();
            }
        }

        return new ExameDtos.SerieResponse(parametro.getId(), parametro.getNome(),
                primeiraUnidade == null ? parametro.getUnidadePadrao() : primeiraUnidade,
                pontos, misturadas);
    }

    // ------------------------------------------------------------- solicitacao

    @Transactional(readOnly = true)
    public List<ExameDtos.SolicitacaoResponse> solicitacoes(Long pacienteId) {
        pacienteService.exigirDaConta(pacienteId);
        return solicitacaoRepository.doPaciente(pacienteId, contextoAtual.contaId()).stream()
                .map(ExameDtos.SolicitacaoResponse::de)
                .toList();
    }

    @Transactional
    public ExameDtos.SolicitacaoResponse solicitar(Long pacienteId,
                                                   ExameDtos.SolicitacaoRequest req) {
        pacienteService.exigirDaConta(pacienteId);
        var solicitacao = new SolicitacaoDeExame(contextoAtual.contaId(), pacienteId, req.data());
        solicitacao.setObservacao(req.observacao());

        for (Long parametroId : req.parametroIds().stream().distinct().toList()) {
            solicitacao.getParametros().add(
                    new ParametroSolicitado(solicitacao, exigirParametro(parametroId)));
        }
        solicitacaoRepository.save(solicitacao);
        return ExameDtos.SolicitacaoResponse.de(solicitacao);
    }

    // ------------------------------------------------------------------ laudo

    @Transactional
    public void anexarLaudo(Long id, String nome, String tipo, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException("Envie um arquivo não vazio");
        }
        if (conteudo.length > LIMITE_DO_LAUDO) {
            throw new RegraDeNegocioException(
                    "O laudo pode ter no máximo %d MB".formatted(LIMITE_DO_LAUDO / (1024 * 1024)));
        }
        Exame exame = exigirExame(id);
        exame.setLaudoNome(nome);
        exame.setLaudoTipo(tipo);
        // Substitui o anterior, se houver: a chave e o proprio id do exame.
        laudoRepository.save(new LaudoDoExame(exame.getId(), conteudo));
    }

    /** Nome, tipo e conteudo do laudo, prontos para a resposta HTTP. */
    public record Laudo(String nome, String tipo, byte[] conteudo) {}

    @Transactional(readOnly = true)
    public Laudo laudo(Long id) {
        Exame exame = exigirExame(id);
        LaudoDoExame arquivo = laudoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Laudo do exame", id));
        return new Laudo(exame.getLaudoNome(), exame.getLaudoTipo(), arquivo.getConteudo());
    }

    /** Laudo de consultorio e um PDF de poucas centenas de KB; 5 MB e folga. */
    private static final int LIMITE_DO_LAUDO = 5 * 1024 * 1024;

    // ------------------------------------------------------------------ apoio

    private Exame exigirExame(Long id) {
        return exameRepository.buscarDaConta(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Exame", id));
    }

    private ParametroExame exigirParametro(Long id) {
        return parametroRepository.buscarVisivel(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Parâmetro de exame", id));
    }

    /** Usado pela agenda de hoje e pelo prontuario: o que mudou desde a ultima. */
    @Transactional(readOnly = true)
    public LocalDate ultimaColeta(Long pacienteId) {
        return exameRepository.doPaciente(pacienteId, contextoAtual.contaId()).stream()
                .map(Exame::getDataColeta)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
