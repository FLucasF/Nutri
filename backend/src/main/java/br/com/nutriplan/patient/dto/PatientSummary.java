package br.com.nutriplan.patient.dto;

import java.util.List;

import br.com.nutriplan.patient.domain.Patient;

/** A lean projection for listings and pickers. */
public record PatientSummary(
        Long id,
        String name,
        String email,
        Integer age,
        boolean active,
        /**
         * As TAGs do paciente.
         *
         * Vêm na listagem porque é nela que elas servem: "ter uma estimativa só
         * ou para procurar tal paciente sem ter que olhar diretamente para o
         * nome". Buscá-las por paciente depois seria uma consulta por linha.
         */
        List<String> tags
) {
    public static PatientSummary from(Patient p) {
        return from(p, List.of());
    }

    public static PatientSummary from(Patient p, List<String> tags) {
        return new PatientSummary(p.getId(), p.getName(), p.getEmail(),
                p.getAge(), p.isActive(), tags);
    }
}
