package br.com.nutriplan.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default implementation: it writes the link to the application log.
 *
 * It serves development and demonstration, and makes it obvious that there is
 * no real delivery — a silent stub would make the flow look complete when it is
 * not. In production, an implementation of {@link RecoverySender} with SMTP
 * replaces this one automatically, through the condition below.
 */
@Component
@ConditionalOnMissingBean(ignored = LoggingSender.class, value = RecoverySender.class)
@Slf4j
public class LoggingSender implements RecoverySender {

    @Override
    public void send(String email, String name, String token, long expiryMinutes) {
        log.warn("""
                
                ============================================================
                RECUPERACAO DE SENHA — nao ha envio de e-mail configurado.
                Para: {} <{}>
                Token (vale {} minutos): {}
                ============================================================
                """, name, email, expiryMinutes, token);
    }
}
