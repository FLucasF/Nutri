package br.com.nutriplan.energy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nutriplan.energy.domain.EnergyPlan;

public interface EnergyPlanRepository extends JpaRepository<EnergyPlan, Long> {

    @Query("""
           select distinct p from EnergyPlan p
           left join fetch p.equations
           where p.accountId = :accountId and p.patientId = :patientId
           order by p.date desc, p.id desc
           """)
    List<EnergyPlan> ofPatient(@Param("accountId") Long accountId,
                               @Param("patientId") Long patientId);

    @Query("""
           select p from EnergyPlan p
           left join fetch p.equations
           where p.id = :id and p.accountId = :accountId
           """)
    Optional<EnergyPlan> find(@Param("id") Long id, @Param("accountId") Long accountId);
}
