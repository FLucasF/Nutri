package br.com.nutriplan.labtest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nutriplan.labtest.domain.LabtestPanel;

public interface LabtestPanelRepository extends JpaRepository<LabtestPanel, Long> {

    /**
     * Os painéis visíveis ao consultório: os do sistema mais os próprios.
     *
     * Os próprios vêm primeiro. Ele criou aquele painel porque usa, e o que se
     * usa todo dia não deveria estar abaixo de 25 que se usam raramente.
     */
    @Query("""
           select distinct p from LabtestPanel p
           left join fetch p.parameters pp
           left join fetch pp.parameter
           where p.active = true and (p.accountId is null or p.accountId = :accountId)
           order by case when p.accountId is null then 1 else 0 end, p.order, p.name
           """)
    List<LabtestPanel> visibleTo(@Param("accountId") Long accountId);

    @Query("""
           select p from LabtestPanel p
           left join fetch p.parameters pp
           left join fetch pp.parameter
           where p.id = :id and (p.accountId is null or p.accountId = :accountId)
           """)
    Optional<LabtestPanel> visibleFind(@Param("id") Long id, @Param("accountId") Long accountId);
}
