package br.com.nutriplan.schedule.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import br.com.nutriplan.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An appointment in the practice's schedule.
 *
 * The interval is stored as start plus duration, and not as start and end.
 * Duration is what the professional reports when booking, and deriving the end
 * eliminates the inconsistent state of an end before the start — which no
 * validation would have to watch for if it simply cannot exist.
 */
@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "ix_appointment_account", columnList = "account_id, start_at"),
        @Index(name = "ix_appointment_patient", columnList = "patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends AccountEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime start;

    @Column(name = "minutes_duration", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentType type = AppointmentType.FOLLOWUP;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(length = 1000)
    private String notes;

    /** Reason for the cancellation or the no-show, when recorded. */
    @Column(name = "outcome_reason", length = 500)
    private String reasonOutcome;

    /**
     * Parceiro a quem esta consulta se atribui, quando difere de quem indicou
     * o paciente. Vazio na maioria: o relatório de indicações cai então no
     * parceiro do paciente.
     */
    @Column(name = "partner_id")
    private Long partnerId;

    /** Pacote de trabalho de que esta consulta faz parte. */
    @Column(name = "package_id")
    private Long packageId;

    public Appointment(Long accountId, Long patientId, LocalDateTime start, int durationMinutes) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.start = start;
        this.durationMinutes = durationMinutes;
    }

    public LocalDateTime getEnd() {
        return start.plusMinutes(durationMinutes);
    }

    /**
     * Applies the status change, refusing an invalid transition.
     *
     * The rule lives in the entity and not in the service because it is an
     * invariant of the appointment itself: any path that changes the status
     * has to respect it.
     */
    public void changeStatusTo(AppointmentStatus destination, String reason) {
        if (destination == status) {
            return;
        }
        if (!status.podeIrTo(destination)) {
            throw new BusinessRuleException(
                    "Um atendimento %s não pode passar para %s.".formatted(
                            status.getDescription().toLowerCase(),
                            destination.getDescription().toLowerCase()));
        }
        this.status = destination;
        if (reason != null && !reason.isBlank()) {
            this.reasonOutcome = reason;
        }
    }

    /**
     * Reports overlap with another interval.
     *
     * The comparison is strictly less-than on purpose: the end of one
     * appointment coinciding with the start of another is a full schedule, not
     * a conflict.
     */
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && getEnd().isAfter(otherStart);
    }

    public boolean occupiesTime() {
        return status.occupiesTime();
    }
}
