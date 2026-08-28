package br.com.nutriplan.prescricao.repository;

import br.com.nutriplan.prescricao.domain.PlanoAlimentar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanoAlimentarRepository extends JpaRepository<PlanoAlimentar, Long> {

    Optional<PlanoAlimentar> findByIdAndContaId(Long id, Long contaId);

    @Query("""
           select p from PlanoAlimentar p
           where p.contaId = :contaId
             and (:pacienteId is null or p.pacienteId = :pacienteId)
             and (:modelo is null or p.modelo = :modelo)
             and (:termo is null or lower(p.titulo) like lower(concat('%', :termo, '%')))
           """)
    Page<PlanoAlimentar> buscar(@Param("contaId") Long contaId,
                                @Param("pacienteId") Long pacienteId,
                                @Param("modelo") Boolean modelo,
                                @Param("termo") String termo,
                                Pageable pageable);

    /**
     * Carrega o plano pelo endereco publico com as refeicoes.
     *
     * Busca apenas um nivel de colecao: o Hibernate recusa buscar refeicoes e
     * itens no mesmo fetch join, porque ambas sao listas ordenadas e o produto
     * cartesiano tornaria a ordem irrecuperavel. Os itens vem em seguida, em
     * lote, pelo @BatchSize declarado em Refeicao.
     */
    @Query("""
           select distinct p from PlanoAlimentar p
           left join fetch p.refeicoes
           where p.identificadorPublico = :identificador
           """)
    Optional<PlanoAlimentar> buscarPorIdentificadorPublico(
            @Param("identificador") String identificador);

    @Query("""
           select distinct p from PlanoAlimentar p
           left join fetch p.refeicoes
           where p.id = :id and p.contaId = :contaId
           """)
    Optional<PlanoAlimentar> carregarCompleto(@Param("id") Long id, @Param("contaId") Long contaId);

    List<PlanoAlimentar> findByContaIdAndPacienteIdOrderByCriadoEmDesc(Long contaId, Long pacienteId);

    long countByContaIdAndPacienteId(Long contaId, Long pacienteId);
}
