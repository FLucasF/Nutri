package br.com.nutriplan.partner.web;

import br.com.nutriplan.partner.dto.PartnerDtos;
import br.com.nutriplan.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
@Tag(name = "Parceiros")
public class PartnerController {

    private final PartnerService service;

    @GetMapping
    @Operation(summary = "Os parceiros de indicação, com quantos pacientes cada um trouxe")
    public List<PartnerDtos.PartnerResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um parceiro")
    public PartnerDtos.PartnerResponse create(@Valid @RequestBody PartnerDtos.PartnerRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera um parceiro")
    public PartnerDtos.PartnerResponse update(@PathVariable Long id,
                                              @Valid @RequestBody PartnerDtos.PartnerRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Desativa o parceiro; os pacientes indicados por ele continuam apontando para ele")
    public PartnerDtos.PartnerResponse deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reativa o parceiro")
    public PartnerDtos.PartnerResponse reactivate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @GetMapping("/report")
    @Operation(summary = "Relatório de indicações do período",
            description = "Por parceiro: pacientes indicados, consultas do período (com "
                    + "realizadas e faltas) e a receita paga no período atribuída a ele.")
    public PartnerDtos.ReferralReport report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
