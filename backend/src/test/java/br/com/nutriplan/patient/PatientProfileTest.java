package br.com.nutriplan.patient;

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
@DisplayName("perfil do paciente")
class PatientProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri perfil",
                                "email", "perfil" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 99999"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();

        String created = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Vitória de Araújo Ferreira", "sex", "FEMALE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        patient = json.readTree(created).get("id").asLong();
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode send(String method, String url, String body, int expected) throws Exception {
        var request = "PUT".equals(method) ? put(url) : post(url);
        String answer = mvc.perform(request
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isBlank() ? null : json.readTree(answer);
    }

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("as TAGs que ele cita chegam como ponto de partida")
    void theTagsHeMentionsAreSeeded() throws Exception {
        JsonNode tags = getJson("/api/patient-tags");
        var names = new java.util.ArrayList<String>();
        tags.forEach(t -> names.add(t.get("name").asText()));

        assertThat(names).contains("HIPERTROFIA", "EMAGRECIMENTO", "DIABÉTICO", "CELÍACO");
        assertThat(tags.get(0).get("systemTag").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a TAG do consultório vem antes das do sistema")
    void theOwnTagComesFirst() throws Exception {
        send("POST", "/api/patient-tags", "{\"name\":\"pós-bariátrico\"}", 201);

        JsonNode tags = getJson("/api/patient-tags");
        // Normalizada para maiúscula: a TAG é um rótulo, não uma frase.
        assertThat(tags.get(0).get("name").asText()).isEqualTo("PÓS-BARIÁTRICO");
        assertThat(tags.get(0).get("own").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("marcar TAGs no paciente é uma operação só")
    void taggingAPatientIsOneOperation() throws Exception {
        JsonNode tags = getJson("/api/patient-tags");
        long um = tags.get(0).get("id").asLong();
        long dois = tags.get(1).get("id").asLong();

        JsonNode applied = send("PUT", "/api/patients/" + patient + "/tags",
                "{\"tagIds\":[%d,%d]}".formatted(um, dois), 200);
        assertThat(applied).hasSize(2);

        // Reenviar com uma só desmarca a outra: o conjunto é o que chega.
        JsonNode again = send("PUT", "/api/patients/" + patient + "/tags",
                "{\"tagIds\":[%d]}".formatted(um), 200);
        assertThat(again).hasSize(1);
        assertThat(getJson("/api/patients/" + patient + "/tags")).hasSize(1);
    }

    @Test
    @DisplayName("as anotações formam um feed, da mais recente para a mais antiga")
    void notesFormAFeed() throws Exception {
        send("POST", "/api/patients/" + patient + "/notes",
                "{\"body\":\"Chegou atrasada, mas trouxe o diário alimentar.\"}", 201);
        send("POST", "/api/patients/" + patient + "/notes",
                "{\"body\":\"Caso interessante de intolerância.\"}", 201);

        JsonNode notes = getJson("/api/patients/" + patient + "/notes");
        assertThat(notes).hasSize(2);

        // A anotação passou a ser documento formatado, como o resto do sistema.
        // A frase enviada como texto solto vira um documento de um parágrafo e
        // continua dizendo o que dizia — que é o que este teste precisa fixar.
        // Afirmar a igualdade literal com a frase voltaria a exigir que o campo
        // fosse texto puro, e é justamente isso que deixou de ser verdade.
        String body = notes.get(0).get("body").asText();
        assertThat(body).contains("Caso interessante de intolerância.");
        assertThat(body).startsWith("{\"type\":\"doc\"");
        assertThat(notes.get(0).get("createdAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("apelido e condição biológica entram no cadastro")
    void nicknameAndConditionAreRecorded() throws Exception {
        JsonNode updated = send("PUT", "/api/patients/" + patient, """
                {"name":"Vitória de Araújo Ferreira","sex":"FEMALE",
                 "nickname":"Vitinha","biologicalCondition":"PREGNANT"}""", 200);

        assertThat(updated.get("nickname").asText()).isEqualTo("Vitinha");
        assertThat(updated.get("biologicalCondition").asText()).isEqualTo("PREGNANT");
    }

    @Test
    @DisplayName("condição biológica não se aplica a homem")
    void theConditionDoesNotApplyToMen() throws Exception {
        String created = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Daniel Lacerda","sex":"MALE",
                                 "biologicalCondition":"PREGNANT"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Guardar isso seria registrar um fato que não existe.
        assertThat(json.readTree(created).hasNonNull("biologicalCondition")).isFalse();
    }
}
