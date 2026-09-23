package br.com.nutriplan.patient.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nutriplan.patient.domain.PatientNote;

public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {

    /** Do mais recente para o mais antigo: é um feed, não um arquivo. */
    List<PatientNote> findByAccountIdAndPatientIdOrderByCreatedAtDesc(
            Long accountId, Long patientId);
}
