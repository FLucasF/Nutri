package br.com.nutriplan.auth.dto;

import br.com.nutriplan.auth.domain.Perfil;
import br.com.nutriplan.auth.domain.Plano;

public record TokenResponse(
        String token,
        String tipo,
        long expiraEmSegundos,
        UsuarioResumo usuario
) {
    public record UsuarioResumo(Long id, String nome, String email, Perfil perfil, Long contaId, Plano plano) {}

    public static TokenResponse de(String token, long expiraEm,
                                   br.com.nutriplan.auth.service.UsuarioAutenticado u) {
        return new TokenResponse(token, "Bearer", expiraEm, new UsuarioResumo(
                u.getUsuarioId(), u.getNome(), u.getEmail(), u.getPerfil(), u.getContaId(), u.getPlano()));
    }
}
