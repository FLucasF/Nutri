package br.com.nutriplan.labtest.repository;

import br.com.nutriplan.labtest.domain.LabtestReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabtestReportRepository extends JpaRepository<LabtestReport, Long> {
}
