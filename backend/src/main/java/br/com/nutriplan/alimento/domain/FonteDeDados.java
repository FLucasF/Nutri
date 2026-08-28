package br.com.nutriplan.alimento.domain;

/**
 * Procedencia da composicao nutricional. Precisa aparecer na prescricao:
 * o nutricionista responde tecnicamente pelo dado que prescreve, e valor de
 * tabela nacional, rotulo de fabricante e receita propria nao tem o mesmo peso.
 */
public enum FonteDeDados {

    /** Tabela Brasileira de Composicao de Alimentos - NEPA/Unicamp, 4a edicao. */
    TACO("TACO - NEPA/Unicamp, 4a ed."),

    /** Tabela Brasileira de Composicao de Alimentos - FoRC/USP. */
    TBCA("TBCA - FoRC/USP"),

    /** Tabela de Composicao Nutricional dos Alimentos Consumidos no Brasil - IBGE. */
    IBGE("IBGE - POF"),

    /** Rotulo declarado pelo fabricante. */
    FABRICANTE("Rotulo do fabricante"),

    /**
     * Produtos industrializados do Open Food Facts, base colaborativa aberta.
     * A licenca ODbL exige que a atribuicao acompanhe o dado onde ele aparecer —
     * por isso a descricao abaixo vai junto na prescricao e nos relatorios.
     */
    OPEN_FOOD_FACTS("Open Food Facts (ODbL)"),

    /** Alimento ou receita cadastrado pelo proprio nutricionista. */
    PERSONALIZADO("Cadastro próprio"),

    /** Receita composta por outros alimentos, com composicao calculada. */
    RECEITA("Receita calculada");

    private final String descricao;

    FonteDeDados(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Fontes publicas ficam disponiveis para todos os consultorios. */
    public boolean ehBasePublica() {
        return this == TACO || this == TBCA || this == IBGE || this == OPEN_FOOD_FACTS;
    }
}
