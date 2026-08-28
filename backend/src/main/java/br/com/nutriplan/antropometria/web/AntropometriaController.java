package br.com.nutriplan.antropometria.web;

import br.com.nutriplan.antropometria.domain.Dobra;
import br.com.nutriplan.antropometria.domain.ProtocoloComposicao;
import br.com.nutriplan.antropometria.dto.AntropometriaDtos;
import br.com.nutriplan.antropometria.service.AvaliacaoAntropometricaService;
import br.com.nutriplan.paciente.domain.Sexo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Antropometria")
public class AntropometriaController {

    private final AvaliacaoAntropometricaService avaliacaoService;

    @GetMapping("/antropometria/protocolos")
    @Operation(summary = "Lista os protocolos de composição corporal e as dobras que cada um exige")
    public List<AntropometriaDtos.ProtocoloResponse> protocolos() {
        return Arrays.stream(ProtocoloComposicao.values())
                .map(p -> new AntropometriaDtos.ProtocoloResponse(
                        p, p.getDescricao(), p.exigeSexo(), p.exigeIdade(),
                        p.dobrasExigidas(Sexo.FEMININO).stream().map(Dobra::name).toList(),
                        p.dobrasExigidas(Sexo.MASCULINO).stream().map(Dobra::name).toList()))
                .toList();
    }

    @GetMapping("/pacientes/{pacienteId}/avaliacoes")
    @Operation(summary = "Lista as avaliações antropométricas do paciente, em ordem cronológica")
    public List<AntropometriaDtos.AvaliacaoResponse> listar(@PathVariable Long pacienteId) {
        return avaliacaoService.listarDoPaciente(pacienteId);
    }

    @GetMapping("/pacientes/{pacienteId}/evolucao")
    @Operation(summary = "Evolução das medidas entre avaliações",
            description = "Variação só é apresentada quando as duas pontas são comparáveis: "
                    + "medida presente nas duas, e composição estimada pelo mesmo protocolo.")
    public AntropometriaDtos.EvolucaoResponse evolucao(@PathVariable Long pacienteId) {
        return avaliacaoService.evolucao(pacienteId);
    }

    @PostMapping("/pacientes/{pacienteId}/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra uma avaliação antropométrica")
    public AntropometriaDtos.AvaliacaoResponse criar(
            @PathVariable Long pacienteId,
            @Valid @RequestBody AntropometriaDtos.AvaliacaoRequest req) {
        return avaliacaoService.criar(pacienteId, req);
    }

    @GetMapping("/avaliacoes/{id}")
    @Operation(summary = "Detalha uma avaliação")
    public AntropometriaDtos.AvaliacaoResponse detalhar(@PathVariable Long id) {
        return avaliacaoService.detalhar(id);
    }

    @PutMapping("/avaliacoes/{id}")
    @Operation(summary = "Atualiza uma avaliação, recalculando o que dela deriva")
    public AntropometriaDtos.AvaliacaoResponse atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AntropometriaDtos.AvaliacaoRequest req) {
        return avaliacaoService.atualizar(id, req);
    }

    @DeleteMapping("/avaliacoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma avaliação")
    public void remover(@PathVariable Long id) {
        avaliacaoService.remover(id);
    }
}
