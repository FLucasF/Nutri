package br.com.nutriplan.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalErrorHandler {

    /*
     * Toda resposta daqui declara que e JSON.
     *
     * Sem isso, quem pede o PDF de um plano com `Accept: application/pdf` e
     * esbarra numa regra de negocio nao recebe a regra: o Spring nao consegue
     * render o corpo de erro no formato pedido, e a resposta vira 500. O
     * erro precisa se explicar independentemente do que o chamador aceita —
     * e especialmente quando o chamador esperava um arquivo.
     */

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(404, "Não encontrado", e.getMessage(), req.getRequestURI()));
    }

    /**
     * Access to another nutritionist's data answers 404, not 403: a 403 would
     * confirm that the record exists.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> accessDenied(ForbiddenException e, HttpServletRequest req) {
        log.warn("Tentativa de acesso cruzado: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(404, "Não encontrado", "Recurso não encontrado", req.getRequestURI()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> businessRule(BusinessRuleException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(422, "Regra de negocio", e.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErrorResponse.InvalidField> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.InvalidField(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON).body(new ErrorResponse(
                Instant.now(), 400, "Validacao", "Dados inválidos", req.getRequestURI(), fields));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Violacao de integridade em {}", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(409, "Conflito",
                        "Operação viola uma restricao de integridade dos dados", req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> notAuthenticated(AuthenticationException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(401, "Não autenticado", "Credenciais inválidas", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> withoutPermission(AccessDeniedException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(403, "Sem permissão", "Acesso negado", req.getRequestURI()));
    }

    /**
     * A route that does not exist.
     *
     * Without this the request falls into the generic handler and answers 500
     * "unexpected error", with a whole stack in the log. A wrong path is not a
     * server failure: it is a 404, and the caller needs to read that and not an
     * internal error.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> routeNonexistent(NoResourceFoundException e,
                                                        HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(404, "Rota não encontrada",
                        "Este endereço não existe nesta API. Confira o caminho e o método HTTP.",
                        req.getRequestURI()));
    }

    /**
     * Right path, wrong verb.
     *
     * It answers 405 and not 404 on purpose: a 404 would say the address does
     * not exist, and the caller would go check the path — which is right. The
     * distinction between "does not exist" and "exists, but not with this
     * method" is the difference between searching in the wrong place and
     * fixing one line.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException e,
                                                           HttpServletRequest req) {
        var accepted = e.getSupportedHttpMethods();
        String list = accepted == null || accepted.isEmpty() ? null
                : accepted.stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(405, "Método não permitido",
                        "Este endereço não responde a " + e.getMethod()
                                + (list == null ? "." : ". Métodos aceitos: " + list + "."),
                        req.getRequestURI()));
    }

    /**
     * A body the server cannot read: broken JSON, unknown enum, date in the
     * wrong format.
     *
     * This used to fall into the generic handler too, and the client received
     * "unexpected error" without the one piece of information that solves the
     * problem — which field. When the cause is an invalid value, Jackson knows
     * the field and the accepted values; the message passes both along.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> bodyUnreadable(HttpMessageNotReadableException e,
                                                      HttpServletRequest req) {
        if (e.getCause() instanceof InvalidFormatException failure) {
            String field = fieldPath(failure);
            String accepted = valuesAccepted(failure);
            String message = "O campo \"" + field + "\" não aceita o valor enviado"
                    + (accepted == null ? "." : ". Valores aceitos: " + accepted + ".");
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON).body(new ErrorResponse(
                    Instant.now(), 400, "Dados inválidos", message, req.getRequestURI(),
                    List.of(new ErrorResponse.InvalidField(field, message))));
        }
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON).body(ErrorResponse.from(400, "Dados inválidos",
                "O conteúdo enviado não pôde ser lido. Confira o formato dos dados.",
                req.getRequestURI()));
    }

    /** A path or query parameter with the wrong type: /patients/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalidParameter(MethodArgumentTypeMismatchException e,
                                                          HttpServletRequest req) {
        String message = "O parâmetro \"" + e.getName() + "\" recebeu um valor que não serve.";
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON).body(new ErrorResponse(
                Instant.now(), 400, "Dados inválidos", message, req.getRequestURI(),
                List.of(new ErrorResponse.InvalidField(e.getName(), message))));
    }

    /** The path to the field, like "meals[2].items[0].quantity". */
    private String fieldPath(InvalidFormatException failure) {
        StringBuilder path = new StringBuilder();
        for (var step : failure.getPath()) {
            if (step.getFieldName() != null) {
                if (path.length() > 0) {
                    path.append('.');
                }
                path.append(step.getFieldName());
            } else if (step.getIndex() >= 0) {
                path.append('[').append(step.getIndex()).append(']');
            }
        }
        return path.length() == 0 ? "body" : path.toString();
    }

    /** For an enum, the list of what serves. For the other types, nothing to offer. */
    private String valuesAccepted(InvalidFormatException failure) {
        Class<?> type = failure.getTargetType();
        if (type == null || !type.isEnum()) {
            return null;
        }
        return java.util.Arrays.stream(type.getEnumConstants())
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest req) {
        log.error("Erro inesperado em {}", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(500, "Erro interno",
                        "Ocorreu um erro inesperado", req.getRequestURI()));
    }
}
