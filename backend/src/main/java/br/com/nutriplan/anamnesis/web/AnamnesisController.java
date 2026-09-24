package br.com.nutriplan.anamnesis.web;

import java.util.List;

import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nutriplan.anamnesis.dto.AnamnesisDtos;
import br.com.nutriplan.anamnesis.service.AnamnesisPdfGenerator;
import br.com.nutriplan.anamnesis.service.AnamnesisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Anamnese")
public class AnamnesisController {

    private final AnamnesisService anamnesisService;
    private final AnamnesisPdfGenerator pdfGenerator;

    // ------------------------------------------------------------- os campos

    @GetMapping("/anamnesis-fields")
    @Operation(summary = "Lista os campos de destaque do consultório",
            description = "São os pontos que aparecem na listagem sem abrir a anamnese.")
    public List<AnamnesisDtos.FieldResponse> fields() {
        return anamnesisService.fields();
    }

    @PutMapping("/anamnesis-fields")
    @Operation(summary = "Redefine os campos de destaque",
            description = "A lista inteira chega de uma vez, porque a ordem é do conjunto. "
                    + "O que sair da lista é desativado, não apagado: anamneses já "
                    + "escritas apontam para ele.")
    public List<AnamnesisDtos.FieldResponse> saveFields(
            @Valid @RequestBody AnamnesisDtos.FieldsRequest request) {
        return anamnesisService.saveFields(request);
    }

    // ------------------------------------------------------------ a anamnese

    @GetMapping("/patients/{patientId}/anamneses")
    @Operation(summary = "Lista as anamneses do paciente, da mais recente para a mais antiga")
    public List<AnamnesisDtos.AnamnesisSummary> ofPatient(@PathVariable Long patientId) {
        return anamnesisService.ofPatient(patientId);
    }

    @GetMapping("/anamneses/{id}")
    @Operation(summary = "Abre uma anamnese")
    public AnamnesisDtos.AnamnesisResponse detail(@PathVariable Long id) {
        return anamnesisService.detail(id);
    }

    @PostMapping("/anamneses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma anamnese")
    public AnamnesisDtos.AnamnesisResponse create(
            @Valid @RequestBody AnamnesisDtos.AnamnesisRequest request) {
        return anamnesisService.create(request);
    }

    @PutMapping("/anamneses/{id}")
    @Operation(summary = "Atualiza uma anamnese")
    public AnamnesisDtos.AnamnesisResponse update(
            @PathVariable Long id, @Valid @RequestBody AnamnesisDtos.AnamnesisRequest request) {
        return anamnesisService.update(id, request);
    }

    @PostMapping("/anamneses/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Duplica a anamnese, com a data de hoje",
            description = "É como a consulta de retorno começa da anterior, e não do zero.")
    public AnamnesisDtos.AnamnesisResponse duplicate(@PathVariable Long id) {
        return anamnesisService.duplicate(id);
    }

    @PostMapping("/anamneses/from-sending/{sendingId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria a anamnese a partir do que o paciente respondeu antes da consulta",
            description = "Uma vez só por envio: o envio é o registro do que o paciente disse, "
                    + "e a anamnese feita dele é o registro que o profissional passa a editar.")
    public AnamnesisDtos.AnamnesisResponse fromSending(@PathVariable Long sendingId) {
        return anamnesisService.fromSending(sendingId);
    }

    @DeleteMapping("/anamneses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a anamnese")
    public void remove(@PathVariable Long id) {
        anamnesisService.remove(id);
    }

    @GetMapping(value = "/anamneses/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Gera o PDF da anamnese")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        var anamnesis = anamnesisService.detail(id);
        byte[] bytes = pdfGenerator.generate(anamnesis);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("anamnese-" + id + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
