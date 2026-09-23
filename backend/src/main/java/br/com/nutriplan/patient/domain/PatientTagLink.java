package br.com.nutriplan.patient.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A ligação entre um paciente e uma TAG. */
@Entity
@Table(name = "patient_tag_link",
        indexes = @Index(name = "ix_tag_link_patient", columnList = "patient_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tag_link", columnNames = {"patient_id", "tag_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PatientTagLink extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private PatientTag tag;

    public PatientTagLink(Long patientId, PatientTag tag) {
        this.patientId = patientId;
        this.tag = tag;
    }
}
