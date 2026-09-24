package br.com.nutriplan.partner.service;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.finance.domain.FinanceTransaction;
import br.com.nutriplan.finance.repository.FinanceTransactionRepository;
import br.com.nutriplan.partner.domain.Partner;
import br.com.nutriplan.partner.dto.PartnerDtos;
import br.com.nutriplan.partner.repository.PartnerRepository;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.repository.AppointmentRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Parceiros de indicação e o relatório do que cada um trouxe.
 *
 * A atribuição de uma consulta segue uma regra só, escrita em
 * {@link #attribute}: vale o parceiro marcado na consulta e, sem marcação, o
 * parceiro que indicou o paciente. A receita segue a consulta a que se refere
 * ou, sem consulta, o paciente.
 */
@Service
@RequiredArgsConstructor
public class PartnerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final PartnerRepository repository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public List<PartnerDtos.PartnerResponse> list(boolean includeInactive) {
        Long accountId = currentContext.accountId();
        Map<Long, Long> referred = new HashMap<>();
        for (Patient p : patientRepository.findByAccountIdAndPartnerIdIsNotNull(accountId)) {
            referred.merge(p.getPartnerId(), 1L, Long::sum);
        }
        return repository.findByAccountIdOrderByNameAsc(accountId).stream()
                .filter(p -> includeInactive || p.isActive())
                .map(p -> PartnerDtos.PartnerResponse.from(p, referred.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional
    public PartnerDtos.PartnerResponse create(PartnerDtos.PartnerRequest req) {
        var partner = new Partner(currentContext.accountId(), req.name().trim());
        apply(req, partner);
        return PartnerDtos.PartnerResponse.from(repository.save(partner), 0L);
    }

    @Transactional
    public PartnerDtos.PartnerResponse update(Long id, PartnerDtos.PartnerRequest req) {
        Partner partner = require(id);
        partner.setName(req.name().trim());
        apply(req, partner);
        return PartnerDtos.PartnerResponse.from(partner, referredBy(partner));
    }

    /** Desativar não desfaz indicação nenhuma: os pacientes continuam apontando para ele. */
    @Transactional
    public PartnerDtos.PartnerResponse setActive(Long id, boolean active) {
        Partner partner = require(id);
        partner.setActive(active);
        return PartnerDtos.PartnerResponse.from(partner, referredBy(partner));
    }

    @Transactional(readOnly = true)
    public Partner require(Long id) {
        return repository.findByIdAndAccountId(id, currentContext.accountId())
                .orElseThrow(() -> new NotFoundException("Parceiro", id));
    }

    // ------------------------------------------------------------- relatório

    @Transactional(readOnly = true)
    public PartnerDtos.ReferralReport report(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("O fim do período não pode ser anterior ao início");
        }
        Long accountId = currentContext.accountId();
        List<Partner> partners = repository.findByAccountIdOrderByNameAsc(accountId);

        Map<Long, Long> patientPartner = new HashMap<>();
        Map<Long, Long> referred = new HashMap<>();
        for (Patient p : patientRepository.findByAccountIdAndPartnerIdIsNotNull(accountId)) {
            patientPartner.put(p.getId(), p.getPartnerId());
            referred.merge(p.getPartnerId(), 1L, Long::sum);
        }

        // [consultas, realizadas, faltas] por parceiro. Canceladas não contam:
        // uma consulta que não aconteceu não é indicação que rendeu.
        Map<Long, long[]> counts = new HashMap<>();
        List<Appointment> inPeriod = appointmentRepository.inRange(
                accountId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), null);
        for (Appointment a : inPeriod) {
            Long partnerId = attribute(a, patientPartner);
            if (partnerId == null || a.getStatus() == AppointmentStatus.CANCELED) {
                continue;
            }
            long[] c = counts.computeIfAbsent(partnerId, k -> new long[3]);
            c[0]++;
            if (a.getStatus() == AppointmentStatus.COMPLETED) {
                c[1]++;
            } else if (a.getStatus() == AppointmentStatus.NOSHOW) {
                c[2]++;
            }
        }

        // A receita é a paga no período, e a consulta a que ela se refere pode
        // ser de fora dele — um pacote pago hoje por encontros do mês passado.
        List<FinanceTransaction> paid = transactionRepository.paidBetween(accountId, from, to).stream()
                .filter(t -> t.getType().isIncome())
                .toList();
        Set<Long> appointmentIds = paid.stream()
                .map(FinanceTransaction::getAppointmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Appointment> appointments = appointmentIds.isEmpty()
                ? Map.of()
                : appointmentRepository.findAllById(appointmentIds).stream()
                        .filter(a -> a.belongs(accountId))
                        .collect(Collectors.toMap(Appointment::getId, Function.identity()));

        Map<Long, BigDecimal> revenue = new HashMap<>();
        for (FinanceTransaction t : paid) {
            Appointment a = t.getAppointmentId() == null ? null : appointments.get(t.getAppointmentId());
            Long partnerId = a == null ? null : attribute(a, patientPartner);
            if (partnerId == null && t.getPatientId() != null) {
                partnerId = patientPartner.get(t.getPatientId());
            }
            if (partnerId == null) {
                continue;
            }
            revenue.merge(partnerId, t.getValue(), BigDecimal::add);
        }

        List<PartnerDtos.ReferralRow> rows = partners.stream()
                .map(p -> {
                    long[] c = counts.getOrDefault(p.getId(), new long[3]);
                    return new PartnerDtos.ReferralRow(
                            p.getId(), p.getName(), p.getKind(),
                            referred.getOrDefault(p.getId(), 0L),
                            c[0], c[1], c[2],
                            revenue.getOrDefault(p.getId(), ZERO));
                })
                .toList();

        long totalPatients = rows.stream().mapToLong(PartnerDtos.ReferralRow::patientsReferred).sum();
        long totalAppointments = rows.stream().mapToLong(PartnerDtos.ReferralRow::appointments).sum();
        BigDecimal totalRevenue = rows.stream()
                .map(PartnerDtos.ReferralRow::revenuePaid)
                .reduce(ZERO, BigDecimal::add);

        return new PartnerDtos.ReferralReport(from, to, rows, totalPatients, totalAppointments, totalRevenue);
    }

    /** O parceiro da consulta ou, sem ele, o que indicou o paciente. */
    private static Long attribute(Appointment a, Map<Long, Long> patientPartner) {
        return a.getPartnerId() != null ? a.getPartnerId() : patientPartner.get(a.getPatientId());
    }

    private long referredBy(Partner partner) {
        return patientRepository.findByAccountIdAndPartnerIdIsNotNull(partner.getAccountId()).stream()
                .filter(p -> partner.getId().equals(p.getPartnerId()))
                .count();
    }

    private static void apply(PartnerDtos.PartnerRequest req, Partner partner) {
        partner.setKind(blankToNull(req.kind()));
        partner.setContact(blankToNull(req.contact()));
        partner.setNotes(blankToNull(req.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
