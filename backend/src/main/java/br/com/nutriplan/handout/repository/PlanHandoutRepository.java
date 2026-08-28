package br.com.nutriplan.handout.repository;

import br.com.nutriplan.handout.domain.PlanHandout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanHandoutRepository extends JpaRepository<PlanHandout, Long> {

    List<PlanHandout> findByPlanIdOrderByOrderAsc(Long planId);

    void deleteByPlanId(Long planId);
}
