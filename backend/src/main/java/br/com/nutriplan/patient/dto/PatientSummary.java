package br.com.nutriplan.patient.dto;

import br.com.nutriplan.patient.domain.Patient;

/** A lean projection for listings and pickers. */
public record PatientSummary(Long id, String name, String email, Integer age, boolean active) {
    public static PatientSummary from(Patient p) {
        return new PatientSummary(p.getId(), p.getName(), p.getEmail(), p.getAge(), p.isActive());
    }
}
