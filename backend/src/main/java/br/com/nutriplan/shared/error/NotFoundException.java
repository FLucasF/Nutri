package br.com.nutriplan.shared.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String resource, Object id) {
        super("%s não encontrado(a): %s".formatted(resource, id));
    }

    public NotFoundException(String message) {
        super(message);
    }
}
