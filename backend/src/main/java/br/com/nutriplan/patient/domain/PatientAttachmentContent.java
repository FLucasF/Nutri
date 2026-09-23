package br.com.nutriplan.patient.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Os bytes do anexo, à parte da ficha.
 *
 * Toda leitura do paciente lista os anexos; nenhuma delas precisa dos arquivos.
 * Guardá-los juntos faria a lista arrastar megabytes de PDF para desenhar
 * nomes.
 */
@Entity
@Table(name = "patient_attachment_content")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PatientAttachmentContent {

    @Id
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(nullable = false)
    private byte[] content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    public PatientAttachmentContent(Long attachmentId, byte[] content) {
        this.attachmentId = attachmentId;
        this.content = content;
    }
}
