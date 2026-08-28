package br.com.nutriplan.agenda.service;

import br.com.nutriplan.auth.domain.Conta;
import br.com.nutriplan.auth.repository.ContaRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Assinatura da agenda em calendário externo.
 *
 * Serve um feed iCalendar num endereço com UUID, que Google Agenda, Apple
 * Calendar e Outlook assinam nativamente. O calendário busca o endereço de
 * tempos em tempos e reflete o que mudou.
 *
 * <p><b>O que isto não é.</b> Não é integração com a API do Google. Ela exigiria
 * credencial de aplicativo, tela de consentimento revisada e um segundo sistema
 * de tokens para manter, e daria em troca a mesma coisa que o profissional
 * quer: ver os atendimentos no calendário que já usa. O que fica de fora é o
 * caminho de volta — criar um atendimento aqui a partir de um evento criado no
 * Google.
 *
 * <p><b>A ressalva.</b> Quem recebe o endereço vê a agenda, com nome de
 * paciente. É a mesma autorização por posse de link do plano público, e por
 * isso o endereço só existe depois que o nutricionista o pede, e pode ser
 * regerado — o que invalida o anterior.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssinaturaDaAgendaService {

    /**
     * Janela do feed.
     *
     * Um mês para trás porque o calendário do usuário costuma mostrar o mês
     * corrente inteiro; seis para frente porque agenda de consultório raramente
     * vai além disso, e servir o histórico completo faria o arquivo crescer sem
     * limite a cada ano de uso.
     */
    private static final int MESES_PARA_TRAS = 1;
    private static final int MESES_PARA_FRENTE = 6;

    private final ContaRepository contaRepository;
    private final AgendamentoService agendamentoService;
    private final CalendarioIcs calendario;
    private final ContextoAtual contextoAtual;

    public record Assinatura(String token) {}

    @Transactional(readOnly = true)
    public Assinatura atual() {
        return new Assinatura(exigirConta().getTokenAgenda());
    }

    /** Gera ou regenera. Regenerar invalida o endereço que já foi entregue. */
    @Transactional
    public Assinatura gerar() {
        Conta conta = exigirConta();
        String token = conta.gerarTokenDaAgenda();
        log.info("Assinatura da agenda gerada: conta={}", conta.getId());
        return new Assinatura(token);
    }

    @Transactional
    public void revogar() {
        exigirConta().setTokenAgenda(null);
    }

    /**
     * O calendário, servido sem credencial — autorizado pela posse do endereço.
     */
    @Transactional(readOnly = true)
    public String calendarioDe(String token) {
        Conta conta = contaRepository.findByTokenAgenda(token)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Agenda não encontrada. Confira o endereço da assinatura."));

        LocalDate hoje = LocalDate.now();
        var atendimentos = agendamentoService.naFaixaDaConta(
                conta.getId(),
                hoje.minusMonths(MESES_PARA_TRAS),
                hoje.plusMonths(MESES_PARA_FRENTE));

        return calendario.gerar(conta.getNome(), atendimentos);
    }

    private Conta exigirConta() {
        return contaRepository.findById(contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conta", contextoAtual.contaId()));
    }
}
