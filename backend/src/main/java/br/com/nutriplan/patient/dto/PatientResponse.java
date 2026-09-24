package br.com.nutriplan.patient.dto;

import br.com.nutriplan.patient.domain.BiologicalCondition;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.domain.Sex;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.nutriplan.anthropometry.domain.AnthropometricAssessment;
import br.com.nutriplan.anthropometry.domain.HealthyWeightRange;
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
        Instant createdAt,
        /**
         * A faixa de peso saudável pela última altura medida, e o último peso.
         *
         * O cliente pediu a faixa "na antropometria, nos relatórios, à direita
         * da área de prescrição e onde for possível". A ficha e o editor do
         * cardápio a leem daqui, sem refazer a conta.
         */
        HealthyWeightRange healthyWeight,
        BigDecimal lastWeightKg,
        LocalDate lastAssessmentDate
) {
    public static PatientResponse from(Patient p) {
        return from(p, null);
    }

    public static PatientResponse from(Patient p, AnthropometricAssessment last) {
        return new PatientResponse(
                p.getId(), p.getName(), p.getEmail(), p.getPhone(),
                p.getDateBirth(), p.getAge(), p.getSex(), p.getCpf(),
                p.getNickname(), p.getBiologicalCondition(),
                p.getOccupation(), p.getGoal(), p.getNotes(),
                p.isActive(), p.hasAccessToApp(), p.getCreatedAt(),
                last == null ? null : HealthyWeightRange.forAdult(last.getHeightCm(), p.getAge()),
                last == null ? null : last.getWeightKg(),
                last == null ? null : last.getDate());
    }
}
