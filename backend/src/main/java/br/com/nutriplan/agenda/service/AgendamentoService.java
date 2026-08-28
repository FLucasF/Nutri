package br.com.nutriplan.agenda.service;

import br.com.nutriplan.agenda.domain.Agendamento;
import br.com.nutriplan.agenda.domain.SituacaoAtendimento;
import br.com.nutriplan.agenda.dto.AgendaDtos;
import br.com.nutriplan.agenda.repository.AgendamentoRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.repository.PacienteRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgendamentoService {

    /**
     * Janela de busca de candidatos a conflito, em horas para cada lado.
     *
     * Precisa ser maior que o atendimento mais longo permitido (10 horas), para
     * que nenhum conflito escape do recorte feito no banco.
     */
    private static final int JANELA_HORAS = 12;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    /** A data que aparece na mensagem de conflito. Quem lê escreve 27/08, não 2026-08-27. */
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final AgendamentoRepository agendamentoRepository;
    private final PacienteRepository pacienteRepository;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public AgendaDtos.DiaDaAgendaResponse doDia(LocalDate data) {
        Long contaId = contextoAtual.contaId();
        List<Agendamento> agendamentos = agendamentoRepository.naFaixa(
                contaId, data.atStartOfDay(), data.plusDays(1).atStartOfDay(), null);

        Map<Long, String> nomes = nomesDePacientes(agendamentos);
        List<AgendaDtos.AgendamentoResponse> respostas = agendamentos.stream()
                .map(a -> montar(a, nomes))
                .toList();

        return new AgendaDtos.DiaDaAgendaResponse(
                data,
                respostas.size(),
                (int) agendamentos.stream().filter(a -> a.getSituacao() == SituacaoAtendimento.REALIZADO).count(),
                (int) agendamentos.stream().filter(a -> a.getSituacao() == SituacaoAtendimento.FALTOU).count(),
                respostas);
    }

    @Transactional(readOnly = true)
    public List<AgendaDtos.AgendamentoResponse> naFaixa(LocalDate de, LocalDate ate,
                                                        SituacaoAtendimento situacao) {
        if (ate.isBefore(de)) {
            throw new RegraDeNegocioException("O fim do período não pode ser anterior ao início");
        }
        Long contaId = contextoAtual.contaId();
        List<Agendamento> agendamentos = agendamentoRepository.naFaixa(
                contaId, de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), situacao);

        Map<Long, String> nomes = nomesDePacientes(agendamentos);
        return agendamentos.stream().map(a -> montar(a, nomes)).toList();
    }

    /**
     * A mesma faixa, para uma conta informada em vez da conta logada.
     *
     * Existe para a assinatura iCalendar, que e servida sem autenticacao — a
     * conta ali vem do endereco do feed, e nao do contexto. E o unico caminho
     * do modulo que nao passa pelo contexto, e por isso recebe o contaId
     * explicitamente, em vez de o servico chamador mexer no contexto.
     */
    @Transactional(readOnly = true)
    public List<AgendaDtos.AgendamentoResponse> naFaixaDaConta(Long contaId, LocalDate de,
                                                               LocalDate ate) {
        List<Agendamento> agendamentos = agendamentoRepository.naFaixa(
                contaId, de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), null);
        Map<Long, String> nomes = nomesDePacientes(agendamentos);
        return agendamentos.stream().map(a -> montar(a, nomes)).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendaDtos.AgendamentoResponse> doPaciente(Long pacienteId) {
        Long contaId = contextoAtual.contaId();
        Paciente paciente = exigirPaciente(pacienteId, contaId);
        Map<Long, String> nomes = Map.of(paciente.getId(), paciente.getNome());

        return agendamentoRepository
                .findByContaIdAndPacienteIdOrderByInicioDesc(contaId, pacienteId)
                .stream()
                .map(a -> montar(a, nomes))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgendaDtos.AgendamentoResponse detalhar(Long id) {
        Agendamento agendamento = exigirDaConta(id);
        return montar(agendamento, nomesDePacientes(List.of(agendamento)));
    }

    @Transactional(readOnly = true)
    public Agendamento exigirDaConta(Long id) {
        return agendamentoRepository.findByIdAndContaId(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento", id));
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public AgendaDtos.AgendamentoResponse agendar(AgendaDtos.AgendamentoRequest req) {
        Long contaId = contextoAtual.contaId();
        exigirPaciente(req.pacienteId(), contaId);

        var agendamento = new Agendamento(contaId, req.pacienteId(), req.inicio(), req.duracaoMinutos());
        agendamento.setTipo(req.tipo());
        agendamento.setObservacao(req.observacao());

        exigirHorarioLivre(agendamento, contaId, null);
        agendamentoRepository.save(agendamento);

        log.info("Atendimento agendado: id={} paciente={} início={}",
                agendamento.getId(), req.pacienteId(), req.inicio());
        return montar(agendamento, nomesDePacientes(List.of(agendamento)));
    }

    @Transactional
    public AgendaDtos.AgendamentoResponse remarcar(Long id, AgendaDtos.AgendamentoRequest req) {
        Long contaId = contextoAtual.contaId();
        Agendamento agendamento = exigirDaConta(id);

        if (agendamento.getSituacao().ehTerminal()) {
            throw new RegraDeNegocioException(
                    "Um atendimento %s não pode ser remarcado. Crie um novo."
                            .formatted(agendamento.getSituacao().getDescricao().toLowerCase()));
        }
        exigirPaciente(req.pacienteId(), contaId);

        agendamento.setPacienteId(req.pacienteId());
        agendamento.setInicio(req.inicio());
        agendamento.setDuracaoMinutos(req.duracaoMinutos());
        agendamento.setTipo(req.tipo());
        agendamento.setObservacao(req.observacao());

        // Ignora a si mesmo na checagem: um atendimento não conflita consigo.
        exigirHorarioLivre(agendamento, contaId, id);

        return montar(agendamento, nomesDePacientes(List.of(agendamento)));
    }

    @Transactional
    public AgendaDtos.AgendamentoResponse mudarSituacao(Long id,
                                                        AgendaDtos.MudancaDeSituacaoRequest req) {
        Agendamento agendamento = exigirDaConta(id);
        agendamento.mudarSituacaoPara(req.situacao(), req.motivo());

        log.info("Atendimento {} passou para {}", id, req.situacao());
        return montar(agendamento, nomesDePacientes(List.of(agendamento)));
    }

    @Transactional
    public void remover(Long id) {
        agendamentoRepository.delete(exigirDaConta(id));
    }

    // ------------------------------------------------------------------ regras

    /**
     * Recusa o agendamento que se sobrepõe a outro já ocupando o horário.
     *
     * O banco devolve os candidatos do entorno; a decisão de sobreposição fica
     * com a própria entidade, que é onde a regra está escrita.
     */
    private void exigirHorarioLivre(Agendamento novo, Long contaId, Long ignorarId) {
        List<Agendamento> candidatos = agendamentoRepository.candidatosAConflito(
                contaId,
                novo.getInicio().minusHours(JANELA_HORAS),
                novo.getFim().plusHours(JANELA_HORAS));

        for (Agendamento existente : candidatos) {
            if (ignorarId != null && ignorarId.equals(existente.getId())) {
                continue;
            }
            if (existente.sobrepoe(novo.getInicio(), novo.getFim())) {
                throw new RegraDeNegocioException(
                        "Conflito de horário: já existe atendimento das %s às %s em %s."
                                .formatted(
                                        existente.getInicio().format(HORA),
                                        existente.getFim().format(HORA),
                                        existente.getInicio().format(DATA)));
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    private AgendaDtos.AgendamentoResponse montar(Agendamento a, Map<Long, String> nomes) {
        return new AgendaDtos.AgendamentoResponse(
                a.getId(), a.getPacienteId(), nomes.get(a.getPacienteId()),
                a.getInicio(), a.getFim(), a.getDuracaoMinutos(),
                a.getTipo(), a.getTipo().getDescricao(),
                a.getSituacao(), a.getSituacao().getDescricao(),
                List.copyOf(a.getSituacao().transicoesPermitidas()),
                a.getObservacao(), a.getMotivoDesfecho());
    }

    private Map<Long, String> nomesDePacientes(List<Agendamento> agendamentos) {
        List<Long> ids = agendamentos.stream()
                .map(Agendamento::getPacienteId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nomes = new HashMap<>();
        pacienteRepository.findAllById(ids).forEach(p -> nomes.put(p.getId(), p.getNome()));
        return nomes;
    }

    private Paciente exigirPaciente(Long pacienteId, Long contaId) {
        return pacienteRepository.findByIdAndContaId(pacienteId, contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
    }

    /** Horário de início e fim como LocalDateTime, para uso em testes e relatórios. */
    public static LocalDateTime em(LocalDate data, int hora, int minuto) {
        return data.atTime(hora, minuto);
    }
}
