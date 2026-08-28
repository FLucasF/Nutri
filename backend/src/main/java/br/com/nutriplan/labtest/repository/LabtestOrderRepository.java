package br.com.nutriplan.labtest.repository;

import br.com.nutriplan.labtest.domain.LabtestOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabtestOrderRepository extends JpaRepository<LabtestOrder, Long> {

    @Query("""
           select s from LabtestOrder s
           where s.patientId = :patientId and s.accountId = :accountId
           order by s.date desc
           """)
    List<LabtestOrder> forPatient(@Param("patientId") Long patientId,
                                        @Param("accountId") Long accountId);

    Optional<LabtestOrder> findByIdAndAccountId(Long id, Long accountId);
}
