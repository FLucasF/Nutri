package br.com.nutriplan.exame.repository;

import br.com.nutriplan.exame.domain.SolicitacaoDeExame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoDeExameRepository extends JpaRepository<SolicitacaoDeExame, Long> {

    @Query("""
           select s from SolicitacaoDeExame s
           where s.pacienteId = :pacienteId and s.contaId = :contaId
           order by s.data desc
           """)
    List<SolicitacaoDeExame> doPaciente(@Param("pacienteId") Long pacienteId,
                                        @Param("contaId") Long contaId);

    Optional<SolicitacaoDeExame> findByIdAndContaId(Long id, Long contaId);
}
