package br.com.nutriplan.patient.dto;

import java.time.Instant;
import java.time.LocalDate;

import br.com.nutriplan.patient.domain.AttachmentKind;
import br.com.nutriplan.patient.domain.PatientAttachment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos dos arquivos e links anexados ao paciente. */
public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record LinkRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 2000) String url,
            @Size(max = 1000) String notes,
            LocalDate referenceDate
    ) {}

    public record AttachmentResponse(
            Long id,
            String title,
            AttachmentKind kind,
            String kindDescription,
            String fileName,
            String fileType,
            Long fileSize,
            String url,
            String notes,
            LocalDate referenceDate,
            Instant createdAt
    ) {
        public static AttachmentResponse from(PatientAttachment a) {
            return new AttachmentResponse(a.getId(), a.getTitle(), a.getKind(),
                    a.getKind().getDescription(), a.getFileName(), a.getFileType(),
                    a.getFileSize(), a.getUrl(), a.getNotes(), a.getReferenceDate(),
                    a.getCreatedAt());
        }
    }
}
