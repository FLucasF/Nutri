package br.com.nutriplan.paciente.web;

import br.com.nutriplan.paciente.dto.PacienteRequest;
import br.com.nutriplan.paciente.dto.PacienteResponse;
import br.com.nutriplan.paciente.dto.PacienteResumo;
import br.com.nutriplan.paciente.service.PacienteService;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
@Tag(name = "Pacientes")
public class PacienteController {

    private final PacienteService pacienteService;

    @GetMapping
    @Operation(summary = "Lista pacientes do consultório, com busca por nome, e-mail ou telefone")
    public Page<PacienteResumo> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {
        return pacienteService.listar(termo, ativo, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um paciente")
    public PacienteResponse buscar(@PathVariable Long id) {
        return pacienteService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um paciente")
    public PacienteResponse criar(@Valid @RequestBody PacienteRequest req) {
        return pacienteService.criar(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de um paciente")
    public PacienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest req) {
        return pacienteService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa um paciente, preservando o histórico")
    public void inativar(@PathVariable Long id) {
        pacienteService.inativar(id);
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa pacientes de uma planilha CSV",
            description = "As colunas são reconhecidas por nome, ignorando acento e caixa: "
                    + "\"nome\" (obrigatoria), \"email\", \"telefone\", \"nascimento\", "
                    + "\"sexo\", \"cpf\", \"profissao\", \"objetivo\" e \"observacoes\". "
                    + "Linha sem nome e ignorada, e o limite de pacientes do plano continua "
                    + "valendo — a importação para quando ele e atingido.")
    public PacienteService.ResultadoDaImportacao importar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(defaultValue = ",") String separador) throws IOException {

        if (arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie um arquivo CSV não vazio");
        }
        if (separador.length() != 1) {
            throw new RegraDeNegocioException(
                    "O separador deve ser um único caractere, como \",\" ou \";\"");
        }
        try (var leitura = new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8)) {
            return pacienteService.importar(leitura, separador.charAt(0));
        }
    }

    @PostMapping("/{id}/reativar")
    @Operation(summary = "Reativa um paciente inativo")
    public PacienteResponse reativar(@PathVariable Long id) {
        return pacienteService.reativar(id);
    }
}
