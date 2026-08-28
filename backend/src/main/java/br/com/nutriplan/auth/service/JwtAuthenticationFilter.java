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
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final JpaUserDetailsService detailsUserService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        jwtService.validate(header.substring(PREFIX.length()))
                .ifPresent(claims -> authenticate(claims.getSubject(),
                        claims.get(JwtService.CLAIM_PASSWORD_VERSION, Integer.class)));

        chain.doFilter(request, response);
    }

    /**
     * Reloads the user from the database instead of trusting the claims: it
     * guarantees that deactivating an account or changing a role takes effect
     * immediately, without waiting for the token to expire.
     */
    private void authenticate(String subject, Integer versionNoToken) {
        try {
            AuthenticatedUser user = detailsUserService.loadById(Long.valueOf(subject));
            if (!user.isEnabled()) {
                return;
            }
            // A token issued before the last password change no longer holds.
            //
            // Authentication is stateless, so there is no session to end when
            // somebody resets the password. This comparison is what drops the
            // open sessions — without it, whoever obtained the old password
            // would stay inside until the token expired on its own.
            //
            // A token without the claim counts as version zero: that is what
            // the tokens issued before this feature existed carry, and none of
            // them needs to be dropped.
            int version = versionNoToken == null ? 0 : versionNoToken;
            if (version != user.getPasswordVersion()) {
                logger.debug("Token de versão de senha antiga para o usuário " + subject);
                return;
            }
            var auth = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (NumberFormatException | UsernameNotFoundException e) {
            logger.debug("Token com subject inválido: " + subject);
        }
    }
}
