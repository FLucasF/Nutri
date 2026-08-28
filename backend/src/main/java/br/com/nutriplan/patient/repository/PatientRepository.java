package br.com.nutriplan.patient.repository;

import br.com.nutriplan.patient.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Every method takes the accountId explicitly. There is no query without that
 * filter by design: it is what prevents one practice from reading another's
 * data.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByIdAndAccountId(Long id, Long accountId);

    @Query("""
           select p from Patient p
           where p.accountId = :accountId
             and (:active is null or p.active = :active)
             and (:term is null
                  or lower(p.name)     like lower(concat('%', :term, '%'))
                  or lower(p.email)    like lower(concat('%', :term, '%'))
                  or p.phone        like concat('%', :term, '%'))
           """)
    Page<Patient> find(@Param("accountId") Long accountId,
                          @Param("term") String term,
                          @Param("active") Boolean active,
                          Pageable pageable);

    long countByAccountIdAndActiveTrue(Long accountId);

    boolean existsByAccountIdAndCpf(Long accountId, String cpf);

    Optional<Patient> findByUserId(Long userId);
}
