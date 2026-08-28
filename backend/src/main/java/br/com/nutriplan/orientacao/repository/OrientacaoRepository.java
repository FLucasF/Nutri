package br.com.nutriplan.orientacao.repository;

import br.com.nutriplan.orientacao.domain.Orientacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrientacaoRepository extends JpaRepository<Orientacao, Long> {

    /**
     * Biblioteca visivel para a conta: os modelos do sistema mais os textos do
     * proprio consultorio. Mesma consulta do acervo de alimentos.
     */
    @Query("""
           select o from Orientacao o
           where o.ativo = true
             and (o.contaId is null or o.contaId = :contaId)
             and (:termo is null or lower(o.titulo) like concat('%', :termo, '%'))
           order by case when o.contaId is null then 1 else 0 end, o.titulo
           """)
    Page<Orientacao> visiveisPara(@Param("contaId") Long contaId,
                                  @Param("termo") String termo,
                                  Pageable pageable);

    @Query("""
           select o from Orientacao o
           where o.id = :id and (o.contaId is null or o.contaId = :contaId)
           """)
    Optional<Orientacao> buscarVisivel(@Param("id") Long id, @Param("contaId") Long contaId);
}
