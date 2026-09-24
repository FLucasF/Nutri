package br.com.nutriplan.patient.repository;

import br.com.nutriplan.patient.domain.NoteTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteTemplateRepository extends JpaRepository<NoteTemplate, Long> {

    List<NoteTemplate> findByAccountIdOrderByNameAsc(Long accountId);

    Optional<NoteTemplate> findByIdAndAccountId(Long id, Long accountId);
}
