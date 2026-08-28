package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.RecoveryToken;
import br.com.nutriplan.auth.domain.User;
import br.com.nutriplan.auth.repository.RecoveryTokenRepository;
import br.com.nutriplan.auth.repository.UserRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Password reset by link.
 *
 * Three security decisions hold this service up, and each one exists against a
 * concrete attack.
 *
 * <p><b>Asking for recovery never says whether the email exists.</b> The answer
 * is the same for a registered and an unregistered address. An endpoint that
 * answers "email not found" is an oracle: you can sweep a list of addresses and
 * find out who has an account, which is already enough information for a
 * targeted scam.
 *
 * <p><b>The database stores the hash of the token, and not the token.</b>
 * Whoever reads the database — a leaked dump, a badly kept backup — cannot
 * reset anybody's password. It is the same reason the password is stored as a
 * hash.
 *
 * <p><b>Resetting drops the open sessions.</b> Authentication is stateless and
 * there is no list of sessions to end; what exists is the date of the last
 * change, against which the filter compares the token's issue. Without that,
 * whoever obtained the old password would stay inside until the token expired —
 * precisely the case in which the password is being changed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryService {

    /**
     * One hour. Short enough to reduce the window for an intercepted link, and
     * long enough for someone who only opens their email at the end of the day.
     */
    private static final Duration EXPIRY = Duration.ofHours(1);

    private static final SecureRandom DRAW = new SecureRandom();

    private final UserRepository userRepository;
    private final RecoveryTokenRepository tokenRepository;
    private final PasswordEncoder encoder;
    private final RecoverySender sender;

    /**
     * Creates the token and hands it to the sender.
     *
     * It returns nothing and does not fail on a nonexistent email: the caller
     * must not be able to tell the two cases apart.
     */
    @Transactional
    public void request(String email) {
        Instant now = Instant.now();
        userRepository.findByEmailComAccount(email == null ? "" : email.trim())
                .filter(User::isActive)
                .ifPresentOrElse(user -> {
                    // Asking again cancels the previous request: two valid links
                    // at the same time double the window without serving
                    // anything.
                    tokenRepository.invalidatePendingDe(user.getId(), now);

                    String token = draw();
                    tokenRepository.save(new RecoveryToken(
                            user.getId(), summarize(token), now.plus(EXPIRY)));

                    sender.send(user.getEmail(), user.getName(), token,
                            EXPIRY.toMinutes());
                    log.info("Recuperação de senha solicitada: usuário={}", user.getId());
                }, () -> log.info("Recuperação pedida para e-mail sem conta ativa; "
                        + "resposta identica ao caso com conta."));
    }

    @Transactional
    public void reset(String token, String novaPassword) {
        Instant now = Instant.now();

        RecoveryToken entry = tokenRepository.findByTokenHash(summarize(token))
                .filter(t -> t.usable(now))
                .orElseThrow(() -> new BusinessRuleException(
                        "Este link não vale mais. Peça a recuperação de novo."));

        User user = userRepository.findById(entry.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessRuleException(
                        "Este link não vale mais. Peça a recuperação de novo."));

        // It increments the password version, and with that drops the open sessions.
        user.changePassword(encoder.encode(novaPassword), now);
        entry.setUsedAt(now);

        log.info("Senha redefinida: usuário={}", user.getId());
    }

    /**
     * 32 bytes of cryptographic randomness, in base64 without padding.
     *
     * It travels in the URL, so it has to survive an email client that wraps
     * lines and a browser that re-escapes characters.
     */
    private String draw() {
        byte[] bytes = new byte[32];
        DRAW.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 and not BCrypt, here.
     *
     * BCrypt is slow on purpose, which is a virtue for a password — which is
     * short and guessable — and unnecessary for a 256-bit drawn token, which
     * does not break by brute force. And BCrypt salts every hash, which would
     * prevent looking the token up directly in the database.
     */
    private String summarize(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
