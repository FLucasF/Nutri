package br.com.nutriplan.config;

import br.com.nutriplan.shared.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Exceptions thrown inside the filter chain do not pass through the
 * DispatcherServlet, so the @RestControllerAdvice does not see them. These two
 * handlers guarantee that 401 and 403 come out with the same JSON body as the
 * rest of the API — and that a missing credential becomes a 401, not the 403
 * Spring returns by default when no entry point is configured.
 */
@Component
@RequiredArgsConstructor
public class SecurityAnswers {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint notAuthenticated() {
        return (req, res, ex) -> write(res, req, 401, "Não autenticado",
                "Autenticação necessaria para acessar este recurso");
    }

    public AccessDeniedHandler accessDenied() {
        return (req, res, ex) -> write(res, req, 403, "Sem permissão",
                "Seu perfil não tem permissão para esta operação");
    }

    private void write(HttpServletResponse res, HttpServletRequest req,
                          int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(),
                ErrorResponse.from(status, error, message, req.getRequestURI()));
    }

    // Referenced only to make the contract of the handled types explicit.
    @SuppressWarnings("unused")
    private static final Class<?>[] HANDLED = {AuthenticationException.class, AccessDeniedException.class};
}
