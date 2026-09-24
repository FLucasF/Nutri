package br.com.nutriplan.shared.error;

/**
 * O recurso existe, mas pede uma confirmação antes de abrir — a data de
 * nascimento no link do plano. Responde 403 com a mensagem, para a página
 * saber que deve perguntar, e não que o link está errado.
 */
public class AccessConfirmationRequiredException extends RuntimeException {
    public AccessConfirmationRequiredException(String message) {
        super(message);
    }
}
