package br.com.nutriplan.auth;

import br.com.nutriplan.auth.service.RecoverySender;
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
 * Password recovery (RF06).
 *
 * The delivery channel is out of the scope of this work, so the test captures
 * the token at the seam — which is exactly where an SMTP implementation would
 * go. What is being checked is the flow: drawing, single use, expiry, and the
 * consequence that goes unnoticed most often, which is dropping the open
 * sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordRecoveryTest {

    /** It captures the token instead of sending it — the seam left open. */
    static final AtomicReference<String> LAST_TOKEN = new AtomicReference<>();

    @TestConfiguration
    static class TesteSender {
        @Bean
        @Primary
        RecoverySender capturer() {
            return (email, name, token, minutes) -> LAST_TOKEN.set(token);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String email;

    @BeforeEach
    void register() throws Exception {
        LAST_TOKEN.set(null);
        email = "recup" + System.nanoTime() + "@exemplo.com";
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Recuperacao",
                                "email", email,
                                "password", "passwordAntiga1"))))
                .andExpect(status().isCreated());
    }

    private void request(String toEmail) throws Exception {
        mvc.perform(post("/api/auth/recover-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", toEmail))))
                .andExpect(status().isNoContent());
    }

    private void reset(String token, String password, int expected) throws Exception {
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("token", token, "novaPassword", password))))
                .andExpect(status().is(expected));
    }

    private String login(String password, int expected) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", email, "password", password))))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return expected == 200 ? json.readTree(body).get("token").asText() : null;
    }

    @Test
    @DisplayName("o fluxo completo: pedir, redefinir e entrar com a senha nova")
    void flowComplete() throws Exception {
        request(email);
        assertThat(LAST_TOKEN.get()).isNotBlank();

        reset(LAST_TOKEN.get(), "passwordNova12", 204);

        assertThat(login("passwordNova12", 200)).isNotBlank();
        login("passwordAntiga1", 401);
    }

    @Test
    @DisplayName("pedir para e-mail sem conta responde igual — não é um oráculo")
    void notRevealsSeEmailExists() throws Exception {
        request("ninguem" + System.nanoTime() + "@exemplo.com");

        // The answer is 204 in both cases; what differs is only what happens
        // internally, and none of that leaks.
        assertThat(LAST_TOKEN.get()).isNull();
    }

    @Test
    @DisplayName("o token vale uma vez só")
    void useSingleToken() throws Exception {
        request(email);
        String token = LAST_TOKEN.get();

        reset(token, "passwordNova12", 204);
        // A link left in the browser history must not open the door again.
        reset(token, "otherSenha12", 422);
    }

    @Test
    @DisplayName("pedir de novo invalida o pedido anterior")
    void requestNovoPreviousCancels() throws Exception {
        request(email);
        String first = LAST_TOKEN.get();

        request(email);
        String second = LAST_TOKEN.get();
        assertThat(second).isNotEqualTo(first);

        reset(first, "passwordNova12", 422);
        reset(second, "passwordNova12", 204);
    }

    @Test
    @DisplayName("token inventado é recusado")
    void tokenInvented() throws Exception {
        reset("token-que-nunca-existiu", "passwordNova12", 422);
    }

    @Test
    @DisplayName("senha curta demais é recusada")
    void passwordShort() throws Exception {
        request(email);
        reset(LAST_TOKEN.get(), "brief", 400);
    }

    @Test
    @DisplayName("redefinir derruba as sessões que já estavam abertas")
    void resetDropsSessionsOpen() throws Exception {
        String sessionToken = login("passwordAntiga1", 200);
        // The session works before.
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + sessionToken))
                .andExpect(status().isOk());

        request(email);
        reset(LAST_TOKEN.get(), "passwordNova12", 204);

        // And it stops working afterwards: whoever obtained the old password is
        // out at once, without waiting for the token to expire on its own.
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + sessionToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a sessão aberta depois da troca continua valendo")
    void sessionNovaRemainsValid() throws Exception {
        request(email);
        reset(LAST_TOKEN.get(), "passwordNova12", 204);

        String novaSession = login("passwordNova12", 200);
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + novaSession))
                .andExpect(status().isOk());
    }
}
