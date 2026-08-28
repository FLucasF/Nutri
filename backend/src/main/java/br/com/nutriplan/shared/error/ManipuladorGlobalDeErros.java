package br.com.nutriplan.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
public class ManipuladorGlobalDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(RecursoNaoEncontradoException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResposta.de(404, "Não encontrado", e.getMessage(), req.getRequestURI()));
    }

    /**
     * Acesso a dado de outro nutricionista responde 404, nao 403: um 403
     * confirmaria que o registro existe.
     */
    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResposta> acessoNegado(AcessoNegadoException e, HttpServletRequest req) {
        log.warn("Tentativa de acesso cruzado: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResposta.de(404, "Não encontrado", "Recurso não encontrado", req.getRequestURI()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResposta> regraDeNegocio(RegraDeNegocioException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErroResposta.de(422, "Regra de negocio", e.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErroResposta.CampoInvalido> campos = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErroResposta.CampoInvalido(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErroResposta(
                Instant.now(), 400, "Validacao", "Dados inválidos", req.getRequestURI(), campos));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Violacao de integridade em {}", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResposta.de(409, "Conflito",
                        "Operação viola uma restricao de integridade dos dados", req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> naoAutenticado(AuthenticationException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErroResposta.de(401, "Não autenticado", "Credenciais inválidas", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> semPermissao(AccessDeniedException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErroResposta.de(403, "Sem permissão", "Acesso negado", req.getRequestURI()));
    }

    /**
     * Rota que nao existe.
     *
     * Sem isto o pedido cai no manipulador generico e responde 500 "erro
     * inesperado", com pilha inteira no log. Um caminho errado nao e falha do
     * servidor: e 404, e quem chamou precisa ler isso e nao um erro interno.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException e,
                                                        HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResposta.de(404, "Rota não encontrada",
                        "Este endereço não existe nesta API. Confira o caminho e o método HTTP.",
                        req.getRequestURI()));
    }

    /**
     * Caminho certo, verbo errado.
     *
     * Responde 405 e nao 404 de proposito: 404 diria que o endereco nao existe,
     * e quem chamou iria conferir o caminho — que esta certo. A distincao entre
     * "não existe" e "existe, mas não com este método" e a diferenca entre
     * procurar no lugar errado e corrigir numa linha.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> metodoNaoSuportado(HttpRequestMethodNotSupportedException e,
                                                           HttpServletRequest req) {
        var aceitos = e.getSupportedHttpMethods();
        String lista = aceitos == null || aceitos.isEmpty() ? null
                : aceitos.stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErroResposta.de(405, "Método não permitido",
                        "Este endereço não responde a " + e.getMethod()
                                + (lista == null ? "." : ". Métodos aceitos: " + lista + "."),
                        req.getRequestURI()));
    }

    /**
     * Corpo que o servidor nao consegue ler: JSON quebrado, enum desconhecido,
     * data em formato errado.
     *
     * Tambem caia no generico antes, e o cliente recebia "erro inesperado" sem
     * a unica informacao que resolve o problema — qual campo. Quando a causa e
     * um valor invalido, Jackson sabe o campo e os valores aceitos; a mensagem
     * repassa os dois.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> corpoIlegivel(HttpMessageNotReadableException e,
                                                      HttpServletRequest req) {
        if (e.getCause() instanceof InvalidFormatException falha) {
            String campo = caminhoDoCampo(falha);
            String aceitos = valoresAceitos(falha);
            String mensagem = "O campo \"" + campo + "\" não aceita o valor enviado"
                    + (aceitos == null ? "." : ". Valores aceitos: " + aceitos + ".");
            return ResponseEntity.badRequest().body(new ErroResposta(
                    Instant.now(), 400, "Dados inválidos", mensagem, req.getRequestURI(),
                    List.of(new ErroResposta.CampoInvalido(campo, mensagem))));
        }
        return ResponseEntity.badRequest().body(ErroResposta.de(400, "Dados inválidos",
                "O conteúdo enviado não pôde ser lido. Confira o formato dos dados.",
                req.getRequestURI()));
    }

    /** Parametro de rota ou de consulta com tipo errado: /pacientes/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> parametroInvalido(MethodArgumentTypeMismatchException e,
                                                          HttpServletRequest req) {
        String mensagem = "O parâmetro \"" + e.getName() + "\" recebeu um valor que não serve.";
        return ResponseEntity.badRequest().body(new ErroResposta(
                Instant.now(), 400, "Dados inválidos", mensagem, req.getRequestURI(),
                List.of(new ErroResposta.CampoInvalido(e.getName(), mensagem))));
    }

    /** O caminho ate o campo, como "refeicoes[2].itens[0].quantidade". */
    private String caminhoDoCampo(InvalidFormatException falha) {
        StringBuilder caminho = new StringBuilder();
        for (var passo : falha.getPath()) {
            if (passo.getFieldName() != null) {
                if (caminho.length() > 0) {
                    caminho.append('.');
                }
                caminho.append(passo.getFieldName());
            } else if (passo.getIndex() >= 0) {
                caminho.append('[').append(passo.getIndex()).append(']');
            }
        }
        return caminho.length() == 0 ? "corpo" : caminho.toString();
    }

    /** Para enum, a lista do que serve. Para os demais tipos, nada a oferecer. */
    private String valoresAceitos(InvalidFormatException falha) {
        Class<?> tipo = falha.getTargetType();
        if (tipo == null || !tipo.isEnum()) {
            return null;
        }
        return java.util.Arrays.stream(tipo.getEnumConstants())
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception e, HttpServletRequest req) {
        log.error("Erro inesperado em {}", req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResposta.de(500, "Erro interno",
                        "Ocorreu um erro inesperado", req.getRequestURI()));
    }
}
