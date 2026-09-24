package br.com.nutriplan.finance.domain;

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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A financial transaction of the practice.
 *
 * The amount is always positive. What tells income from expense is the
 * {@link TransactionType}, and not the sign — storing an expense as a negative
 * number would spread the rule across every sum in the system, and one slip
 * would be enough to turn an expense into income.
 *
 * The three dates have distinct roles and are not interchangeable: the accrual
 * date says which month the transaction belongs to, the due date says when it
 * should have been paid, and the payment date says when it actually came in. It
 * is that separation that makes it possible to settle on a cash basis and on an
 * accrual basis without mixing one with the other.
 */
@Entity
@Table(name = "finance_transaction", indexes = {
        @Index(name = "ix_transaction_account", columnList = "account_id, accrual"),
        @Index(name = "ix_transaction_patient", columnList = "patient_id"),
        @Index(name = "ix_transaction_due", columnList = "account_id, status, due")
})
@Getter
@Setter
@NoArgsConstructor
public class FinanceTransaction extends AccountEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /** Month the transaction belongs to, regardless of when it is paid. */
    @Column(nullable = false)
    private LocalDate accrual;

    @Column
    private LocalDate due;

    /** Filled in on settlement. Null while pending. */
    @Column(name = "payment_date")
    private LocalDate datePayment;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "payment_form", length = 40)
    private String paymentMethod;

    @Column(length = 500)
    private String description;

    /** Optional: not every income comes from a patient, nor every expense. */
    @Column(name = "patient_id")
    private Long patientId;

    /** Optional: it ties the charge to the appointment that produced it. */
    @Column(name = "appointment_id")
    private Long appointmentId;

    /** Número do recibo ou da nota emitida fora do sistema, quando há. */
    @Column(name = "document_number", length = 60)
    private String documentNumber;

    /** As parcelas de um mesmo parcelamento compartilham o grupo. */
    @Column(name = "installment_group", length = 36)
    private String installmentGroup;

    @Column(name = "installment_index")
    private Integer installmentIndex;

    @Column(name = "installment_count")
    private Integer installmentCount;

    /** Pacote de trabalho que originou a receita. */
    @Column(name = "package_id")
    private Long packageId;

    /** "2/6" quando o lançamento é uma parcela; nulo quando é à vista. */
    public String installmentLabel() {
        return installmentIndex == null || installmentCount == null
                ? null
                : installmentIndex + "/" + installmentCount;
    }

    public FinanceTransaction(Long accountId, TransactionType type,
                                BigDecimal value, LocalDate accrual, String category) {
        setAccountId(accountId);
        this.type = type;
        this.value = value;
        this.accrual = accrual;
        this.category = category;
    }

    /** Settles the transaction on the reported date. */
    public void recordPayment(LocalDate date) {
        if (status == TransactionStatus.CANCELED) {
            throw new BusinessRuleException(
                    "Um lançamento cancelado não pode ser marcado como pago.");
        }
        this.status = TransactionStatus.PAID;
        this.datePayment = date != null ? date : LocalDate.now();
    }

    public void refund() {
        if (status != TransactionStatus.PAID) {
            throw new BusinessRuleException("Só é possível estornar lançamento pago.");
        }
        this.status = TransactionStatus.PENDING;
        this.datePayment = null;
    }

    public void cancel() {
        if (status == TransactionStatus.PAID) {
            throw new BusinessRuleException(
                    "Estorne o pagamento antes de cancelar o lançamento.");
        }
        this.status = TransactionStatus.CANCELED;
    }

    /** Overdue is what passed the date and is still unpaid. */
    public boolean estaOverdueAt(LocalDate reference) {
        return status == TransactionStatus.PENDING
                && due != null
                && due.isBefore(reference);
    }

    public boolean entersNoBox() {
        return status == TransactionStatus.PAID;
    }

    public boolean entersNoExpected() {
        return status != TransactionStatus.CANCELED;
    }
}
