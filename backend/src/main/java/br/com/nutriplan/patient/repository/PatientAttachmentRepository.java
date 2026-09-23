package br.com.nutriplan.patient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nutriplan.patient.domain.PatientAttachment;

public interface PatientAttachmentRepository extends JpaRepository<PatientAttachment, Long> {

    /** Do mais recente para o mais antigo, pela data do documento. */
    List<PatientAttachment> findByAccountIdAndPatientIdOrderByReferenceDateDescIdDesc(
            Long accountId, Long patientId);

    Optional<PatientAttachment> findByIdAndAccountId(Long id, Long accountId);
}
