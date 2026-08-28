package br.com.nutriplan.auth.repository;

import br.com.nutriplan.auth.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u join fetch u.conta where lower(u.email) = lower(:email)")
    Optional<Usuario> buscarPorEmailComConta(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Usuarios do consultorio, ativos e inativos: a lista serve para gerir. */
    @Query("select u from Usuario u where u.conta.id = :contaId order by u.nome")
    java.util.List<Usuario> findByContaIdOrderByNomeAsc(@org.springframework.data.repository.query.Param("contaId") Long contaId);

    /** Profissional responsavel pela conta, para assinar o plano do paciente. */
    Optional<Usuario> findFirstByContaIdAndPerfilAndAtivoTrue(
            Long contaId, br.com.nutriplan.auth.domain.Perfil perfil);
}
