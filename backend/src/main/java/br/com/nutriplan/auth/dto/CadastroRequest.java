package br.com.nutriplan.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Auto-cadastro de nutricionista: cria a conta e o usuario dono numa so operacao. */
public record CadastroRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String senha,
        @Size(max = 30) String crn,
        @Size(max = 20) String telefone
) {}
