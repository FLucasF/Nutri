package br.com.nutriplan.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class JwtService {

    /** A short name: the claim travels in every request. */
    public static final String CLAIM_PASSWORD_VERSION = "sv";

    private final SecretKey key;
    private final Duration expiry;

    public JwtService(@Value("${nutriplan.jwt.secret}") String secret,
                      @Value("${nutriplan.jwt.expiration-minutes}") long minutes) {
        // It accepts the secret in base64 (production) or as plain text (dev).
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException | IllegalArgumentException notEhBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "nutriplan.jwt.secret precisa ter ao menos 32 bytes para HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiry = Duration.ofMinutes(minutes);
    }

    public String generate(AuthenticatedUser u) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(u.getUserId().toString())
                .claims(Map.of(
                        "email", u.getEmail(),
                        "name", u.getName(),
                        "role", u.getRole().name(),
                        "accountId", u.getAccountId(),
                        // The password version in force at issue. Changing the
                        // password increments the number and makes every
                        // earlier token stop matching — which is what drops the
                        // open sessions in a system that keeps no session.
                        CLAIM_PASSWORD_VERSION, u.getPasswordVersion()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    /** Returns the claims if the token is valid and not expired; empty otherwise. */
    public Optional<Claims> validate(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT rejeitado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long expiryAtSeconds() {
        return expiry.toSeconds();
    }
}
