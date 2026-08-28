package br.com.nutriplan.paciente;

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
class PacienteTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenNutriA;
    private String tokenNutriB;

    @BeforeEach
    void autenticar() throws Exception {
        tokenNutriA = cadastrar("Nutri A", "nutri.a+" + System.nanoTime() + "@exemplo.com");
        tokenNutriB = cadastrar("Nutri B", "nutri.b+" + System.nanoTime() + "@exemplo.com");
    }

    private String cadastrar(String nome, String email) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "nome", nome, "email", email, "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asText();
    }

    private long criarPaciente(String token, String nome) throws Exception {
        String corpo = mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "nome", nome,
                                "email", "paciente@exemplo.com",
                                "dataNascimento", "1990-05-20",
                                "sexo", "FEMININO"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    @Test
    @DisplayName("calcula a idade a partir da data de nascimento")
    void criaPacienteComIdadeCalculada() throws Exception {
        int idadeEsperada = java.time.Period.between(
                java.time.LocalDate.of(1990, 5, 20), java.time.LocalDate.now()).getYears();

        long id = criarPaciente(tokenNutriA, "Maria Silva");

        mvc.perform(get("/api/pacientes/" + id).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.idade").value(idadeEsperada))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("um consultorio nao enxerga paciente de outro")
    void isolaPacientesEntreContas() throws Exception {
        long idDeA = criarPaciente(tokenNutriA, "Paciente da Nutri A");

        // B nao le, nao altera e nao inativa o paciente de A.
        mvc.perform(get("/api/pacientes/" + idDeA).header("Authorization", "Bearer " + tokenNutriB))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/pacientes/" + idDeA)
                        .header("Authorization", "Bearer " + tokenNutriB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Invadido\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/pacientes/" + idDeA).header("Authorization", "Bearer " + tokenNutriB))
                .andExpect(status().isNotFound());

        // ...e a listagem de B sai vazia.
        mvc.perform(get("/api/pacientes").header("Authorization", "Bearer " + tokenNutriB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // A continua enxergando o proprio paciente intacto.
        mvc.perform(get("/api/pacientes/" + idDeA).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Paciente da Nutri A"));
    }

    @Test
    @DisplayName("busca por termo filtra dentro da propria conta")
    void buscaPorTermo() throws Exception {
        criarPaciente(tokenNutriA, "Joana Prado");
        criarPaciente(tokenNutriA, "Ricardo Alves");

        mvc.perform(get("/api/pacientes").param("termo", "joana")
                        .header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Joana Prado"));
    }

    @Test
    @DisplayName("inativar preserva o registro em vez de apagar")
    void inativaSemApagar() throws Exception {
        long id = criarPaciente(tokenNutriA, "Paulo Mendes");

        mvc.perform(delete("/api/pacientes/" + id).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/pacientes/" + id).header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mvc.perform(post("/api/pacientes/" + id + "/reativar")
                        .header("Authorization", "Bearer " + tokenNutriA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("o plano experimental barra o sexto paciente ativo")
    void aplicaLimiteDoPlanoExperimental() throws Exception {
        for (int i = 1; i <= 5; i++) {
            criarPaciente(tokenNutriA, "Paciente " + i);
        }

        mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + tokenNutriA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Paciente 6\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("recusa data de nascimento no futuro")
    void recusaNascimentoFuturo() throws Exception {
        mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + tokenNutriA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Futuro\",\"dataNascimento\":\""
                                + java.time.LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("dataNascimento"));
    }
}
