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

    /** Nome curto: a claim viaja em toda requisicao. */
    public static final String CLAIM_VERSAO_DA_SENHA = "sv";

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(@Value("${nutriplan.jwt.secret}") String segredo,
                      @Value("${nutriplan.jwt.expiration-minutes}") long minutos) {
        // Aceita o segredo em base64 (producao) ou como texto puro (dev).
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(segredo);
        } catch (DecodingException | IllegalArgumentException naoEhBase64) {
            bytes = segredo.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "nutriplan.jwt.secret precisa ter ao menos 32 bytes para HS256");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
        this.validade = Duration.ofMinutes(minutos);
    }

    public String gerar(UsuarioAutenticado u) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(u.getUsuarioId().toString())
                .claims(Map.of(
                        "email", u.getEmail(),
                        "nome", u.getNome(),
                        "perfil", u.getPerfil().name(),
                        "contaId", u.getContaId(),
                        // Versao da senha vigente na emissao. Trocar a senha
                        // incrementa o numero e faz todo token anterior deixar
                        // de casar — e o que derruba as sessoes abertas num
                        // sistema que nao guarda sessao.
                        CLAIM_VERSAO_DA_SENHA, u.getSenhaVersao()))
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave)
                .compact();
    }

    /** Devolve as claims se o token for valido e nao expirado; vazio caso contrario. */
    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT rejeitado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long validadeEmSegundos() {
        return validade.toSeconds();
    }
}
