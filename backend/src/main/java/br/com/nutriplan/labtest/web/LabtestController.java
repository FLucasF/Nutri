package br.com.nutriplan.labtest.web;

import br.com.nutriplan.labtest.dto.LabtestDtos;
import br.com.nutriplan.labtest.service.LabtestService;
import br.com.nutriplan.shared.error.BusinessRuleException;
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
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Exames laboratoriais")
public class LabtestController {

    private final LabtestService labtestService;

    // ------------------------------------------------------------- parameters

    @GetMapping("/labtests/parameters")
    @Operation(summary = "Lista os parâmetros do catálogo e as faixas de referência",
            description = "Traz o catálogo do sistema e os parâmetros próprios do consultório. "
                    + "As faixas semeadas são as usualmente citadas para adulto: o laudo do "
                    + "laboratório prevalece, e por isso a faixa usada vai gravada em cada exame.")
    public List<LabtestDtos.ParameterResponse> parameters() {
        return labtestService.parameters();
    }

    @PostMapping("/labtests/parameters")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um parâmetro próprio do consultório")
    public LabtestDtos.ParameterResponse createParameter(
            @Valid @RequestBody LabtestDtos.ParameterRequest req) {
        return labtestService.createParameter(req);
    }

    // -------------------------------------------------------------- lab tests

    @PostMapping("/labtests/parameters/{id}/ranges")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra a faixa de referência do laboratório do consultório",
            description = "É o que faz a classificação baixo/normal/alto sair sozinha. "
                    + "A faixa não vem pronta porque varia de laboratório e de método, e "
                    + "uma faixa errada marcaria como alterado um resultado normal. "
                    + "Sexo e idade são opcionais: com eles, a faixa tem precedência sobre "
                    + "a geral.")
    public LabtestDtos.ParameterResponse addRange(
            @PathVariable Long id, @Valid @RequestBody LabtestDtos.RangeRequest req) {
        return labtestService.addRange(id, req);
    }

    @GetMapping("/patients/{patientId}/labtests")
    @Operation(summary = "Exames do paciente, da coleta mais recente para a mais antiga")
    public List<LabtestDtos.LabtestResponse> forPatient(@PathVariable Long patientId) {
        return labtestService.forPatient(patientId);
    }

    @PostMapping("/patients/{patientId}/labtests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra um resultado",
            description = "A classificação contra a faixa de referência é decidida agora e "
                    + "gravada com o resultado. Valor ausente significa parâmetro pedido e "
                    + "ainda não determinado — nunca zero.")
    public LabtestDtos.LabtestResponse entry(@PathVariable Long patientId,
                                             @Valid @RequestBody LabtestDtos.LabtestRequest req) {
        return labtestService.entry(patientId, req);
    }

    @GetMapping("/patients/{patientId}/labtests/series/{parameterId}")
    @Operation(summary = "Série histórica de um parâmetro",
            description = "Da coleta mais antiga para a mais recente, com a variação entre elas. "
                    + "`unidadesMisturadas` avisa quando a série tem coletas em unidades "
                    + "diferentes — nesse caso os pontos não são comparáveis.")
    public LabtestDtos.SeriesResponse series(@PathVariable Long patientId,
                                         @PathVariable Long parameterId) {
        return labtestService.series(patientId, parameterId);
    }

    @PutMapping("/labtests/{id}")
    @Operation(summary = "Corrige um resultado, reclassificando-o")
    public LabtestDtos.LabtestResponse update(@PathVariable Long id,
                                             @Valid @RequestBody LabtestDtos.LabtestRequest req) {
        return labtestService.update(id, req);
    }

    @DeleteMapping("/labtests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um resultado")
    public void remove(@PathVariable Long id) {
        labtestService.remove(id);
    }

    // ----------------------------------------------------------------- report

    @PostMapping(value = "/labtests/{id}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anexa o laudo ao resultado", description = "Até 5 MB.")
    public void attachReport(@PathVariable Long id,
                            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessRuleException("Envie um arquivo não vazio");
        }
        labtestService.attachReport(id, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
    }

    @GetMapping("/labtests/{id}/report")
    @Operation(summary = "Baixa o laudo anexado")
    public ResponseEntity<byte[]> report(@PathVariable Long id) {
        var report = labtestService.report(id);
        return ResponseEntity.ok()
                .contentType(report.type() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(report.type()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + report.name() + "\"")
                .body(report.content());
    }

    // ------------------------------------------------------------------- order

    @GetMapping("/patients/{patientId}/requests-from-labtest")
    @Operation(summary = "Solicitações de exame feitas ao paciente")
    public List<LabtestDtos.OrderResponse> requests(@PathVariable Long patientId) {
        return labtestService.requests(patientId);
    }

    @PostMapping("/patients/{patientId}/requests-from-labtest")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra o pedido de exames entregue ao paciente",
            description = "Cada exame diz de que painel veio e se está ligado. Desligado, "
                    + "fica no pedido e sai do PDF.")
    public LabtestDtos.OrderResponse request(
            @PathVariable Long patientId,
            @Valid @RequestBody LabtestDtos.OrderRequest req) {
        return labtestService.request(patientId, req);
    }

    @PutMapping("/patients/{patientId}/requests-from-labtest/{orderId}")
    @Operation(summary = "Religa, desliga ou troca os exames de um pedido")
    public LabtestDtos.OrderResponse updateRequest(
            @PathVariable Long patientId,
            @PathVariable Long orderId,
            @Valid @RequestBody LabtestDtos.OrderUpdateRequest req) {
        return labtestService.updateRequest(patientId, orderId, req);
    }

    @GetMapping(value = "/patients/{patientId}/requests-from-labtest/{orderId}/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "O pedido de exames em PDF, agrupado por painel",
            description = "Só os exames ligados saem na folha, cada grupo sob o nome do "
                    + "painel de que veio.")
    public ResponseEntity<byte[]> requestPdf(@PathVariable Long patientId,
                                             @PathVariable Long orderId) {
        byte[] content = labtestService.requestPdf(patientId, orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("pedido-de-exames-" + orderId + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }
}
