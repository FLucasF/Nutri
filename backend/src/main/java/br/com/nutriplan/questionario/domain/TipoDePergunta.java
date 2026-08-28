package br.com.nutriplan.questionario.domain;

/** Como a pergunta e respondida. */
public enum TipoDePergunta {

    TEXTO("Texto livre"),
    NUMERO("Número"),
    ESCOLHA_UNICA("Escolha única"),
    MULTIPLA("Múltipla escolha");

    private final String descricao;

    TipoDePergunta(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean temOpcoes() {
        return this == ESCOLHA_UNICA || this == MULTIPLA;
    }
}
