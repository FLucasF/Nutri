package br.com.nutriplan.anamnesis.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The general anamnesis — what the client calls the heart of the consultation.
 *
 * Three things live in the same record, and they answer different questions.
 * The body is the consultation written out, in the editor's format. The values
 * are the short labelled points the practice declared, and they are what shows
 * in the listing so the purpose of a visit can be read without opening it. The
 * answers are the questionnaire the anamnesis was built from — the client's
 * "anamnese programável" — filled in by the professional during the visit or
 * imported from what the patient answered before it.
 *
 * Summarising the free text automatically would fill the same space by
 * guessing. A declared field says what the professional meant; a summariser
 * says what it inferred, and a clinical record is no place for the difference.
 */
@Entity
@Table(name = "anamnesis", indexes = {
        @Index(name = "ix_anamnesis_patient", columnList = "patient_id"),
        @Index(name = "ix_anamnesis_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Anamnesis extends AccountEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    /** The editor's document, as JSON. Null while it is still blank. */
    @Column(length = 40_000)
    private String body;

    /** The questionnaire this anamnesis was built from, when it was. */
    @Column(name = "questionnaire_id")
    private Long questionnaireId;

    /** Frozen: the questionnaire may be renamed or removed later. */
    @Column(name = "questionnaire_name", length = 150)
    private String questionnaireName;

    @Column(name = "template_version")
    private Integer templateVersion;

    /** The pre-consultation sending this anamnesis was imported from, when it was. */
    @Column(name = "sending_id")
    private Long sendingId;

    @OneToMany(mappedBy = "anamnesis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order")
    private List<AnamnesisValue> values = new ArrayList<>();

    /*
     * Not fetched together with the values: Hibernate refuses to join-fetch
     * two lists at once, and the batch size keeps a listing of fifty
     * anamneses at one extra query instead of fifty.
     */
    @OneToMany(mappedBy = "anamnesis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order")
    @BatchSize(size = 50)
    private List<AnamnesisAnswer> answers = new ArrayList<>();

    public Anamnesis(Long accountId, Long patientId, String name, LocalDate date) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.name = name;
        this.date = date;
    }

    public void clearValues() {
        values.clear();
    }

    public void add(AnamnesisValue value) {
        value.setAnamnesis(this);
        values.add(value);
    }

    public void clearAnswers() {
        answers.clear();
    }

    public void add(AnamnesisAnswer answer) {
        answer.setAnamnesis(this);
        answers.add(answer);
    }

    public boolean fromQuestionnaire() {
        return questionnaireId != null;
    }
}
