package br.com.nutriplan.shared.error;

import java.time.Instant;
import java.util.List;

/** Corpo padrao de erro da API. */
public record ErroResposta(
        Instant momento,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<CampoInvalido> campos
) {
    public record CampoInvalido(String campo, String mensagem) {}

    public static ErroResposta de(int status, String erro, String mensagem, String caminho) {
        return new ErroResposta(Instant.now(), status, erro, mensagem, caminho, null);
    }
}
