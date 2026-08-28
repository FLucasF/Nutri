package br.com.nutriplan.agenda.web;

import br.com.nutriplan.agenda.domain.SituacaoAtendimento;
import br.com.nutriplan.agenda.domain.TipoAtendimento;
import br.com.nutriplan.agenda.dto.AgendaDtos;
import br.com.nutriplan.agenda.service.AgendamentoService;
import br.com.nutriplan.agenda.service.AssinaturaDaAgendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/agenda")
@RequiredArgsConstructor
@Tag(name = "Agenda")
public class AgendaController {

    private final AgendamentoService agendamentoService;
    private final AssinaturaDaAgendaService assinaturaService;

    @GetMapping("/tipos")
    @Operation(summary = "Lista os tipos de atendimento e a duração que cada um sugere")
    public List<AgendaDtos.TipoResponse> tipos() {
        return Arrays.stream(TipoAtendimento.values())
                .map(t -> new AgendaDtos.TipoResponse(
                        t, t.getDescricao(), t.getDuracaoSugeridaMinutos()))
                .toList();
    }

    @GetMapping("/dia")
    @Operation(summary = "Agenda de um dia, com o resumo de realizados e faltas")
    public AgendaDtos.DiaDaAgendaResponse doDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return agendamentoService.doDia(data);
    }

    @GetMapping
    @Operation(summary = "Atendimentos de um período")
    public List<AgendaDtos.AgendamentoResponse> naFaixa(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) SituacaoAtendimento situacao) {
        return agendamentoService.naFaixa(de, ate, situacao);
    }

    @GetMapping("/paciente/{pacienteId}")
    @Operation(summary = "Histórico de atendimentos de um paciente, incluindo faltas")
    public List<AgendaDtos.AgendamentoResponse> doPaciente(@PathVariable Long pacienteId) {
        return agendamentoService.doPaciente(pacienteId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um atendimento")
    public AgendaDtos.AgendamentoResponse detalhar(@PathVariable Long id) {
        return agendamentoService.detalhar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agenda um atendimento",
            description = "Recusa horário que se sobreponha a outro atendimento não cancelado.")
    public AgendaDtos.AgendamentoResponse agendar(
            @Valid @RequestBody AgendaDtos.AgendamentoRequest req) {
        return agendamentoService.agendar(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Remarca um atendimento")
    public AgendaDtos.AgendamentoResponse remarcar(
            @PathVariable Long id,
            @Valid @RequestBody AgendaDtos.AgendamentoRequest req) {
        return agendamentoService.remarcar(id, req);
    }

    @PostMapping("/{id}/situacao")
    @Operation(summary = "Registra o desfecho do atendimento",
            description = "Situações terminais não retrocedem: um atendimento realizado "
                    + "não volta a agendado.")
    public AgendaDtos.AgendamentoResponse mudarSituacao(
            @PathVariable Long id,
            @Valid @RequestBody AgendaDtos.MudancaDeSituacaoRequest req) {
        return agendamentoService.mudarSituacao(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um atendimento")
    public void remover(@PathVariable Long id) {
        agendamentoService.remover(id);
    }

    // ------------------------------------------------------ assinatura (ics)

    @GetMapping("/assinatura")
    @Operation(summary = "Endereço da assinatura iCalendar da agenda",
            description = "Vazio enquanto não gerado. Quem recebe o endereço vê a agenda, "
                    + "com nome de paciente — a mesma autorização por posse de link do plano.")
    public AssinaturaDaAgendaService.Assinatura assinatura() {
        return assinaturaService.atual();
    }

    @PostMapping("/assinatura")
    @Operation(summary = "Gera ou regenera a assinatura",
            description = "Regerar invalida o endereço que já foi entregue ao calendário.")
    public AssinaturaDaAgendaService.Assinatura gerarAssinatura() {
        return assinaturaService.gerar();
    }

    @DeleteMapping("/assinatura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desliga a assinatura")
    public void revogarAssinatura() {
        assinaturaService.revogar();
    }
}
