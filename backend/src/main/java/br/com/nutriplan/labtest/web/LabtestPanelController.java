package br.com.nutriplan.labtest.web;

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

import br.com.nutriplan.labtest.dto.LabtestDtos;
import br.com.nutriplan.labtest.service.LabtestPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/labtests/panels")
@RequiredArgsConstructor
@Tag(name = "Painéis de biomarcadores")
public class LabtestPanelController {

    private final LabtestPanelService labtestPanelService;

    @GetMapping
    @Operation(summary = "Lista os painéis visíveis ao consultório",
            description = "Os do consultório vêm primeiro, marcados para a tela poder "
                    + "diferenciá-los dos 25 que o sistema traz.")
    public List<LabtestDtos.PanelResponse> list() {
        return labtestPanelService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um painel, com os parâmetros na ordem")
    public LabtestDtos.PanelResponse detail(@PathVariable Long id) {
        return labtestPanelService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um painel próprio")
    public LabtestDtos.PanelResponse create(
            @Valid @RequestBody LabtestDtos.PanelRequest request) {
        return labtestPanelService.create(request);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Copia o painel para a lista do consultório",
            description = "É o caminho para adaptar um painel do sistema sem alterar o original.")
    public LabtestDtos.PanelResponse duplicate(@PathVariable Long id) {
        return labtestPanelService.duplicate(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um painel do consultório")
    public LabtestDtos.PanelResponse update(
            @PathVariable Long id, @Valid @RequestBody LabtestDtos.PanelRequest request) {
        return labtestPanelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um painel do consultório")
    public void remove(@PathVariable Long id) {
        labtestPanelService.remove(id);
    }
}
