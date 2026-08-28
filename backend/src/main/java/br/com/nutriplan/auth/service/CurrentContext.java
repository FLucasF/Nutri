package br.com.nutriplan.auth.service;

import br.com.nutriplan.shared.error.BusinessRuleException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single point of access to the authenticated user. Every service that
 * reads or writes clinical data must obtain the accountId from here — never
 * from a parameter coming from the client, on pain of allowing cross access
 * between practices.
 */
@Component
public class CurrentContext {

    public Optional<AuthenticatedUser> user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof AuthenticatedUser u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public AuthenticatedUser requireUser() {
        return user().orElseThrow(() -> new BusinessRuleException("Nenhum usuário autenticado no contexto"));
    }

    /** Account (tenant) of the current request. */
    public Long accountId() {
        return requireUser().getAccountId();
    }

    public Long userId() {
        return requireUser().getUserId();
    }
}
