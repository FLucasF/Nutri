package br.com.nutriplan.patient.dto;

import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.domain.Sex;

import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
        Long id,
        String name,
        String email,
        String phone,
        LocalDate dateBirth,
        Integer age,
        Sex sex,
        String cpf,
        String occupation,
        String goal,
        String notes,
        boolean active,
        boolean hasAccessAoApp,
        Instant createdAt
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(), p.getName(), p.getEmail(), p.getPhone(),
                p.getDateBirth(), p.getAge(), p.getSex(), p.getCpf(),
                p.getOccupation(), p.getGoal(), p.getNotes(),
                p.isActive(), p.hasAccessAoApp(), p.getCreatedAt());
    }
}
