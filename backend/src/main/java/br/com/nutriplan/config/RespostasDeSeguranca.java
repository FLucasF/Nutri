package br.com.nutriplan.config;

import br.com.nutriplan.shared.error.ErroResposta;
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
 * Excecoes lancadas dentro da cadeia de filtros nao passam pelo
 * DispatcherServlet, entao o @RestControllerAdvice nao as enxerga. Estes dois
 * handlers garantem que 401 e 403 saiam com o mesmo corpo JSON do resto da API
 * — e que falta de credencial vire 401, nao o 403 que o Spring devolve por
 * padrao quando nao ha entry point configurado.
 */
@Component
@RequiredArgsConstructor
public class RespostasDeSeguranca {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint naoAutenticado() {
        return (req, res, ex) -> escrever(res, req, 401, "Não autenticado",
                "Autenticação necessaria para acessar este recurso");
    }

    public AccessDeniedHandler acessoNegado() {
        return (req, res, ex) -> escrever(res, req, 403, "Sem permissão",
                "Seu perfil não tem permissão para esta operação");
    }

    private void escrever(HttpServletResponse res, HttpServletRequest req,
                          int status, String erro, String mensagem) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(),
                ErroResposta.de(status, erro, mensagem, req.getRequestURI()));
    }

    // Referenciados apenas para deixar explicito o contrato dos tipos tratados.
    @SuppressWarnings("unused")
    private static final Class<?>[] TRATADAS = {AuthenticationException.class, AccessDeniedException.class};
}
