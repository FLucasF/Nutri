package br.com.nutriplan.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida que o contexto sobe (Flyway + mapeamento JPA + security) e que o
 * fluxo cadastro -> login -> rota protegida funciona ponta a ponta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FluxoAutenticacaoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String CADASTRO = """
            {"nome":"Ana Souza","email":"ana@exemplo.com","senha":"senhaSegura1","crn":"CRN-4 12345"}""";

    @Test
    void cadastraLogaEAcessaRotaProtegida() throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON).content(CADASTRO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.perfil").value("NUTRICIONISTA"))
                .andExpect(jsonPath("$.usuario.plano").value("EXPERIMENTAL"))
                .andReturn().getResponse().getContentAsString();

        JsonNode no = json.readTree(corpo);
        String token = no.get("token").asText();
        assertThat(no.get("usuario").get("contaId").asLong()).isPositive();

        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@exemplo.com","senha":"senhaSegura1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejeitaSenhaErrada() throws Exception {
        mvc.perform(post("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome":"Bruno Lima","email":"bruno@exemplo.com","senha":"senhaSegura1"}"""))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bruno@exemplo.com","senha":"senhaErrada9"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bloqueiaRotaProtegidaSemToken() throws Exception {
        mvc.perform(get("/api/auth/eu")).andExpect(status().isUnauthorized());
    }

    @Test
    void recusaEmailDuplicado() throws Exception {
        String req = """
                {"nome":"Carla Dias","email":"carla@exemplo.com","senha":"senhaSegura1"}""";
        mvc.perform(post("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/cadastro")
                .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnprocessableEntity());
    }
}
