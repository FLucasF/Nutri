package br.com.nutriplan.questionnaire.repository;

import br.com.nutriplan.questionnaire.domain.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {

    /** System templates plus the practice's own; its own first. */
    @Query("""
           select q from Questionnaire q
           where q.active = true and (q.accountId is null or q.accountId = :accountId)
           order by case when q.accountId is null then 1 else 0 end, q.name
           """)
    List<Questionnaire> visibleTo(@Param("accountId") Long accountId);

    @Query("""
           select q from Questionnaire q
           where q.id = :id and (q.accountId is null or q.accountId = :accountId)
           """)
    Optional<Questionnaire> visibleFind(@Param("id") Long id, @Param("accountId") Long accountId);
}
