package br.com.nutriplan.handout.web;

import br.com.nutriplan.handout.dto.HandoutDtos;
import br.com.nutriplan.handout.service.HandoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Orientações nutricionais")
public class HandoutController {

    private final HandoutService handoutService;

    // ---------------------------------------------------------------- library

    @GetMapping("/handouts")
    @Operation(summary = "Lista a biblioteca de orientações",
            description = "Traz os modelos do sistema e os textos do próprio consultório. "
                    + "Os do consultório vêm primeiro.")
    public Page<HandoutDtos.HandoutResponse> list(
            @RequestParam(required = false) String term,
            @PageableDefault(size = 25) Pageable pageable) {
        return handoutService.list(term, pageable);
    }

    @GetMapping("/handouts/{id}")
    @Operation(summary = "Detalha uma orientação")
    public HandoutDtos.HandoutResponse detail(@PathVariable Long id) {
        return handoutService.detail(id);
    }

    @PostMapping("/handouts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma orientação própria")
    public HandoutDtos.HandoutResponse create(
            @Valid @RequestBody HandoutDtos.HandoutRequest req) {
        return handoutService.create(req);
    }

    @PostMapping("/handouts/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cópia a orientação para a biblioteca do consultório",
            description = "É o caminho para adaptar um modelo do sistema sem alterar o original.")
    public HandoutDtos.HandoutResponse duplicate(@PathVariable Long id) {
        return handoutService.duplicate(id);
    }

    @PutMapping("/handouts/{id}")
    @Operation(summary = "Atualiza uma orientação do consultório")
    public HandoutDtos.HandoutResponse update(
            @PathVariable Long id, @Valid @RequestBody HandoutDtos.HandoutRequest req) {
        return handoutService.update(id, req);
    }

    @DeleteMapping("/handouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa a orientação",
            description = "Não apaga: planos já entregues a referenciam como procedência.")
    public void remove(@PathVariable Long id) {
        handoutService.remove(id);
    }

    @PostMapping(value = "/handouts/{id}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anexa uma imagem à orientação",
            description = "Até 2 MB. Uma figura de prato dividido vale mais que o parágrafo "
                    + "que a descreve, e é o material que o paciente consulta na cozinha.")
    public void attachImage(@PathVariable Long id,
                             @RequestParam("file") MultipartFile file) throws IOException {
        handoutService.attachImage(id, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
    }

    @GetMapping("/handouts/{id}/image")
    @Operation(summary = "Baixa a imagem da orientação")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        return answer(handoutService.image(id));
    }

    @GetMapping("/prescriptions/{planId}/handouts/{attachmentId}/image")
    @Operation(summary = "Baixa a imagem entregue neste plano",
            description = "É a cópia congelada no anexo, e não a da biblioteca.")
    public ResponseEntity<byte[]> planImage(@PathVariable Long planId,
                                                @PathVariable Long attachmentId) {
        return answer(handoutService.planImage(planId, attachmentId));
    }

    private ResponseEntity<byte[]> answer(HandoutService.Image image) {
        return ResponseEntity.ok()
                .contentType(image.type() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(image.type()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.name() + "\"")
                .body(image.content());
    }

    // ------------------------------------------------------------- on the plan

    @GetMapping("/prescriptions/{planId}/handouts")
    @Operation(summary = "Orientações anexadas ao plano")
    public List<HandoutDtos.PlanHandoutResponse> forPlan(@PathVariable Long planId) {
        return handoutService.forPlan(planId);
    }

    @PostMapping("/prescriptions/{planId}/handouts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa uma orientação ao plano",
            description = "O texto é copiado no momento do anexo: editar a biblioteca depois "
                    + "não altera o que o paciente recebeu, e o texto anexado pode ser adaptado "
                    + "a este paciente sem sujar o modelo.")
    public HandoutDtos.PlanHandoutResponse attach(
            @PathVariable Long planId, @Valid @RequestBody HandoutDtos.AttachmentRequest req) {
        return handoutService.attach(planId, req);
    }

    @PutMapping("/prescriptions/{planId}/handouts/{attachmentId}")
    @Operation(summary = "Edita o texto anexado a este plano")
    public HandoutDtos.PlanHandoutResponse editNoPlan(
            @PathVariable Long planId, @PathVariable Long attachmentId,
            @Valid @RequestBody HandoutDtos.HandoutRequest req) {
        return handoutService.editNoPlan(planId, attachmentId, req);
    }

    @DeleteMapping("/prescriptions/{planId}/handouts/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a orientação do plano")
    public void detach(@PathVariable Long planId, @PathVariable Long attachmentId) {
        handoutService.detach(planId, attachmentId);
    }
}
