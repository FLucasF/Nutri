package br.com.nutriplan.questionnaire.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The answer to one question.
 *
 * It keeps the wording, and not only the question key. If the nutritionist
 * removes a question from the template later, the old answer cannot lose the
 * wording: the patient answered that question, and not the current version of
 * the form.
 */
@Entity
@Table(name = "item_answer",
        indexes = @Index(name = "ix_item_answer", columnList = "answer_id"))
@Getter
@Setter
@NoArgsConstructor
public class AnswerItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_resposta"))
    private QuestionnaireAnswer answer;

    /** Provenance. It goes null if the question is removed from the template. */
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "text_question", nullable = false, length = 500)
    private String questionText;

    @Column(name = "amount", length = 2000)
    private String value;

    @Column
    private Integer points;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    public AnswerItem(QuestionnaireAnswer answer, Question question, String value) {
        this.answer = answer;
        this.questionId = question.getId();
        this.questionText = question.getStatement();
        this.value = value;
        this.order = question.getOrder();
    }
}
