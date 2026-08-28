package br.com.nutriplan.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nutritionist self-signup: it creates the account and the owner user in a single operation. */
public record SignupRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String password,
        @Size(max = 30) String crn,
        @Size(max = 20) String phone
) {}
