package br.com.nutriplan.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementacao padrao: escreve o link no log da aplicacao.
 *
 * Serve a desenvolvimento e a demonstracao, e deixa evidente que nao ha envio
 * de verdade — um stub silencioso faria o fluxo parecer completo quando nao
 * esta. Em producao, uma implementacao de {@link EnviadorDeRecuperacao} com
 * SMTP substitui esta automaticamente, pela condicao abaixo.
 */
@Component
@ConditionalOnMissingBean(ignored = EnviadorQueRegistraNoLog.class, value = EnviadorDeRecuperacao.class)
@Slf4j
public class EnviadorQueRegistraNoLog implements EnviadorDeRecuperacao {

    @Override
    public void enviar(String email, String nome, String token, long validadeMinutos) {
        log.warn("""
                
                ============================================================
                RECUPERACAO DE SENHA — nao ha envio de e-mail configurado.
                Para: {} <{}>
                Token (vale {} minutos): {}
                ============================================================
                """, nome, email, validadeMinutos, token);
    }
}
