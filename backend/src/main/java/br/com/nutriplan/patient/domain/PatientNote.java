package br.com.nutriplan.patient.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma anotação do nutricionista sobre o paciente.
 *
 * "anotações do nutricionista sobre o paciente p. ex., o nutricionista se
 * sentiu ofendido com algo que o paciente fez ou falou, o nutricionista vê que
 * é um caso legal aí já deixa lá, como se fosse um tweet sabe?"
 *
 * São datadas e sucessivas, então não cabem no campo único de observações que
 * o paciente já tem — esse continua sendo a descrição dele, e não o diário.
 */
@Entity
@Table(name = "patient_note",
        indexes = @Index(name = "ix_patient_note_patient", columnList = "patient_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class PatientNote extends AccountEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 8000)
    private String body;

    public PatientNote(Long accountId, Long patientId, String body) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.body = body;
    }
}
