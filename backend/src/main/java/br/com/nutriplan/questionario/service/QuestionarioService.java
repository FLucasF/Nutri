package br.com.nutriplan.questionario.service;

import br.com.nutriplan.auth.repository.ContaRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.paciente.service.PacienteService;
import br.com.nutriplan.questionario.domain.FaixaDeCorte;
import br.com.nutriplan.questionario.domain.ItemDeResposta;
import br.com.nutriplan.questionario.domain.Opcao;
import br.com.nutriplan.questionario.domain.Pergunta;
import br.com.nutriplan.questionario.domain.Questionario;
import br.com.nutriplan.questionario.domain.RespostaDeQuestionario;
import br.com.nutriplan.questionario.domain.TipoDePergunta;
import br.com.nutriplan.questionario.dto.QuestionarioDtos;
import br.com.nutriplan.questionario.repository.QuestionarioRepository;
import br.com.nutriplan.questionario.repository.RespostaDeQuestionarioRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Questionários pré-consulta.
 *
 * O paciente responde por link, sem conta — o mesmo mecanismo pelo qual ele lê
 * o plano. É recurso de tempo do nutricionista, e não de engajamento do
 * paciente: chegar à consulta com a leitura feita é o que ele compra aqui.
 *
 * O serviço tem dois lados que quase não se tocam. O do consultório monta o
 * formulário e lê as respostas, sempre autenticado. O do paciente serve e
 * recebe o formulário sem credencial nenhuma, autorizado apenas pela posse do
 * identificador — e por isso responde por um contrato próprio, que não tem
 * campo para nome de paciente nem para identificador de conta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionarioService {

    private final QuestionarioRepository questionarioRepository;
    private final RespostaDeQuestionarioRepository respostaRepository;
    private final PacienteService pacienteService;
    private final ContaRepository contaRepository;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------- biblioteca

    @Transactional(readOnly = true)
    public List<QuestionarioDtos.QuestionarioResponse> listar() {
        return questionarioRepository.visiveisPara(contextoAtual.contaId()).stream()
                .map(QuestionarioDtos.QuestionarioResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionarioDtos.QuestionarioResponse detalhar(Long id) {
        return QuestionarioDtos.QuestionarioResponse.de(exigirVisivel(id));
    }

    @Transactional
    public QuestionarioDtos.QuestionarioResponse criar(QuestionarioDtos.QuestionarioRequest req) {
        var questionario = new Questionario(contextoAtual.contaId(), req.nome());
        aplicar(req, questionario);
        questionarioRepository.save(questionario);
        log.info("Questionário criado: id={} conta={}",
                questionario.getId(), questionario.getContaId());
        return QuestionarioDtos.QuestionarioResponse.de(questionario);
    }

    /** Copia um modelo do sistema para a biblioteca do consultório, já editável. */
    @Transactional
    public QuestionarioDtos.QuestionarioResponse duplicar(Long id) {
        Questionario origem = exigirVisivel(id);
        var copia = new Questionario(contextoAtual.contaId(),
                recortar(origem.getNome() + " (minha versão)", 150));
        copia.setDescricao(origem.getDescricao());
        copia.setInstrumento(origem.getInstrumento());
        copia.setVersao(origem.getVersao());
        copia.setPontuavel(origem.isPontuavel());
        copia.setFaixaDeCorte(origem.getFaixaDeCorte());

        int ordem = 1;
        for (Pergunta p : origem.getPerguntas()) {
            var nova = new Pergunta(copia, p.getEnunciado(), p.getTipo(), ordem++);
            nova.setObrigatoria(p.isObrigatoria());
            nova.setOpcoes(p.getOpcoes());
            nova.setAjuda(p.getAjuda());
            copia.getPerguntas().add(nova);
        }
        questionarioRepository.save(copia);
        return QuestionarioDtos.QuestionarioResponse.de(copia);
    }

    /**
     * Substitui o questionário e incrementa a versão do modelo.
     *
     * A versão é o que permite saber, depois, qual formulário o paciente viu —
     * as respostas já recebidas guardam o número que responderam.
     */
    @Transactional
    public QuestionarioDtos.QuestionarioResponse atualizar(
            Long id, QuestionarioDtos.QuestionarioRequest req) {

        Questionario questionario = exigirDaConta(id);
        questionario.setNome(req.nome());
        aplicar(req, questionario);
        questionario.setVersaoModelo(questionario.getVersaoModelo() + 1);
        return QuestionarioDtos.QuestionarioResponse.de(questionario);
    }

    @Transactional
    public void remover(Long id) {
        Questionario questionario = exigirDaConta(id);
        // Inativa: respostas já recebidas apontam para ele como procedência.
        questionario.setAtivo(false);
    }

    private void aplicar(QuestionarioDtos.QuestionarioRequest req, Questionario questionario) {
        questionario.setDescricao(req.descricao());
        questionario.setInstrumento(req.instrumento());
        questionario.setVersao(req.versao());
        questionario.setPontuavel(req.pontuavel());
        questionario.setFaixaDeCorte(req.faixaDeCorte());

        questionario.getPerguntas().clear();
        int ordem = 1;
        for (var pedido : req.perguntas()) {
            if (pedido.tipo().temOpcoes() && !StringUtils.hasText(pedido.opcoes())) {
                throw new RegraDeNegocioException(
                        "A pergunta \"%s\" e de escolha e precisa de alternativas."
                                .formatted(pedido.enunciado()));
            }
            var pergunta = new Pergunta(questionario, pedido.enunciado(), pedido.tipo(), ordem++);
            pergunta.setObrigatoria(pedido.obrigatoria());
            pergunta.setOpcoes(pedido.opcoes());
            pergunta.setAjuda(pedido.ajuda());
            questionario.getPerguntas().add(pergunta);
        }
    }

    // ------------------------------------------------------------------ envio

    @Transactional(readOnly = true)
    public List<QuestionarioDtos.RespostaResponse> doPaciente(Long pacienteId) {
        pacienteService.exigirDaConta(pacienteId);
        return respostaRepository.doPaciente(pacienteId, contextoAtual.contaId()).stream()
                .map(QuestionarioDtos.RespostaResponse::de)
                .toList();
    }

    /** Respostas ligadas a um atendimento — o que a tela da consulta mostra. */
    @Transactional(readOnly = true)
    public List<QuestionarioDtos.RespostaResponse> doAgendamento(Long agendamentoId) {
        return respostaRepository.doAgendamento(agendamentoId, contextoAtual.contaId()).stream()
                .map(QuestionarioDtos.RespostaResponse::de)
                .toList();
    }

    @Transactional
    public QuestionarioDtos.RespostaResponse enviar(Long pacienteId,
                                                    QuestionarioDtos.EnvioRequest req) {
        pacienteService.exigirDaConta(pacienteId);
        Questionario questionario = exigirVisivel(req.questionarioId());

        var resposta = new RespostaDeQuestionario(
                contextoAtual.contaId(), pacienteId, questionario);
        resposta.setAgendamentoId(req.agendamentoId());
        respostaRepository.save(resposta);

        log.info("Questionário enviado: paciente={} questionário={} link={}",
                pacienteId, questionario.getId(), resposta.getIdentificadorPublico());
        return QuestionarioDtos.RespostaResponse.de(resposta);
    }

    @Transactional
    public void cancelarEnvio(Long id) {
        respostaRepository.delete(respostaRepository
                .buscarDaConta(id, contextoAtual.contaId())
                .filter(RespostaDeQuestionario::pendente)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Envio pendente", id)));
    }

    // ------------------------------------------------------- lado do paciente

    /** O formulário, servido sem credencial — autorizado pela posse do link. */
    @Transactional(readOnly = true)
    public QuestionarioDtos.FormularioPublicoResponse formulario(String identificador) {
        RespostaDeQuestionario envio = exigirEnvio(identificador);
        var conta = contaRepository.findById(envio.getContaId()).orElse(null);

        return new QuestionarioDtos.FormularioPublicoResponse(
                conta == null ? null : conta.getNome(),
                envio.getQuestionario().getNome(),
                envio.getQuestionario().getDescricao(),
                !envio.pendente(),
                envio.getQuestionario().getPerguntas().stream()
                        .map(QuestionarioDtos.PerguntaResponse::de)
                        .toList());
    }

    /**
     * Recebe as respostas do paciente.
     *
     * O escore só sai com todas as obrigatórias respondidas. Somar o que veio e
     * apresentar como pontuação produziria um número que aparenta exatidão — é
     * a mesma regra das dobras cutâneas, onde faltando uma o sistema não estima
     * e diz qual falta.
     */
    @Transactional
    public void preencher(String identificador, QuestionarioDtos.PreenchimentoRequest req) {
        RespostaDeQuestionario envio = exigirEnvio(identificador);
        if (!envio.pendente()) {
            // Um link já respondido não aceita outra resposta: o que ficou
            // gravado é o registro de uma consulta que aconteceu.
            throw new RegraDeNegocioException("Este formulário já foi respondido.");
        }

        Map<Long, String> porPergunta = new LinkedHashMap<>();
        req.respostas().forEach(r -> porPergunta.put(r.perguntaId(), r.valor()));

        List<String> faltando = new ArrayList<>();
        envio.getItens().clear();
        Integer escore = envio.getQuestionario().isPontuavel() ? 0 : null;

        for (Pergunta pergunta : envio.getQuestionario().getPerguntas()) {
            String valor = porPergunta.get(pergunta.getId());
            if (!StringUtils.hasText(valor)) {
                if (pergunta.isObrigatoria()) {
                    faltando.add(pergunta.getEnunciado());
                }
                continue;
            }
            var item = new ItemDeResposta(envio, pergunta, recortar(valor, 2000));
            if (pergunta.getTipo() == TipoDePergunta.ESCOLHA_UNICA) {
                Integer pontos = Opcao.pontosDe(pergunta.getOpcoes(), valor);
                item.setPontos(pontos);
                if (escore != null && pontos != null) {
                    escore += pontos;
                }
            }
            envio.getItens().add(item);
        }

        if (!faltando.isEmpty()) {
            throw new RegraDeNegocioException(
                    "Falta responder: " + String.join("; ", faltando));
        }

        envio.setRespondidoEm(Instant.now());
        envio.setEscore(escore);
        envio.setClassificacao(FaixaDeCorte.classificar(envio.getFaixaDeCorte(), escore));
        log.info("Questionário respondido: envio={} escore={}", envio.getId(), escore);
    }

    // ------------------------------------------------------------------ apoio

    private RespostaDeQuestionario exigirEnvio(String identificador) {
        return respostaRepository.findByIdentificadorPublico(identificador)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Formulário não encontrado. Confira o link recebido."));
    }

    private Questionario exigirVisivel(Long id) {
        return questionarioRepository.buscarVisivel(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Questionário", id));
    }

    private Questionario exigirDaConta(Long id) {
        Questionario questionario = exigirVisivel(id);
        if (questionario.ehModeloDoSistema()) {
            throw new RegraDeNegocioException(
                    "Modelos do sistema não podem ser alterados. Duplique para criar a sua versão.");
        }
        return questionario;
    }

    private String recortar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
