package br.com.nutriplan.anamnesis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nutriplan.anamnesis.domain.AnamnesisField;

public interface AnamnesisFieldRepository extends JpaRepository<AnamnesisField, Long> {

    List<AnamnesisField> findByAccountIdAndActiveTrueOrderByOrderAsc(Long accountId);

    List<AnamnesisField> findByAccountIdOrderByOrderAsc(Long accountId);

    Optional<AnamnesisField> findByIdAndAccountId(Long id, Long accountId);
}
