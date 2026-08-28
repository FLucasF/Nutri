package br.com.nutriplan.food.repository;

import br.com.nutriplan.food.domain.HouseholdMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HouseholdMeasureRepository extends JpaRepository<HouseholdMeasure, Long> {

    /**
     * Portions a practice sees for a food: those of the base catalog plus the
     * ones it registered itself.
     *
     * Its own come first: when the nutritionist creates their own version of
     * "tablespoon", it is theirs that should appear at the top of the list.
     */
    @Query("""
           select m from HouseholdMeasure m
           where m.food.id = :foodId
             and (m.accountId is null or m.accountId = :accountId)
           order by case when m.accountId is null then 1 else 0 end, m.description asc
           """)
    List<HouseholdMeasure> visibleTo(@Param("foodId") Long foodId,
                                     @Param("accountId") Long accountId);

    @Query("""
           select m from HouseholdMeasure m
           where m.id = :id
             and m.food.id = :foodId
             and (m.accountId is null or m.accountId = :accountId)
           """)
    Optional<HouseholdMeasure> visibleTo(@Param("id") Long id,
                                        @Param("foodId") Long foodId,
                                        @Param("accountId") Long accountId);

    boolean existsByFoodIdAndAccountIdIsNull(Long foodId);

    long countByAccountIdIsNull();

    /** Portions of the base catalog from a specific source. */
    @Query("""
           select count(m) from HouseholdMeasure m
           where m.accountId is null and m.food.source = :source
           """)
    long countNoCatalogBySource(@Param("source") br.com.nutriplan.food.domain.DataSource source);

    /** Foods from a source that ended up with no usual portion at all. */
    @Query("""
           select count(a) from Food a
           where a.source = :source
             and not exists (select 1 from HouseholdMeasure m where m.food = a)
           """)
    long countWithoutNoneServing(@Param("source") br.com.nutriplan.food.domain.DataSource source);

    @Query("""
           select count(m) > 0 from HouseholdMeasure m
           where m.food.id = :foodId
             and m.accountId = :accountId
             and lower(m.description) = lower(:description)
           """)
    boolean alreadyExistsAtAccount(@Param("foodId") Long foodId,
                            @Param("accountId") Long accountId,
                            @Param("description") String description);
}
