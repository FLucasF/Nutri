package br.com.nutriplan.shared.error;

/** Violacao de invariante de dominio — vira 422 na borda HTTP. */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
