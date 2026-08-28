package br.com.nutriplan.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The practice's team and what the receptionist can do (RF03, RF05).
 *
 * It exists so that the nutritionist does not lend out their own password —
 * which is what happens in a practice without this feature, and is worse than
 * any permission flaw: a shared login makes auditing useless, because every
 * action ends up in the owner's name.
 *
 * The separation of what they see comes from the domain, not from convenience:
 * prescribing is an act reserved to the nutritionist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticeTeamTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenNutri;
    private String emailAssistant;
    private long idAssistant;

    @BeforeEach
    void prepare() throws Exception {
        String prefix = "team" + System.nanoTime();
        tokenNutri = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Dra. Helena",
                                "email", prefix + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        emailAssistant = "sec" + prefix + "@exemplo.com";
        idAssistant = json.readTree(mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + tokenNutri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Joana Recepcao",
                                "email", emailAssistant,
                                "initialPassword", "joana1Password"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginAsAssistant() throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", emailAssistant, "password", "joana1Password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    // --------------------------------------------------------------- signup

    @Test
    @DisplayName("o nutricionista cadastra a secretária e ela entra com a própria senha")
    void assistantEntersOwnComPassword() throws Exception {
        String token = loginAsAssistant();

        JsonNode eu = json.readTree(mvc.perform(get("/api/auth/eu")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(eu.get("role").asText()).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("a equipe aparece na listagem do consultório")
    void listTeam() throws Exception {
        JsonNode team = json.readTree(mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(team).hasSize(2);
        assertThat(team.toString()).contains("ASSISTANT").contains("NUTRITIONIST");
    }

    @Test
    @DisplayName("e-mail já usado é recusado")
    void emailRepeated() throws Exception {
        mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + tokenNutri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Outra", "email", emailAssistant,
                                "initialPassword", "otherSenha12"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------- o que ela faz

    @Test
    @DisplayName("a secretária opera a agenda e o cadastro de pacientes")
    void assistantOperatesReception() throws Exception {
        String token = loginAsAssistant();

        mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Paciente da Joana"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/schedule/day").param("date", java.time.LocalDate.now().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "secretária não acessa {0}")
    @ValueSource(strings = {
            "/api/prescriptions",
            "/api/finance/transactions",
            "/api/recipes",
            "/api/handouts",
            "/api/labtests/parameters",
            "/api/users",
    })
    @DisplayName("o que é privativo do nutricionista responde 403 para a secretária")
    void assistantNotPrivateAccesses(String route) throws Exception {
        String token = loginAsAssistant();

        mvc.perform(get(route).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a secretária não abre a antropometria nem os exames de um paciente")
    void assistantNotOpensClinical() throws Exception {
        String token = loginAsAssistant();
        long patient = json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tokenNutri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Marina"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/patients/" + patient + "/assessments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients/" + patient + "/labtests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------------- deactivation

    @Test
    @DisplayName("desativar derruba a sessão aberta, e não só o próximo login")
    void disableDropsSession() throws Exception {
        String token = loginAsAssistant();
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/users/" + idAssistant)
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o nutricionista dono não pode ser desativado")
    void ownerNotEhDisabled() throws Exception {
        JsonNode team = json.readTree(mvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        long idOwner = -1;
        for (JsonNode u : team) {
            if (u.get("role").asText().equals("NUTRITIONIST")) {
                idOwner = u.get("id").asLong();
            }
        }

        mvc.perform(delete("/api/users/" + idOwner)
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("um consultório não gere o usuário de outro")
    void notGenerateOtherPracticeUser() throws Exception {
        String other = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Bruna",
                                "email", "bruna" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(delete("/api/users/" + idAssistant)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }
}
