package br.com.nutriplan.handout.repository;

import br.com.nutriplan.handout.domain.Handout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HandoutRepository extends JpaRepository<Handout, Long> {

    /**
     * The library visible to the account: the system templates plus the
     * practice's own texts. Same query as the food catalog.
     */
    /*
     * O termo de busca vai com tipo declarado.
     *
     * Sem o cast, um termo nulo dentro do concat faz o PostgreSQL assumir
     * bytea e recusar a consulta inteira. So aparece no banco de verdade: o H2
     * em modo de compatibilidade assume texto e responde normalmente.
     */
    @Query("""
           select o from Handout o
           where o.active = true
             and (o.accountId is null or o.accountId = :accountId)
             and (cast(:term as string) is null or lower(o.title) like concat('%', cast(:term as string), '%'))
           order by case when o.accountId is null then 1 else 0 end, o.title
           """)
    Page<Handout> visibleTo(@Param("accountId") Long accountId,
                                  @Param("term") String term,
                                  Pageable pageable);

    @Query("""
           select o from Handout o
           where o.id = :id and (o.accountId is null or o.accountId = :accountId)
           """)
    Optional<Handout> visibleFind(@Param("id") Long id, @Param("accountId") Long accountId);
}
