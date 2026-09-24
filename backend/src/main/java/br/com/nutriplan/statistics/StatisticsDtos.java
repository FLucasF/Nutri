package br.com.nutriplan.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contratos das estatísticas do consultório. */
public final class StatisticsDtos {

    private StatisticsDtos() {
    }

    /** Um mês do período. */
    public record MonthRow(
            /** "2026-09", para ordenar. */
            String month,
            /** "set/2026", para ler. */
            String label,
            int appointments,
            int completed,
            int noshows,
            int canceled,
            int firstConsultations,
            int followups,
            int newPatients,
            int assessments,
            int plans,
            BigDecimal revenuePaid
    ) {}

    /** Paciente ativo que não vem há tempo demais — ou que nunca veio. */
    public record InactivePatient(
            Long id,
            String name,
            LocalDate lastVisit,
            long daysSince
    ) {}

    public record StatisticsResponse(
            LocalDate from,
            LocalDate to,
            int activePatients,
            int newPatients,
            int appointments,
            int completed,
            int noshows,
            BigDecimal revenuePaid,
            List<MonthRow> months,
            int inactivityDays,
            List<InactivePatient> withoutVisit
    ) {}
}
