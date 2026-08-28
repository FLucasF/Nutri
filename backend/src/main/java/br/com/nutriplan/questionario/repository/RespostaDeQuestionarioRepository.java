package br.com.nutriplan.questionario.repository;

import br.com.nutriplan.questionario.domain.RespostaDeQuestionario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RespostaDeQuestionarioRepository
        extends JpaRepository<RespostaDeQuestionario, Long> {

    @Query("""
           select r from RespostaDeQuestionario r join fetch r.questionario
           where r.pacienteId = :pacienteId and r.contaId = :contaId
           order by r.enviadoEm desc
           """)
    List<RespostaDeQuestionario> doPaciente(@Param("pacienteId") Long pacienteId,
                                            @Param("contaId") Long contaId);

    @Query("""
           select r from RespostaDeQuestionario r join fetch r.questionario
           where r.agendamentoId = :agendamentoId and r.contaId = :contaId
           order by r.enviadoEm desc
           """)
    List<RespostaDeQuestionario> doAgendamento(@Param("agendamentoId") Long agendamentoId,
                                               @Param("contaId") Long contaId);

    Optional<RespostaDeQuestionario> findByIdentificadorPublico(String identificadorPublico);

    @Query("""
           select r from RespostaDeQuestionario r join fetch r.questionario
           where r.id = :id and r.contaId = :contaId
           """)
    Optional<RespostaDeQuestionario> buscarDaConta(@Param("id") Long id,
                                                   @Param("contaId") Long contaId);
}
