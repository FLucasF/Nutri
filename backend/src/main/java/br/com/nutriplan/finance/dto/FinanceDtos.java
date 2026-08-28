package br.com.nutriplan.finance.dto;

import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.finance.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Input and output contracts of the finance module. */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record TransactionRequest(
            @NotNull TransactionType type,
            @NotNull @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
            BigDecimal value,
            @NotNull LocalDate accrual,
            LocalDate due,
            @NotBlank @Size(max = 80) String category,
            @Size(max = 40) String paymentMethod,
            @Size(max = 500) String description,
            Long patientId,
            Long appointmentId
    ) {}

    public record PaymentRequest(LocalDate datePayment) {}

    public record TransactionResponse(
            Long id,
            TransactionType type,
            String typeDescription,
            TransactionStatus status,
            String statusDescription,
            BigDecimal value,
            LocalDate accrual,
            LocalDate due,
            LocalDate datePayment,
            String category,
            String paymentMethod,
            String description,
            Long patientId,
            String patientName,
            Long appointmentId,
            boolean overdue
    ) {}

    /**
     * Settlement of a period.
     *
     * Settled and expected come apart on purpose: adding up what has not
     * arrived yet would make the practice look like it has money it does not.
     */
    public record SummaryResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal totalReceived,
            BigDecimal totalReceive,
            BigDecimal expensesPaid,
            BigDecimal expensesPay,
            BigDecimal resultEfetivado,
            BigDecimal resultExpected,
            int transactions,
            List<TotalByCategory> byCategory
    ) {}

    public record TotalByCategory(
            String category,
            TransactionType type,
            BigDecimal total,
            int transactions
    ) {}

    /** Receipt for a paid transaction. */
    public record ReceiptResponse(
            Long transactionId,
            String practiceName,
            String profissionalName,
            String profissionalCrn,
            String payerName,
            BigDecimal value,
            String valueByWords,
            LocalDate datePayment,
            String related,
            String emitidoAt
    ) {}
}
