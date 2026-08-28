package br.com.nutriplan.questionnaire;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pre-appointment questionnaires (RF90–RF95).
 *
 * The patient answers by link, without an account — the same mechanism by which
 * they read the plan. What these tests protect is the usual set of rules: the
 * record keeps what happened on that day, and the calculation does not fake
 * exactness when data is missing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionnaireTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        token = register("quest");
        tokenB = register("questB");
        patient = json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Marina Duarte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(String prefix) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String tk, String url, String body, int expected) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (tk != null) {
            request = request.header("Authorization", "Bearer " + tk);
        }
        String r = mvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    /** A scorable questionnaire with three multiple-choice questions. */
    private long createScorable() throws Exception {
        return postJson(token, "/api/questionnaires", """
                {"name":"Triagem alimentar","scorable":true,
                 "cutoffRange":"0-2=Baixo|3-4=Moderado|5-9=Alto",
                 "questions":[
                   {"statement":"Come fora de casa?","type":"CHOICE_SINGLE","required":true,
                    "options":"Raramente=0|As vezes=1|Todo dia=2"},
                   {"statement":"Bebe refrigerante?","type":"CHOICE_SINGLE","required":true,
                    "options":"Nunca=0|As vezes=1|Todo dia=2"},
                   {"statement":"Algo mais?","type":"TEXT","required":false}]}""", 201)
                .get("id").asLong();
    }

    private String send(long questionnaireId) throws Exception {
        return postJson(token, "/api/patients/" + patient + "/questionnaires",
                """
                {"questionnaireId":%d}""".formatted(questionnaireId), 201)
                .get("publicIdentifier").asText();
    }

    // ---------------------------------------------------------------- library

    @Test
    @DisplayName("o sistema traz um modelo de pré-consulta, não editável")
    void systemTemplate() throws Exception {
        JsonNode list = getJson(token, "/api/questionnaires");
        assertThat(list).isNotEmpty();

        JsonNode template = list.get(0);
        assertThat(template.get("systemTemplate").asBoolean()).isTrue();
        assertThat(template.get("name").asText()).isEqualTo("Pré-consulta");
        assertThat(template.get("questions")).hasSize(10);

        mvc.perform(put("/api/questionnaires/" + template.get("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Meu","questions":[{"statement":"X","type":"TEXT"}]}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("duplicar o modelo traz as perguntas junto")
    void duplicateBringsQuestions() throws Exception {
        long template = getJson(token, "/api/questionnaires").get(0).get("id").asLong();

        JsonNode copies = postJson(token, "/api/questionnaires/" + template + "/duplicate", "", 201);

        assertThat(copies.get("editable").asBoolean()).isTrue();
        assertThat(copies.get("questions")).hasSize(10);
    }

    @Test
    @DisplayName("pergunta de escolha sem alternativa é recusada")
    void choiceWithoutChoice() throws Exception {
        postJson(token, "/api/questionnaires", """
                {"name":"Torto","questions":[
                  {"statement":"Escolha","type":"CHOICE_SINGLE","required":true}]}""", 422);
    }

    @Test
    @DisplayName("questionário sem pergunta é recusado")
    void questionnaireEmpty() throws Exception {
        postJson(token, "/api/questionnaires", """
                {"name":"Vazio","questions":[]}""", 400);
    }

    // ---------------------------------------------------------- patient side

    @Test
    @DisplayName("o paciente abre e responde sem conta")
    void patientRespondsWithoutAccount() throws Exception {
        long questionnaire = createScorable();
        String link = send(questionnaire);

        // With no authentication header at all.
        JsonNode form = json.readTree(mvc.perform(
                        get("/api/public/questionnaires/" + link))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(form.get("title").asText()).isEqualTo("Triagem alimentar");
        assertThat(form.get("alreadyAnswered").asBoolean()).isFalse();
        assertThat(form.get("questions")).hasSize(3);
        // The public contract has no field for the patient or for the account.
        assertThat(form.toString()).doesNotContain("Marina");
    }

    @Test
    @DisplayName("o escore sai da soma das alternativas, e a classificação da faixa")
    void scoreEClassification() throws Exception {
        long questionnaire = createScorable();
        String link = send(questionnaire);
        JsonNode form = json.readTree(mvc.perform(
                        get("/api/public/questionnaires/" + link))
                .andReturn().getResponse().getContentAsString());

        long p1 = form.get("questions").get(0).get("id").asLong();
        long p2 = form.get("questions").get(1).get("id").asLong();

        // "Todo dia" is worth 2 in both: score 4, band "3-4=Moderado".
        postJson(null, "/api/public/questionnaires/" + link, """
                {"answers":[{"questionId":%d,"value":"Todo dia"},
                              {"questionId":%d,"value":"Todo dia"}]}"""
                .formatted(p1, p2), 204);

        JsonNode answer = getJson(token, "/api/patients/" + patient + "/questionnaires").get(0);
        assertThat(answer.get("score").asInt()).isEqualTo(4);
        assertThat(answer.get("classification").asText()).isEqualTo("Moderado");
        assertThat(answer.get("pending").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("falta de resposta obrigatória é recusada dizendo qual falta")
    void noshowRequired() throws Exception {
        long questionnaire = createScorable();
        String link = send(questionnaire);
        JsonNode form = json.readTree(mvc.perform(
                        get("/api/public/questionnaires/" + link))
                .andReturn().getResponse().getContentAsString());
        long p1 = form.get("questions").get(0).get("id").asLong();

        String error = mvc.perform(post("/api/public/questionnaires/" + link)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":%d,"value":"Raramente"}]}"""
                                .formatted(p1)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        // It says which one is missing, instead of scoring halfway.
        assertThat(error).contains("Bebe refrigerante");
    }

    @Test
    @DisplayName("o link aceita resposta uma vez só")
    void answerSingle() throws Exception {
        long questionnaire = createScorable();
        String link = send(questionnaire);
        JsonNode form = json.readTree(mvc.perform(
                        get("/api/public/questionnaires/" + link))
                .andReturn().getResponse().getContentAsString());
        long p1 = form.get("questions").get(0).get("id").asLong();
        long p2 = form.get("questions").get(1).get("id").asLong();
        String body = """
                {"answers":[{"questionId":%d,"value":"Nunca"},
                              {"questionId":%d,"value":"Nunca"}]}""".formatted(p1, p2);

        postJson(null, "/api/public/questionnaires/" + link, body, 204);
        postJson(null, "/api/public/questionnaires/" + link, body, 422);
    }

    @Test
    @DisplayName("link inventado responde 404")
    void linkInvented() throws Exception {
        mvc.perform(get("/api/public/questionnaires/not-exists"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ version

    @Test
    @DisplayName("editar o questionário não altera a resposta já recebida")
    void editNotReachesAnswerReceived() throws Exception {
        long questionnaire = createScorable();
        String link = send(questionnaire);
        JsonNode form = json.readTree(mvc.perform(
                        get("/api/public/questionnaires/" + link))
                .andReturn().getResponse().getContentAsString());
        long p1 = form.get("questions").get(0).get("id").asLong();
        long p2 = form.get("questions").get(1).get("id").asLong();

        postJson(null, "/api/public/questionnaires/" + link, """
                {"answers":[{"questionId":%d,"value":"As vezes"},
                              {"questionId":%d,"value":"Nunca"}]}""".formatted(p1, p2), 204);

        // The template loses one question and gains another.
        mvc.perform(put("/api/questionnaires/" + questionnaire)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Triagem alimentar","scorable":true,
                                 "questions":[{"statement":"Pergunta nova","type":"TEXT"}]}"""))
                .andExpect(status().isOk());

        JsonNode answer = getJson(token, "/api/patients/" + patient + "/questionnaires").get(0);
        assertThat(answer.get("templateVersion").asInt())
                .as("a resposta guarda a versao que respondeu")
                .isEqualTo(1);
        assertThat(answer.get("items").toString())
                .as("o enunciado respondido continua la, mesmo removido do modelo")
                .contains("Come fora de casa");
    }

    // ---------------------------------------------------------- isolation

    @Test
    @DisplayName("o questionário próprio não aparece para outro consultório")
    void isolatesLibrary() throws Exception {
        createScorable();
        assertThat(getJson(tokenB, "/api/questionnaires").toString())
                .doesNotContain("Triagem alimentar");
    }

    @Test
    @DisplayName("um consultório não lê as respostas do paciente de outro")
    void isolatesAnswers() throws Exception {
        long questionnaire = createScorable();
        send(questionnaire);

        mvc.perform(get("/api/patients/" + patient + "/questionnaires")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("as respostas aparecem ligadas ao atendimento")
    void answerLinkedAoAppointment() throws Exception {
        long questionnaire = createScorable();
        long appointment = json.readTree(mvc.perform(post("/api/schedule")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":%d,"start":"%sT09:00:00","durationMinutes":60,
                                 "type":"FIRST_CONSULTATION"}"""
                                .formatted(patient, java.time.LocalDate.now().plusDays(3))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        postJson(token, "/api/patients/" + patient + "/questionnaires", """
                {"questionnaireId":%d,"appointmentId":%d}""".formatted(questionnaire, appointment),
                201);

        assertThat(getJson(token, "/api/schedule/" + appointment + "/questionnaires")).hasSize(1);
    }
}
