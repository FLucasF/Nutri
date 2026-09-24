package br.com.nutriplan.anthropometry.domain;

/**
 * As faixas de percentual de gordura de Pollock e Wilmore (1993), por sexo e
 * idade.
 *
 * A ordem é a da tabela: da menor gordura ("Excelente") para a maior ("Muito
 * ruim"). "Acima da média" tem, portanto, menos gordura que "Média" — é uma
 * avaliação de aptidão, não uma quantidade. {@link #VERY_LOW} não está na
 * tabela: cobre o valor abaixo do piso de "Excelente", onde a gordura fica
 * perto da essencial e o achado merece atenção, não elogio.
 */
public enum FatClassification {
    VERY_LOW("Muito baixo"),
    EXCELLENT("Excelente"),
    GOOD("Bom"),
    ABOVE_AVERAGE("Acima da média"),
    AVERAGE("Média"),
    BELOW_AVERAGE("Abaixo da média"),
    POOR("Ruim"),
    VERY_POOR("Muito ruim");

    private final String description;

    FatClassification(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
