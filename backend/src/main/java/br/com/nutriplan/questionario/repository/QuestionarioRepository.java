package br.com.nutriplan.questionario.repository;

import br.com.nutriplan.questionario.domain.Questionario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionarioRepository extends JpaRepository<Questionario, Long> {

    /** Modelos do sistema mais os do proprio consultorio; os proprios primeiro. */
    @Query("""
           select q from Questionario q
           where q.ativo = true and (q.contaId is null or q.contaId = :contaId)
           order by case when q.contaId is null then 1 else 0 end, q.nome
           """)
    List<Questionario> visiveisPara(@Param("contaId") Long contaId);

    @Query("""
           select q from Questionario q
           where q.id = :id and (q.contaId is null or q.contaId = :contaId)
           """)
    Optional<Questionario> buscarVisivel(@Param("id") Long id, @Param("contaId") Long contaId);
}
