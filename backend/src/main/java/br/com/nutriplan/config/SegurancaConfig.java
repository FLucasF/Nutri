package br.com.nutriplan.config;

import br.com.nutriplan.auth.service.DetalhesUsuarioService;
import br.com.nutriplan.auth.service.FiltroAutenticacaoJwt;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SegurancaConfig {

    private final FiltroAutenticacaoJwt filtroJwt;
    private final DetalhesUsuarioService detalhesUsuarioService;
    private final RespostasDeSeguranca respostasDeSeguranca;

    @Value("${nutriplan.cors.allowed-origins}")
    private String origensPermitidas;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(detalhesUsuarioService);
        // Sem isso o Spring pula o BCrypt quando o usuario nao existe, e a
        // diferenca de tempo de resposta denuncia quais e-mails estao cadastrados.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/cadastro").permitAll()
                // Recuperacao de senha e, por definicao, para quem nao consegue entrar.
                .requestMatchers("/api/auth/recuperar-senha", "/api/auth/redefinir-senha").permitAll()
                .requestMatchers("/actuator/health", "/h2/**").permitAll()
                .requestMatchers("/docs/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Plano aberto pelo link entregue ao paciente. A autorizacao e a
                // posse do identificador, um UUID; o servico so serve plano publicado.
                .requestMatchers(HttpMethod.GET, "/api/publico/planos/**").permitAll()
                // Questionario pre-consulta: mesma autorizacao por posse do link.
                .requestMatchers("/api/publico/questionarios/**").permitAll()
                // Feed iCalendar: um calendario que assina um endereco nao
                // sabe mandar cabecalho de token.
                .requestMatchers(HttpMethod.GET, "/api/publico/agenda/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/alimentos/**").authenticated()
                // O app do paciente enxerga apenas o proprio prontuario.
                .requestMatchers("/api/app/**").hasRole("PACIENTE")
                // Ato privativo do nutricionista, ou dado que a secretaria nao
                // precisa ver para operar a recepcao. A separacao e do dominio:
                // prescrever e ato privativo, e o financeiro e do dono.
                .requestMatchers("/api/financeiro/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/prescricoes/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/receitas/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/orientacoes/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/exames/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/questionarios/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/pacientes/*/questionarios/**")
                        .hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/avaliacoes/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/pacientes/*/avaliacoes/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/pacientes/*/evolucao").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/pacientes/*/exames/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/pacientes/*/solicitacoes-de-exame/**")
                        .hasAnyRole("NUTRICIONISTA", "ADMIN")
                .requestMatchers("/api/antropometria/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                // Gerir a equipe e do dono da conta.
                .requestMatchers("/api/usuarios/**").hasAnyRole("NUTRICIONISTA", "ADMIN")
                // O que sobra — agenda e cadastro de paciente — a secretaria faz.
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(respostasDeSeguranca.naoAutenticado())
                .accessDeniedHandler(respostasDeSeguranca.acessoNegado()))
            .headers(h -> h.frameOptions(f -> f.sameOrigin())) // console H2 em dev
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origensPermitidas.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
