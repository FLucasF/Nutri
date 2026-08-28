package br.com.nutriplan.auth.repository;

import br.com.nutriplan.auth.domain.TokenDeRecuperacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TokenDeRecuperacaoRepository extends JpaRepository<TokenDeRecuperacao, Long> {

    Optional<TokenDeRecuperacao> findByTokenHash(String tokenHash);

    /**
     * Invalida os pedidos anteriores do usuario.
     *
     * Pedir de novo cancela o pedido antigo: dois links validos ao mesmo tempo
     * dobrariam a janela de ataque sem servir a ninguem.
     */
    @Modifying
    @Query("""
           update TokenDeRecuperacao t set t.usadoEm = :agora
           where t.usuarioId = :usuarioId and t.usadoEm is null
           """)
    void invalidarPendentesDe(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);
}
