package br.com.nutriplan.patient.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nutriplan.patient.dto.ProfileDtos;
import br.com.nutriplan.patient.service.PatientProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Perfil do paciente")
public class PatientProfileController {

    private final PatientProfileService patientProfileService;

    @GetMapping("/patient-tags")
    @Operation(summary = "Lista as TAGs disponíveis",
            description = "As do consultório vêm primeiro; o sistema traz oito como ponto "
                    + "de partida.")
    public List<ProfileDtos.TagResponse> tags() {
        return patientProfileService.tags();
    }

    @PostMapping("/patient-tags")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma TAG do consultório")
    public ProfileDtos.TagResponse createTag(@Valid @RequestBody ProfileDtos.TagRequest request) {
        return patientProfileService.createTag(request);
    }

    @GetMapping("/patients/{patientId}/tags")
    @Operation(summary = "As TAGs deste paciente")
    public List<ProfileDtos.TagResponse> tagsOf(@PathVariable Long patientId) {
        return patientProfileService.tagsOf(patientId);
    }

    @PutMapping("/patients/{patientId}/tags")
    @Operation(summary = "Redefine as TAGs do paciente",
            description = "O conjunto inteiro chega de uma vez: marcar e desmarcar é uma "
                    + "operação só para quem usa.")
    public List<ProfileDtos.TagResponse> setTags(
            @PathVariable Long patientId,
            @RequestBody ProfileDtos.PatientTagsRequest request) {
        return patientProfileService.setTags(patientId, request);
    }

    @GetMapping("/patients/{patientId}/notes")
    @Operation(summary = "As anotações sobre o paciente, da mais recente para a mais antiga")
    public List<ProfileDtos.NoteResponse> notes(@PathVariable Long patientId) {
        return patientProfileService.notes(patientId);
    }

    @PostMapping("/patients/{patientId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Acrescenta uma anotação",
            description = "É o bloco que o cliente descreve como \"um tweet\": datado, "
                    + "curto e sucessivo. Não substitui as observações do cadastro.")
    public ProfileDtos.NoteResponse addNote(
            @PathVariable Long patientId, @Valid @RequestBody ProfileDtos.NoteRequest request) {
        return patientProfileService.addNote(patientId, request);
    }

    @DeleteMapping("/patients/{patientId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma anotação")
    public void removeNote(@PathVariable Long patientId, @PathVariable Long noteId) {
        patientProfileService.removeNote(patientId, noteId);
    }
}
