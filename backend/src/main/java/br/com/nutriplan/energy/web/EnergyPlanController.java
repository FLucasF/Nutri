package br.com.nutriplan.energy.web;

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

import br.com.nutriplan.energy.dto.EnergyDtos;
import br.com.nutriplan.energy.service.EnergyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Cálculo energético")
public class EnergyPlanController {

    private final EnergyPlanService energyPlanService;

    @GetMapping("/energy-plans/options")
    @Operation(summary = "Equações e níveis de atividade disponíveis",
            description = "Sai dos enums do domínio, então a tela não pode oferecer uma "
                    + "equação que o cálculo não conhece.")
    public EnergyDtos.OptionsResponse options() {
        return energyPlanService.options();
    }

    @GetMapping("/patients/{patientId}/energy-plans")
    @Operation(summary = "Lista os cálculos do paciente, do mais recente para o mais antigo")
    public List<EnergyDtos.EnergyPlanSummary> ofPatient(@PathVariable Long patientId) {
        return energyPlanService.ofPatient(patientId);
    }

    @GetMapping("/energy-plans/{id}")
    @Operation(summary = "Abre um cálculo energético")
    public EnergyDtos.EnergyPlanResponse detail(@PathVariable Long id) {
        return energyPlanService.detail(id);
    }

    @PostMapping("/energy-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um cálculo energético",
            description = "A média sai sobre o gasto do dia de cada equação selecionada. "
                    + "Equação basal recebe o fator de atividade; equação de gasto total "
                    + "não, porque o nível já está nos coeficientes dela.")
    public EnergyDtos.EnergyPlanResponse create(
            @Valid @RequestBody EnergyDtos.EnergyPlanRequest request) {
        return energyPlanService.create(request);
    }

    @PutMapping("/energy-plans/{id}")
    @Operation(summary = "Atualiza um cálculo energético")
    public EnergyDtos.EnergyPlanResponse update(
            @PathVariable Long id, @Valid @RequestBody EnergyDtos.EnergyPlanRequest request) {
        return energyPlanService.update(id, request);
    }

    @DeleteMapping("/energy-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui o cálculo energético")
    public void remove(@PathVariable Long id) {
        energyPlanService.remove(id);
    }
}
