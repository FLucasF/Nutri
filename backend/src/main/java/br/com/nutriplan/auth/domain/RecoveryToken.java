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
 * Password reset token.
 *
 * It stores the hash, and not the token. Whoever reads the database cannot
 * reset anybody's password — the same reason the password is stored as a hash.
 */
@Entity
@Table(name = "recovery_token",
        indexes = @Index(name = "ix_recovery_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class RecoveryToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Filled in on use: the token is good once only. */
    @Column(name = "used_at")
    private Instant usedAt;

    public RecoveryToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean usable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
