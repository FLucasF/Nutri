package br.com.nutriplan.patient.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nutriplan.patient.domain.PatientTag;

public interface PatientTagRepository extends JpaRepository<PatientTag, Long> {

    /** As TAGs do sistema mais as do consultório, as próprias primeiro. */
    @Query("""
           select t from PatientTag t
           where t.active = true and (t.accountId is null or t.accountId = :accountId)
           order by case when t.accountId is null then 1 else 0 end, t.name
           """)
    List<PatientTag> visibleTo(@Param("accountId") Long accountId);

    @Query("""
           select t from PatientTag t
           where t.id = :id and (t.accountId is null or t.accountId = :accountId)
           """)
    Optional<PatientTag> visibleFind(@Param("id") Long id, @Param("accountId") Long accountId);

    Optional<PatientTag> findByAccountIdAndName(Long accountId, String name);
}
