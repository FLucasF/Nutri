package br.com.nutriplan.questionario.web;

import br.com.nutriplan.questionario.dto.QuestionarioDtos;
import br.com.nutriplan.questionario.service.QuestionarioService;
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
 * O formulário como o paciente o responde.
 *
 * Sem autenticação: a autorização é a posse do identificador, um UUID. É o
 * mesmo desenho do plano público, inclusive na consequência — o contrato de
 * resposta simplesmente não tem campo para nome de paciente nem para
 * identificador de conta, e a proteção está na forma, e não em lembrar de
 * filtrar a cada alteração.
 */
@RestController
@RequestMapping("/api/publico/questionarios")
@RequiredArgsConstructor
@Tag(name = "Questionário do paciente")
public class FormularioPublicoController {

    private final QuestionarioService questionarioService;

    @GetMapping("/{identificador}")
    @Operation(summary = "Abre o formulário pelo link recebido")
    public QuestionarioDtos.FormularioPublicoResponse abrir(@PathVariable String identificador) {
        return questionarioService.formulario(identificador);
    }

    @PostMapping("/{identificador}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Envia as respostas",
            description = "Aceita uma vez só. Falta de resposta obrigatória é recusada dizendo "
                    + "qual falta, em vez de pontuar pela metade.")
    public void responder(@PathVariable String identificador,
                          @Valid @RequestBody QuestionarioDtos.PreenchimentoRequest req) {
        questionarioService.preencher(identificador, req);
    }
}
