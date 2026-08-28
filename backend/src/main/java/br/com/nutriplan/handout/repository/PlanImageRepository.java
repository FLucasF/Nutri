package br.com.nutriplan.handout.repository;

import br.com.nutriplan.handout.domain.PlanImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanImageRepository extends JpaRepository<PlanImage, Long> {
}
