package br.com.nutriplan.servicepackage.repository;

import br.com.nutriplan.servicepackage.domain.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    List<ServicePackage> findByAccountIdOrderByNameAsc(Long accountId);

    Optional<ServicePackage> findByIdAndAccountId(Long id, Long accountId);
}
