package br.com.nutriplan.patient.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nutriplan.patient.domain.PatientAttachmentContent;

public interface PatientAttachmentContentRepository
        extends JpaRepository<PatientAttachmentContent, Long> {
}
