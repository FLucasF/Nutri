package br.com.nutriplan.prescription.domain;

/**
 * What a line inside a meal is.
 *
 * The client describes the bar that Word draws when you type "---" and press
 * Enter, and wants it between foods of the same meal: "eu quero que o paciente
 * coma o farelo de aveia com o mamão, eu iria adicionar os dois, clicar nesse
 * botão, isso iria separar".
 *
 * That makes the separator a position in the meal's order, not a food — which
 * is why it lives here and not in a table of its own. It carries no grams, so
 * it never reaches the totals.
 */
public enum MealItemKind {

    FOOD("Alimento"),

    /** A rule between foods. It groups what is meant to be eaten together. */
    SEPARATOR("Separador");

    private final String description;

    MealItemKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
