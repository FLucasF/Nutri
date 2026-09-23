package br.com.nutriplan.patient.domain;

import java.time.LocalDate;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um arquivo ou link guardado junto do prontuário do paciente.
 *
 * É a área "Arquivos Anexos" que ele lista na página 2, e é também a resposta
 * à dúvida dele sobre migração: em vez de recriar cardápio antigo um a um, ele
 * anexa o PDF do Webdiet ao paciente e o histórico fica onde deveria estar.
 * O próximo planejamento de cada paciente nasce aqui, na consulta seguinte —
 * sem data de corte e sem importador.
 */
@Entity
@Table(name = "patient_attachment",
        indexes = @Index(name = "ix_attachment_patient",
                columnList = "patient_id, reference_date"))
@Getter
@Setter
@NoArgsConstructor
public class PatientAttachment extends AccountEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentKind kind;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(length = 2000)
    private String url;

    @Column(length = 1000)
    private String notes;

    /** A data do documento, que raramente é a data em que ele foi anexado. */
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    public PatientAttachment(Long accountId, Long patientId, String title, AttachmentKind kind) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.title = title;
        this.kind = kind;
    }

    public boolean isFile() {
        return kind == AttachmentKind.FILE;
    }
}
