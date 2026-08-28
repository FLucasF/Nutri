package br.com.nutriplan.paciente.dto;

import br.com.nutriplan.paciente.domain.Sexo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PacienteRequest(
        @NotBlank @Size(max = 150) String nome,
        @Email @Size(max = 180) String email,
        @Size(max = 20) String telefone,
        @PastOrPresent(message = "A data de nascimento não pode ser futura") LocalDate dataNascimento,
        Sexo sexo,
        @Size(max = 20) String cpf,
        @Size(max = 100) String profissao,
        @Size(max = 500) String objetivo,
        @Size(max = 2000) String observacoes
) {}
