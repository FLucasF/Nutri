package br.com.nutriplan.finance.service;

import br.com.nutriplan.auth.domain.Account;
import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.User;
import br.com.nutriplan.auth.repository.AccountRepository;
import br.com.nutriplan.auth.repository.UserRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.finance.domain.FinanceTransaction;
import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.finance.domain.TransactionType;
import br.com.nutriplan.finance.dto.FinanceDtos;
import br.com.nutriplan.finance.repository.FinanceTransactionRepository;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.domain.AppointmentType;
import br.com.nutriplan.schedule.repository.AppointmentRepository;
import br.com.nutriplan.servicepackage.domain.ServicePackage;
import br.com.nutriplan.servicepackage.repository.ServicePackageRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceTransactionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final FinanceTransactionRepository transactionRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServicePackageRepository packageRepository;
    private final CurrentContext contextCurrent;

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public Page<FinanceDtos.TransactionResponse> list(LocalDate from, LocalDate to,
                                                          TransactionType type,
                                                          TransactionStatus status,
                                                          Long patientId,
                                                          Pageable pageable) {
        Long accountId = contextCurrent.accountId();
        Page<FinanceTransaction> page = transactionRepository.find(
                accountId, from, to, type, status, patientId, pageable);

        Names names = namesOf(page.getContent());
        LocalDate today = LocalDate.now();
        return page.map(l -> build(l, names, today));
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.TransactionResponse> overdue(LocalDate reference) {
        Long accountId = contextCurrent.accountId();
        LocalDate date = reference != null ? reference : LocalDate.now();

        List<FinanceTransaction> transactions = transactionRepository.overdueAt(accountId, date);
        Names names = namesOf(transactions);
        return transactions.stream().map(l -> build(l, names, date)).toList();
    }

    @Transactional(readOnly = true)
    public FinanceDtos.TransactionResponse detail(Long id) {
        return single(accountRequire(id));
    }

    @Transactional(readOnly = true)
    public FinanceTransaction accountRequire(Long id) {
        return transactionRepository.findByIdAndAccountId(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Lançamento", id));
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public FinanceDtos.TransactionResponse create(FinanceDtos.TransactionRequest req) {
        Long accountId = contextCurrent.accountId();

        if (req.patientId() != null) {
            requirePatient(req.patientId(), accountId);
        }
        requirePackage(req.packageId(), accountId);

        int installments = req.installmentsOrOne();
        if (installments > 1) {
            return createInstallments(req, accountId, installments);
        }

        var transaction = new FinanceTransaction(
                accountId, req.type(), req.value(), req.accrual(), req.category());
        apply(req, transaction);
        transactionRepository.save(transaction);

        log.info("Lançamento criado: id={} tipo={} valor={} conta={}",
                transaction.getId(), req.type(), req.value(), accountId);
        return single(transaction);
    }

    /**
     * Um parcelamento: n lançamentos com o mesmo grupo, numerados, cada um na
     * competência e no vencimento do seu mês.
     *
     * O valor se divide em centavos exatos e a sobra fica na primeira
     * parcela, para que a soma das parcelas seja o valor combinado — e não
     * um centavo a menos.
     */
    private FinanceDtos.TransactionResponse createInstallments(FinanceDtos.TransactionRequest req,
                                                               Long accountId, int count) {
        String group = UUID.randomUUID().toString();
        BigDecimal total = req.value().setScale(2, RoundingMode.HALF_UP);
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(each.multiply(BigDecimal.valueOf(count)));
        LocalDate due = req.due() != null ? req.due() : req.accrual();

        FinanceTransaction first = null;
        for (int i = 1; i <= count; i++) {
            BigDecimal value = i == 1 ? each.add(remainder) : each;
            var transaction = new FinanceTransaction(
                    accountId, req.type(), value, req.accrual().plusMonths(i - 1L), req.category());
            apply(req, transaction);
            transaction.setDue(due.plusMonths(i - 1L));
            transaction.setInstallmentGroup(group);
            transaction.setInstallmentIndex(i);
            transaction.setInstallmentCount(count);
            transactionRepository.save(transaction);
            if (first == null) {
                first = transaction;
            }
        }

        log.info("Parcelamento criado: {} parcelas, total={} conta={}", count, total, accountId);
        return single(first);
    }

    @Transactional
    public FinanceDtos.TransactionResponse update(Long id,
                                                       FinanceDtos.TransactionRequest req) {
        FinanceTransaction transaction = accountRequire(id);
        if (req.patientId() != null) {
            requirePatient(req.patientId(), transaction.getAccountId());
        }
        requirePackage(req.packageId(), transaction.getAccountId());
        transaction.setType(req.type());
        transaction.setValue(req.value());
        transaction.setAccrual(req.accrual());
        transaction.setCategory(req.category());
        apply(req, transaction);

        return single(transaction);
    }

    @Transactional
    public FinanceDtos.TransactionResponse recordPayment(Long id,
                                                                FinanceDtos.PaymentRequest req) {
        FinanceTransaction transaction = accountRequire(id);
        LocalDate date = req != null && req.datePayment() != null
                ? req.datePayment()
                : LocalDate.now();

        requireNotFuture(date);
        transaction.recordPayment(date);

        log.info("Pagamento registrado: lançamento={} data={}", id, date);
        return single(transaction);
    }

    /**
     * O pagamento registrado a partir da consulta.
     *
     * Se a consulta já tinha uma cobrança pendente, é ela que se quita — a
     * agenda e o financeiro falam do mesmo lançamento. Sem cobrança, nasce
     * uma receita já paga, ligada à consulta, ao paciente e ao pacote. Uma
     * consulta paga não recebe segundo pagamento: o caminho é o estorno.
     */
    @Transactional
    public FinanceDtos.TransactionResponse payAppointment(Long appointmentId,
                                                          FinanceDtos.AppointmentPaymentRequest req) {
        Long accountId = contextCurrent.accountId();
        Appointment appointment = appointmentRepository.findByIdAndAccountId(appointmentId, accountId)
                .orElseThrow(() -> new NotFoundException("Agendamento", appointmentId));
        if (appointment.getStatus() == AppointmentStatus.CANCELED) {
            throw new BusinessRuleException("Uma consulta cancelada não recebe pagamento.");
        }

        List<FinanceTransaction> existing = transactionRepository
                .findByAccountIdAndAppointmentId(accountId, appointmentId).stream()
                .filter(t -> t.getStatus() != TransactionStatus.CANCELED)
                .toList();
        if (existing.stream().anyMatch(t -> t.getStatus() == TransactionStatus.PAID)) {
            throw new BusinessRuleException(
                    "Esta consulta já tem pagamento registrado. Para corrigir, estorne o lançamento no financeiro.");
        }

        LocalDate date = req == null || req.datePayment() == null ? LocalDate.now() : req.datePayment();
        requireNotFuture(date);

        Optional<FinanceTransaction> pending = existing.stream()
                .filter(t -> t.getStatus() == TransactionStatus.PENDING)
                .findFirst();
        FinanceTransaction transaction;
        if (pending.isPresent()) {
            transaction = pending.get();
            if (req != null && req.value() != null) {
                transaction.setValue(req.value());
            }
        } else {
            ServicePackage pack = appointment.getPackageId() == null ? null
                    : packageRepository.findById(appointment.getPackageId()).orElse(null);
            BigDecimal value = req != null && req.value() != null ? req.value()
                    : pack != null ? pack.getAmount() : null;
            if (value == null || value.signum() <= 0) {
                throw new BusinessRuleException(
                        "Informe o valor recebido: a consulta não tem pacote com valor.");
            }
            String category = req != null && StringUtils.hasText(req.category()) ? req.category()
                    : pack != null ? "Pacote" : categoryOf(appointment.getType());
            transaction = new FinanceTransaction(
                    accountId, TransactionType.INCOME, value, appointment.getStart().toLocalDate(), category);
            transaction.setDue(appointment.getStart().toLocalDate());
            transaction.setPatientId(appointment.getPatientId());
            transaction.setAppointmentId(appointment.getId());
            transaction.setPackageId(appointment.getPackageId());
            String when = appointment.getType().getDescription() + " de " + appointment.getStart().format(DATE);
            transaction.setDescription(pack != null ? pack.getName() + " · " + when : when);
        }
        if (req != null && StringUtils.hasText(req.paymentMethod())) {
            transaction.setPaymentMethod(req.paymentMethod());
        }
        if (req != null && StringUtils.hasText(req.documentNumber())) {
            transaction.setDocumentNumber(req.documentNumber().trim());
        }
        transaction.recordPayment(date);
        transactionRepository.save(transaction);

        log.info("Pagamento registrado pela consulta: consulta={} lançamento={} valor={}",
                appointmentId, transaction.getId(), transaction.getValue());
        return single(transaction);
    }

    @Transactional
    public FinanceDtos.TransactionResponse refund(Long id) {
        FinanceTransaction transaction = accountRequire(id);
        transaction.refund();
        return single(transaction);
    }

    @Transactional
    public FinanceDtos.TransactionResponse cancel(Long id) {
        FinanceTransaction transaction = accountRequire(id);
        transaction.cancel();
        return single(transaction);
    }

    @Transactional
    public void remove(Long id) {
        transactionRepository.delete(accountRequire(id));
    }

    // --------------------------------------------------------------- settlement

    /**
     * Settles the period by accrual date.
     *
     * It returns settled and expected separately. The settled considers only
     * what was paid; the expected includes what is still pending. A single
     * number would mix money that came in with money that might.
     */
    @Transactional(readOnly = true)
    public FinanceDtos.SummaryResponse settle(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("O fim do período não pode ser anterior ao início");
        }
        Long accountId = contextCurrent.accountId();
        List<FinanceTransaction> transactions = transactionRepository.forAccrual(accountId, from, to);

        BigDecimal received = ZERO;
        BigDecimal receive = ZERO;
        BigDecimal expensesPaid = ZERO;
        BigDecimal expensesPay = ZERO;

        Map<String, FinanceDtos.TotalByCategory> categories = new java.util.LinkedHashMap<>();

        for (FinanceTransaction l : transactions) {
            if (l.getStatus() == TransactionStatus.CANCELED) {
                continue;
            }
            boolean paid = l.entersNoBox();
            if (l.getType().isIncome()) {
                if (paid) received = received.add(l.getValue());
                else receive = receive.add(l.getValue());
            } else {
                if (paid) expensesPaid = expensesPaid.add(l.getValue());
                else expensesPay = expensesPay.add(l.getValue());
            }

            String key = l.getType() + "|" + l.getCategory();
            categories.merge(key,
                    new FinanceDtos.TotalByCategory(l.getCategory(), l.getType(), l.getValue(), 1),
                    (a, b) -> new FinanceDtos.TotalByCategory(
                            a.category(), a.type(), a.total().add(b.total()),
                            a.transactions() + b.transactions()));
        }

        BigDecimal efetivado = received.subtract(expensesPaid);
        BigDecimal expected = received.add(receive).subtract(expensesPaid).subtract(expensesPay);

        return new FinanceDtos.SummaryResponse(
                from, to, received, receive, expensesPaid, expensesPay,
                efetivado, expected,
                (int) transactions.stream().filter(FinanceTransaction::entersNoExpected).count(),
                List.copyOf(categories.values()));
    }

    // ------------------------------------------------------------------- receipt

    /**
     * Receipt for a paid transaction.
     *
     * A receipt for an amount not received would be a false statement, so only
     * a settled transaction produces one.
     */
    @Transactional(readOnly = true)
    public FinanceDtos.ReceiptResponse receipt(Long id) {
        FinanceTransaction transaction = accountRequire(id);

        if (transaction.getStatus() != TransactionStatus.PAID) {
            throw new BusinessRuleException(
                    "Só é possível emitir recibo de lançamento pago.");
        }
        if (!transaction.getType().isIncome()) {
            throw new BusinessRuleException("Recibo se emite sobre receita, não sobre despesa.");
        }

        Account account = accountRepository.findById(transaction.getAccountId()).orElse(null);
        User profissional = userRepository
                .findFirstByAccountIdAndRoleAndActiveTrue(transaction.getAccountId(), Role.NUTRITIONIST)
                .orElse(null);
        String payer = transaction.getPatientId() == null ? null
                : patientRepository.findById(transaction.getPatientId())
                        .map(Patient::getName).orElse(null);

        String related = transaction.getDescription() != null
                ? transaction.getDescription() : transaction.getCategory();
        String installment = transaction.installmentLabel();
        if (installment != null) {
            related = related + " · parcela " + installment;
        }

        return new FinanceDtos.ReceiptResponse(
                transaction.getId(),
                account == null ? null : account.getName(),
                profissional == null ? null : profissional.getName(),
                profissional == null ? null : profissional.getCrn(),
                payer,
                transaction.getValue(),
                ValueByWords.inBrl(transaction.getValue()),
                transaction.getDatePayment(),
                related,
                LocalDate.now().toString(),
                transaction.getDocumentNumber(),
                installment);
    }

    // ------------------------------------------------------------------- apoio

    private static void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "A data de pagamento não pode ser futura: o valor ainda não entrou.");
        }
    }

    /** A categoria que a agenda sugere para a receita de uma consulta. */
    private static String categoryOf(AppointmentType type) {
        return switch (type) {
            case FIRST_CONSULTATION, COUNSELING -> "Consulta";
            case FOLLOWUP -> "Retorno";
            case ASSESSMENT -> "Avaliação";
            case OTHER -> "Outros";
        };
    }

    private void apply(FinanceDtos.TransactionRequest req, FinanceTransaction transaction) {
        transaction.setDue(req.due());
        transaction.setPaymentMethod(req.paymentMethod());
        transaction.setDescription(req.description());
        transaction.setPatientId(req.patientId());
        transaction.setAppointmentId(req.appointmentId());
        transaction.setDocumentNumber(
                StringUtils.hasText(req.documentNumber()) ? req.documentNumber().trim() : null);
        transaction.setPackageId(req.packageId());
    }

    /** Os nomes que a resposta mostra no lugar de ids. */
    private record Names(Map<Long, String> patients, Map<Long, String> packages) {}

    private FinanceDtos.TransactionResponse single(FinanceTransaction transaction) {
        return build(transaction, namesOf(List.of(transaction)), LocalDate.now());
    }

    private FinanceDtos.TransactionResponse build(FinanceTransaction l,
                                                     Names names,
                                                     LocalDate reference) {
        return new FinanceDtos.TransactionResponse(
                l.getId(), l.getType(), l.getType().getDescription(),
                l.getStatus(), l.getStatus().getDescription(),
                l.getValue(), l.getAccrual(), l.getDue(), l.getDatePayment(),
                l.getCategory(), l.getPaymentMethod(), l.getDescription(),
                // Map.of() throws an NPE on a null key, and a transaction with no
                // patient has a null patientId — hence the check before the
                // query.
                l.getPatientId(),
                l.getPatientId() == null ? null : names.patients().get(l.getPatientId()),
                l.getAppointmentId(),
                l.estaOverdueAt(reference),
                l.getDocumentNumber(),
                l.getInstallmentIndex(), l.getInstallmentCount(), l.getInstallmentGroup(),
                l.getPackageId(),
                l.getPackageId() == null ? null : names.packages().get(l.getPackageId()));
    }

    private Names namesOf(List<FinanceTransaction> transactions) {
        Map<Long, String> patients = new HashMap<>();
        List<Long> patientIds = transactions.stream()
                .map(FinanceTransaction::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!patientIds.isEmpty()) {
            patientRepository.findAllById(patientIds).forEach(p -> patients.put(p.getId(), p.getName()));
        }
        Map<Long, String> packages = new HashMap<>();
        List<Long> packageIds = transactions.stream()
                .map(FinanceTransaction::getPackageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!packageIds.isEmpty()) {
            packageRepository.findAllById(packageIds).forEach(p -> packages.put(p.getId(), p.getName()));
        }
        return new Names(patients, packages);
    }

    private Patient requirePatient(Long patientId, Long accountId) {
        return patientRepository.findByIdAndAccountId(patientId, accountId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }

    private ServicePackage requirePackage(Long packageId, Long accountId) {
        if (packageId == null) {
            return null;
        }
        return packageRepository.findByIdAndAccountId(packageId, accountId)
                .orElseThrow(() -> new NotFoundException("Pacote", packageId));
    }
}
