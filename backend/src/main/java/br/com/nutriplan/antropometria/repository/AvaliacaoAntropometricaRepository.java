package br.com.nutriplan.antropometria.repository;

import br.com.nutriplan.antropometria.domain.AvaliacaoAntropometrica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoAntropometricaRepository
        extends JpaRepository<AvaliacaoAntropometrica, Long> {

    Optional<AvaliacaoAntropometrica> findByIdAndContaId(Long id, Long contaId);

    /** Série do paciente em ordem cronológica — a base da tela de evolução. */
    List<AvaliacaoAntropometrica> findByContaIdAndPacienteIdOrderByDataAscIdAsc(
            Long contaId, Long pacienteId);

    /** Avaliação mais recente, para pré-preencher a próxima consulta. */
    Optional<AvaliacaoAntropometrica> findFirstByContaIdAndPacienteIdOrderByDataDescIdDesc(
            Long contaId, Long pacienteId);

    long countByContaIdAndPacienteId(Long contaId, Long pacienteId);
}
