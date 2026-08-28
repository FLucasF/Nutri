package br.com.nutriplan.schedule.dto;

import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.domain.AppointmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Input and output contracts of the schedule module. */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record AppointmentRequest(
            @NotNull Long patientId,
            @NotNull LocalDateTime start,
            @NotNull @Min(value = 5, message = "A duração mínima é de 5 minutos")
            @Max(value = 600, message = "A duração máxima é de 10 horas")
            Integer durationMinutes,
            @NotNull AppointmentType type,
            @Size(max = 1000) String notes
    ) {}

    public record StatusChangeRequest(
            @NotNull AppointmentStatus status,
            @Size(max = 500) String reason
    ) {}

    public record AppointmentResponse(
            Long id,
            Long patientId,
            String patientName,
            LocalDateTime start,
            LocalDateTime end,
            Integer durationMinutes,
            AppointmentType type,
            String typeDescription,
            AppointmentStatus status,
            String statusDescription,
            List<AppointmentStatus> transitionsAllowed,
            String notes,
            String reasonOutcome
    ) {}

    /** One day of the schedule, with what the professional needs to see at a glance. */
    public record ScheduleDayResponse(
            java.time.LocalDate date,
            int appointmentsTotal,
            int completed,
            int noshows,
            List<AppointmentResponse> appointments
    ) {}

    /** Catalog of types, with the duration each one suggests. */
    public record TypeResponse(AppointmentType type, String description, int durationSuggestedMinutes) {}
}
