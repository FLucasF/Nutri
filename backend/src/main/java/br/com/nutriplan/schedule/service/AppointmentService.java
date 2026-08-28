package br.com.nutriplan.schedule.service;

import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.dto.ScheduleDtos;
import br.com.nutriplan.schedule.repository.AppointmentRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    /**
     * Window for searching conflict candidates, in hours on each side.
     *
     * It has to be larger than the longest allowed appointment (10 hours), so
     * that no conflict escapes the cut made in the database.
     */
    private static final int WINDOW_HOURS = 12;

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    /** The date that appears in the conflict message. Whoever reads it writes 27/08, not 2026-08-27. */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final CurrentContext contextCurrent;

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public ScheduleDtos.ScheduleDayResponse forDay(LocalDate date) {
        Long accountId = contextCurrent.accountId();
        List<Appointment> appointments = appointmentRepository.inRange(
                accountId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), null);

        Map<Long, String> names = patientsNames(appointments);
        List<ScheduleDtos.AppointmentResponse> answers = appointments.stream()
                .map(a -> build(a, names))
                .toList();

        return new ScheduleDtos.ScheduleDayResponse(
                date,
                answers.size(),
                (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count(),
                (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NOSHOW).count(),
                answers);
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.AppointmentResponse> inRange(LocalDate from, LocalDate to,
                                                        AppointmentStatus status) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("O fim do período não pode ser anterior ao início");
        }
        Long accountId = contextCurrent.accountId();
        List<Appointment> appointments = appointmentRepository.inRange(
                accountId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), status);

        Map<Long, String> names = patientsNames(appointments);
        return appointments.stream().map(a -> build(a, names)).toList();
    }

    /**
     * The same range, for a given account instead of the logged-in one.
     *
     * It exists for the iCalendar subscription, which is served without
     * authentication — the account there comes from the address of the feed,
     * and not from the context. It is the only path in the module that does not
     * go through the context, and so it takes the accountId explicitly, rather
     * than having the calling service touch the context.
     */
    @Transactional(readOnly = true)
    public List<ScheduleDtos.AppointmentResponse> naAccountRange(Long accountId, LocalDate from,
                                                               LocalDate to) {
        List<Appointment> appointments = appointmentRepository.inRange(
                accountId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), null);
        Map<Long, String> names = patientsNames(appointments);
        return appointments.stream().map(a -> build(a, names)).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.AppointmentResponse> forPatient(Long patientId) {
        Long accountId = contextCurrent.accountId();
        Patient patient = requirePatient(patientId, accountId);
        Map<Long, String> names = Map.of(patient.getId(), patient.getName());

        return appointmentRepository
                .findByAccountIdAndPatientIdOrderByStartDesc(accountId, patientId)
                .stream()
                .map(a -> build(a, names))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDtos.AppointmentResponse detail(Long id) {
        Appointment appointment = accountRequire(id);
        return build(appointment, patientsNames(List.of(appointment)));
    }

    @Transactional(readOnly = true)
    public Appointment accountRequire(Long id) {
        return appointmentRepository.findByIdAndAccountId(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Agendamento", id));
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public ScheduleDtos.AppointmentResponse schedule(ScheduleDtos.AppointmentRequest req) {
        Long accountId = contextCurrent.accountId();
        requirePatient(req.patientId(), accountId);

        var appointment = new Appointment(accountId, req.patientId(), req.start(), req.durationMinutes());
        appointment.setType(req.type());
        appointment.setNotes(req.notes());

        requireFreeTime(appointment, accountId, null);
        appointmentRepository.save(appointment);

        log.info("Atendimento agendado: id={} paciente={} início={}",
                appointment.getId(), req.patientId(), req.start());
        return build(appointment, patientsNames(List.of(appointment)));
    }

    @Transactional
    public ScheduleDtos.AppointmentResponse reschedule(Long id, ScheduleDtos.AppointmentRequest req) {
        Long accountId = contextCurrent.accountId();
        Appointment appointment = accountRequire(id);

        if (appointment.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    "Um atendimento %s não pode ser remarcado. Crie um novo."
                            .formatted(appointment.getStatus().getDescription().toLowerCase()));
        }
        requirePatient(req.patientId(), accountId);

        appointment.setPatientId(req.patientId());
        appointment.setStart(req.start());
        appointment.setDurationMinutes(req.durationMinutes());
        appointment.setType(req.type());
        appointment.setNotes(req.notes());

        // It ignores itself in the check: an appointment does not conflict with itself.
        requireFreeTime(appointment, accountId, id);

        return build(appointment, patientsNames(List.of(appointment)));
    }

    @Transactional
    public ScheduleDtos.AppointmentResponse changeStatus(Long id,
                                                        ScheduleDtos.StatusChangeRequest req) {
        Appointment appointment = accountRequire(id);
        appointment.changeStatusTo(req.status(), req.reason());

        log.info("Atendimento {} passou para {}", id, req.status());
        return build(appointment, patientsNames(List.of(appointment)));
    }

    @Transactional
    public void remove(Long id) {
        appointmentRepository.delete(accountRequire(id));
    }

    // ------------------------------------------------------------------ regras

    /**
     * Refuses an appointment that overlaps another already occupying the hour.
     *
     * The database returns the candidates around it; the overlap decision stays
     * with the entity itself, which is where the rule is written.
     */
    private void requireFreeTime(Appointment novo, Long accountId, Long ignoreId) {
        List<Appointment> candidates = appointmentRepository.candidatesConflict(
                accountId,
                novo.getStart().minusHours(WINDOW_HOURS),
                novo.getEnd().plusHours(WINDOW_HOURS));

        for (Appointment existing : candidates) {
            if (ignoreId != null && ignoreId.equals(existing.getId())) {
                continue;
            }
            if (existing.overlaps(novo.getStart(), novo.getEnd())) {
                throw new BusinessRuleException(
                        "Conflito de horário: já existe atendimento das %s às %s em %s."
                                .formatted(
                                        existing.getStart().format(HOUR),
                                        existing.getEnd().format(HOUR),
                                        existing.getStart().format(DATE)));
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    private ScheduleDtos.AppointmentResponse build(Appointment a, Map<Long, String> names) {
        return new ScheduleDtos.AppointmentResponse(
                a.getId(), a.getPatientId(), names.get(a.getPatientId()),
                a.getStart(), a.getEnd(), a.getDurationMinutes(),
                a.getType(), a.getType().getDescription(),
                a.getStatus(), a.getStatus().getDescription(),
                List.copyOf(a.getStatus().transitionsAllowed()),
                a.getNotes(), a.getReasonOutcome());
    }

    private Map<Long, String> patientsNames(List<Appointment> appointments) {
        List<Long> ids = appointments.stream()
                .map(Appointment::getPatientId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        patientRepository.findAllById(ids).forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    private Patient requirePatient(Long patientId, Long accountId) {
        return patientRepository.findByIdAndAccountId(patientId, accountId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }

    /** Start and end time as LocalDateTime, for use in tests and reports. */
    public static LocalDateTime em(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute);
    }
}
