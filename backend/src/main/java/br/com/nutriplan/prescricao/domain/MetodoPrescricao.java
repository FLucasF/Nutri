package br.com.nutriplan.prescricao.domain;

/**
 * Como o plano expressa o que o paciente deve comer.
 *
 * A escolha muda o que o sistema exige e o que calcula: um plano qualitativo
 * nao tem quantidade para somar, e cobrar isso do profissional seria burocracia
 * sem valor clinico.
 */
public enum MetodoPrescricao {

    /** Cada item traz alimento e quantidade definidos. Totaliza nutrientes. */
    ALIMENTOS("Por alimentos", true),

    /**
     * Cada item traz opcoes intercambiaveis de valor nutricional proximo.
     * Totaliza pela opcao principal, que e a referencia do calculo.
     */
    EQUIVALENTES("Por equivalentes", true),

    /**
     * Orientacao sem quantificar — "salada a vontade", "uma fruta".
     * Nao totaliza: nao ha quantidade a somar.
     */
    QUALITATIVO("Qualitativo", false);

    private final String descricao;
    private final boolean quantificado;

    MetodoPrescricao(String descricao, boolean quantificado) {
        this.descricao = descricao;
        this.quantificado = quantificado;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Indica se os itens exigem quantidade e entram na totalizacao. */
    public boolean ehQuantificado() {
        return quantificado;
    }

    public boolean admiteEquivalentes() {
        return this == EQUIVALENTES;
    }
}
