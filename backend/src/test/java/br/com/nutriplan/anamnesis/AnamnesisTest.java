package br.com.nutriplan.anamnesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
@DisplayName("anamnese")
class AnamnesisTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        tokenA = registerNutri("anamA");
        tokenB = registerNutri("anamB");
        patient = createPatient(tokenA, "Marina Duarte");
    }

    // ------------------------------------------------------------------ apoio

    private String registerNutri(String prefix) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 99999"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createPatient(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode getJson(String token, String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode send(String method, String token, String url, String body, int expected)
            throws Exception {
        var request = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> throw new IllegalArgumentException(method);
        };
        String responseBody = mvc.perform(request
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return responseBody.isBlank() ? null : json.readTree(responseBody);
    }

    /** Declares the two fields the client described: purpose and goal. */
    private JsonNode declareFields(String token) throws Exception {
        return send("PUT", token, "/api/anamnesis-fields", """
                {"fields":[
                   {"label":"Propósito da consulta","showInListing":true},
                   {"label":"Objetivo","showInListing":true},
                   {"label":"Observação interna","showInListing":false}]}""", 200);
    }

    /**
     * A document escaped to travel inside a JSON string. It has to stay on one
     * line: a real newline in there would make the request itself malformed.
     */
    private static final String BODY =
            "{\\\"type\\\":\\\"doc\\\",\\\"content\\\":[{\\\"type\\\":\\\"paragraph\\\","
                    + "\\\"content\\\":[{\\\"type\\\":\\\"text\\\","
                    + "\\\"text\\\":\\\"Relato da consulta.\\\"}]}]}";

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("os campos declarados viram destaque na listagem")
    void declaredFieldsBecomeHighlights() throws Exception {
        JsonNode fields = declareFields(tokenA);
        long purpose = fields.get(0).get("id").asLong();
        long goal = fields.get(1).get("id").asLong();
        long internal = fields.get(2).get("id").asLong();

        send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Primeira consulta","date":"2026-09-10",
                 "body":"%s",
                 "values":[{"fieldId":%d,"value":"Emagrecimento"},
                           {"fieldId":%d,"value":"Perder 8 kg"},
                           {"fieldId":%d,"value":"Chegou atrasada"}]}"""
                .formatted(patient, BODY, purpose, goal, internal), 201);

        JsonNode listing = getJson(tokenA, "/api/patients/" + patient + "/anamneses");
        assertThat(listing).hasSize(1);
        JsonNode highlights = listing.get(0).get("highlights");
        // Only the two marked to show: the internal note stays out of the listing.
        assertThat(highlights).hasSize(2);
        assertThat(highlights.get(0).get("label").asText()).isEqualTo("Propósito da consulta");
        assertThat(highlights.get(0).get("value").asText()).isEqualTo("Emagrecimento");
    }

    @Test
    @DisplayName("renomear o campo não reescreve a anamnese antiga")
    void renamingAFieldDoesNotRewriteThePast() throws Exception {
        JsonNode fields = declareFields(tokenA);
        long goal = fields.get(1).get("id").asLong();

        JsonNode created = send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Consulta","date":"2026-09-10",
                 "values":[{"fieldId":%d,"value":"Perder 8 kg"}]}"""
                .formatted(patient, goal), 201);
        long id = created.get("id").asLong();

        // The practice renames the field a year later.
        send("PUT", tokenA, "/api/anamnesis-fields", """
                {"fields":[
                   {"label":"Propósito da consulta","showInListing":true},
                   {"label":"Meta","showInListing":true}]}""", 200);

        JsonNode reread = getJson(tokenA, "/api/anamneses/" + id);
        // The record still says what it said: the patient answered that label.
        assertThat(reread.get("values").get(0).get("label").asText()).isEqualTo("Objetivo");
    }

    @Test
    @DisplayName("campo que sai da lista é desativado, e a anamnese antiga sobrevive")
    void removedFieldKeepsPastRecords() throws Exception {
        JsonNode fields = declareFields(tokenA);
        long goal = fields.get(1).get("id").asLong();

        JsonNode created = send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Consulta","date":"2026-09-10",
                 "values":[{"fieldId":%d,"value":"Perder 8 kg"}]}"""
                .formatted(patient, goal), 201);
        long id = created.get("id").asLong();

        send("PUT", tokenA, "/api/anamnesis-fields",
                "{\"fields\":[{\"label\":\"Propósito da consulta\",\"showInListing\":true}]}", 200);

        assertThat(getJson(tokenA, "/api/anamnesis-fields")).hasSize(1);
        // The value survives the field leaving the list.
        assertThat(getJson(tokenA, "/api/anamneses/" + id).get("values")).hasSize(1);
        // But it no longer shows up top, because the field is not on the list.
        assertThat(getJson(tokenA, "/api/patients/" + patient + "/anamneses")
                .get(0).get("highlights")).isEmpty();
    }

    @Test
    @DisplayName("recusa corpo fora do modelo do editor")
    void refusesBodyOutsideTheEditorModel() throws Exception {
        send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Consulta","date":"2026-09-10",
                 "body":"{\\"type\\":\\"doc\\",\\"content\\":[{\\"type\\":\\"heading\\"}]}"}"""
                .formatted(patient), 422);
    }

    @Test
    @DisplayName("a anamnese de um consultório não existe para o outro")
    void oneAccountDoesNotSeeAnother() throws Exception {
        JsonNode created = send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Consulta","date":"2026-09-10"}"""
                .formatted(patient), 201);
        long id = created.get("id").asLong();

        mvc.perform(get("/api/anamneses/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("duplicar copia corpo e valores, com a data de hoje")
    void duplicateCopiesEverything() throws Exception {
        JsonNode fields = declareFields(tokenA);
        long goal = fields.get(1).get("id").asLong();

        JsonNode created = send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Primeira","date":"2026-01-10","body":"%s",
                 "values":[{"fieldId":%d,"value":"Perder 8 kg"}]}"""
                .formatted(patient, BODY, goal), 201);

        JsonNode copy = send("POST", tokenA,
                "/api/anamneses/" + created.get("id").asLong() + "/duplicate", "", 201);

        assertThat(copy.get("name").asText()).isEqualTo("Primeira (cópia)");
        assertThat(copy.get("date").asText()).isNotEqualTo("2026-01-10");
        assertThat(copy.get("body").asText()).isEqualTo(created.get("body").asText());
        assertThat(copy.get("values").get(0).get("value").asText()).isEqualTo("Perder 8 kg");
    }

    @Test
    @DisplayName("gera o PDF da anamnese")
    void generatesThePdf() throws Exception {
        JsonNode fields = declareFields(tokenA);
        long goal = fields.get(1).get("id").asLong();

        JsonNode created = send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Consulta","date":"2026-09-10","body":"%s",
                 "values":[{"fieldId":%d,"value":"Perder 8 kg"}]}"""
                .formatted(patient, BODY, goal), 201);

        byte[] pdf = mvc.perform(get("/api/anamneses/" + created.get("id").asLong() + "/pdf")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).startsWith('%', 'P', 'D', 'F');
        assertThat(pdf.length).isGreaterThan(800);
    }

    @Test
    @DisplayName("excluir tira da listagem")
    void removeTakesItOutOfTheListing() throws Exception {
        JsonNode created = send("POST", tokenA, "/api/anamneses", """
                {"patientId":%d,"name":"Consulta","date":"2026-09-10"}"""
                .formatted(patient), 201);

        mvc.perform(delete("/api/anamneses/" + created.get("id").asLong())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertThat(getJson(tokenA, "/api/patients/" + patient + "/anamneses")).isEmpty();
    }
}
