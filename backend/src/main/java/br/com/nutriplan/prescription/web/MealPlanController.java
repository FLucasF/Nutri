package br.com.nutriplan.prescription.web;

import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.service.PlanPrintingService;
import br.com.nutriplan.prescription.service.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
@Tag(name = "Prescrição")
public class MealPlanController {

    private final MealPlanService planService;
    private final PlanPrintingService printingService;
    private final br.com.nutriplan.prescription.service.AdequacyService adequacyService;

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera o plano alimentar em PDF, pronto para entregar na consulta",
            description = "Funciona também sobre rascunho: conferir a folha antes de publicar "
                    + "faz parte do trabalho, e a folha se identifica como rascunho.")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        var pdf = printingService.issue(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // "inline": the professional checks it before sending, and a
                // direct download would force opening the file outside.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.content());
    }

    @GetMapping
    @Operation(summary = "Lista planos alimentares do consultório")
    public Page<PrescriptionDtos.PlanSummary> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Boolean template,
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return planService.list(patientId, template, term, pageable);
    }

    @GetMapping("/{id}/adequacy")
    @Operation(summary = "Micronutrientes do dia contra as DRI do paciente",
            description = "RDA ou AI por sexo e idade; para o sódio, o limite (CDRR). De 80 a "
                    + "120% da referência está adequado. Gestantes, lactantes e modelos sem "
                    + "paciente respondem sem comparação, dizendo por quê.")
    public PrescriptionDtos.AdequacyResponse adequacy(@PathVariable Long id) {
        return adequacyService.adequacy(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um plano, com totais por refeição e do dia")
    public PrescriptionDtos.PlanResponse detail(@PathVariable Long id) {
        return planService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um plano alimentar")
    public PrescriptionDtos.PlanResponse create(@Valid @RequestBody PrescriptionDtos.PlanRequest req) {
        return planService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Substitui o conteúdo de um plano")
    public PrescriptionDtos.PlanResponse update(@PathVariable Long id,
                                                  @Valid @RequestBody PrescriptionDtos.PlanRequest req) {
        return planService.update(id, req);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Pública o plano, liberando o link do paciente")
    public PrescriptionDtos.PlanResponse publish(@PathVariable Long id) {
        return planService.publish(id);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Encerra o plano, que permanece visível marcado como encerrado")
    public PrescriptionDtos.PlanResponse close(@PathVariable Long id) {
        return planService.close(id);
    }

    @PostMapping("/{id}/draft")
    @Operation(summary = "Devolve o plano para rascunho, tirando-o do ar")
    public PrescriptionDtos.PlanResponse backToDraft(@PathVariable Long id) {
        return planService.backToDraft(id);
    }

    @PostMapping("/{id}/regenerate-link")
    @Operation(summary = "Gera um novo endereço público, invalidando o link já entregue")
    public PrescriptionDtos.PlanResponse regenerateLink(@PathVariable Long id) {
        return planService.regenerateLink(id);
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplica o plano como novo rascunho")
    public PrescriptionDtos.PlanResponse duplicate(
            @PathVariable Long id,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String title) {
        return planService.duplicate(id, patientId, title);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um plano")
    public void remove(@PathVariable Long id) {
        planService.remove(id);
    }
}
