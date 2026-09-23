package br.com.nutriplan.prescription.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nutriplan.prescription.domain.Meal;

/**
 * The practice's saved meals.
 *
 * A favourite is a {@link Meal} with no plan, which is why this repository
 * reads the same table as the meals inside plans and filters on that.
 */
public interface FavoriteMealRepository extends JpaRepository<Meal, Long> {

    /**
     * Busca só os itens, e não as substituições junto.
     *
     * O Hibernate recusa trazer duas listas na mesma consulta
     * (MultipleBagFetchException). As substituições vêm depois, em lote, pelo
     * {@code @BatchSize} declarado em MealItem — é o mesmo caminho que o
     * repositório dos planos usa.
     */
    @Query("""
           select distinct m from Meal m
           left join fetch m.items
           where m.plan is null and m.accountId = :accountId
           order by m.favoriteName
           """)
    List<Meal> favoritesOf(@Param("accountId") Long accountId);

    @Query("""
           select m from Meal m
           left join fetch m.items
           where m.id = :id and m.plan is null and m.accountId = :accountId
           """)
    Optional<Meal> findFavorite(@Param("id") Long id, @Param("accountId") Long accountId);
}
