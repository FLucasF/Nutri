package br.com.nutriplan.agenda.domain;

/**
 * Natureza do atendimento.
 *
 * Serve para a agenda ser legível de relance e para o financeiro distinguir o
 * que cobrar: primeira consulta e retorno costumam ter valores diferentes.
 * A duração sugerida é ponto de partida, não imposição — o profissional altera.
 */
public enum TipoAtendimento {

    PRIMEIRA_CONSULTA("Primeira consulta", 60),
    RETORNO("Retorno", 30),
    Avaliação("Avaliação antropométrica", 45),
    Orientação("Orientação", 30),
    OUTRO("Outro", 30);

    private final String descricao;
    private final int duracaoSugeridaMinutos;

    TipoAtendimento(String descricao, int duracaoSugeridaMinutos) {
        this.descricao = descricao;
        this.duracaoSugeridaMinutos = duracaoSugeridaMinutos;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getDuracaoSugeridaMinutos() {
        return duracaoSugeridaMinutos;
    }
}
