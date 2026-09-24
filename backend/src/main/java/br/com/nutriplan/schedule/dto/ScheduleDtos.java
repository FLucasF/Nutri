package br.com.nutriplan.schedule.dto;

import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.domain.AppointmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
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
            @Size(max = 1000) String notes,
            /** Parceiro a quem a consulta se atribui, quando difere de quem indicou o paciente. */
            Long partnerId,
            /** Pacote de trabalho de que a consulta faz parte. */
            Long packageId,
            /**
             * Marcar também os próximos encontros do pacote, no mesmo horário,
             * de intervalo em intervalo. Só ao agendar; remarcar não cria série.
             */
            Boolean createSeries
    ) {
        public boolean wantsSeries() {
            return Boolean.TRUE.equals(createSeries);
        }
    }

    public record StatusChangeRequest(
            @NotNull AppointmentStatus status,
            @Size(max = 500) String reason
    ) {}

    /** O lançamento financeiro ligado à consulta, quando há um que não foi cancelado. */
    public record PaymentInfo(
            Long transactionId,
            TransactionStatus status,
            String statusDescription,
            BigDecimal value,
            LocalDate datePayment
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
            String reasonOutcome,
            Long partnerId,
            String partnerName,
            Long packageId,
            String packageName,
            BigDecimal packageAmount,
            PaymentInfo payment,
            /** Só na resposta de um agendamento com série: quantos encontros a mais foram marcados. */
            Integer seriesCreated,
            /** Os encontros da série que não couberam na agenda, e por quê. */
            List<String> seriesSkipped
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
