package br.com.nutriplan.financeiro.domain;

/**
 * Situacao de um lancamento.
 *
 * A distincao entre pendente e pago e o que separa a apuracao por caixa da
 * apuracao por competencia: somar o que ainda nao entrou faria o consultorio
 * parecer ter dinheiro que nao tem.
 */
public enum SituacaoLancamento {

    PENDENTE("Pendente"),
    PAGO("Pago"),
    CANCELADO("Cancelado");

    private final String descricao;

    SituacaoLancamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
