package br.com.nutriplan.patient.web;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.nutriplan.patient.dto.AttachmentDtos;
import br.com.nutriplan.patient.service.PatientAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/patients/{patientId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Arquivos anexos")
public class PatientAttachmentController {

    private final PatientAttachmentService attachmentService;

    @GetMapping
    @Operation(summary = "Lista os anexos do paciente, do mais recente para o mais antigo")
    public List<AttachmentDtos.AttachmentResponse> list(@PathVariable Long patientId) {
        return attachmentService.list(patientId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa um arquivo ao prontuário",
            description = "Até 15 MB. É por aqui que o cardápio do sistema antigo entra "
                    + "sem precisar ser recriado: o histórico fica junto do paciente, e o "
                    + "próximo planejamento dele nasce aqui.")
    public AttachmentDtos.AttachmentResponse attachFile(
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "referenceDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate)
            throws IOException {
        return attachmentService.attachFile(patientId, title, notes, referenceDate,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Guarda um link junto do prontuário",
            description = "Serve para o endereço do plano no sistema antigo, que ele "
                    + "mencionou querer guardar em algum canto.")
    public AttachmentDtos.AttachmentResponse attachLink(
            @PathVariable Long patientId,
            @Valid @RequestBody AttachmentDtos.LinkRequest request) {
        return attachmentService.attachLink(patientId, request);
    }

    @GetMapping("/{attachmentId}/file")
    @Operation(summary = "Baixa o arquivo anexado")
    public ResponseEntity<byte[]> download(@PathVariable Long patientId,
                                           @PathVariable Long attachmentId) {
        var file = attachmentService.download(patientId, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.name() == null ? "anexo" : file.name())
                        .build().toString())
                .contentType(file.type() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.type()))
                .body(file.content());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove o anexo")
    public void remove(@PathVariable Long patientId, @PathVariable Long attachmentId) {
        attachmentService.remove(patientId, attachmentId);
    }
}
