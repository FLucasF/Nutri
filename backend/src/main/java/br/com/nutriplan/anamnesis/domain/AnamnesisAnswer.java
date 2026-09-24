package br.com.nutriplan.anamnesis.domain;

import br.com.nutriplan.questionnaire.domain.QuestionType;
import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What was answered to one question of the anamnesis questionnaire.
 *
 * It keeps the statement and the type, and not only the question id, for the
 * reason every record here keeps its wording: the questionnaire can change
 * after this anamnesis, and the anamnesis is the record of a consultation on
 * a date. A question removed from the model later still says what was asked.
 */
@Entity
@Table(name = "anamnesis_answer",
        indexes = @Index(name = "ix_anamnesis_answer_anamnesis", columnList = "anamnesis_id"))
@Getter
@Setter
@NoArgsConstructor
public class AnamnesisAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anamnesis_id", nullable = false)
    private Anamnesis anamnesis;

    /** Null once the question is removed from the questionnaire. */
    @Column(name = "question_id")
    private Long questionId;

    @Column(nullable = false, length = 500)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType type;

    /** The column is {@code content} because {@code value} is reserved in H2. */
    @Column(name = "content", length = 4000)
    private String value;

    /** Whether the answer shows in the patient's anamnesis listing. */
    @Column(nullable = false)
    private boolean highlight;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    public AnamnesisAnswer(Long questionId, String statement, QuestionType type,
                           String value, boolean highlight, Integer order) {
        this.questionId = questionId;
        this.statement = statement;
        this.type = type;
        this.value = value;
        this.highlight = highlight;
        this.order = order;
    }

    public boolean hasValue() {
        return value != null && !value.isBlank();
    }
}
