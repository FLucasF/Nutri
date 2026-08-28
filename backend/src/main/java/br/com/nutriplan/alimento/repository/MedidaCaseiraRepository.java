package br.com.nutriplan.alimento.repository;

import br.com.nutriplan.alimento.domain.MedidaCaseira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedidaCaseiraRepository extends JpaRepository<MedidaCaseira, Long> {

    /**
     * Porcoes que um consultorio enxerga para um alimento: as do acervo base
     * mais as que ele mesmo cadastrou.
     *
     * As proprias vem primeiro: quando o nutricionista cria a propria versao de
     * "colher de sopa", e a dele que deve aparecer no topo da lista.
     */
    @Query("""
           select m from MedidaCaseira m
           where m.alimento.id = :alimentoId
             and (m.contaId is null or m.contaId = :contaId)
           order by case when m.contaId is null then 1 else 0 end, m.descricao asc
           """)
    List<MedidaCaseira> visiveisPara(@Param("alimentoId") Long alimentoId,
                                     @Param("contaId") Long contaId);

    @Query("""
           select m from MedidaCaseira m
           where m.id = :id
             and m.alimento.id = :alimentoId
             and (m.contaId is null or m.contaId = :contaId)
           """)
    Optional<MedidaCaseira> visivelPara(@Param("id") Long id,
                                        @Param("alimentoId") Long alimentoId,
                                        @Param("contaId") Long contaId);

    boolean existsByAlimentoIdAndContaIdIsNull(Long alimentoId);

    long countByContaIdIsNull();

    /** Porcoes do acervo base de uma fonte especifica. */
    @Query("""
           select count(m) from MedidaCaseira m
           where m.contaId is null and m.alimento.fonte = :fonte
           """)
    long contarNoAcervoPorFonte(@Param("fonte") br.com.nutriplan.alimento.domain.FonteDeDados fonte);

    /** Alimentos de uma fonte que ficaram sem nenhuma porcao usual. */
    @Query("""
           select count(a) from Alimento a
           where a.fonte = :fonte
             and not exists (select 1 from MedidaCaseira m where m.alimento = a)
           """)
    long contarSemNenhumaPorcao(@Param("fonte") br.com.nutriplan.alimento.domain.FonteDeDados fonte);

    @Query("""
           select count(m) > 0 from MedidaCaseira m
           where m.alimento.id = :alimentoId
             and m.contaId = :contaId
             and lower(m.descricao) = lower(:descricao)
           """)
    boolean jaExisteNaConta(@Param("alimentoId") Long alimentoId,
                            @Param("contaId") Long contaId,
                            @Param("descricao") String descricao);
}
