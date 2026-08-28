package br.com.nutriplan.labtest.repository;

import br.com.nutriplan.labtest.domain.Labtest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabtestRepository extends JpaRepository<Labtest, Long> {

    /**
     * Lab tests for a patient, from the most recent to the oldest.
     *
     * The parameter comes along because every row of the list shows its name —
     * without the fetch it would be N queries to draw one screen.
     */
    @Query("""
           select e from Labtest e join fetch e.parameter
           where e.patientId = :patientId and e.accountId = :accountId
           order by e.dateCollection desc, e.parameter.name asc
           """)
    List<Labtest> forPatient(@Param("patientId") Long patientId,
                           @Param("accountId") Long accountId);

    /** Historical series of a parameter, from the oldest to the most recent. */
    @Query("""
           select e from Labtest e join fetch e.parameter
           where e.patientId = :patientId and e.accountId = :accountId
             and e.parameter.id = :parameterId
           order by e.dateCollection asc
           """)
    List<Labtest> series(@Param("patientId") Long patientId,
                      @Param("parameterId") Long parameterId,
                      @Param("accountId") Long accountId);

    @Query("select e from Labtest e join fetch e.parameter where e.id = :id and e.accountId = :accountId")
    Optional<Labtest> accountFind(@Param("id") Long id, @Param("accountId") Long accountId);
}
