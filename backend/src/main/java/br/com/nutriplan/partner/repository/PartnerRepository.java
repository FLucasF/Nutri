package br.com.nutriplan.partner.repository;

import br.com.nutriplan.partner.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    List<Partner> findByAccountIdOrderByNameAsc(Long accountId);

    Optional<Partner> findByIdAndAccountId(Long id, Long accountId);
}
