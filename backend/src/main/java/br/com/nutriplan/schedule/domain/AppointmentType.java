package br.com.nutriplan.schedule.domain;

/**
 * Nature of the appointment.
 *
 * It serves to make the schedule readable at a glance and to let the finance
 * side tell apart what to charge: a first consultation and a follow-up usually
 * have different prices. The suggested duration is a starting point, not an
 * imposition — the professional changes it.
 */
public enum AppointmentType {

    FIRST_CONSULTATION("Primeira consulta", 60),
    FOLLOWUP("Retorno", 30),
    ASSESSMENT("Avaliação antropométrica", 45),
    COUNSELING("Orientação", 30),
    OTHER("Outro", 30);

    private final String description;
    private final int durationSuggestedMinutes;

    AppointmentType(String description, int durationSuggestedMinutes) {
        this.description = description;
        this.durationSuggestedMinutes = durationSuggestedMinutes;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationSuggestedMinutes() {
        return durationSuggestedMinutes;
    }
}
