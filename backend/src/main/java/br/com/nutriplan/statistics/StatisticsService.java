package br.com.nutriplan.statistics;

import br.com.nutriplan.anthropometry.repository.AnthropometricAssessmentRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.finance.domain.FinanceTransaction;
import br.com.nutriplan.finance.repository.FinanceTransactionRepository;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.prescription.repository.MealPlanRepository;
import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.schedule.domain.AppointmentType;
import br.com.nutriplan.schedule.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Os números do consultório, mês a mês.
 *
 * "Consultas por mês, retornos, pacientes sem consulta há 60 dias": o que o
 * cliente pediu para saber se o consultório está andando. Cada série vem de
 * uma consulta só ao banco e é somada aqui — o volume é o de um consultório,
 * não o de um hospital.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    /** Depois de quanto tempo sem consulta realizada um paciente ativo aparece na lista. */
    public static final int INACTIVITY_DAYS = 60;

    private static final String[] MONTHS = {
            "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final AnthropometricAssessmentRepository assessmentRepository;
    private final MealPlanRepository mealPlanRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public StatisticsDtos.StatisticsResponse overview(int months) {
        int span = Math.max(1, Math.min(months, 24));
        Long accountId = currentContext.accountId();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusMonths(span - 1L).withDayOfMonth(1);
        // Até o fim do mês corrente: o que já está marcado para as próximas
        // semanas conta como agendado deste mês.
        LocalDate to = today.withDayOfMonth(today.lengthOfMonth());

        // [consultas, realizadas, faltas, canceladas, primeiras, retornos,
        //  pacientes novos, avaliações, cardápios]
        Map<YearMonth, int[]> acc = new LinkedHashMap<>();
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(to)); ym = ym.plusMonths(1)) {
            acc.put(ym, new int[9]);
        }
        Map<YearMonth, BigDecimal> revenue = new HashMap<>();

        for (Appointment a : appointmentRepository.inRange(
                accountId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), null)) {
            int[] c = acc.get(YearMonth.from(a.getStart()));
            if (c == null) {
                continue;
            }
            c[0]++;
            switch (a.getStatus()) {
                case COMPLETED -> c[1]++;
                case NOSHOW -> c[2]++;
                case CANCELED -> c[3]++;
                default -> { }
            }
            if (a.getType() == AppointmentType.FIRST_CONSULTATION) {
                c[4]++;
            } else if (a.getType() == AppointmentType.FOLLOWUP) {
                c[5]++;
            }
        }

        List<Patient> patients = patientRepository.findByAccountId(accountId);
        for (Patient p : patients) {
            if (p.getCreatedAt() == null) {
                continue;
            }
            int[] c = acc.get(YearMonth.from(p.getCreatedAt().atZone(zone).toLocalDate()));
            if (c != null) {
                c[6]++;
            }
        }
        for (LocalDate date : assessmentRepository.datesSince(accountId, from)) {
            int[] c = acc.get(YearMonth.from(date));
            if (c != null) {
                c[7]++;
            }
        }
        for (Instant created : mealPlanRepository.createdSince(accountId, from.atStartOfDay(zone).toInstant())) {
            int[] c = acc.get(YearMonth.from(created.atZone(zone).toLocalDate()));
            if (c != null) {
                c[8]++;
            }
        }
        for (FinanceTransaction t : transactionRepository.paidBetween(accountId, from, to)) {
            if (t.getType().isIncome() && t.getDatePayment() != null) {
                revenue.merge(YearMonth.from(t.getDatePayment()), t.getValue(), BigDecimal::add);
            }
        }

        List<StatisticsDtos.MonthRow> rows = acc.entrySet().stream()
                .map(e -> {
                    int[] c = e.getValue();
                    return new StatisticsDtos.MonthRow(
                            e.getKey().toString(), label(e.getKey()),
                            c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8],
                            revenue.getOrDefault(e.getKey(), ZERO));
                })
                .toList();

        // Sem consulta: a última realizada ficou para trás do limite, ou o
        // paciente foi cadastrado há mais tempo que isso e nunca veio.
        Map<Long, LocalDate> lastVisit = new HashMap<>();
        for (Object[] row : appointmentRepository.lastCompletedByPatient(accountId)) {
            lastVisit.put((Long) row[0], ((LocalDateTime) row[1]).toLocalDate());
        }
        LocalDate threshold = today.minusDays(INACTIVITY_DAYS);
        List<StatisticsDtos.InactivePatient> withoutVisit = patients.stream()
                .filter(Patient::isActive)
                .map(p -> {
                    LocalDate last = lastVisit.get(p.getId());
                    LocalDate reference = last != null ? last
                            : p.getCreatedAt() == null ? null : p.getCreatedAt().atZone(zone).toLocalDate();
                    if (reference == null || reference.isAfter(threshold)) {
                        return null;
                    }
                    return new StatisticsDtos.InactivePatient(
                            p.getId(), p.getName(), last, ChronoUnit.DAYS.between(reference, today));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(StatisticsDtos.InactivePatient::daysSince).reversed()
                        .thenComparing(StatisticsDtos.InactivePatient::name))
                .toList();

        return new StatisticsDtos.StatisticsResponse(
                from, to,
                (int) patients.stream().filter(Patient::isActive).count(),
                rows.stream().mapToInt(StatisticsDtos.MonthRow::newPatients).sum(),
                rows.stream().mapToInt(StatisticsDtos.MonthRow::appointments).sum(),
                rows.stream().mapToInt(StatisticsDtos.MonthRow::completed).sum(),
                rows.stream().mapToInt(StatisticsDtos.MonthRow::noshows).sum(),
                rows.stream().map(StatisticsDtos.MonthRow::revenuePaid).reduce(ZERO, BigDecimal::add),
                rows, INACTIVITY_DAYS, withoutVisit);
    }

    private static String label(YearMonth ym) {
        return MONTHS[ym.getMonthValue() - 1] + "/" + ym.getYear();
    }
}
