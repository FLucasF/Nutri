package br.com.nutriplan.financeiro.web;

import br.com.nutriplan.financeiro.domain.SituacaoLancamento;
import br.com.nutriplan.financeiro.domain.TipoLancamento;
import br.com.nutriplan.financeiro.dto.FinanceiroDtos;
import br.com.nutriplan.financeiro.service.LancamentoFinanceiroService;
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
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
@Tag(name = "Financeiro")
public class FinanceiroController {

    private final LancamentoFinanceiroService lancamentoService;

    @GetMapping("/lancamentos")
    @Operation(summary = "Lista lançamentos por competência, tipo, situação ou paciente")
    public Page<FinanceiroDtos.LancamentoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) TipoLancamento tipo,
            @RequestParam(required = false) SituacaoLancamento situacao,
            @RequestParam(required = false) Long pacienteId,
            @PageableDefault(size = 30) Pageable pageable) {
        return lancamentoService.listar(de, ate, tipo, situacao, pacienteId, pageable);
    }

    @GetMapping("/vencidos")
    @Operation(summary = "Lançamentos pendentes já vencidos",
            description = "A data de referência vem do cliente para que o relatório seja "
                    + "reproduzível: apurar os vencidos de uma data deve dar o mesmo "
                    + "resultado hoje e daqui a um mês.")
    public List<FinanceiroDtos.LancamentoResponse> vencidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate referencia) {
        return lancamentoService.vencidos(referencia);
    }

    @GetMapping("/apuracao")
    @Operation(summary = "Apura o resultado de um período",
            description = "Separa efetivado de previsto: somar o que ainda não entrou faria "
                    + "o consultório parecer ter dinheiro que não tem.")
    public FinanceiroDtos.ApuracaoResponse apurar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return lancamentoService.apurar(de, ate);
    }

    @GetMapping("/lancamentos/{id}")
    @Operation(summary = "Detalha um lançamento")
    public FinanceiroDtos.LancamentoResponse detalhar(@PathVariable Long id) {
        return lancamentoService.detalhar(id);
    }

    @GetMapping("/lancamentos/{id}/recibo")
    @Operation(summary = "Emite o recibo de um lançamento pago")
    public FinanceiroDtos.ReciboResponse recibo(@PathVariable Long id) {
        return lancamentoService.recibo(id);
    }

    @PostMapping("/lancamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra um lançamento")
    public FinanceiroDtos.LancamentoResponse criar(
            @Valid @RequestBody FinanceiroDtos.LancamentoRequest req) {
        return lancamentoService.criar(req);
    }

    @PutMapping("/lancamentos/{id}")
    @Operation(summary = "Atualiza um lançamento")
    public FinanceiroDtos.LancamentoResponse atualizar(
            @PathVariable Long id,
            @Valid @RequestBody FinanceiroDtos.LancamentoRequest req) {
        return lancamentoService.atualizar(id, req);
    }

    @PostMapping("/lancamentos/{id}/pagar")
    @Operation(summary = "Registra o recebimento ou pagamento")
    public FinanceiroDtos.LancamentoResponse pagar(
            @PathVariable Long id,
            @RequestBody(required = false) FinanceiroDtos.PagamentoRequest req) {
        return lancamentoService.registrarPagamento(id, req);
    }

    @PostMapping("/lancamentos/{id}/estornar")
    @Operation(summary = "Desfaz o pagamento, devolvendo o lançamento a pendente")
    public FinanceiroDtos.LancamentoResponse estornar(@PathVariable Long id) {
        return lancamentoService.estornar(id);
    }

    @PostMapping("/lancamentos/{id}/cancelar")
    @Operation(summary = "Cancela um lançamento pendente")
    public FinanceiroDtos.LancamentoResponse cancelar(@PathVariable Long id) {
        return lancamentoService.cancelar(id);
    }

    @DeleteMapping("/lancamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um lançamento")
    public void remover(@PathVariable Long id) {
        lancamentoService.remover(id);
    }
}
