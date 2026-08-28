package br.com.nutriplan.exame.repository;

import br.com.nutriplan.exame.domain.Exame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExameRepository extends JpaRepository<Exame, Long> {

    /**
     * Exames de um paciente, do mais recente para o mais antigo.
     *
     * O parametro vem junto porque toda linha da lista mostra o nome dele —
     * sem o fetch seriam N consultas para desenhar uma tela.
     */
    @Query("""
           select e from Exame e join fetch e.parametro
           where e.pacienteId = :pacienteId and e.contaId = :contaId
           order by e.dataColeta desc, e.parametro.nome asc
           """)
    List<Exame> doPaciente(@Param("pacienteId") Long pacienteId,
                           @Param("contaId") Long contaId);

    /** Serie historica de um parametro, do mais antigo para o mais recente. */
    @Query("""
           select e from Exame e join fetch e.parametro
           where e.pacienteId = :pacienteId and e.contaId = :contaId
             and e.parametro.id = :parametroId
           order by e.dataColeta asc
           """)
    List<Exame> serie(@Param("pacienteId") Long pacienteId,
                      @Param("parametroId") Long parametroId,
                      @Param("contaId") Long contaId);

    @Query("select e from Exame e join fetch e.parametro where e.id = :id and e.contaId = :contaId")
    Optional<Exame> buscarDaConta(@Param("id") Long id, @Param("contaId") Long contaId);
}
