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
 * Equipe do consultório e o que a secretária pode fazer (RF03, RF05).
 *
 * Existe para o nutricionista não emprestar a própria senha — que é o que
 * acontece num consultório sem esse recurso, e é pior que qualquer falha de
 * permissão: um login compartilhado torna a auditoria inútil, porque toda ação
 * fica no nome do dono.
 *
 * A separação do que ela vê é do domínio, não de conveniência: prescrever é ato
 * privativo do nutricionista.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipeDoConsultorioTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenNutri;
    private String emailSecretaria;
    private long idSecretaria;

    @BeforeEach
    void preparar() throws Exception {
        String prefixo = "equipe" + System.nanoTime();
        tokenNutri = json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Dra. Helena",
                                "email", prefixo + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        emailSecretaria = "sec" + prefixo + "@exemplo.com";
        idSecretaria = json.readTree(mvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenNutri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Joana Recepcao",
                                "email", emailSecretaria,
                                "senhaInicial", "senhaDaJoana1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String entrarComoSecretaria() throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", emailSecretaria, "senha", "senhaDaJoana1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    // ------------------------------------------------------------- cadastro

    @Test
    @DisplayName("o nutricionista cadastra a secretária e ela entra com a própria senha")
    void secretariaEntraComAPropriaSenha() throws Exception {
        String token = entrarComoSecretaria();

        JsonNode eu = json.readTree(mvc.perform(get("/api/auth/eu")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(eu.get("perfil").asText()).isEqualTo("SECRETARIA");
    }

    @Test
    @DisplayName("a equipe aparece na listagem do consultório")
    void listaAEquipe() throws Exception {
        JsonNode equipe = json.readTree(mvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(equipe).hasSize(2);
        assertThat(equipe.toString()).contains("SECRETARIA").contains("NUTRICIONISTA");
    }

    @Test
    @DisplayName("e-mail já usado é recusado")
    void emailRepetido() throws Exception {
        mvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenNutri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Outra", "email", emailSecretaria,
                                "senhaInicial", "outraSenha12"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------- o que ela faz

    @Test
    @DisplayName("a secretária opera a agenda e o cadastro de pacientes")
    void secretariaOperaARecepcao() throws Exception {
        String token = entrarComoSecretaria();

        mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Paciente da Joana"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/agenda/dia").param("data", java.time.LocalDate.now().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "secretária não acessa {0}")
    @ValueSource(strings = {
            "/api/prescricoes",
            "/api/financeiro/lancamentos",
            "/api/receitas",
            "/api/orientacoes",
            "/api/exames/parametros",
            "/api/usuarios",
    })
    @DisplayName("o que é privativo do nutricionista responde 403 para a secretária")
    void secretariaNaoAcessaOPrivativo(String rota) throws Exception {
        String token = entrarComoSecretaria();

        mvc.perform(get(rota).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a secretária não abre a antropometria nem os exames de um paciente")
    void secretariaNaoAbreOClinico() throws Exception {
        String token = entrarComoSecretaria();
        long paciente = json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + tokenNutri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Marina"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/pacientes/" + paciente + "/avaliacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pacientes/" + paciente + "/exames")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------- desativação

    @Test
    @DisplayName("desativar derruba a sessão aberta, e não só o próximo login")
    void desativarDerrubaASessao() throws Exception {
        String token = entrarComoSecretaria();
        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/usuarios/" + idSecretaria)
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/eu").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("o nutricionista dono não pode ser desativado")
    void donoNaoEhDesativado() throws Exception {
        JsonNode equipe = json.readTree(mvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        long idDono = -1;
        for (JsonNode u : equipe) {
            if (u.get("perfil").asText().equals("NUTRICIONISTA")) {
                idDono = u.get("id").asLong();
            }
        }

        mvc.perform(delete("/api/usuarios/" + idDono)
                        .header("Authorization", "Bearer " + tokenNutri))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("um consultório não gere o usuário de outro")
    void naoGereUsuarioDeOutroConsultorio() throws Exception {
        String outro = json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Bruna",
                                "email", "bruna" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(delete("/api/usuarios/" + idSecretaria)
                        .header("Authorization", "Bearer " + outro))
                .andExpect(status().isNotFound());
    }
}
