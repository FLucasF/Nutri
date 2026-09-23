package br.com.nutriplan.food.repository;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.DataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    boolean existsBySource(DataSource source);

    /**
     * Searches the catalog visible to the account: the public bases
     * (account_id null) plus the foods registered by the practice itself.
     *
     * The term is compared against search_description, already without accents
     * and in lowercase, so that "acucar" finds "Açúcar".
     */
    /*
     * O termo de busca vai com tipo declarado.
     *
     * Sem o cast, um termo nulo dentro do concat faz o PostgreSQL assumir
     * bytea e recusar a consulta inteira. So aparece no banco de verdade: o H2
     * em modo de compatibilidade assume texto e responde normalmente.
     */
    @Query("""
           select a from Food a
           where a.active = true
             and (a.accountId is null or a.accountId = :accountId)
             and (cast(:term as string) is null or a.descriptionSearch like concat('%', cast(:term as string), '%'))
             and (:group is null or a.group = :group)
             and (:source is null or a.source = :source)
           """)
    Page<Food> find(@Param("accountId") Long accountId,
                          @Param("term") String term,
                          @Param("group") String group,
                          @Param("source") DataSource source,
                          Pageable pageable);

    /**
     * Search by term, ordered by clinical relevance.
     *
     * Without ordering by relevance the base is unusable: there are 21 thousand
     * processed products against 597 reference foods, and alphabetical order
     * makes "banana" return "&Joy Frutas Banana + Cacau" before the fruit.
     *
     * Three criteria, in this order:
     *
     * 1. **Provenance.** TACO first, then TBCA and IBGE, and last the
     *    manufacturer's product. Whoever types "leite" wants milk, not a
     *    dulce-de-leche wafer. TACO comes ahead of IBGE because it
     *    characterizes the basic food; IBGE complements it with preparations,
     *    which matter when the basic one does not answer — "feijoada" does not
     *    exist in TACO.
     *
     * 2. **How the term matched.** The test is by *whole word*, not by prefix:
     *    without that, "arroz" returns "Arrozina" — where the five letters are
     *    only the start of another word — before plain rice. The order is:
     *    identical name, term opening the name, term as a word in the middle,
     *    and only then term glued inside another word.
     *
     * 3. **Length of the name.** "Arroz, tipo 1, cozido" before "Arroz de forno
     *    com frango e creme de milho" — a short name is usually the generic
     *    one.
     *
     * The term arrives already normalized, without accents and in lowercase.
     */
    @Query("""
           select a from Food a
           where a.active = true
             and (a.accountId is null or a.accountId = :accountId)
             and a.descriptionSearch like concat('%', cast(:term as string), '%')
             and (:group is null or a.group = :group)
             and (:source is null or a.source = :source)
           order by
             case a.source
                  when br.com.nutriplan.food.domain.DataSource.TACO then 0
                  when br.com.nutriplan.food.domain.DataSource.TBCA then 1
                  when br.com.nutriplan.food.domain.DataSource.IBGE then 2
                  else 3 end,
             case when a.descriptionSearch = :term then 0
                  when a.descriptionSearch like concat(:term, ' %')  then 1
                  when a.descriptionSearch like concat(:term, ',%')  then 1
                  when a.descriptionSearch like concat('% ', :term)       then 2
                  when a.descriptionSearch like concat('% ', :term, ' %') then 2
                  when a.descriptionSearch like concat('% ', :term, ',%') then 2
                  when a.descriptionSearch like concat(:term, '%') then 3
                  else 4 end,
             length(a.description),
             a.description
           """)
    Page<Food> findByRelevance(@Param("accountId") Long accountId,
                                       @Param("term") String term,
                                       @Param("group") String group,
                                       @Param("source") DataSource source,
                                       Pageable pageable);

    /**
     * Locates the product by barcode, within the visible catalog.
     *
     * It returns a list, and not a single record, because the code is not
     * unique in the base: the same EAN can exist in the public catalog and in
     * the practice's own registration, and there is duplication inside Open
     * Food Facts itself, which is collaborative. The ordering puts the
     * practice's food first — whoever registered their own product wants to
     * see their own product.
     */
    @Query("""
           select a from Food a
           where a.active = true
             and a.codeBarcode = :code
             and (a.accountId is null or a.accountId = :accountId)
           order by case when a.accountId is null then 1 else 0 end, a.id
           """)
    List<Food> byBarcodeCode(@Param("code") String code,
                                     @Param("accountId") Long accountId);

    /** Loads the food only if it is public or belongs to the account itself. */
    @Query("""
           select a from Food a
           where a.id = :id
             and (a.accountId is null or a.accountId = :accountId)
           """)
    Optional<Food> visibleFind(@Param("id") Long id, @Param("accountId") Long accountId);

    /** Loads several at once, to build a meal without N+1 queries. */
    @Query("""
           select a from Food a
           where a.id in :ids
             and (a.accountId is null or a.accountId = :accountId)
           """)
    List<Food> visibleFind(@Param("ids") List<Long> ids, @Param("accountId") Long accountId);

    /**
     * The practice's recipes. It never brings a recipe from another account,
     * and never brings the public bases: a recipe is always authored.
     */
    @Query("""
           select a from Food a
           where a.active = true
             and a.source = br.com.nutriplan.food.domain.DataSource.RECIPE
             and a.accountId = :accountId
             and (cast(:term as string) is null or a.descriptionSearch like concat('%', cast(:term as string), '%'))
           order by a.description
           """)
    Page<Food> findRecipes(@Param("accountId") Long accountId,
                                  @Param("term") String term,
                                  Pageable pageable);

    @Query("select distinct a.group from Food a where a.group is not null order by a.group")
    List<String> listGroups();

    /** Used by the measures importer to match the table code with the generated id. */
    List<Food> findBySource(DataSource source);

    long countBySource(DataSource source);
}
