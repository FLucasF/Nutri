package br.com.nutriplan.questionnaire.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One submission of a questionnaire to a patient, and what they answered.
 *
 * The address is a UUID, like the public plan's: a sequential id would let
 * someone open the neighbour's form by adding 1 to the link.
 */
@Entity
@Table(name = "questionnaire_answer", indexes = {
        @Index(name = "ix_answer_patient", columnList = "patient_id"),
        @Index(name = "ix_public_answer", columnList = "public_identifier")
})
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireAnswer extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionnaire_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_resposta_questionario"))
    private Questionnaire questionnaire;

    /** The version of the template the patient saw. */
    @Column(name = "version_template", nullable = false)
    private int templateVersion;

    @Column(name = "public_identifier", nullable = false, length = 36)
    private String publicIdentifier = UUID.randomUUID().toString();

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    /** Null while pending. Once filled in, the link stops accepting an answer. */
    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column
    private Integer score;

    @Column(length = 120)
    private String classification;

    /** The cutoff rule used, frozen here. */
    @Column(name = "cutoff_range", length = 500)
    private String cutoffRange;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order asc")
    @BatchSize(size = 50)
    private List<AnswerItem> items = new ArrayList<>();

    public QuestionnaireAnswer(Long accountId, Long patientId, Questionnaire questionnaire) {
        this.accountId = accountId;
        this.patientId = patientId;
        this.questionnaire = questionnaire;
        this.templateVersion = questionnaire.getTemplateVersion();
        this.cutoffRange = questionnaire.getCutoffRange();
    }

    public boolean pending() {
        return answeredAt == null;
    }
}
