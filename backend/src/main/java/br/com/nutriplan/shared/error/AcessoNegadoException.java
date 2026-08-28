package br.com.nutriplan.shared.error;

/**
 * Levantada quando um nutricionista tenta acessar dado de outro.
 * Deliberadamente distinta de RecursoNaoEncontrado para o log, mas
 * respondida como 404 para nao vazar existencia de registros alheios.
 */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String recurso, Object id) {
        super("Acesso negado a %s: %s".formatted(recurso, id));
    }
}
