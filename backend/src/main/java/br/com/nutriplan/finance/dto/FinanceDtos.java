package br.com.nutriplan.finance.dto;

import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.finance.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            Long appointmentId,
            /** Número do recibo ou da nota emitida fora do sistema, quando há. */
            @Size(max = 60) String documentNumber,
            /** Pacote de trabalho que originou a receita. */
            Long packageId,
            /**
             * Em quantas parcelas dividir o valor. Só vale ao criar: cada
             * parcela vira um lançamento, na competência do seu mês. Nulo ou 1
             * é à vista.
             */
            @Min(value = 1, message = "O parcelamento mínimo é de uma parcela")
            @Max(value = 48, message = "O parcelamento máximo é de 48 parcelas")
            Integer installments
    ) {
        public int installmentsOrOne() {
            return installments == null || installments < 1 ? 1 : installments;
        }
    }

    public record PaymentRequest(LocalDate datePayment) {}

    /**
     * Pagamento registrado a partir da consulta.
     *
     * O valor vem do pacote da consulta quando não informado; sem pacote e
     * sem valor, o pedido é recusado — não se registra recebimento de
     * quantia desconhecida.
     */
    public record AppointmentPaymentRequest(
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
            BigDecimal value,
            @Size(max = 40) String paymentMethod,
            LocalDate datePayment,
            @Size(max = 60) String documentNumber,
            @Size(max = 80) String category
    ) {}

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
            boolean overdue,
            String documentNumber,
            Integer installmentIndex,
            Integer installmentCount,
            String installmentGroup,
            Long packageId,
            String packageName
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
            String emitidoAt,
            String documentNumber,
            /** "2/6" quando o lançamento é uma parcela. */
            String installment
    ) {}
}
