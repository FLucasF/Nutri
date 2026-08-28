package br.com.nutriplan.financeiro.service;

import br.com.nutriplan.auth.domain.Conta;
import br.com.nutriplan.auth.domain.Perfil;
import br.com.nutriplan.auth.domain.Usuario;
import br.com.nutriplan.auth.repository.ContaRepository;
import br.com.nutriplan.auth.repository.UsuarioRepository;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.financeiro.domain.LancamentoFinanceiro;
import br.com.nutriplan.financeiro.domain.SituacaoLancamento;
import br.com.nutriplan.financeiro.domain.TipoLancamento;
import br.com.nutriplan.financeiro.dto.FinanceiroDtos;
import br.com.nutriplan.financeiro.repository.LancamentoFinanceiroRepository;
import br.com.nutriplan.paciente.domain.Paciente;
import br.com.nutriplan.paciente.repository.PacienteRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LancamentoFinanceiroService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final LancamentoFinanceiroRepository lancamentoRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContaRepository contaRepository;
    private final ContextoAtual contextoAtual;

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Page<FinanceiroDtos.LancamentoResponse> listar(LocalDate de, LocalDate ate,
                                                          TipoLancamento tipo,
                                                          SituacaoLancamento situacao,
                                                          Long pacienteId,
                                                          Pageable pageable) {
        Long contaId = contextoAtual.contaId();
        Page<LancamentoFinanceiro> pagina = lancamentoRepository.buscar(
                contaId, de, ate, tipo, situacao, pacienteId, pageable);

        Map<Long, String> nomes = nomesDePacientes(pagina.getContent());
        LocalDate hoje = LocalDate.now();
        return pagina.map(l -> montar(l, nomes, hoje));
    }

    @Transactional(readOnly = true)
    public List<FinanceiroDtos.LancamentoResponse> vencidos(LocalDate referencia) {
        Long contaId = contextoAtual.contaId();
        LocalDate data = referencia != null ? referencia : LocalDate.now();

        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.vencidosEm(contaId, data);
        Map<Long, String> nomes = nomesDePacientes(lancamentos);
        return lancamentos.stream().map(l -> montar(l, nomes, data)).toList();
    }

    @Transactional(readOnly = true)
    public FinanceiroDtos.LancamentoResponse detalhar(Long id) {
        LancamentoFinanceiro lancamento = exigirDaConta(id);
        return montar(lancamento, nomesDePacientes(List.of(lancamento)), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public LancamentoFinanceiro exigirDaConta(Long id) {
        return lancamentoRepository.findByIdAndContaId(id, contextoAtual.contaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento", id));
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public FinanceiroDtos.LancamentoResponse criar(FinanceiroDtos.LancamentoRequest req) {
        Long contaId = contextoAtual.contaId();

        if (req.pacienteId() != null) {
            exigirPaciente(req.pacienteId(), contaId);
        }

        var lancamento = new LancamentoFinanceiro(
                contaId, req.tipo(), req.valor(), req.competencia(), req.categoria());
        aplicar(req, lancamento);
        lancamentoRepository.save(lancamento);

        log.info("Lançamento criado: id={} tipo={} valor={} conta={}",
                lancamento.getId(), req.tipo(), req.valor(), contaId);
        return montar(lancamento, nomesDePacientes(List.of(lancamento)), LocalDate.now());
    }

    @Transactional
    public FinanceiroDtos.LancamentoResponse atualizar(Long id,
                                                       FinanceiroDtos.LancamentoRequest req) {
        LancamentoFinanceiro lancamento = exigirDaConta(id);
        if (req.pacienteId() != null) {
            exigirPaciente(req.pacienteId(), lancamento.getContaId());
        }
        lancamento.setTipo(req.tipo());
        lancamento.setValor(req.valor());
        lancamento.setCompetencia(req.competencia());
        lancamento.setCategoria(req.categoria());
        aplicar(req, lancamento);

        return montar(lancamento, nomesDePacientes(List.of(lancamento)), LocalDate.now());
    }

    @Transactional
    public FinanceiroDtos.LancamentoResponse registrarPagamento(Long id,
                                                                FinanceiroDtos.PagamentoRequest req) {
        LancamentoFinanceiro lancamento = exigirDaConta(id);
        LocalDate data = req != null && req.dataPagamento() != null
                ? req.dataPagamento()
                : LocalDate.now();

        if (data.isAfter(LocalDate.now())) {
            throw new RegraDeNegocioException(
                    "A data de pagamento não pode ser futura: o valor ainda não entrou.");
        }
        lancamento.registrarPagamento(data);

        log.info("Pagamento registrado: lançamento={} data={}", id, data);
        return montar(lancamento, nomesDePacientes(List.of(lancamento)), LocalDate.now());
    }

    @Transactional
    public FinanceiroDtos.LancamentoResponse estornar(Long id) {
        LancamentoFinanceiro lancamento = exigirDaConta(id);
        lancamento.estornar();
        return montar(lancamento, nomesDePacientes(List.of(lancamento)), LocalDate.now());
    }

    @Transactional
    public FinanceiroDtos.LancamentoResponse cancelar(Long id) {
        LancamentoFinanceiro lancamento = exigirDaConta(id);
        lancamento.cancelar();
        return montar(lancamento, nomesDePacientes(List.of(lancamento)), LocalDate.now());
    }

    @Transactional
    public void remover(Long id) {
        lancamentoRepository.delete(exigirDaConta(id));
    }

    // ----------------------------------------------------------------- apuração

    /**
     * Apura o período pela competência.
     *
     * Devolve efetivado e previsto separadamente. O efetivado considera apenas
     * o que foi pago; o previsto inclui o que está pendente. Um número só
     * misturaria dinheiro que entrou com dinheiro que talvez entre.
     */
    @Transactional(readOnly = true)
    public FinanceiroDtos.ApuracaoResponse apurar(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new RegraDeNegocioException("O fim do período não pode ser anterior ao início");
        }
        Long contaId = contextoAtual.contaId();
        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.daCompetencia(contaId, de, ate);

        BigDecimal recebido = ZERO;
        BigDecimal aReceber = ZERO;
        BigDecimal despesasPagas = ZERO;
        BigDecimal despesasAPagar = ZERO;

        Map<String, FinanceiroDtos.TotalPorCategoria> categorias = new java.util.LinkedHashMap<>();

        for (LancamentoFinanceiro l : lancamentos) {
            if (l.getSituacao() == SituacaoLancamento.CANCELADO) {
                continue;
            }
            boolean pago = l.entraNoCaixa();
            if (l.getTipo().ehReceita()) {
                if (pago) recebido = recebido.add(l.getValor());
                else aReceber = aReceber.add(l.getValor());
            } else {
                if (pago) despesasPagas = despesasPagas.add(l.getValor());
                else despesasAPagar = despesasAPagar.add(l.getValor());
            }

            String chave = l.getTipo() + "|" + l.getCategoria();
            categorias.merge(chave,
                    new FinanceiroDtos.TotalPorCategoria(l.getCategoria(), l.getTipo(), l.getValor(), 1),
                    (a, b) -> new FinanceiroDtos.TotalPorCategoria(
                            a.categoria(), a.tipo(), a.total().add(b.total()),
                            a.lancamentos() + b.lancamentos()));
        }

        BigDecimal efetivado = recebido.subtract(despesasPagas);
        BigDecimal previsto = recebido.add(aReceber).subtract(despesasPagas).subtract(despesasAPagar);

        return new FinanceiroDtos.ApuracaoResponse(
                de, ate, recebido, aReceber, despesasPagas, despesasAPagar,
                efetivado, previsto,
                (int) lancamentos.stream().filter(LancamentoFinanceiro::entraNoPrevisto).count(),
                List.copyOf(categorias.values()));
    }

    // -------------------------------------------------------------------- recibo

    /**
     * Recibo de um lançamento pago.
     *
     * Recibo de valor não recebido seria declaração falsa, então só lançamento
     * efetivado gera recibo.
     */
    @Transactional(readOnly = true)
    public FinanceiroDtos.ReciboResponse recibo(Long id) {
        LancamentoFinanceiro lancamento = exigirDaConta(id);

        if (lancamento.getSituacao() != SituacaoLancamento.PAGO) {
            throw new RegraDeNegocioException(
                    "Só é possível emitir recibo de lançamento pago.");
        }
        if (!lancamento.getTipo().ehReceita()) {
            throw new RegraDeNegocioException("Recibo se emite sobre receita, não sobre despesa.");
        }

        Conta conta = contaRepository.findById(lancamento.getContaId()).orElse(null);
        Usuario profissional = usuarioRepository
                .findFirstByContaIdAndPerfilAndAtivoTrue(lancamento.getContaId(), Perfil.NUTRICIONISTA)
                .orElse(null);
        String pagador = lancamento.getPacienteId() == null ? null
                : pacienteRepository.findById(lancamento.getPacienteId())
                        .map(Paciente::getNome).orElse(null);

        return new FinanceiroDtos.ReciboResponse(
                lancamento.getId(),
                conta == null ? null : conta.getNome(),
                profissional == null ? null : profissional.getNome(),
                profissional == null ? null : profissional.getCrn(),
                pagador,
                lancamento.getValor(),
                ValorPorExtenso.emReais(lancamento.getValor()),
                lancamento.getDataPagamento(),
                lancamento.getDescricao() != null ? lancamento.getDescricao() : lancamento.getCategoria(),
                LocalDate.now().toString());
    }

    // ------------------------------------------------------------------- apoio

    private void aplicar(FinanceiroDtos.LancamentoRequest req, LancamentoFinanceiro lancamento) {
        lancamento.setVencimento(req.vencimento());
        lancamento.setFormaPagamento(req.formaPagamento());
        lancamento.setDescricao(req.descricao());
        lancamento.setPacienteId(req.pacienteId());
        lancamento.setAgendamentoId(req.agendamentoId());
    }

    private FinanceiroDtos.LancamentoResponse montar(LancamentoFinanceiro l,
                                                     Map<Long, String> nomes,
                                                     LocalDate referencia) {
        return new FinanceiroDtos.LancamentoResponse(
                l.getId(), l.getTipo(), l.getTipo().getDescricao(),
                l.getSituacao(), l.getSituacao().getDescricao(),
                l.getValor(), l.getCompetencia(), l.getVencimento(), l.getDataPagamento(),
                l.getCategoria(), l.getFormaPagamento(), l.getDescricao(),
                // Map.of() lanca NPE em chave nula, e lancamento sem paciente
                // tem pacienteId nulo — daí a checagem antes da consulta.
                l.getPacienteId(),
                l.getPacienteId() == null ? null : nomes.get(l.getPacienteId()),
                l.getAgendamentoId(),
                l.estaVencidoEm(referencia));
    }

    private Map<Long, String> nomesDePacientes(List<LancamentoFinanceiro> lancamentos) {
        List<Long> ids = lancamentos.stream()
                .map(LancamentoFinanceiro::getPacienteId)
                .filter(java.util.Objects::nonNull)
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
}
