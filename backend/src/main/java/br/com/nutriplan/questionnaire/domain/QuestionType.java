package br.com.nutriplan.questionnaire.domain;

/** How the question is answered. */
public enum QuestionType {

    TEXT("Texto livre"),
    NUMBER("Número"),
    CHOICE_SINGLE("Escolha única"),
    MULTIPLE("Múltipla escolha");

    private final String description;

    QuestionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasOptions() {
        return this == CHOICE_SINGLE || this == MULTIPLE;
    }
}
