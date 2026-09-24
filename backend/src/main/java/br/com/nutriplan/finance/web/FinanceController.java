package br.com.nutriplan.finance.web;

import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.finance.domain.TransactionType;
import br.com.nutriplan.finance.dto.FinanceDtos;
import br.com.nutriplan.finance.service.FinanceTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@Tag(name = "Financeiro")
public class FinanceController {

    private final FinanceTransactionService transactionService;

    @GetMapping("/transactions")
    @Operation(summary = "Lista lançamentos por competência, tipo, situação ou paciente")
    public Page<FinanceDtos.TransactionResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) Long patientId,
            @PageableDefault(size = 30) Pageable pageable) {
        return transactionService.list(from, to, type, status, patientId, pageable);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Lançamentos pendentes já vencidos",
            description = "A data de referência vem do cliente para que o relatório seja "
                    + "reproduzível: apurar os vencidos de uma data deve dar o mesmo "
                    + "resultado hoje e daqui a um mês.")
    public List<FinanceDtos.TransactionResponse> overdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reference) {
        return transactionService.overdue(reference);
    }

    @GetMapping("/summary")
    @Operation(summary = "Apura o resultado de um período",
            description = "Separa efetivado de previsto: somar o que ainda não entrou faria "
                    + "o consultório parecer ter dinheiro que não tem.")
    public FinanceDtos.SummaryResponse settle(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return transactionService.settle(from, to);
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Detalha um lançamento")
    public FinanceDtos.TransactionResponse detail(@PathVariable Long id) {
        return transactionService.detail(id);
    }

    @GetMapping("/transactions/{id}/receipt")
    @Operation(summary = "Emite o recibo de um lançamento pago")
    public FinanceDtos.ReceiptResponse receipt(@PathVariable Long id) {
        return transactionService.receipt(id);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra um lançamento",
            description = "Com parcelas, cria um lançamento por parcela, cada um na "
                    + "competência do seu mês, e devolve o primeiro.")
    public FinanceDtos.TransactionResponse create(
            @Valid @RequestBody FinanceDtos.TransactionRequest req) {
        return transactionService.create(req);
    }

    @PostMapping("/appointments/{appointmentId}/payment")
    @Operation(summary = "Registra o pagamento de uma consulta",
            description = "Quita a cobrança pendente da consulta ou, sem cobrança, cria a "
                    + "receita já paga, ligada à consulta, ao paciente e ao pacote. O valor "
                    + "vem do pacote quando não informado.")
    public FinanceDtos.TransactionResponse payAppointment(
            @PathVariable Long appointmentId,
            @Valid @RequestBody(required = false) FinanceDtos.AppointmentPaymentRequest req) {
        return transactionService.payAppointment(appointmentId, req);
    }

    @PutMapping("/transactions/{id}")
    @Operation(summary = "Atualiza um lançamento")
    public FinanceDtos.TransactionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody FinanceDtos.TransactionRequest req) {
        return transactionService.update(id, req);
    }

    @PostMapping("/transactions/{id}/pay")
    @Operation(summary = "Registra o recebimento ou pagamento")
    public FinanceDtos.TransactionResponse pay(
            @PathVariable Long id,
            @RequestBody(required = false) FinanceDtos.PaymentRequest req) {
        return transactionService.recordPayment(id, req);
    }

    @PostMapping("/transactions/{id}/refund")
    @Operation(summary = "Desfaz o pagamento, devolvendo o lançamento a pendente")
    public FinanceDtos.TransactionResponse refund(@PathVariable Long id) {
        return transactionService.refund(id);
    }

    @PostMapping("/transactions/{id}/cancel")
    @Operation(summary = "Cancela um lançamento pendente")
    public FinanceDtos.TransactionResponse cancel(@PathVariable Long id) {
        return transactionService.cancel(id);
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um lançamento")
    public void remove(@PathVariable Long id) {
        transactionService.remove(id);
    }
}
