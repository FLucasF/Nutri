package br.com.nutriplan.labtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("painéis de biomarcadores")
class LabtestPanelTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void prepare() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri painel",
                                "email", "painel" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 99999"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode postJson(String url, String body, int expected) throws Exception {
        String answer = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isBlank() ? null : json.readTree(answer);
    }

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("os 25 painéis do documento chegam carregados")
    void theTwentyFivePanelsAreSeeded() throws Exception {
        JsonNode panels = getJson("/api/labtests/panels");
        assertThat(panels).hasSize(25);

        JsonNode hepatica = null;
        for (JsonNode panel : panels) {
            if (panel.get("name").asText().contains("Hepática")) hepatica = panel;
        }
        assertThat(hepatica).isNotNull();
        assertThat(hepatica.get("systemPanel").asBoolean()).isTrue();
        // Oito, como o documento lista na página 6.
        assertThat(hepatica.get("parameters")).hasSize(8);
        assertThat(hepatica.get("parameters").get(0).get("name").asText())
                .isEqualTo("Aspartato Amino Transferase (AST)");
    }

    @Test
    @DisplayName("um parâmetro usado em vários painéis não é duplicado")
    void aSharedParameterIsNotDuplicated() throws Exception {
        // "Glicemia de jejum" aparece em vários painéis dele e já existia no
        // sistema. Duas linhas com o mesmo nome partiriam o histórico do
        // paciente em dois parâmetros diferentes.
        JsonNode parameters = getJson("/api/labtests/parameters");
        JsonNode items = parameters.has("content") ? parameters.get("content") : parameters;

        long glicemias = 0;
        for (JsonNode parameter : items) {
            if ("Glicemia de jejum".equals(parameter.get("name").asText())) glicemias++;
        }
        assertThat(glicemias).isEqualTo(1);
    }

    @Test
    @DisplayName("o consultório monta o painel dele, e ele vem marcado e primeiro")
    void ownPanelComesFirstAndFlagged() throws Exception {
        JsonNode parameters = getJson("/api/labtests/parameters");
        JsonNode items = parameters.has("content") ? parameters.get("content") : parameters;
        long first = items.get(0).get("id").asLong();
        long second = items.get(1).get("id").asLong();

        JsonNode own = postJson("/api/labtests/panels", """
                {"name":"Primeira consulta","parameterIds":[%d,%d]}"""
                .formatted(first, second), 201);

        assertThat(own.get("own").asBoolean()).isTrue();
        assertThat(own.get("systemPanel").asBoolean()).isFalse();
        assertThat(own.get("parameters")).hasSize(2);

        // O que ele usa todo dia não fica abaixo de 25 que usa raramente.
        JsonNode panels = getJson("/api/labtests/panels");
        assertThat(panels.get(0).get("name").asText()).isEqualTo("Primeira consulta");
        assertThat(panels).hasSize(26);
    }

    @Test
    @DisplayName("painel do sistema não é editável, mas é duplicável")
    void systemPanelIsNotEditableButIsCopyable() throws Exception {
        long systemId = getJson("/api/labtests/panels").get(0).get("id").asLong();

        mvc.perform(put("/api/labtests/panels/" + systemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meu\",\"parameterIds\":[1]}"))
                .andExpect(status().is(422));

        JsonNode copy = postJson("/api/labtests/panels/" + systemId + "/duplicate", "", 201);
        assertThat(copy.get("own").asBoolean()).isTrue();
        assertThat(copy.get("name").asText()).endsWith("(cópia)");
        assertThat(copy.get("parameters")).isNotEmpty();
    }

    @Test
    @DisplayName("a faixa cadastrada destrava a classificação automática")
    void theRegisteredRangeUnlocksAutomaticClassification() throws Exception {
        // É o pedido da página 15: "colocarmos a avaliação via código". A faixa
        // não vem pronta para os 154 parâmetros porque varia de laboratório.
        JsonNode parameters = getJson("/api/labtests/parameters");
        JsonNode items = parameters.has("content") ? parameters.get("content") : parameters;

        JsonNode semFaixa = null;
        for (JsonNode parameter : items) {
            if (parameter.get("ranges").isEmpty()) semFaixa = parameter;
        }
        assertThat(semFaixa).as("os parâmetros dos painéis chegam sem faixa").isNotNull();
        long id = semFaixa.get("id").asLong();

        JsonNode comFaixa = postJson("/api/labtests/parameters/" + id + "/ranges",
                "{\"minimum\":10,\"maximum\":40}", 201);
        assertThat(comFaixa.get("ranges")).hasSize(1);
    }

    @Test
    @DisplayName("faixa sem limite nenhum não classifica nada")
    void aRangeWithoutBoundsClassifiesNothing() throws Exception {
        JsonNode parameters = getJson("/api/labtests/parameters");
        JsonNode items = parameters.has("content") ? parameters.get("content") : parameters;
        long id = items.get(0).get("id").asLong();

        postJson("/api/labtests/parameters/" + id + "/ranges", "{}", 422);
        postJson("/api/labtests/parameters/" + id + "/ranges",
                "{\"minimum\":50,\"maximum\":10}", 422);
    }

    @Test
    @DisplayName("painel vazio não preenche pedido nenhum")
    void anEmptyPanelIsRefused() throws Exception {
        postJson("/api/labtests/panels", "{\"name\":\"Vazio\",\"parameterIds\":[]}", 400);
    }
}
