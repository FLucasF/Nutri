package br.com.nutriplan.agenda.repository;

import br.com.nutriplan.agenda.domain.Agendamento;
import br.com.nutriplan.agenda.domain.SituacaoAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Optional<Agendamento> findByIdAndContaId(Long id, Long contaId);

    /**
     * Candidatos a conflito: atendimentos que ocupam horário e começam dentro
     * de uma janela ao redor do intervalo pretendido.
     *
     * A sobreposição exata **não** é decidida aqui. Calcular o fim em JPQL
     * exigiria uma função de data específica do banco, o que quebraria a
     * portabilidade entre H2 e PostgreSQL (AD-11). Em vez disso, a consulta
     * recorta um conjunto pequeno — os atendimentos do entorno — e a decisão
     * fica com {@code Agendamento.sobrepoe}, onde a regra já vive.
     *
     * O filtro por conta é o que mantém agendas de consultórios independentes;
     * cancelados são excluídos porque liberam o horário.
     */
    @Query("""
           select a from Agendamento a
           where a.contaId = :contaId
             and a.situacao <> br.com.nutriplan.agenda.domain.SituacaoAtendimento.CANCELADO
             and a.inicio > :janelaInicio
             and a.inicio < :janelaFim
           """)
    List<Agendamento> candidatosAConflito(@Param("contaId") Long contaId,
                                          @Param("janelaInicio") LocalDateTime janelaInicio,
                                          @Param("janelaFim") LocalDateTime janelaFim);

    @Query("""
           select a from Agendamento a
           where a.contaId = :contaId
             and a.inicio >= :de and a.inicio < :ate
             and (:situacao is null or a.situacao = :situacao)
           order by a.inicio asc
           """)
    List<Agendamento> naFaixa(@Param("contaId") Long contaId,
                              @Param("de") LocalDateTime de,
                              @Param("ate") LocalDateTime ate,
                              @Param("situacao") SituacaoAtendimento situacao);

    List<Agendamento> findByContaIdAndPacienteIdOrderByInicioDesc(Long contaId, Long pacienteId);

    long countByContaIdAndPacienteIdAndSituacao(Long contaId, Long pacienteId,
                                                SituacaoAtendimento situacao);
}
