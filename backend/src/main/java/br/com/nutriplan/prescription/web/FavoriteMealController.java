package br.com.nutriplan.prescription.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.service.FavoriteMealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/meal-favorites")
@RequiredArgsConstructor
@Tag(name = "Refeições favoritas")
public class FavoriteMealController {

    private final FavoriteMealService favoriteMealService;

    @GetMapping
    @Operation(summary = "Lista as refeições salvas do consultório")
    public List<PrescriptionDtos.FavoriteMealResponse> list() {
        return favoriteMealService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Abre uma refeição salva, com os itens")
    public PrescriptionDtos.FavoriteMealResponse detail(@PathVariable Long id) {
        return favoriteMealService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Salva a refeição para reutilizar",
            description = "A refeição vai inteira no corpo, então funciona mesmo antes de "
                    + "o plano ser salvo. O que fica guardado é uma cópia: apagar o plano "
                    + "de origem não leva a refeição salva junto.")
    public PrescriptionDtos.FavoriteMealResponse save(
            @Valid @RequestBody PrescriptionDtos.FavoriteMealRequest request) {
        return favoriteMealService.save(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a refeição da lista de salvas")
    public void remove(@PathVariable Long id) {
        favoriteMealService.remove(id);
    }
}
