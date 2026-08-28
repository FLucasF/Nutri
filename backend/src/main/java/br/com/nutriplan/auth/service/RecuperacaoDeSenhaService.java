package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.TokenDeRecuperacao;
import br.com.nutriplan.auth.domain.Usuario;
import br.com.nutriplan.auth.repository.TokenDeRecuperacaoRepository;
import br.com.nutriplan.auth.repository.UsuarioRepository;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
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
 * Redefinição de senha por link.
 *
 * Três decisões de segurança sustentam este serviço, e cada uma existe contra
 * um ataque concreto.
 *
 * <p><b>Pedir recuperação nunca diz se o e-mail existe.</b> A resposta é a
 * mesma para endereço cadastrado e não cadastrado. Um endpoint que responde
 * "e-mail não encontrado" é um oráculo: dá para varrer uma lista de endereços e
 * descobrir quem tem conta, o que já é informação suficiente para um golpe
 * dirigido.
 *
 * <p><b>O banco guarda o hash do token, e não o token.</b> Quem ler o banco —
 * um dump vazado, um backup mal guardado — não consegue redefinir a senha de
 * ninguém. É a mesma razão pela qual a senha é guardada como hash.
 *
 * <p><b>Redefinir derruba as sessões abertas.</b> A autenticação é sem estado e
 * não há lista de sessões para encerrar; o que existe é a data da última troca,
 * contra a qual o filtro compara a emissão do token. Sem isso, quem obteve a
 * senha antiga continuaria dentro até o token expirar — justamente o caso em
 * que a senha está sendo trocada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecuperacaoDeSenhaService {

    /**
     * Uma hora. Curto o bastante para reduzir a janela de um link interceptado,
     * e longo o bastante para quem só abre o e-mail no fim do expediente.
     */
    private static final Duration VALIDADE = Duration.ofHours(1);

    private static final SecureRandom SORTEIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenDeRecuperacaoRepository tokenRepository;
    private final PasswordEncoder codificador;
    private final EnviadorDeRecuperacao enviador;

    /**
     * Cria o token e o entrega ao enviador.
     *
     * Não devolve nada e não falha por e-mail inexistente: quem chamou não pode
     * distinguir os dois casos.
     */
    @Transactional
    public void solicitar(String email) {
        Instant agora = Instant.now();
        usuarioRepository.buscarPorEmailComConta(email == null ? "" : email.trim())
                .filter(Usuario::isAtivo)
                .ifPresentOrElse(usuario -> {
                    // Pedir de novo cancela o pedido anterior: dois links
                    // válidos ao mesmo tempo dobram a janela sem servir a nada.
                    tokenRepository.invalidarPendentesDe(usuario.getId(), agora);

                    String token = sortear();
                    tokenRepository.save(new TokenDeRecuperacao(
                            usuario.getId(), resumir(token), agora.plus(VALIDADE)));

                    enviador.enviar(usuario.getEmail(), usuario.getNome(), token,
                            VALIDADE.toMinutes());
                    log.info("Recuperação de senha solicitada: usuário={}", usuario.getId());
                }, () -> log.info("Recuperação pedida para e-mail sem conta ativa; "
                        + "resposta identica ao caso com conta."));
    }

    @Transactional
    public void redefinir(String token, String novaSenha) {
        Instant agora = Instant.now();

        TokenDeRecuperacao registro = tokenRepository.findByTokenHash(resumir(token))
                .filter(t -> t.utilizavel(agora))
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Este link não vale mais. Peça a recuperação de novo."));

        Usuario usuario = usuarioRepository.findById(registro.getUsuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Este link não vale mais. Peça a recuperação de novo."));

        // Incrementa a versão da senha, e com isso derruba as sessões abertas.
        usuario.trocarSenha(codificador.encode(novaSenha), agora);
        registro.setUsadoEm(agora);

        log.info("Senha redefinida: usuário={}", usuario.getId());
    }

    /**
     * 32 bytes de aleatoriedade criptográfica, em base64 sem padding.
     *
     * Vai na URL, então precisa sobreviver a cliente de e-mail que quebra
     * linha e a navegador que reescapa caractere.
     */
    private String sortear() {
        byte[] bytes = new byte[32];
        SORTEIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 e não BCrypt, aqui.
     *
     * BCrypt é lento de propósito, o que é uma virtude para senha — que é curta
     * e adivinhável — e desnecessário para um token de 256 bits sorteado, que
     * não se quebra por força bruta. E BCrypt salga cada hash, o que impediria
     * a busca direta pelo token no banco.
     */
    private String resumir(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
