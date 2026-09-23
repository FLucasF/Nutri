package br.com.nutriplan.anamnesis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nutriplan.anamnesis.domain.Anamnesis;

public interface AnamnesisRepository extends JpaRepository<Anamnesis, Long> {

    /**
     * The patient's anamneses, newest first.
     *
     * The values come in the same query: the listing shows them, so fetching
     * them one record at a time would be a query per row for data the screen
     * always needs.
     */
    @Query("""
           select distinct a from Anamnesis a
           left join fetch a.values
           where a.accountId = :accountId and a.patientId = :patientId
           order by a.date desc, a.id desc
           """)
    List<Anamnesis> ofPatient(@Param("accountId") Long accountId,
                              @Param("patientId") Long patientId);

    @Query("""
           select a from Anamnesis a
           left join fetch a.values
           where a.id = :id and a.accountId = :accountId
           """)
    Optional<Anamnesis> find(@Param("id") Long id, @Param("accountId") Long accountId);
}
