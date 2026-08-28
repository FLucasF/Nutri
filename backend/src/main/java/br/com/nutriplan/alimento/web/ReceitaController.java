package br.com.nutriplan.alimento.web;

import br.com.nutriplan.alimento.dto.ReceitaDtos;
import br.com.nutriplan.alimento.service.ReceitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/api/receitas")
@RequiredArgsConstructor
@Tag(name = "Receitas")
public class ReceitaController {

    private final ReceitaService receitaService;

    @GetMapping
    @Operation(summary = "Lista as receitas do consultório")
    public Page<ReceitaDtos.ReceitaResumo> listar(
            @RequestParam(required = false) String termo,
            @PageableDefault(size = 25) Pageable pageable) {
        return receitaService.listar(termo, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha a receita com ingredientes e composição calculada",
            description = "A composição vem por 100 g e, quando a receita informa em quantas "
                    + "porções rende, também por porção. `rendimentoEstimado` avisa que o peso "
                    + "final não foi informado e a soma dos ingredientes foi usada em seu lugar; "
                    + "`nutrientesIncompletos` lista o que foi somado a partir de apenas parte "
                    + "dos ingredientes, e por isso e piso e não total.")
    public ReceitaDtos.ReceitaResponse detalhar(@PathVariable Long id) {
        return receitaService.detalhar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma receita",
            description = "A receita entra no acervo como alimento de fonte RECEITA: aparece na "
                    + "busca, ganha as porções derivadas do rendimento e pode ser prescrita como "
                    + "qualquer outro item.")
    public ReceitaDtos.ReceitaResponse criar(@Valid @RequestBody ReceitaDtos.ReceitaRequest req) {
        return receitaService.criar(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Substitui a receita")
    public ReceitaDtos.ReceitaResponse atualizar(
            @PathVariable Long id, @Valid @RequestBody ReceitaDtos.ReceitaRequest req) {
        return receitaService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa a receita",
            description = "Não apaga: a receita pode estar prescrita num plano ativo, e o plano "
                    + "precisa continuar dizendo o que foi prescrito.")
    public void remover(@PathVariable Long id) {
        receitaService.remover(id);
    }
}
