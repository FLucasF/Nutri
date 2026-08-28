package br.com.nutriplan.auth.repository;

import br.com.nutriplan.auth.domain.RecoveryToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RecoveryTokenRepository extends JpaRepository<RecoveryToken, Long> {

    Optional<RecoveryToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates the user's earlier requests.
     *
     * Asking again cancels the old request: two valid links at the same time
     * would double the attack window without serving anyone.
     */
    @Modifying
    @Query("""
           update RecoveryToken t set t.usedAt = :now
           where t.userId = :userId and t.usedAt is null
           """)
    void invalidatePendingDe(@Param("userId") Long userId, @Param("now") Instant now);
}
