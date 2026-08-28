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
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceTransactionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final FinanceTransactionRepository transactionRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
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

        Map<Long, String> names = patientsNames(page.getContent());
        LocalDate today = LocalDate.now();
        return page.map(l -> build(l, names, today));
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.TransactionResponse> overdue(LocalDate reference) {
        Long accountId = contextCurrent.accountId();
        LocalDate date = reference != null ? reference : LocalDate.now();

        List<FinanceTransaction> transactions = transactionRepository.overdueAt(accountId, date);
        Map<Long, String> names = patientsNames(transactions);
        return transactions.stream().map(l -> build(l, names, date)).toList();
    }

    @Transactional(readOnly = true)
    public FinanceDtos.TransactionResponse detail(Long id) {
        FinanceTransaction transaction = accountRequire(id);
        return build(transaction, patientsNames(List.of(transaction)), LocalDate.now());
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

        var transaction = new FinanceTransaction(
                accountId, req.type(), req.value(), req.accrual(), req.category());
        apply(req, transaction);
        transactionRepository.save(transaction);

        log.info("Lançamento criado: id={} tipo={} valor={} conta={}",
                transaction.getId(), req.type(), req.value(), accountId);
        return build(transaction, patientsNames(List.of(transaction)), LocalDate.now());
    }

    @Transactional
    public FinanceDtos.TransactionResponse update(Long id,
                                                       FinanceDtos.TransactionRequest req) {
        FinanceTransaction transaction = accountRequire(id);
        if (req.patientId() != null) {
            requirePatient(req.patientId(), transaction.getAccountId());
        }
        transaction.setType(req.type());
        transaction.setValue(req.value());
        transaction.setAccrual(req.accrual());
        transaction.setCategory(req.category());
        apply(req, transaction);

        return build(transaction, patientsNames(List.of(transaction)), LocalDate.now());
    }

    @Transactional
    public FinanceDtos.TransactionResponse recordPayment(Long id,
                                                                FinanceDtos.PaymentRequest req) {
        FinanceTransaction transaction = accountRequire(id);
        LocalDate date = req != null && req.datePayment() != null
                ? req.datePayment()
                : LocalDate.now();

        if (date.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "A data de pagamento não pode ser futura: o valor ainda não entrou.");
        }
        transaction.recordPayment(date);

        log.info("Pagamento registrado: lançamento={} data={}", id, date);
        return build(transaction, patientsNames(List.of(transaction)), LocalDate.now());
    }

    @Transactional
    public FinanceDtos.TransactionResponse refund(Long id) {
        FinanceTransaction transaction = accountRequire(id);
        transaction.refund();
        return build(transaction, patientsNames(List.of(transaction)), LocalDate.now());
    }

    @Transactional
    public FinanceDtos.TransactionResponse cancel(Long id) {
        FinanceTransaction transaction = accountRequire(id);
        transaction.cancel();
        return build(transaction, patientsNames(List.of(transaction)), LocalDate.now());
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

        return new FinanceDtos.ReceiptResponse(
                transaction.getId(),
                account == null ? null : account.getName(),
                profissional == null ? null : profissional.getName(),
                profissional == null ? null : profissional.getCrn(),
                payer,
                transaction.getValue(),
                ValueByWords.inBrl(transaction.getValue()),
                transaction.getDatePayment(),
                transaction.getDescription() != null ? transaction.getDescription() : transaction.getCategory(),
                LocalDate.now().toString());
    }

    // ------------------------------------------------------------------- apoio

    private void apply(FinanceDtos.TransactionRequest req, FinanceTransaction transaction) {
        transaction.setDue(req.due());
        transaction.setPaymentMethod(req.paymentMethod());
        transaction.setDescription(req.description());
        transaction.setPatientId(req.patientId());
        transaction.setAppointmentId(req.appointmentId());
    }

    private FinanceDtos.TransactionResponse build(FinanceTransaction l,
                                                     Map<Long, String> names,
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
                l.getPatientId() == null ? null : names.get(l.getPatientId()),
                l.getAppointmentId(),
                l.estaOverdueAt(reference));
    }

    private Map<Long, String> patientsNames(List<FinanceTransaction> transactions) {
        List<Long> ids = transactions.stream()
                .map(FinanceTransaction::getPatientId)
                .filter(java.util.Objects::nonNull)
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
}
