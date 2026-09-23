package br.com.nutriplan.patient.dto;

import br.com.nutriplan.patient.domain.BiologicalCondition;
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
        String nickname,
        /** Só preenchida para mulher; a ausência diz "nenhuma das duas". */
        BiologicalCondition biologicalCondition,
        String occupation,
        String goal,
        String notes,
        boolean active,
        boolean hasAccessToApp,
        Instant createdAt
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(), p.getName(), p.getEmail(), p.getPhone(),
                p.getDateBirth(), p.getAge(), p.getSex(), p.getCpf(),
                p.getNickname(), p.getBiologicalCondition(),
                p.getOccupation(), p.getGoal(), p.getNotes(),
                p.isActive(), p.hasAccessToApp(), p.getCreatedAt());
    }
}
