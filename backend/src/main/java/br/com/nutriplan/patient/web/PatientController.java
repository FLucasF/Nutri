package br.com.nutriplan.patient.web;

import br.com.nutriplan.patient.dto.PatientRequest;
import br.com.nutriplan.patient.dto.PatientResponse;
import br.com.nutriplan.patient.dto.PatientSummary;
import br.com.nutriplan.patient.service.PatientService;
import br.com.nutriplan.shared.error.BusinessRuleException;
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
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Pacientes")
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @Operation(summary = "Lista pacientes do consultório, com busca por nome, e-mail ou telefone")
    public Page<PatientSummary> list(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return patientService.list(term, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um paciente")
    public PatientResponse find(@PathVariable Long id) {
        return patientService.find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um paciente")
    public PatientResponse create(@Valid @RequestBody PatientRequest req) {
        return patientService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados de um paciente")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest req) {
        return patientService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativa um paciente, preservando o histórico")
    public void deactivate(@PathVariable Long id) {
        patientService.deactivate(id);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa pacientes de uma planilha CSV",
            description = "As colunas são reconhecidas por nome, ignorando acento e caixa: "
                    + "\"nome\" (obrigatoria), \"email\", \"telefone\", \"nascimento\", "
                    + "\"sexo\", \"cpf\", \"profissao\", \"objetivo\" e \"observacoes\". "
                    + "Linha sem nome e ignorada, e o limite de pacientes do plano continua "
                    + "valendo — a importação para quando ele e atingido.")
    public PatientService.ImportResult importAll(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = ",") String separator) throws IOException {

        if (file.isEmpty()) {
            throw new BusinessRuleException("Envie um arquivo CSV não vazio");
        }
        if (separator.length() != 1) {
            throw new BusinessRuleException(
                    "O separador deve ser um único caractere, como \",\" ou \";\"");
        }
        try (var read = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return patientService.importAll(read, separator.charAt(0));
        }
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reativa um paciente inativo")
    public PatientResponse reactivate(@PathVariable Long id) {
        return patientService.reactivate(id);
    }
}
