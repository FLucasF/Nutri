package br.com.nutriplan.anamnesis.dto;

import java.time.LocalDate;
import java.util.List;

import br.com.nutriplan.anamnesis.domain.Anamnesis;
import br.com.nutriplan.anamnesis.domain.AnamnesisField;
import br.com.nutriplan.anamnesis.domain.AnamnesisValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AnamnesisDtos {

    private AnamnesisDtos() {
    }

    // ------------------------------------------------------------- os campos

    public record FieldRequest(
            @NotBlank @Size(max = 120) String label,
            boolean showInListing
    ) {}

    /** The whole list at once: reordering is a property of the set, not of one row. */
    public record FieldsRequest(@Valid @NotNull List<FieldRequest> fields) {}

    public record FieldResponse(
            Long id,
            String label,
            Integer order,
            boolean showInListing
    ) {
        public static FieldResponse from(AnamnesisField field) {
            return new FieldResponse(field.getId(), field.getLabel(),
                    field.getOrder(), field.isShowInListing());
        }
    }

    // ------------------------------------------------------------- a anamnese

    public record ValueRequest(
            Long fieldId,
            @Size(max = 500) String value
    ) {}

    public record AnamnesisRequest(
            @NotNull Long patientId,
            @NotBlank @Size(max = 150) String name,
            @NotNull LocalDate date,
            /** The editor's document, as JSON. Checked by RichTextDocument. */
            @Size(max = 40_000) String body,
            @Valid List<ValueRequest> values
    ) {}

    public record ValueResponse(Long fieldId, String label, String value, Integer order) {
        public static ValueResponse from(AnamnesisValue value) {
            return new ValueResponse(value.getFieldId(), value.getLabel(),
                    value.getValue(), value.getOrder());
        }
    }

    /** The row in the patient's listing: enough to choose without opening. */
    public record AnamnesisSummary(
            Long id,
            String name,
            LocalDate date,
            /** Only the values the practice marked to show here. */
            List<ValueResponse> highlights
    ) {}

    public record AnamnesisResponse(
            Long id,
            Long patientId,
            /**
             * De quem é esta anamnese.
             *
             * Sai aqui porque o PDF a imprime no cabeçalho: a folha circula
             * solta — impressa, anexada a um e-mail, arquivada numa pasta — e
             * fora da tela nada mais diz de quem ela é. O identificador não
             * serve: quem lê o papel não tem como resolvê-lo.
             */
            String patientName,
            String name,
            LocalDate date,
            String body,
            List<ValueResponse> values
    ) {
        public static AnamnesisResponse from(Anamnesis anamnesis, String patientName) {
            return new AnamnesisResponse(
                    anamnesis.getId(),
                    anamnesis.getPatientId(),
                    patientName,
                    anamnesis.getName(),
                    anamnesis.getDate(),
                    anamnesis.getBody(),
                    anamnesis.getValues().stream().map(ValueResponse::from).toList());
        }
    }
}
