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
 * Checks that the context comes up (Flyway + JPA mapping + security) and that
 * the signup -> login -> protected route flow works end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String SIGNUP = """
            {"name":"Ana Souza","email":"ana@exemplo.com","password":"passwordSegura1","crn":"CRN-4 12345"}""";

    @Test
    void registersLogsEAccessesRouteProtected() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("NUTRITIONIST"))
                .andExpect(jsonPath("$.user.plan").value("EXPERIMENTAL"))
                .andReturn().getResponse().getContentAsString();

        JsonNode no = json.readTree(body);
        String token = no.get("token").asText();
        assertThat(no.get("user").get("accountId").asLong()).isPositive();

        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@exemplo.com"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@exemplo.com","password":"passwordSegura1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejectsPasswordWrong() throws Exception {
        mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Bruno Lima","email":"bruno@exemplo.com","password":"passwordSegura1"}"""))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bruno@exemplo.com","password":"passwordErrada9"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blocksRouteProtectedWithoutToken() throws Exception {
        mvc.perform(get("/api/auth/eu")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEmailDuplicated() throws Exception {
        String req = """
                {"name":"Carla Dias","email":"carla@exemplo.com","password":"passwordSegura1"}""";
        mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnprocessableEntity());
    }
}
