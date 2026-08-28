package br.com.nutriplan.questionnaire.web;

import br.com.nutriplan.questionnaire.dto.QuestionnaireDtos;
import br.com.nutriplan.questionnaire.service.QuestionnaireService;
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
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;

    // ---------------------------------------------------------------- library

    @GetMapping("/questionnaires")
    @Operation(summary = "Lista os questionários visíveis para o consultório")
    public List<QuestionnaireDtos.QuestionnaireResponse> list() {
        return questionnaireService.list();
    }

    @GetMapping("/questionnaires/{id}")
    @Operation(summary = "Detalha um questionário com as perguntas")
    public QuestionnaireDtos.QuestionnaireResponse detail(@PathVariable Long id) {
        return questionnaireService.detail(id);
    }

    @PostMapping("/questionnaires")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um questionário próprio")
    public QuestionnaireDtos.QuestionnaireResponse create(
            @Valid @RequestBody QuestionnaireDtos.QuestionnaireRequest req) {
        return questionnaireService.create(req);
    }

    @PostMapping("/questionnaires/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cópia o questionário para a biblioteca do consultório")
    public QuestionnaireDtos.QuestionnaireResponse duplicate(@PathVariable Long id) {
        return questionnaireService.duplicate(id);
    }

    @PutMapping("/questionnaires/{id}")
    @Operation(summary = "Substitui o questionário",
            description = "Incrementa a versão do modelo. As respostas já recebidas guardam a "
                    + "versão que responderam, e continuam exibindo as perguntas de então.")
    public QuestionnaireDtos.QuestionnaireResponse update(
            @PathVariable Long id, @Valid @RequestBody QuestionnaireDtos.QuestionnaireRequest req) {
        return questionnaireService.update(id, req);
    }

    @DeleteMapping("/questionnaires/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa o questionário")
    public void remove(@PathVariable Long id) {
        questionnaireService.remove(id);
    }

    // ---------------------------------------------------------------- sending

    @GetMapping("/patients/{patientId}/questionnaires")
    @Operation(summary = "Questionários enviados ao paciente, respondidos ou não")
    public List<QuestionnaireDtos.AnswerResponse> forPatient(@PathVariable Long patientId) {
        return questionnaireService.forPatient(patientId);
    }

    @PostMapping("/patients/{patientId}/questionnaires")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia um questionário ao paciente",
            description = "Devolve o identificador público do formulário. O paciente responde "
                    + "por esse link, sem conta — o mesmo mecanismo do plano.")
    public QuestionnaireDtos.AnswerResponse send(
            @PathVariable Long patientId,
            @Valid @RequestBody QuestionnaireDtos.SendingRequest req) {
        return questionnaireService.send(patientId, req);
    }

    @GetMapping("/schedule/{appointmentId}/questionnaires")
    @Operation(summary = "Respostas ligadas a um atendimento",
            description = "É o que a tela da consulta mostra, para o profissional chegar com a "
                    + "leitura feita.")
    public List<QuestionnaireDtos.AnswerResponse> forAppointment(
            @PathVariable Long appointmentId) {
        return questionnaireService.forAppointment(appointmentId);
    }

    @DeleteMapping("/questionnaires/sendings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela um envio ainda não respondido")
    public void cancelSending(@PathVariable Long id) {
        questionnaireService.cancelSending(id);
    }
}
