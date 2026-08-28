package br.com.nutriplan.exame.web;

import br.com.nutriplan.exame.dto.ExameDtos;
import br.com.nutriplan.exame.service.ExameService;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Exames laboratoriais")
public class ExameController {

    private final ExameService exameService;

    // ------------------------------------------------------------- parâmetros

    @GetMapping("/exames/parametros")
    @Operation(summary = "Lista os parâmetros do catálogo e as faixas de referência",
            description = "Traz o catálogo do sistema e os parâmetros próprios do consultório. "
                    + "As faixas semeadas são as usualmente citadas para adulto: o laudo do "
                    + "laboratório prevalece, e por isso a faixa usada vai gravada em cada exame.")
    public List<ExameDtos.ParametroResponse> parametros() {
        return exameService.parametros();
    }

    @PostMapping("/exames/parametros")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um parâmetro próprio do consultório")
    public ExameDtos.ParametroResponse criarParametro(
            @Valid @RequestBody ExameDtos.ParametroRequest req) {
        return exameService.criarParametro(req);
    }

    // ----------------------------------------------------------------- exames

    @GetMapping("/pacientes/{pacienteId}/exames")
    @Operation(summary = "Exames do paciente, da coleta mais recente para a mais antiga")
    public List<ExameDtos.ExameResponse> doPaciente(@PathVariable Long pacienteId) {
        return exameService.doPaciente(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/exames")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra um resultado",
            description = "A classificação contra a faixa de referência é decidida agora e "
                    + "gravada com o resultado. Valor ausente significa parâmetro pedido e "
                    + "ainda não determinado — nunca zero.")
    public ExameDtos.ExameResponse registrar(@PathVariable Long pacienteId,
                                             @Valid @RequestBody ExameDtos.ExameRequest req) {
        return exameService.registrar(pacienteId, req);
    }

    @GetMapping("/pacientes/{pacienteId}/exames/serie/{parametroId}")
    @Operation(summary = "Série histórica de um parâmetro",
            description = "Da coleta mais antiga para a mais recente, com a variação entre elas. "
                    + "`unidadesMisturadas` avisa quando a série tem coletas em unidades "
                    + "diferentes — nesse caso os pontos não são comparáveis.")
    public ExameDtos.SerieResponse serie(@PathVariable Long pacienteId,
                                         @PathVariable Long parametroId) {
        return exameService.serie(pacienteId, parametroId);
    }

    @PutMapping("/exames/{id}")
    @Operation(summary = "Corrige um resultado, reclassificando-o")
    public ExameDtos.ExameResponse atualizar(@PathVariable Long id,
                                             @Valid @RequestBody ExameDtos.ExameRequest req) {
        return exameService.atualizar(id, req);
    }

    @DeleteMapping("/exames/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um resultado")
    public void remover(@PathVariable Long id) {
        exameService.remover(id);
    }

    // ------------------------------------------------------------------ laudo

    @PostMapping(value = "/exames/{id}/laudo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anexa o laudo ao resultado", description = "Até 5 MB.")
    public void anexarLaudo(@PathVariable Long id,
                            @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        if (arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie um arquivo não vazio");
        }
        exameService.anexarLaudo(id, arquivo.getOriginalFilename(),
                arquivo.getContentType(), arquivo.getBytes());
    }

    @GetMapping("/exames/{id}/laudo")
    @Operation(summary = "Baixa o laudo anexado")
    public ResponseEntity<byte[]> laudo(@PathVariable Long id) {
        var laudo = exameService.laudo(id);
        return ResponseEntity.ok()
                .contentType(laudo.tipo() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(laudo.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + laudo.nome() + "\"")
                .body(laudo.conteudo());
    }

    // ------------------------------------------------------------- solicitação

    @GetMapping("/pacientes/{pacienteId}/solicitacoes-de-exame")
    @Operation(summary = "Solicitações de exame feitas ao paciente")
    public List<ExameDtos.SolicitacaoResponse> solicitacoes(@PathVariable Long pacienteId) {
        return exameService.solicitacoes(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/solicitacoes-de-exame")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra o pedido de exames entregue ao paciente")
    public ExameDtos.SolicitacaoResponse solicitar(
            @PathVariable Long pacienteId,
            @Valid @RequestBody ExameDtos.SolicitacaoRequest req) {
        return exameService.solicitar(pacienteId, req);
    }
}
