package br.com.nutriplan.questionnaire.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "question",
        indexes = @Index(name = "ix_question_questionnaire", columnList = "questionnaire_id"))
@Getter
@Setter
@NoArgsConstructor
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionnaire_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pergunta_questionario"))
    private Questionnaire questionnaire;

    @Column(nullable = false, length = 500)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    /** Alternatives, with the score of each when the form scores. */
    @Column(length = 2000)
    private String options;

    @Column(length = 500)
    private String ajuda;

    /**
     * Whether the answer shows in the anamnesis listing without opening the
     * record — the same job the practice's declared fields do.
     */
    @Column(nullable = false)
    private boolean highlight = false;

    public Question(Questionnaire questionnaire, String statement, QuestionType type, int order) {
        this.questionnaire = questionnaire;
        this.statement = statement;
        this.type = type;
        this.order = order;
    }

    public List<Option> optionsAnalisadas() {
        return Option.analyze(options);
    }
}
