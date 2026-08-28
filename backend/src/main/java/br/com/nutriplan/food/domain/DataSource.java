package br.com.nutriplan.food.domain;

/**
 * Provenance of the nutritional composition. It has to appear in the
 * prescription: the nutritionist is technically answerable for the data they
 * prescribe, and a national table value, a manufacturer's label and an
 * in-house recipe do not carry the same weight.
 */
public enum DataSource {

    /** Tabela Brasileira de Composição de Alimentos - NEPA/Unicamp, 4th edition. */
    TACO("TACO - NEPA/Unicamp, 4a ed."),

    /** Tabela Brasileira de Composição de Alimentos - FoRC/USP. */
    TBCA("TBCA - FoRC/USP"),

    /** Tabela de Composição Nutricional dos Alimentos Consumidos no Brasil - IBGE. */
    IBGE("IBGE - POF"),

    /** Label declared by the manufacturer. */
    MANUFACTURER("Rotulo do fabricante"),

    /**
     * Processed products from Open Food Facts, an open collaborative base.
     * The ODbL license requires the attribution to travel with the data
     * wherever it appears — which is why the description below goes along in
     * the prescription and in the reports.
     */
    OPEN_FOOD_FACTS("Open Food Facts (ODbL)"),

    /** A food or recipe registered by the nutritionist themselves. */
    CUSTOM("Cadastro próprio"),

    /** A recipe composed of other foods, with a calculated composition. */
    RECIPE("Receita calculada");

    private final String description;

    DataSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Public sources stay available to every practice. */
    public boolean isPublicBase() {
        return this == TACO || this == TBCA || this == IBGE || this == OPEN_FOOD_FACTS;
    }
}
