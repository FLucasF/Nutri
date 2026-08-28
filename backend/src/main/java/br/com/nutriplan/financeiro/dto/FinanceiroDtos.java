package br.com.nutriplan.financeiro.dto;

import br.com.nutriplan.financeiro.domain.SituacaoLancamento;
import br.com.nutriplan.financeiro.domain.TipoLancamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contratos de entrada e saída do módulo financeiro. */
public final class FinanceiroDtos {

    private FinanceiroDtos() {
    }

    public record LancamentoRequest(
            @NotNull TipoLancamento tipo,
            @NotNull @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
            BigDecimal valor,
            @NotNull LocalDate competencia,
            LocalDate vencimento,
            @NotBlank @Size(max = 80) String categoria,
            @Size(max = 40) String formaPagamento,
            @Size(max = 500) String descricao,
            Long pacienteId,
            Long agendamentoId
    ) {}

    public record PagamentoRequest(LocalDate dataPagamento) {}

    public record LancamentoResponse(
            Long id,
            TipoLancamento tipo,
            String tipoDescricao,
            SituacaoLancamento situacao,
            String situacaoDescricao,
            BigDecimal valor,
            LocalDate competencia,
            LocalDate vencimento,
            LocalDate dataPagamento,
            String categoria,
            String formaPagamento,
            String descricao,
            Long pacienteId,
            String pacienteNome,
            Long agendamentoId,
            boolean vencido
    ) {}

    /**
     * Apuração de um período.
     *
     * Efetivado e previsto vêm separados de propósito: somar o que ainda não
     * entrou faria o consultório parecer ter dinheiro que não tem.
     */
    public record ApuracaoResponse(
            LocalDate de,
            LocalDate ate,
            BigDecimal totalRecebido,
            BigDecimal totalAReceber,
            BigDecimal despesasPagas,
            BigDecimal despesasAPagar,
            BigDecimal resultadoEfetivado,
            BigDecimal resultadoPrevisto,
            int lancamentos,
            List<TotalPorCategoria> porCategoria
    ) {}

    public record TotalPorCategoria(
            String categoria,
            TipoLancamento tipo,
            BigDecimal total,
            int lancamentos
    ) {}

    /** Recibo de um lançamento pago. */
    public record ReciboResponse(
            Long lancamentoId,
            String consultorioNome,
            String profissionalNome,
            String profissionalCrn,
            String pagadorNome,
            BigDecimal valor,
            String valorPorExtenso,
            LocalDate dataPagamento,
            String referente,
            String emitidoEm
    ) {}
}
