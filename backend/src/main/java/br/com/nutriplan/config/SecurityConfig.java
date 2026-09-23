package br.com.nutriplan.config;

import br.com.nutriplan.auth.service.JpaUserDetailsService;
import br.com.nutriplan.auth.service.JwtAuthenticationFilter;
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
public class SecurityConfig {

    private final JwtAuthenticationFilter filterJwt;
    private final JpaUserDetailsService detailsUserService;
    private final SecurityAnswers securityAnswers;

    @Value("${nutriplan.cors.allowed-origins}")
    private String originsAllowed;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(detailsUserService);
        // Without this Spring skips BCrypt when the user does not exist, and the
        // difference in response time gives away which emails are registered.
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
                .requestMatchers("/api/auth/login", "/api/auth/signup").permitAll()
                // Password recovery is, by definition, for whoever cannot get in.
                .requestMatchers("/api/auth/recover-password", "/api/auth/reset-password").permitAll()
                .requestMatchers("/actuator/health", "/h2/**").permitAll()
                // A aplicação do navegador. Ela é pública porque é só HTML,
                // CSS e JavaScript: quem entra nela ainda precisa de token
                // para qualquer /api, e é a própria tela que pede o login.
                // Sem isto, a tela de acesso viria com 403 e ninguém entraria.
                .requestMatchers(HttpMethod.GET,
                        "/", "/index.html", "/assets/**", "/favicon.ico", "/robots.txt")
                    .permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/patients/**", "/prescriptions/**", "/plan/**", "/form/**",
                        "/schedule/**", "/foods/**", "/recipes/**", "/handouts/**",
                        "/questionnaires/**", "/finance/**", "/team/**", "/access")
                    .permitAll()
                .requestMatchers("/docs/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // A plan opened by the link handed to the patient. The authorization
                // is possession of the identifier, a UUID; the service only
                // serves a published plan.
                .requestMatchers(HttpMethod.GET, "/api/public/plans/**").permitAll()
                // Pre-appointment questionnaire: the same authorization by possession of the link.
                .requestMatchers("/api/public/questionnaires/**").permitAll()
                // An iCalendar feed: a calendar that subscribes to an address
                // does not know how to send a token header.
                .requestMatchers(HttpMethod.GET, "/api/public/schedule/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/foods/**").authenticated()
                // The patient app sees only their own chart.
                .requestMatchers("/api/app/**").hasRole("PATIENT")
                // An act reserved to the nutritionist, or data the receptionist
                // does not need to see in order to run the front desk. The
                // separation comes from the domain: prescribing is reserved,
                // and the finances belong to the owner.
                .requestMatchers("/api/finance/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/prescriptions/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/meal-favorites/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/recipes/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/handouts/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/labtests/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/questionnaires/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/questionnaires/**")
                        .hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/assessments/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/assessments/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/progress").hasAnyRole("NUTRITIONIST", "ADMIN")
                // O relatório de evolução é leitura clínica, como a avaliação
                // de onde ele sai: a recepção agenda, não interpreta dobra.
                .requestMatchers("/api/patients/*/anthropometry-report")
                    .hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/labtests/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/requests-from-labtest/**")
                        .hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/anthropometry/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                // The anamnesis is the consultation written out: the most
                // sensitive text in the chart, and none of it is front-desk work.
                .requestMatchers("/api/anamneses/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/anamnesis-fields").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/anamneses").hasAnyRole("NUTRITIONIST", "ADMIN")
                // As anotações são a leitura que o profissional faz do paciente,
                // e não recado de recepção.
                .requestMatchers("/api/patients/*/notes/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                // Prescrever energia é ato do nutricionista, como prescrever cardápio.
                .requestMatchers("/api/energy-plans/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                .requestMatchers("/api/patients/*/energy-plans").hasAnyRole("NUTRITIONIST", "ADMIN")
                // Managing the team belongs to the account owner.
                .requestMatchers("/api/users/**").hasAnyRole("NUTRITIONIST", "ADMIN")
                // What is left — schedule and patient registration — the receptionist does.
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(securityAnswers.notAuthenticated())
                .accessDeniedHandler(securityAnswers.accessDenied()))
            .headers(h -> h.frameOptions(f -> f.sameOrigin())) // console H2 em dev
            .addFilterBefore(filterJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(originsAllowed.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
