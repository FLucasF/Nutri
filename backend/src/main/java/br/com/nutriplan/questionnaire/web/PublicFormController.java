package br.com.nutriplan.questionnaire.web;

import br.com.nutriplan.questionnaire.dto.QuestionnaireDtos;
import br.com.nutriplan.questionnaire.service.QuestionnaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The form as the patient answers it.
 *
 * Without authentication: the authorization is possession of the identifier, a
 * UUID. It is the same design as the public plan, including the consequence —
 * the answer contract simply has no field for a patient name or an account
 * identifier, and the protection is in the shape, and not in remembering to
 * filter on every change.
 */
@RestController
@RequestMapping("/api/public/questionnaires")
@RequiredArgsConstructor
@Tag(name = "Questionário do paciente")
public class PublicFormController {

    private final QuestionnaireService questionnaireService;

    @GetMapping("/{identifier}")
    @Operation(summary = "Abre o formulário pelo link recebido")
    public QuestionnaireDtos.PublicFormResponse open(@PathVariable String identifier) {
        return questionnaireService.form(identifier);
    }

    @PostMapping("/{identifier}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Envia as respostas",
            description = "Aceita uma vez só. Falta de resposta obrigatória é recusada dizendo "
                    + "qual falta, em vez de pontuar pela metade.")
    public void answer(@PathVariable String identifier,
                          @Valid @RequestBody QuestionnaireDtos.FillingRequest req) {
        questionnaireService.preencher(identifier, req);
    }
}
