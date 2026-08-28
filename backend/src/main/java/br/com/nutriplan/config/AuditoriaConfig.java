package br.com.nutriplan.config;

import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.auth.service.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
public class AuditoriaConfig {

    private final ContextoAtual contextoAtual;

    /** Preenche criadoPor/atualizadoPor com o e-mail do usuario logado. */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> contextoAtual.usuario()
                .map(UsuarioAutenticado::getEmail)
                .or(() -> Optional.of("sistema"));
    }
}
