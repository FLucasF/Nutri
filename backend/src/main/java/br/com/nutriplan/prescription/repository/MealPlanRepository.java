package br.com.nutriplan.prescription.repository;

import br.com.nutriplan.prescription.domain.MealPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    Optional<MealPlan> findByIdAndAccountId(Long id, Long accountId);

    /*
     * O termo de busca vai com tipo declarado.
     *
     * Sem o cast, um termo nulo dentro do concat faz o PostgreSQL assumir
     * bytea e recusar a consulta inteira. So aparece no banco de verdade: o H2
     * em modo de compatibilidade assume texto e responde normalmente.
     */
    @Query("""
           select p from MealPlan p
           where p.accountId = :accountId
             and (:patientId is null or p.patientId = :patientId)
             and (:template is null or p.template = :template)
             and (cast(:term as string) is null or lower(p.title) like lower(concat('%', cast(:term as string), '%')))
           """)
    Page<MealPlan> find(@Param("accountId") Long accountId,
                                @Param("patientId") Long patientId,
                                @Param("template") Boolean template,
                                @Param("term") String term,
                                Pageable pageable);

    /**
     * Loads the plan by its public address, together with the meals.
     *
     * It fetches only one level of collection: Hibernate refuses to fetch meals
     * and items in the same fetch join, because both are ordered lists and the
     * cartesian product would make the order unrecoverable. The items come
     * right after, in a batch, through the @BatchSize declared on Meal.
     */
    @Query("""
           select distinct p from MealPlan p
           left join fetch p.meals
           where p.publicIdentifier = :identifier
           """)
    Optional<MealPlan> findByPublicIdentifier(
            @Param("identifier") String identifier);

    @Query("""
           select distinct p from MealPlan p
           left join fetch p.meals
           where p.id = :id and p.accountId = :accountId
           """)
    Optional<MealPlan> loadComplete(@Param("id") Long id, @Param("accountId") Long accountId);

    List<MealPlan> findByAccountIdAndPatientIdOrderByCreatedAtDesc(Long accountId, Long patientId);

    long countByAccountIdAndPatientId(Long accountId, Long patientId);

    /** Quando cada cardápio (não modelo) foi criado, para as estatísticas. */
    @Query("""
           select p.createdAt from MealPlan p
           where p.accountId = :accountId and p.template = false and p.createdAt >= :from
           """)
    List<java.time.Instant> createdSince(@Param("accountId") Long accountId,
                                         @Param("from") java.time.Instant from);
}
