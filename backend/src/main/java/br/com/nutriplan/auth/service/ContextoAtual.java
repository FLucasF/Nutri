package br.com.nutriplan.auth.service;

import br.com.nutriplan.shared.error.RegraDeNegocioException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Ponto unico de acesso ao usuario autenticado. Todo servico que le ou grava
 * dado clinico deve obter o contaId daqui — nunca de um parametro vindo do
 * cliente, sob pena de permitir acesso cruzado entre consultorios.
 */
@Component
public class ContextoAtual {

    public Optional<UsuarioAutenticado> usuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public UsuarioAutenticado exigirUsuario() {
        return usuario().orElseThrow(() -> new RegraDeNegocioException("Nenhum usuário autenticado no contexto"));
    }

    /** Conta (tenant) do request corrente. */
    public Long contaId() {
        return exigirUsuario().getContaId();
    }

    public Long usuarioId() {
        return exigirUsuario().getUsuarioId();
    }
}
