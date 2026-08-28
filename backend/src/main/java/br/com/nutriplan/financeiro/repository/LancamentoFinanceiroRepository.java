package br.com.nutriplan.financeiro.repository;

import br.com.nutriplan.financeiro.domain.LancamentoFinanceiro;
import br.com.nutriplan.financeiro.domain.SituacaoLancamento;
import br.com.nutriplan.financeiro.domain.TipoLancamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {

    Optional<LancamentoFinanceiro> findByIdAndContaId(Long id, Long contaId);

    @Query("""
           select l from LancamentoFinanceiro l
           where l.contaId = :contaId
             and (:de is null or l.competencia >= :de)
             and (:ate is null or l.competencia <= :ate)
             and (:tipo is null or l.tipo = :tipo)
             and (:situacao is null or l.situacao = :situacao)
             and (:pacienteId is null or l.pacienteId = :pacienteId)
           order by l.competencia desc, l.id desc
           """)
    Page<LancamentoFinanceiro> buscar(@Param("contaId") Long contaId,
                                      @Param("de") LocalDate de,
                                      @Param("ate") LocalDate ate,
                                      @Param("tipo") TipoLancamento tipo,
                                      @Param("situacao") SituacaoLancamento situacao,
                                      @Param("pacienteId") Long pacienteId,
                                      Pageable pageable);

    /** Lançamentos da competência, para apurar o período. */
    @Query("""
           select l from LancamentoFinanceiro l
           where l.contaId = :contaId
             and l.competencia >= :de and l.competencia <= :ate
           """)
    List<LancamentoFinanceiro> daCompetencia(@Param("contaId") Long contaId,
                                             @Param("de") LocalDate de,
                                             @Param("ate") LocalDate ate);

    /**
     * Pendências já vencidas na data de referência.
     *
     * A data vem de fora e não de {@code current_date} para que o relatório
     * seja reproduzível: apurar "os vencidos em 15/09" deve dar o mesmo
     * resultado hoje e daqui a um mês.
     */
    @Query("""
           select l from LancamentoFinanceiro l
           where l.contaId = :contaId
             and l.situacao = br.com.nutriplan.financeiro.domain.SituacaoLancamento.PENDENTE
             and l.vencimento is not null
             and l.vencimento < :referencia
           order by l.vencimento asc
           """)
    List<LancamentoFinanceiro> vencidosEm(@Param("contaId") Long contaId,
                                          @Param("referencia") LocalDate referencia);

    List<LancamentoFinanceiro> findByContaIdAndPacienteIdOrderByCompetenciaDesc(
            Long contaId, Long pacienteId);

    long countByContaIdAndAgendamentoId(Long contaId, Long agendamentoId);
}
