package br.com.nutriplan.shared.error;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String recurso, Object id) {
        super("%s não encontrado(a): %s".formatted(recurso, id));
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
