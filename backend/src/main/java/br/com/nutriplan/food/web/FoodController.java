package br.com.nutriplan.food.web;

import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.dto.FoodDtos;
import br.com.nutriplan.food.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import br.com.nutriplan.shared.error.BusinessRuleException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
@Tag(name = "Alimentos")
public class FoodController {

    private final FoodService foodService;

    @GetMapping
    @Operation(summary = "Busca alimentos nas tabelas de referência e no cadastro próprio")
    public Page<FoodDtos.Summary> find(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) DataSource source,
            @PageableDefault(size = 25, sort = "description", direction = Sort.Direction.ASC) Pageable pageable) {
        return foodService.find(term, group, source, pageable);
    }

    @GetMapping("/groups")
    @Operation(summary = "Lista os grupos de alimentos disponiveis")
    public List<String> groups() {
        return foodService.listGroups();
    }

    @GetMapping("/barcode/{code}")
    @Operation(summary = "Localiza produtos industrializados pelo código de barras",
            description = "Devolve lista porque o código não e chave: o mesmo EAN pode aparecer "
                    + "mais de uma vez na base colaborativa e no cadastro próprio do consultório. "
                    + "O produto do consultório vem primeiro. Lista vazia quando nada casa.")
    public List<FoodDtos.Detail> byBarcodeCode(@PathVariable String code) {
        return foodService.byBarcodeCode(code);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um alimento com composição completa e medidas caseiras")
    public FoodDtos.Detail detail(@PathVariable Long id) {
        return foodService.detail(id);
    }

    @GetMapping("/{id}/serving")
    @Operation(summary = "Calcula a composição de uma porção, em gramas ou por medida caseira")
    public FoodDtos.CalculatedServing serving(
            @PathVariable Long id,
            @RequestParam BigDecimal quantity,
            @RequestParam(required = false) Long measureId) {
        return foodService.calculateServing(id, quantity, measureId);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('NUTRICIONISTA','ADMIN')")
    @Operation(summary = "Importa uma tabela de alimentos em CSV",
            description = "As colunas de nutriente sao reconhecidas por nome (\"energiaKcal\", "
                    + "\"energia_kcal\" ou \"Energia (kcal)\"). Os alimentos importados ficam "
                    + "vinculados a este consultório e não entram na base pública.")
    public FoodDtos.ResultImport importAll(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) DataSource source,
            @RequestParam(defaultValue = ",") String separator) throws IOException {

        if (file.isEmpty()) {
            throw new BusinessRuleException("Envie um arquivo CSV não vazio");
        }
        if (separator.length() != 1) {
            throw new BusinessRuleException("O separador deve ser um único caractere, como \",\" ou \";\"");
        }
        try (var read = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return foodService.importAll(read, source, separator.charAt(0));
        }
    }

    @PostMapping("/{id}/measures")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma porção usual para o alimento",
            description = "Funciona inclusive sobre alimentos das tabelas de referência, "
                    + "que não trazem porções. A medida fica visivel apenas para este consultório "
                    + "e tem precedência sobre a porção equivalente do acervo base.")
    public FoodDtos.MeasureResponse addMeasure(
            @PathVariable Long id,
            @Valid @RequestBody FoodDtos.MeasureRequest req) {
        return foodService.addMeasure(id, req);
    }

    @DeleteMapping("/{id}/measures/{measureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma porção cadastrada por este consultório")
    public void removeMeasure(@PathVariable Long id, @PathVariable Long measureId) {
        foodService.removeMeasure(id, measureId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um alimento próprio do consultório")
    public FoodDtos.Detail create(@Valid @RequestBody FoodDtos.FoodRequest req) {
        return foodService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um alimento próprio")
    public FoodDtos.Detail update(@PathVariable Long id,
                                          @Valid @RequestBody FoodDtos.FoodRequest req) {
        return foodService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa um alimento próprio")
    public void deactivate(@PathVariable Long id) {
        foodService.deactivate(id);
    }
}
