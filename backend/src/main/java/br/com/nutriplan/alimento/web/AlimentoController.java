package br.com.nutriplan.alimento.web;

import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.dto.AlimentoDtos;
import br.com.nutriplan.alimento.service.AlimentoService;
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

import br.com.nutriplan.shared.error.RegraDeNegocioException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/alimentos")
@RequiredArgsConstructor
@Tag(name = "Alimentos")
public class AlimentoController {

    private final AlimentoService alimentoService;

    @GetMapping
    @Operation(summary = "Busca alimentos nas tabelas de referência e no cadastro próprio")
    public Page<AlimentoDtos.Resumo> buscar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) FonteDeDados fonte,
            @PageableDefault(size = 25, sort = "descricao", direction = Sort.Direction.ASC) Pageable pageable) {
        return alimentoService.buscar(termo, grupo, fonte, pageable);
    }

    @GetMapping("/grupos")
    @Operation(summary = "Lista os grupos de alimentos disponiveis")
    public List<String> grupos() {
        return alimentoService.listarGrupos();
    }

    @GetMapping("/codigo-barras/{codigo}")
    @Operation(summary = "Localiza produtos industrializados pelo código de barras",
            description = "Devolve lista porque o código não e chave: o mesmo EAN pode aparecer "
                    + "mais de uma vez na base colaborativa e no cadastro próprio do consultório. "
                    + "O produto do consultório vem primeiro. Lista vazia quando nada casa.")
    public List<AlimentoDtos.Detalhe> porCodigoDeBarras(@PathVariable String codigo) {
        return alimentoService.porCodigoDeBarras(codigo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um alimento com composição completa e medidas caseiras")
    public AlimentoDtos.Detalhe detalhar(@PathVariable Long id) {
        return alimentoService.detalhar(id);
    }

    @GetMapping("/{id}/porcao")
    @Operation(summary = "Calcula a composição de uma porção, em gramas ou por medida caseira")
    public AlimentoDtos.PorcaoCalculada porcao(
            @PathVariable Long id,
            @RequestParam BigDecimal quantidade,
            @RequestParam(required = false) Long medidaId) {
        return alimentoService.calcularPorcao(id, quantidade, medidaId);
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('NUTRICIONISTA','ADMIN')")
    @Operation(summary = "Importa uma tabela de alimentos em CSV",
            description = "As colunas de nutriente sao reconhecidas por nome (\"energiaKcal\", "
                    + "\"energia_kcal\" ou \"Energia (kcal)\"). Os alimentos importados ficam "
                    + "vinculados a este consultório e não entram na base pública.")
    public AlimentoDtos.ResultadoImportacao importar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(required = false) FonteDeDados fonte,
            @RequestParam(defaultValue = ",") String separador) throws IOException {

        if (arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie um arquivo CSV não vazio");
        }
        if (separador.length() != 1) {
            throw new RegraDeNegocioException("O separador deve ser um único caractere, como \",\" ou \";\"");
        }
        try (var leitura = new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8)) {
            return alimentoService.importar(leitura, fonte, separador.charAt(0));
        }
    }

    @PostMapping("/{id}/medidas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma porção usual para o alimento",
            description = "Funciona inclusive sobre alimentos das tabelas de referência, "
                    + "que não trazem porções. A medida fica visivel apenas para este consultório "
                    + "e tem precedência sobre a porção equivalente do acervo base.")
    public AlimentoDtos.MedidaResponse adicionarMedida(
            @PathVariable Long id,
            @Valid @RequestBody AlimentoDtos.MedidaRequest req) {
        return alimentoService.adicionarMedida(id, req);
    }

    @DeleteMapping("/{id}/medidas/{medidaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma porção cadastrada por este consultório")
    public void removerMedida(@PathVariable Long id, @PathVariable Long medidaId) {
        alimentoService.removerMedida(id, medidaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um alimento próprio do consultório")
    public AlimentoDtos.Detalhe criar(@Valid @RequestBody AlimentoDtos.AlimentoRequest req) {
        return alimentoService.criar(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um alimento próprio")
    public AlimentoDtos.Detalhe atualizar(@PathVariable Long id,
                                          @Valid @RequestBody AlimentoDtos.AlimentoRequest req) {
        return alimentoService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa um alimento próprio")
    public void inativar(@PathVariable Long id) {
        alimentoService.inativar(id);
    }
}
