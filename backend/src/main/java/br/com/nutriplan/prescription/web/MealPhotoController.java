package br.com.nutriplan.prescription.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.nutriplan.prescription.service.MealPhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/prescriptions/{planId}/meals/{mealId}/photo")
@RequiredArgsConstructor
@Tag(name = "Foto da refeição")
public class MealPhotoController {

    private final MealPhotoService mealPhotoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anexa a foto do prato à refeição",
            description = "Até 4 MB. Ela sai no PDF do cardápio: o paciente entende "
                    + "'meio prato de salada' vendo meio prato de salada.")
    public void attach(@PathVariable Long planId, @PathVariable Long mealId,
                       @RequestParam("file") MultipartFile file) throws IOException {
        mealPhotoService.attach(planId, mealId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
    }

    @GetMapping
    @Operation(summary = "Baixa a foto da refeição")
    public ResponseEntity<byte[]> read(@PathVariable Long planId, @PathVariable Long mealId) {
        var photo = mealPhotoService.read(planId, mealId);
        return ResponseEntity.ok()
                .contentType(photo.type() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(photo.type()))
                .body(photo.content());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a foto da refeição")
    public void remove(@PathVariable Long planId, @PathVariable Long mealId) {
        mealPhotoService.remove(planId, mealId);
    }
}
