package br.com.nutriplan.patient.domain;

/**
 * Condição biológica da paciente, quando há uma.
 *
 * O cliente lista isso entre os opcionais do cadastro, e só para mulher. Por
 * isso não existe um valor "não gestante": a ausência já diz isso, e um terceiro
 * valor apareceria no formulário de todo mundo sem significar nada para a
 * maioria.
 */
public enum BiologicalCondition {

    PREGNANT("Gestante"),
    LACTATING("Lactante");

    private final String description;

    BiologicalCondition(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
