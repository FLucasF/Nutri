package br.com.nutriplan.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenNutriA;
    private String tokenNutriB;

    @BeforeEach
    void authenticate() throws Exception {
        tokenNutriA = register("Nutri A", "nutri.a+" + System.nanoTime() + "@exemplo.com");
        tokenNutriB = register("Nutri B", "nutri.b+" + System.nanoTime() + "@exemplo.com");
    }

    private String register(String name, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "name", name, "email", email, "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createPatient(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "name", name,
                                "email", "paciente@exemplo.com",
                                "dateBirth", "1990-05-20",
                                "sex", "FEMALE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("calcula a idade a partir da data de nascimento")
    void createsPatientComCalculatedAge() throws Exception {
        int ageExpected = java.time.Period.between(
                java.time.LocalDate.of(1990, 5, 20), java.time.LocalDate.now()).getYears();

        long id = createPatient(tokenNutriA, "Maria Silva");

        mvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.age").value(ageExpected))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("um consultorio nao enxerga paciente de outro")
    void isolatesPatientsBetweenAccounts() throws Exception {
        long idDeA = createPatient(tokenNutriA, "Paciente da Nutri A");

        // B does not read, does not change and does not deactivate A's patient.
        mvc.perform(get("/api/patients/" + idDeA).header("Authorization", "Bearer " + tokenNutriB))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/patients/" + idDeA)
                        .header("Authorization", "Bearer " + tokenNutriB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invadido\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/patients/" + idDeA).header("Authorization", "Bearer " + tokenNutriB))
                .andExpect(status().isNotFound());

        // ...and B's listing comes out empty.
        mvc.perform(get("/api/patients").header("Authorization", "Bearer " + tokenNutriB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // A goes on seeing its own patient intact.
        mvc.perform(get("/api/patients/" + idDeA).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paciente da Nutri A"));
    }

    @Test
    @DisplayName("busca por termo filtra dentro da propria conta")
    void searchByTerm() throws Exception {
        createPatient(tokenNutriA, "Joana Prado");
        createPatient(tokenNutriA, "Ricardo Alves");

        mvc.perform(get("/api/patients").param("term", "joana")
                        .header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Joana Prado"));
    }

    @Test
    @DisplayName("inativar preserva o registro em vez de apagar")
    void inactiveWithoutErase() throws Exception {
        long id = createPatient(tokenNutriA, "Paulo Mendes");

        mvc.perform(delete("/api/patients/" + id).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post("/api/patients/" + id + "/reactivate")
                        .header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("o plano experimental barra o sexto paciente ativo")
    void appliesPlanExperimentalLimit() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createPatient(tokenNutriA, "Paciente " + i);
        }

        mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tokenNutriA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Paciente 6\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("recusa data de nascimento no futuro")
    void rejectsBirthFuture() throws Exception {
        mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tokenNutriA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Futuro\",\"dateBirth\":\""
                                + java.time.LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("dateBirth"));
    }
}
