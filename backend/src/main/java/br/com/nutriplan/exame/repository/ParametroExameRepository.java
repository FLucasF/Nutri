package br.com.nutriplan.exame.repository;

import br.com.nutriplan.exame.domain.ParametroExame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParametroExameRepository extends JpaRepository<ParametroExame, Long> {

    /** Catalogo do sistema mais os parametros proprios do consultorio. */
    @Query("""
           select distinct p from ParametroExame p
           left join fetch p.faixas
           where p.ativo = true
             and (p.contaId is null or p.contaId = :contaId)
           order by p.grupo, p.nome
           """)
    List<ParametroExame> visiveisPara(@Param("contaId") Long contaId);

    @Query("""
           select p from ParametroExame p
           where p.id = :id and (p.contaId is null or p.contaId = :contaId)
           """)
    Optional<ParametroExame> buscarVisivel(@Param("id") Long id, @Param("contaId") Long contaId);
}
