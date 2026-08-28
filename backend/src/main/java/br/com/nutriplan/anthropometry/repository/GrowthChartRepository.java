package br.com.nutriplan.anthropometry.repository;

import br.com.nutriplan.anthropometry.domain.GrowthChart;
import br.com.nutriplan.anthropometry.domain.GrowthIndicator;
import br.com.nutriplan.patient.domain.Sex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrowthChartRepository extends JpaRepository<GrowthChart, Long> {

    Optional<GrowthChart> findByIndicatorAndSexAndMonth(
            GrowthIndicator indicator, Sex sex, Integer month);

    boolean existsByIndicator(GrowthIndicator indicator);
}
