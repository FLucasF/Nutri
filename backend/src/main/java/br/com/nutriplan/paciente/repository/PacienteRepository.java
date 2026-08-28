package br.com.nutriplan.paciente.repository;

import br.com.nutriplan.paciente.domain.Paciente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Todo metodo recebe contaId explicitamente. Nao existe consulta sem esse
 * filtro por design: e o que impede um consultorio de ler dados de outro.
 */
public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    Optional<Paciente> findByIdAndContaId(Long id, Long contaId);

    @Query("""
           select p from Paciente p
           where p.contaId = :contaId
             and (:ativo is null or p.ativo = :ativo)
             and (:termo is null
                  or lower(p.nome)     like lower(concat('%', :termo, '%'))
                  or lower(p.email)    like lower(concat('%', :termo, '%'))
                  or p.telefone        like concat('%', :termo, '%'))
           """)
    Page<Paciente> buscar(@Param("contaId") Long contaId,
                          @Param("termo") String termo,
                          @Param("ativo") Boolean ativo,
                          Pageable pageable);

    long countByContaIdAndAtivoTrue(Long contaId);

    boolean existsByContaIdAndCpf(Long contaId, String cpf);

    Optional<Paciente> findByUsuarioId(Long usuarioId);
}
