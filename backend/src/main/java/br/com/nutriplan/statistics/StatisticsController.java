package br.com.nutriplan.statistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@Tag(name = "Estatísticas")
public class StatisticsController {

    private final StatisticsService service;

    @GetMapping
    @Operation(summary = "Os números do consultório nos últimos meses",
            description = "Consultas por mês com realizadas, faltas e retornos; pacientes "
                    + "novos, avaliações, cardápios e receita paga; e quem está sem "
                    + "consulta há mais de 60 dias.")
    public StatisticsDtos.StatisticsResponse overview(
            @RequestParam(defaultValue = "6") int months) {
        return service.overview(months);
    }
}
