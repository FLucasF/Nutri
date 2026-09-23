package br.com.nutriplan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * O servidor entrega a aplicação do navegador junto com a API.
 *
 * Em desenvolvimento são dois processos: o Vite serve a tela e encaminha o
 * {@code /api} para cá. Em produção vira um só — o {@code dist/} entra no JAR
 * como recurso estático, e é este servidor que o devolve.
 *
 * Isso não é uma economia de hospedagem apenas. Um endereço só significa
 * nenhuma configuração de CORS para acertar, nenhum segundo domínio para
 * renovar certificado, e nenhuma chance de a tela estar no ar enquanto a API
 * está dormindo — que, num plano gratuito que hiberna, é uma falha que aparece
 * como tela em branco sem explicação.
 *
 * <p>O encaminhamento abaixo existe porque as rotas são do navegador, não do
 * servidor. Quem abre {@code /patients/7} direto, ou aperta F5 ali, pede ao
 * servidor um caminho que só o React conhece. Sem o encaminhamento, a resposta
 * é 404 — e o link do plano que o paciente recebe é exatamente um desses.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    /**
     * Os prefixos que pertencem ao servidor, e não à aplicação do navegador.
     *
     * O que começa com um destes segue o seu caminho normal; todo o resto cai
     * no index.html para o React resolver.
     */
    private static final String SERVER_PATHS =
            "api|actuator|docs|v3|swagger-ui|h2|assets|favicon|robots";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");

        // Um nível: /patients, /schedule, /foods…
        registry.addViewController("/{path:^(?!" + SERVER_PATHS + ")[^.]*$}")
                .setViewName("forward:/index.html");

        // Dois níveis: /patients/7, /prescriptions/98, /plan/{uuid}…
        registry.addViewController("/{path:^(?!" + SERVER_PATHS + ")[^.]*$}/{second:[^.]*}")
                .setViewName("forward:/index.html");

        // Três níveis: /patients/7/anthropometry, /patients/7/labtests…
        registry.addViewController(
                        "/{path:^(?!" + SERVER_PATHS + ")[^.]*$}/{second:[^.]*}/{third:[^.]*}")
                .setViewName("forward:/index.html");
    }
}
