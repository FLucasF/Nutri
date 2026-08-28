package br.com.nutriplan.anthropometry.repository;

import br.com.nutriplan.anthropometry.domain.AnthropometricAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnthropometricAssessmentRepository
        extends JpaRepository<AnthropometricAssessment, Long> {

    Optional<AnthropometricAssessment> findByIdAndAccountId(Long id, Long accountId);

    /** The patient's series in chronological order — the basis of the progress screen. */
    List<AnthropometricAssessment> findByAccountIdAndPatientIdOrderByDateAscIdAsc(
            Long accountId, Long patientId);

    /** The most recent assessment, to prefill the next appointment. */
    Optional<AnthropometricAssessment> findFirstByAccountIdAndPatientIdOrderByDateDescIdDesc(
            Long accountId, Long patientId);

    long countByAccountIdAndPatientId(Long accountId, Long patientId);
}
