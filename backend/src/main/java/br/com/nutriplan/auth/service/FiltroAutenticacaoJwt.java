package br.com.nutriplan.auth.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final DetalhesUsuarioService detalhesUsuarioService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIXO)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        jwtService.validar(header.substring(PREFIXO.length()))
                .ifPresent(claims -> autenticar(claims.getSubject(),
                        claims.get(JwtService.CLAIM_VERSAO_DA_SENHA, Integer.class)));

        chain.doFilter(request, response);
    }

    /**
     * Recarrega o usuario do banco em vez de confiar nas claims: garante que
     * desativacao de conta ou troca de perfil valem imediatamente, sem esperar
     * o token expirar.
     */
    private void autenticar(String subject, Integer versaoNoToken) {
        try {
            UsuarioAutenticado usuario = detalhesUsuarioService.carregarPorId(Long.valueOf(subject));
            if (!usuario.isEnabled()) {
                return;
            }
            // Token emitido antes da ultima troca de senha nao vale mais.
            //
            // A autenticacao e sem estado, entao nao ha sessao para encerrar
            // quando alguem redefine a senha. Esta comparacao e o que derruba
            // as sessoes abertas — sem ela, quem obteve a senha antiga
            // continuaria dentro ate o token expirar sozinho.
            //
            // Token sem a claim conta como versao zero: e o que os tokens
            // emitidos antes desta funcionalidade existirem trazem, e nenhum
            // deles precisa ser derrubado.
            int versao = versaoNoToken == null ? 0 : versaoNoToken;
            if (versao != usuario.getSenhaVersao()) {
                logger.debug("Token de versão de senha antiga para o usuário " + subject);
                return;
            }
            var auth = new UsernamePasswordAuthenticationToken(
                    usuario, null, usuario.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (NumberFormatException | UsernameNotFoundException e) {
            logger.debug("Token com subject inválido: " + subject);
        }
    }
}
