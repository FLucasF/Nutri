package br.com.nutriplan.schedule.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Life cycle of a scheduled appointment.
 *
 * The transitions are explicit because the patient's history depends on them:
 * an appointment going back from "COMPLETED" to "SCHEDULED" would erase the
 * record that the consultation happened, and the no-show would stop appearing
 * in the follow-up. Terminal statuses do not go back — correcting a wrong entry
 * is the work of whoever operates the system, not a state transition.
 */
public enum AppointmentStatus {

    SCHEDULED("Agendado"),
    CONFIRMED("Confirmado"),
    COMPLETED("Realizado"),
    NOSHOW("Faltou"),
    CANCELED("Cancelado");

    private final String description;

    AppointmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Statuses reachable from this one. */
    public Set<AppointmentStatus> transitionsAllowed() {
        return switch (this) {
            case SCHEDULED -> EnumSet.of(CONFIRMED, COMPLETED, NOSHOW, CANCELED);
            case CONFIRMED -> EnumSet.of(COMPLETED, NOSHOW, CANCELED);
            // Terminal: the appointment already had an outcome.
            case COMPLETED, NOSHOW, CANCELED -> EnumSet.noneOf(AppointmentStatus.class);
        };
    }

    public boolean podeIrTo(AppointmentStatus destination) {
        return transitionsAllowed().contains(destination);
    }

    /** A canceled appointment frees the slot; the others occupy it. */
    public boolean occupiesTime() {
        return this != CANCELED;
    }

    public boolean isTerminal() {
        return transitionsAllowed().isEmpty();
    }
}
