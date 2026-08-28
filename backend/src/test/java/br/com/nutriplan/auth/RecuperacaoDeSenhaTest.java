package br.com.nutriplan.auth;

import br.com.nutriplan.auth.service.EnviadorDeRecuperacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recuperação de senha (RF06).
 *
 * O canal de entrega está fora do escopo do trabalho, então o teste captura o
 * token no ponto de costura — que é exatamente onde uma implementação de SMTP
 * entraria. O que está sendo verificado é o fluxo: sorteio, uso único,
 * validade, e a consequência que passa despercebida com mais frequência, que é
 * derrubar as sessões abertas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperacaoDeSenhaTest {

    /** Captura o token no lugar de enviá-lo — a costura deixada aberta. */
    static final AtomicReference<String> ULTIMO_TOKEN = new AtomicReference<>();

    @TestConfiguration
    static class EnviadorDeTeste {
        @Bean
        @Primary
        EnviadorDeRecuperacao capturador() {
            return (email, nome, token, minutos) -> ULTIMO_TOKEN.set(token);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String email;

    @BeforeEach
    void cadastrar() throws Exception {
        ULTIMO_TOKEN.set(null);
        email = "recup" + System.nanoTime() + "@exemplo.com";
        mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Recuperacao",
                                "email", email,
                                "senha", "senhaAntiga1"))))
                .andExpect(status().isCreated());
    }

    private void pedir(String paraOEmail) throws Exception {
        mvc.perform(post("/api/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", paraOEmail))))
                .andExpect(status().isNoContent());
    }

    private void redefinir(String token, String senha, int esperado) throws Exception {
        mvc.perform(post("/api/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("token", token, "novaSenha", senha))))
                .andExpect(status().is(esperado));
    }

    private String entrar(String senha, int esperado) throws Exception {
        String corpo = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", email, "senha", senha))))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return esperado == 200 ? json.readTree(corpo).get("token").asText() : null;
    }

    @Test
    @DisplayName("o fluxo completo: pedir, redefinir e entrar com a senha nova")
    void fluxoCompleto() throws Exception {
        pedir(email);
        assertThat(ULTIMO_TOKEN.get()).isNotBlank();

        redefinir(ULTIMO_TOKEN.get(), "senhaNova12", 204);

        assertThat(entrar("senhaNova12", 200)).isNotBlank();
        entrar("senhaAntiga1", 401);
    }

    @Test
    @DisplayName("pedir para e-mail sem conta responde igual — não é um oráculo")
    void naoRevelaSeOEmailExiste() throws Exception {
        pedir("ninguem" + System.nanoTime() + "@exemplo.com");

        // A resposta é 204 nos dois casos; o que difere é só o que acontece
        // internamente, e nada disso vaza.
        assertThat(ULTIMO_TOKEN.get()).isNull();
    }

    @Test
    @DisplayName("o token vale uma vez só")
    void tokenDeUsoUnico() throws Exception {
        pedir(email);
        String token = ULTIMO_TOKEN.get();

        redefinir(token, "senhaNova12", 204);
        // Um link que ficou no histórico do navegador não pode abrir a porta
        // de novo.
        redefinir(token, "outraSenha12", 422);
    }

    @Test
    @DisplayName("pedir de novo invalida o pedido anterior")
    void pedidoNovoCancelaOAnterior() throws Exception {
        pedir(email);
        String primeiro = ULTIMO_TOKEN.get();

        pedir(email);
        String segundo = ULTIMO_TOKEN.get();
        assertThat(segundo).isNotEqualTo(primeiro);

        redefinir(primeiro, "senhaNova12", 422);
        redefinir(segundo, "senhaNova12", 204);
    }

    @Test
    @DisplayName("token inventado é recusado")
    void tokenInventado() throws Exception {
        redefinir("token-que-nunca-existiu", "senhaNova12", 422);
    }

    @Test
    @DisplayName("senha curta demais é recusada")
    void senhaCurta() throws Exception {
        pedir(email);
        redefinir(ULTIMO_TOKEN.get(), "curta", 400);
    }

    @Test
    @DisplayName("redefinir derruba as sessões que já estavam abertas")
    void redefinirDerrubaSessoesAbertas() throws Exception {
        String tokenDeSessao = entrar("senhaAntiga1", 200);
        // A sessão funciona antes.
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + tokenDeSessao))
                .andExpect(status().isOk());

        pedir(email);
        redefinir(ULTIMO_TOKEN.get(), "senhaNova12", 204);

        // E deixa de funcionar depois: quem obteve a senha antiga sai na hora,
        // sem esperar o token expirar sozinho.
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + tokenDeSessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a sessão aberta depois da troca continua valendo")
    void sessaoNovaContinuaValendo() throws Exception {
        pedir(email);
        redefinir(ULTIMO_TOKEN.get(), "senhaNova12", 204);

        String novaSessao = entrar("senhaNova12", 200);
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + novaSessao))
                .andExpect(status().isOk());
    }
}
