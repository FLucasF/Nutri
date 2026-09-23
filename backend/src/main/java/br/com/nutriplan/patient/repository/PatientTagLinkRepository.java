package br.com.nutriplan.patient.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nutriplan.patient.domain.PatientTagLink;

public interface PatientTagLinkRepository extends JpaRepository<PatientTagLink, Long> {

    @Query("""
           select l from PatientTagLink l
           join fetch l.tag
           where l.patientId = :patientId
           order by l.tag.name
           """)
    List<PatientTagLink> ofPatient(@Param("patientId") Long patientId);

    @Query("""
           select l from PatientTagLink l
           join fetch l.tag
           where l.patientId in :patientIds
           """)
    List<PatientTagLink> ofPatients(@Param("patientIds") List<Long> patientIds);

    void deleteByPatientId(Long patientId);
}
