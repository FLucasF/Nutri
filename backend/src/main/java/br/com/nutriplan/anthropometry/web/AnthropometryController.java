package br.com.nutriplan.anthropometry.web;

import br.com.nutriplan.anthropometry.domain.Skinfold;
import br.com.nutriplan.anthropometry.domain.CompositionProtocol;
import br.com.nutriplan.anthropometry.dto.AnthropometryDtos;
import br.com.nutriplan.anthropometry.service.AnthropometricAssessmentService;
import br.com.nutriplan.patient.domain.Sex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Antropometria")
public class AnthropometryController {

    private final AnthropometricAssessmentService assessmentService;

    @GetMapping("/anthropometry/protocols")
    @Operation(summary = "Lista os protocolos de composição corporal e as dobras que cada um exige")
    public List<AnthropometryDtos.ProtocolResponse> protocols() {
        return Arrays.stream(CompositionProtocol.values())
                .map(p -> new AnthropometryDtos.ProtocolResponse(
                        p, p.getDescription(), p.requiresSex(), p.requiresAge(),
                        p.skinfoldsRequired(Sex.FEMALE).stream().map(Skinfold::name).toList(),
                        p.skinfoldsRequired(Sex.MALE).stream().map(Skinfold::name).toList()))
                .toList();
    }

    @GetMapping(value = "/patients/{patientId}/anthropometry-report",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera o relatório de evolução antropométrica, com gráficos",
            description = "Traz peso, IMC e percentual de gordura no tempo, e as dobras e "
                    + "circunferências comparadas entre a primeira e a última avaliação. "
                    + "Exige pelo menos duas avaliações.")
    public ResponseEntity<byte[]> report(@PathVariable Long patientId) {
        var report = assessmentService.report(patientId);
        return ResponseEntity.ok()
                // "inline": a folha é conferida antes de ser mostrada ao
                // paciente, e baixar forçaria abrir o arquivo fora do sistema.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("evolucao-" + patientId + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(report.content());
    }

    @GetMapping(value = "/assessments/{id}/report", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera o relatório de uma avaliação",
            description = "Sai de qualquer avaliação. A versão do profissional traz todas as "
                    + "medidas, o protocolo e a comparação com a anterior; a do paciente fala "
                    + "com ele: peso, IMC e o que ele quer dizer, faixa de peso saudável, "
                    + "gordura, o que mudou e o gráfico do peso.")
    public ResponseEntity<byte[]> assessmentReport(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "PROFESSIONAL")
            br.com.nutriplan.anthropometry.service.AssessmentReportGenerator.Audience audience) {
        var report = assessmentService.assessmentReport(id, audience);
        String prefix = audience == br.com.nutriplan.anthropometry.service.AssessmentReportGenerator.Audience.PATIENT
                ? "avaliacao-paciente-" : "avaliacao-";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(prefix + id + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(report.content());
    }

    @GetMapping("/patients/{patientId}/assessments")
    @Operation(summary = "Lista as avaliações antropométricas do paciente, em ordem cronológica")
    public List<AnthropometryDtos.AssessmentResponse> list(@PathVariable Long patientId) {
        return assessmentService.patientList(patientId);
    }

    @GetMapping("/patients/{patientId}/progress")
    @Operation(summary = "Evolução das medidas entre avaliações",
            description = "Variação só é apresentada quando as duas pontas são comparáveis: "
                    + "medida presente nas duas, e composição estimada pelo mesmo protocolo.")
    public AnthropometryDtos.ProgressResponse progress(@PathVariable Long patientId) {
        return assessmentService.progress(patientId);
    }

    @PostMapping("/patients/{patientId}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra uma avaliação antropométrica")
    public AnthropometryDtos.AssessmentResponse create(
            @PathVariable Long patientId,
            @Valid @RequestBody AnthropometryDtos.AssessmentRequest req) {
        return assessmentService.create(patientId, req);
    }

    @GetMapping("/assessments/{id}")
    @Operation(summary = "Detalha uma avaliação")
    public AnthropometryDtos.AssessmentResponse detail(@PathVariable Long id) {
        return assessmentService.detail(id);
    }

    @PutMapping("/assessments/{id}")
    @Operation(summary = "Atualiza uma avaliação, recalculando o que dela deriva")
    public AnthropometryDtos.AssessmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AnthropometryDtos.AssessmentRequest req) {
        return assessmentService.update(id, req);
    }

    @DeleteMapping("/assessments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma avaliação")
    public void remove(@PathVariable Long id) {
        assessmentService.remove(id);
    }
}
