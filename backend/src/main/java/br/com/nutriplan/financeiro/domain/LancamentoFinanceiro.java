package br.com.nutriplan.financeiro.domain;

import br.com.nutriplan.shared.domain.EntidadeDeConta;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um lançamento financeiro do consultório.
 *
 * O valor é sempre positivo. O que distingue entrada de saída é o
 * {@link TipoLancamento}, e não o sinal — guardar despesa como número negativo
 * espalharia a regra por toda soma do sistema, e bastaria um esquecimento para
 * uma despesa virar receita.
 *
 * As três datas têm papéis distintos e não são intercambiáveis: competência diz
 * a que mês o lançamento pertence, vencimento diz quando deveria ser pago, e a
 * data de pagamento diz quando efetivamente entrou. É essa separação que permite
 * apurar regime de caixa e de competência sem misturar um com o outro.
 */
@Entity
@Table(name = "lancamento_financeiro", indexes = {
        @Index(name = "ix_lancamento_conta", columnList = "conta_id, competencia"),
        @Index(name = "ix_lancamento_paciente", columnList = "paciente_id"),
        @Index(name = "ix_lancamento_vencimento", columnList = "conta_id, situacao, vencimento")
})
@Getter
@Setter
@NoArgsConstructor
public class LancamentoFinanceiro extends EntidadeDeConta {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLancamento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoLancamento situacao = SituacaoLancamento.PENDENTE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    /** Mês a que o lançamento pertence, independentemente de quando for pago. */
    @Column(nullable = false)
    private LocalDate competencia;

    @Column
    private LocalDate vencimento;

    /** Preenchida ao efetivar. Nula enquanto pendente. */
    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(nullable = false, length = 80)
    private String categoria;

    @Column(name = "forma_pagamento", length = 40)
    private String formaPagamento;

    @Column(length = 500)
    private String descricao;

    /** Opcional: nem toda receita vem de paciente, nem toda despesa. */
    @Column(name = "paciente_id")
    private Long pacienteId;

    /** Opcional: liga a cobrança ao atendimento que a originou. */
    @Column(name = "agendamento_id")
    private Long agendamentoId;

    public LancamentoFinanceiro(Long contaId, TipoLancamento tipo,
                                BigDecimal valor, LocalDate competencia, String categoria) {
        setContaId(contaId);
        this.tipo = tipo;
        this.valor = valor;
        this.competencia = competencia;
        this.categoria = categoria;
    }

    /** Efetiva o lançamento na data informada. */
    public void registrarPagamento(LocalDate data) {
        if (situacao == SituacaoLancamento.CANCELADO) {
            throw new RegraDeNegocioException(
                    "Um lançamento cancelado não pode ser marcado como pago.");
        }
        this.situacao = SituacaoLancamento.PAGO;
        this.dataPagamento = data != null ? data : LocalDate.now();
    }

    public void estornar() {
        if (situacao != SituacaoLancamento.PAGO) {
            throw new RegraDeNegocioException("Só é possível estornar lançamento pago.");
        }
        this.situacao = SituacaoLancamento.PENDENTE;
        this.dataPagamento = null;
    }

    public void cancelar() {
        if (situacao == SituacaoLancamento.PAGO) {
            throw new RegraDeNegocioException(
                    "Estorne o pagamento antes de cancelar o lançamento.");
        }
        this.situacao = SituacaoLancamento.CANCELADO;
    }

    /** Vencido é o que passou da data e continua sem pagamento. */
    public boolean estaVencidoEm(LocalDate referencia) {
        return situacao == SituacaoLancamento.PENDENTE
                && vencimento != null
                && vencimento.isBefore(referencia);
    }

    public boolean entraNoCaixa() {
        return situacao == SituacaoLancamento.PAGO;
    }

    public boolean entraNoPrevisto() {
        return situacao != SituacaoLancamento.CANCELADO;
    }
}
