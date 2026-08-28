package br.com.nutriplan.financeiro.domain;

/**
 * Sentido do lancamento no caixa.
 *
 * O valor guardado e sempre positivo; e este tipo que diz se ele soma ou
 * subtrai. Guardar despesa como numero negativo espalharia a regra por toda
 * soma do sistema.
 */
public enum TipoLancamento {

    RECEITA("Receita", 1),
    DESPESA("Despesa", -1);

    private final String descricao;
    private final int sinal;

    TipoLancamento(String descricao, int sinal) {
        this.descricao = descricao;
        this.sinal = sinal;
    }

    public String getDescricao() {
        return descricao;
    }

    /** +1 para receita, -1 para despesa. Usado na apuracao do resultado. */
    public int getSinal() {
        return sinal;
    }

    public boolean ehReceita() {
        return this == RECEITA;
    }
}
