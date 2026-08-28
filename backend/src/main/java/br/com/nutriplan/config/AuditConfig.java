package br.com.nutriplan.config;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.auth.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
public class AuditConfig {

    private final CurrentContext contextCurrent;

    /** Fills createdBy/updatedBy with the email of the logged-in user. */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> contextCurrent.user()
                .map(AuthenticatedUser::getEmail)
                .or(() -> Optional.of("system"));
    }
}
