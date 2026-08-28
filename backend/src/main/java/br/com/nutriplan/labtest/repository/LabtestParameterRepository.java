package br.com.nutriplan.labtest.repository;

import br.com.nutriplan.labtest.domain.LabtestParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabtestParameterRepository extends JpaRepository<LabtestParameter, Long> {

    /** The system catalog plus the practice's own parameters. */
    @Query("""
           select distinct p from LabtestParameter p
           left join fetch p.ranges
           where p.active = true
             and (p.accountId is null or p.accountId = :accountId)
           order by p.group, p.name
           """)
    List<LabtestParameter> visibleTo(@Param("accountId") Long accountId);

    @Query("""
           select p from LabtestParameter p
           where p.id = :id and (p.accountId is null or p.accountId = :accountId)
           """)
    Optional<LabtestParameter> visibleFind(@Param("id") Long id, @Param("accountId") Long accountId);
}
