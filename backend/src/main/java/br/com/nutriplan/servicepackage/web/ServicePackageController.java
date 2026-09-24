package br.com.nutriplan.servicepackage.web;

import br.com.nutriplan.servicepackage.dto.PackageDtos;
import br.com.nutriplan.servicepackage.service.ServicePackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/packages")
@RequiredArgsConstructor
@Tag(name = "Pacotes")
public class ServicePackageController {

    private final ServicePackageService service;

    @GetMapping
    @Operation(summary = "Os pacotes de trabalho do consultório",
            description = "Só os ativos, salvo pedido explícito: um pacote desativado não "
                    + "aparece para agendar, mas continua nos relatórios.")
    public List<PackageDtos.PackageResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um pacote: nome, valor e, se for por encontros, quantos e de quanto em quanto")
    public PackageDtos.PackageResponse create(@Valid @RequestBody PackageDtos.PackageRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera um pacote")
    public PackageDtos.PackageResponse update(@PathVariable Long id,
                                              @Valid @RequestBody PackageDtos.PackageRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Desativa o pacote sem apagar o que já aponta para ele")
    public PackageDtos.PackageResponse deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reativa o pacote")
    public PackageDtos.PackageResponse reactivate(@PathVariable Long id) {
        return service.setActive(id, true);
    }
}
