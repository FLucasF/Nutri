package br.com.nutriplan.questionnaire.domain;

/**
 * How the question is answered.
 *
 * The client asked the anamnesis to be "programável (checkbox, resposta curta,
 * parágrafo)": a short text and a paragraph are different controls on screen,
 * a date is typed with a picker, and a section is not answered at all — it
 * is the heading that groups the questions under it.
 */
public enum QuestionType {

    TEXT("Resposta curta"),
    PARAGRAPH("Parágrafo"),
    NUMBER("Número"),
    DATE("Data"),
    CHOICE_SINGLE("Escolha única"),
    MULTIPLE("Múltipla escolha"),
    SECTION("Seção");

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

    /** A section is a heading: nothing is written under it. */
    public boolean answerable() {
        return this != SECTION;
    }
}
