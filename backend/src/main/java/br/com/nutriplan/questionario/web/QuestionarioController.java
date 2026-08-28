package br.com.nutriplan.questionario.web;

import br.com.nutriplan.questionario.dto.QuestionarioDtos;
import br.com.nutriplan.questionario.service.QuestionarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Questionários")
public class QuestionarioController {

    private final QuestionarioService questionarioService;

    // ------------------------------------------------------------- biblioteca

    @GetMapping("/questionarios")
    @Operation(summary = "Lista os questionários visíveis para o consultório")
    public List<QuestionarioDtos.QuestionarioResponse> listar() {
        return questionarioService.listar();
    }

    @GetMapping("/questionarios/{id}")
    @Operation(summary = "Detalha um questionário com as perguntas")
    public QuestionarioDtos.QuestionarioResponse detalhar(@PathVariable Long id) {
        return questionarioService.detalhar(id);
    }

    @PostMapping("/questionarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um questionário próprio")
    public QuestionarioDtos.QuestionarioResponse criar(
            @Valid @RequestBody QuestionarioDtos.QuestionarioRequest req) {
        return questionarioService.criar(req);
    }

    @PostMapping("/questionarios/{id}/duplicar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cópia o questionário para a biblioteca do consultório")
    public QuestionarioDtos.QuestionarioResponse duplicar(@PathVariable Long id) {
        return questionarioService.duplicar(id);
    }

    @PutMapping("/questionarios/{id}")
    @Operation(summary = "Substitui o questionário",
            description = "Incrementa a versão do modelo. As respostas já recebidas guardam a "
                    + "versão que responderam, e continuam exibindo as perguntas de então.")
    public QuestionarioDtos.QuestionarioResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody QuestionarioDtos.QuestionarioRequest req) {
        return questionarioService.atualizar(id, req);
    }

    @DeleteMapping("/questionarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa o questionário")
    public void remover(@PathVariable Long id) {
        questionarioService.remover(id);
    }

    // ------------------------------------------------------------------ envio

    @GetMapping("/pacientes/{pacienteId}/questionarios")
    @Operation(summary = "Questionários enviados ao paciente, respondidos ou não")
    public List<QuestionarioDtos.RespostaResponse> doPaciente(@PathVariable Long pacienteId) {
        return questionarioService.doPaciente(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/questionarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia um questionário ao paciente",
            description = "Devolve o identificador público do formulário. O paciente responde "
                    + "por esse link, sem conta — o mesmo mecanismo do plano.")
    public QuestionarioDtos.RespostaResponse enviar(
            @PathVariable Long pacienteId,
            @Valid @RequestBody QuestionarioDtos.EnvioRequest req) {
        return questionarioService.enviar(pacienteId, req);
    }

    @GetMapping("/agenda/{agendamentoId}/questionarios")
    @Operation(summary = "Respostas ligadas a um atendimento",
            description = "É o que a tela da consulta mostra, para o profissional chegar com a "
                    + "leitura feita.")
    public List<QuestionarioDtos.RespostaResponse> doAgendamento(
            @PathVariable Long agendamentoId) {
        return questionarioService.doAgendamento(agendamentoId);
    }

    @DeleteMapping("/questionarios/envios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela um envio ainda não respondido")
    public void cancelarEnvio(@PathVariable Long id) {
        questionarioService.cancelarEnvio(id);
    }
}
