package br.com.nutriplan.auth.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Token de redefinicao de senha.
 *
 * Guarda o hash, e nao o token. Quem ler o banco nao consegue redefinir a senha
 * de ninguem — mesma razao pela qual a senha e guardada como hash.
 */
@Entity
@Table(name = "token_de_recuperacao",
        indexes = @Index(name = "ix_recuperacao_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
public class TokenDeRecuperacao extends BaseEntity {

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    /** Preenchido no uso: o token vale uma vez so. */
    @Column(name = "usado_em")
    private Instant usadoEm;

    public TokenDeRecuperacao(Long usuarioId, String tokenHash, Instant expiraEm) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
    }

    public boolean utilizavel(Instant agora) {
        return usadoEm == null && agora.isBefore(expiraEm);
    }
}
