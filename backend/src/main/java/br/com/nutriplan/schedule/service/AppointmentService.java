package br.com.nutriplan.schedule.service;

import br.com.nutriplan.auth.domain.Account;
import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.User;
import br.com.nutriplan.auth.repository.AccountRepository;
import br.com.nutriplan.auth.repository.UserRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.finance.domain.FinanceTransaction;
import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.finance.repository.FinanceTransactionRepository;
import br.com.nutriplan.partner.domain.Partner;
import br.com.nutriplan.partner.repository.PartnerRepository;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.domain.AppointmentType;
import br.com.nutriplan.schedule.dto.ScheduleDtos;
import br.com.nutriplan.schedule.repository.AppointmentRepository;
import br.com.nutriplan.servicepackage.domain.ServicePackage;
import br.com.nutriplan.servicepackage.repository.ServicePackageRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final PartnerRepository partnerRepository;
    private final ServicePackageRepository packageRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AttendanceCertificatePdfGenerator certificateGenerator;
    private final CurrentContext contextCurrent;

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public ScheduleDtos.ScheduleDayResponse forDay(LocalDate date) {
        Long accountId = contextCurrent.accountId();
        List<Appointment> appointments = appointmentRepository.inRange(
                accountId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), null);

        List<ScheduleDtos.AppointmentResponse> answers = buildAll(appointments, accountId);

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
        return buildAll(appointments, accountId);
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
        return buildAll(appointments, accountId);
    }

    @Transactional(readOnly = true)
    public List<ScheduleDtos.AppointmentResponse> forPatient(Long patientId) {
        Long accountId = contextCurrent.accountId();
        requirePatient(patientId, accountId);
        return buildAll(appointmentRepository
                .findByAccountIdAndPatientIdOrderByStartDesc(accountId, patientId), accountId);
    }

    @Transactional(readOnly = true)
    public ScheduleDtos.AppointmentResponse detail(Long id) {
        Appointment appointment = accountRequire(id);
        return single(appointment);
    }

    @Transactional(readOnly = true)
    public Appointment accountRequire(Long id) {
        return appointmentRepository.findByIdAndAccountId(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Agendamento", id));
    }

    /**
     * O atestado de comparecimento, só de consulta realizada: atestar presença
     * em consulta que não aconteceu seria uma declaração falsa.
     */
    @Transactional(readOnly = true)
    public byte[] certificate(Long id) {
        Appointment appointment = accountRequire(id);
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "O atestado de comparecimento só sai de uma consulta realizada. "
                            + "Registre o desfecho antes.");
        }
        Patient patient = requirePatient(appointment.getPatientId(), appointment.getAccountId());
        User professional = userRepository
                .findFirstByAccountIdAndRoleAndActiveTrue(appointment.getAccountId(), Role.NUTRITIONIST)
                .orElse(null);
        String practice = accountRepository.findById(appointment.getAccountId())
                .map(Account::getName).orElse(null);
        return certificateGenerator.generate(patient, appointment,
                professional == null ? null : professional.getName(),
                professional == null ? null : professional.getCrn(),
                practice);
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public ScheduleDtos.AppointmentResponse schedule(ScheduleDtos.AppointmentRequest req) {
        Long accountId = contextCurrent.accountId();
        requirePatient(req.patientId(), accountId);
        requirePartner(req.partnerId(), accountId);
        ServicePackage pack = requirePackage(req.packageId(), accountId, true);

        var appointment = new Appointment(accountId, req.patientId(), req.start(), req.durationMinutes());
        fill(appointment, req);

        requireFreeTime(appointment, accountId, null);
        appointmentRepository.save(appointment);

        Integer created = null;
        List<String> skipped = null;
        if (req.wantsSeries() && pack != null && pack.followupSessions() > 0) {
            SeriesResult series = scheduleSeries(appointment, pack, accountId);
            created = series.created();
            skipped = series.skipped();
        }

        log.info("Atendimento agendado: id={} paciente={} início={} série={}",
                appointment.getId(), req.patientId(), req.start(), created);
        return build(appointment, contextOf(List.of(appointment), accountId), created, skipped);
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
        requirePartner(req.partnerId(), accountId);
        // Trocar de pacote exige um ativo; manter o que já estava, não.
        requirePackage(req.packageId(), accountId, !Objects.equals(req.packageId(), appointment.getPackageId()));

        appointment.setPatientId(req.patientId());
        appointment.setStart(req.start());
        appointment.setDurationMinutes(req.durationMinutes());
        fill(appointment, req);

        // It ignores itself in the check: an appointment does not conflict with itself.
        requireFreeTime(appointment, accountId, id);

        return single(appointment);
    }

    @Transactional
    public ScheduleDtos.AppointmentResponse changeStatus(Long id,
                                                        ScheduleDtos.StatusChangeRequest req) {
        Appointment appointment = accountRequire(id);
        appointment.changeStatusTo(req.status(), req.reason());

        log.info("Atendimento {} passou para {}", id, req.status());
        return single(appointment);
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

    private record SeriesResult(int created, List<String> skipped) {}

    /**
     * Marca os próximos encontros do pacote, no mesmo horário, de intervalo em
     * intervalo.
     *
     * Um encontro cujo horário já está tomado fica de fora e é relatado, em
     * vez de derrubar a série inteira: quem agenda prefere marcar nove e
     * ajustar um a repetir tudo.
     */
    private SeriesResult scheduleSeries(Appointment first, ServicePackage pack, Long accountId) {
        int total = pack.getSessions();
        int created = 0;
        List<String> skipped = new ArrayList<>();

        if (!StringUtils.hasText(first.getNotes())) {
            first.setNotes("Encontro 1 de %d · %s".formatted(total, pack.getName()));
        }
        for (int i = 2; i <= total; i++) {
            LocalDateTime start = first.getStart().plusDays((long) pack.intervalOrWeekly() * (i - 1));
            var next = new Appointment(accountId, first.getPatientId(), start, first.getDurationMinutes());
            next.setType(AppointmentType.FOLLOWUP);
            next.setPartnerId(first.getPartnerId());
            next.setPackageId(pack.getId());
            next.setNotes("Encontro %d de %d · %s".formatted(i, total, pack.getName()));
            try {
                requireFreeTime(next, accountId, null);
            } catch (BusinessRuleException conflict) {
                skipped.add("Encontro %d (%s às %s) ficou de fora: %s".formatted(
                        i, start.format(DATE), start.format(HOUR), conflict.getMessage()));
                continue;
            }
            appointmentRepository.save(next);
            created++;
        }
        return new SeriesResult(created, skipped);
    }

    // ------------------------------------------------------------------ apoio

    private void fill(Appointment appointment, ScheduleDtos.AppointmentRequest req) {
        appointment.setType(req.type());
        appointment.setNotes(req.notes());
        appointment.setPartnerId(req.partnerId());
        appointment.setPackageId(req.packageId());
    }

    /** O que a resposta precisa além da consulta: nomes e o lançamento ligado a ela. */
    private record Context(Map<Long, String> patients,
                           Map<Long, Partner> partners,
                           Map<Long, ServicePackage> packages,
                           Map<Long, FinanceTransaction> payments) {}

    private ScheduleDtos.AppointmentResponse single(Appointment appointment) {
        return build(appointment, contextOf(List.of(appointment), appointment.getAccountId()), null, null);
    }

    private List<ScheduleDtos.AppointmentResponse> buildAll(List<Appointment> appointments, Long accountId) {
        Context ctx = contextOf(appointments, accountId);
        return appointments.stream().map(a -> build(a, ctx, null, null)).toList();
    }

    private Context contextOf(List<Appointment> appointments, Long accountId) {
        if (appointments.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<Long, Partner> partners = new HashMap<>();
        List<Long> partnerIds = appointments.stream()
                .map(Appointment::getPartnerId).filter(Objects::nonNull).distinct().toList();
        if (!partnerIds.isEmpty()) {
            partnerRepository.findAllById(partnerIds).forEach(p -> partners.put(p.getId(), p));
        }
        Map<Long, ServicePackage> packages = new HashMap<>();
        List<Long> packageIds = appointments.stream()
                .map(Appointment::getPackageId).filter(Objects::nonNull).distinct().toList();
        if (!packageIds.isEmpty()) {
            packageRepository.findAllById(packageIds).forEach(p -> packages.put(p.getId(), p));
        }
        // Um lançamento por consulta: o pago vale mais que o pendente, e o
        // cancelado não vale nada.
        Map<Long, FinanceTransaction> payments = new HashMap<>();
        List<Long> ids = appointments.stream().map(Appointment::getId).toList();
        for (FinanceTransaction t : transactionRepository.findByAccountIdAndAppointmentIdIn(accountId, ids)) {
            if (t.getStatus() == TransactionStatus.CANCELED) {
                continue;
            }
            payments.merge(t.getAppointmentId(), t,
                    (kept, other) -> other.getStatus() == TransactionStatus.PAID ? other : kept);
        }
        return new Context(patientsNames(appointments), partners, packages, payments);
    }

    private ScheduleDtos.AppointmentResponse build(Appointment a, Context ctx,
                                                   Integer seriesCreated, List<String> seriesSkipped) {
        Partner partner = a.getPartnerId() == null ? null : ctx.partners().get(a.getPartnerId());
        ServicePackage pack = a.getPackageId() == null ? null : ctx.packages().get(a.getPackageId());
        FinanceTransaction payment = ctx.payments().get(a.getId());
        return new ScheduleDtos.AppointmentResponse(
                a.getId(), a.getPatientId(), ctx.patients().get(a.getPatientId()),
                a.getStart(), a.getEnd(), a.getDurationMinutes(),
                a.getType(), a.getType().getDescription(),
                a.getStatus(), a.getStatus().getDescription(),
                List.copyOf(a.getStatus().transitionsAllowed()),
                a.getNotes(), a.getReasonOutcome(),
                a.getPartnerId(), partner == null ? null : partner.getName(),
                a.getPackageId(), pack == null ? null : pack.getName(),
                pack == null ? null : pack.getAmount(),
                payment == null ? null : new ScheduleDtos.PaymentInfo(
                        payment.getId(), payment.getStatus(), payment.getStatus().getDescription(),
                        payment.getValue(), payment.getDatePayment()),
                seriesCreated, seriesSkipped);
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

    private Partner requirePartner(Long partnerId, Long accountId) {
        if (partnerId == null) {
            return null;
        }
        return partnerRepository.findByIdAndAccountId(partnerId, accountId)
                .orElseThrow(() -> new NotFoundException("Parceiro", partnerId));
    }

    private ServicePackage requirePackage(Long packageId, Long accountId, boolean mustBeActive) {
        if (packageId == null) {
            return null;
        }
        ServicePackage pack = packageRepository.findByIdAndAccountId(packageId, accountId)
                .orElseThrow(() -> new NotFoundException("Pacote", packageId));
        if (mustBeActive && !pack.isActive()) {
            throw new BusinessRuleException(
                    "O pacote \"%s\" está desativado. Reative-o ou escolha outro.".formatted(pack.getName()));
        }
        return pack;
    }

    /** Start and end time as LocalDateTime, for use in tests and reports. */
    public static LocalDateTime em(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute);
    }
}
