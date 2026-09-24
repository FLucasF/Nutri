package br.com.nutriplan.patient.dto;

import br.com.nutriplan.patient.domain.Sex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatientRequest(
        @NotBlank @Size(max = 150) String name,
        @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @PastOrPresent(message = "A data de nascimento não pode ser futura") LocalDate dateBirth,
        Sex sex,
        @Size(max = 20) String cpf,
        @Size(max = 80) String nickname,
        br.com.nutriplan.patient.domain.BiologicalCondition biologicalCondition,
        @Size(max = 100) String occupation,
        @Size(max = 500) String goal,
        @Size(max = 8000) String notes,
        /** Quem indicou o paciente, quando foi indicação. */
        Long partnerId
) {}
