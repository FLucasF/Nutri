package br.com.nutriplan.prescription.web;

import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.service.PublicPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The plan as seen by the patient, through the link they received.
 *
 * The only point in the system that answers without authentication. The
 * authorization is possession of the identifier, and the response contract
 * simply has no field for the practice's internal data.
 */
@RestController
@RequestMapping("/api/public/plans")
@RequiredArgsConstructor
@Tag(name = "Plano do paciente")
@SecurityRequirements
public class PublicPlanController {

    private final PublicPlanService publicPlanService;

    @GetMapping("/{identifier}")
    @Operation(summary = "Abre um plano publicado pelo identificador do link",
            description = "Não exige autenticação. Plano em rascunho responde 404, "
                    + "para não revelar trabalho em andamento.")
    public PrescriptionDtos.PublicPlanResponse open(@PathVariable String identifier) {
        return publicPlanService.byIdentifier(identifier);
    }

    @GetMapping("/{identifier}/handouts/{attachmentId}/image")
    @Operation(summary = "Baixa a figura de uma orientação entregue neste plano",
            description = "Mesma porta do plano: identificador válido e plano visível. "
                    + "A figura é a cópia entregue, e não a da biblioteca.")
    public ResponseEntity<byte[]> handoutImage(@PathVariable String identifier,
                                                     @PathVariable Long attachmentId) {
        var image = publicPlanService.handoutImage(identifier, attachmentId);
        return ResponseEntity.ok()
                .contentType(image.type() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(image.type()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.name() + "\"")
                .body(image.content());
    }
}
