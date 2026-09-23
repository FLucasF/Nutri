package br.com.nutriplan.patient.dto;

import java.time.Instant;
import java.util.List;

import br.com.nutriplan.patient.domain.PatientNote;
import br.com.nutriplan.patient.domain.PatientTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos das TAGs e das anotações do paciente. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record TagRequest(@NotBlank @Size(max = 40) String name) {}

    public record TagResponse(Long id, String name, boolean systemTag, boolean own) {
        public static TagResponse from(PatientTag tag) {
            return new TagResponse(tag.getId(), tag.getName(),
                    tag.isSystemTag(), !tag.isSystemTag());
        }
    }

    /** O conjunto inteiro de TAGs do paciente, de uma vez. */
    public record PatientTagsRequest(List<Long> tagIds) {}

    public record NoteRequest(@NotBlank @Size(max = 8000) String body) {}

    public record NoteResponse(Long id, String body, Instant createdAt, String createdBy) {
        public static NoteResponse from(PatientNote note) {
            return new NoteResponse(note.getId(), note.getBody(),
                    note.getCreatedAt(), note.getCreatedBy());
        }
    }
}
