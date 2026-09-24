package br.com.nutriplan.anamnesis;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O lote 3 do cliente: a anamnese construída como questionário, preenchida na
 * consulta ou importada do que o paciente respondeu antes dela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("anamnese por questionário")
class AnamnesisQuestionnaireTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri anamnese",
                                "email", "anamq" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 12345"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        patient = json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Marina Duarte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    // ------------------------------------------------------------------ apoio

    private JsonNode call(MockHttpServletRequestBuilder request, String tk, String body, int expected)
            throws Exception {
        if (tk != null) {
            request = request.header("Authorization", "Bearer " + tk);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String text = mvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return text.isBlank() ? null : json.readTree(text);
    }

    private JsonNode getJson(String url) throws Exception {
        return call(get(url), token, null, 200);
    }

    private static final String ANAMNESIS_MODEL = """
            {"name":"Anamnese padrão","description":"Da primeira consulta.",
             "questions":[
               {"statement":"Hábitos","type":"SECTION","required":true,"highlight":true},
               {"statement":"Queixa principal","type":"TEXT","required":true,"highlight":true},
               {"statement":"Como é a rotina alimentar?","type":"PARAGRAPH"},
               {"statement":"Data do último exame","type":"DATE"},
               {"statement":"Sintomas","type":"MULTIPLE","options":"Azia|Refluxo|Constipação","highlight":true},
               {"statement":"Pratica atividade física?","type":"CHOICE_SINGLE","options":"Sim|Não"}]}""";

    private JsonNode createModel() throws Exception {
        return call(post("/api/questionnaires"), token, ANAMNESIS_MODEL, 201);
    }

    private long questionId(JsonNode questionnaire, String statement) {
        for (JsonNode q : questionnaire.get("questions")) {
            if (q.get("statement").asText().equals(statement)) {
                return q.get("id").asLong();
            }
        }
        throw new AssertionError("pergunta não encontrada: " + statement);
    }

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("o construtor aceita parágrafo, data, seção e múltipla escolha; a seção não é obrigatória nem destacada")
    void builderAcceptsTheNewTypes() throws Exception {
        JsonNode model = createModel();
        assertThat(model.get("questions")).hasSize(6);
        JsonNode section = model.get("questions").get(0);
        assertThat(section.get("type").asText()).isEqualTo("SECTION");
        assertThat(section.get("required").asBoolean()).isFalse();
        assertThat(section.get("highlight").asBoolean()).isFalse();
        assertThat(model.get("questions").get(1).get("highlight").asBoolean()).isTrue();
        assertThat(model.get("questions").get(2).get("typeDescription").asText()).isEqualTo("Parágrafo");
    }

    @Test
    @DisplayName("a anamnese nasce do questionário, congela os enunciados e leva o destaque à listagem")
    void anamnesisFromQuestionnaire() throws Exception {
        JsonNode model = createModel();
        long modelId = model.get("id").asLong();
        long complaint = questionId(model, "Queixa principal");
        long routine = questionId(model, "Como é a rotina alimentar?");
        long symptoms = questionId(model, "Sintomas");
        long section = questionId(model, "Hábitos");

        JsonNode created = call(post("/api/anamneses"), token, """
                {"patientId":%d,"name":"Primeira consulta","date":"2026-09-24",
                 "questionnaireId":%d,
                 "answers":[{"questionId":%d,"value":"cabeçalho não se responde"},
                            {"questionId":%d,"value":"Cansaço à tarde"},
                            {"questionId":%d,"value":"Pula o café da manhã; janta tarde."},
                            {"questionId":%d,"value":"Azia; Refluxo"}]}"""
                .formatted(patient, modelId, section, complaint, routine, symptoms), 201);
        long id = created.get("id").asLong();
        assertThat(created.get("questionnaireName").asText()).isEqualTo("Anamnese padrão");
        // The section is a heading: whatever was sent for it is not stored.
        assertThat(created.get("answers")).hasSize(3);
        assertThat(created.get("answers").get(0).get("statement").asText()).isEqualTo("Queixa principal");
        assertThat(created.get("answers").get(0).get("type").asText()).isEqualTo("TEXT");
        assertThat(created.get("answers").get(2).get("value").asText()).isEqualTo("Azia; Refluxo");

        JsonNode listing = getJson("/api/patients/" + patient + "/anamneses");
        assertThat(listing.get(0).get("questionnaireName").asText()).isEqualTo("Anamnese padrão");
        JsonNode highlights = listing.get(0).get("highlights");
        assertThat(highlights).hasSize(2);
        assertThat(highlights.get(0).get("label").asText()).isEqualTo("Queixa principal");
        assertThat(highlights.get(1).get("value").asText()).isEqualTo("Azia; Refluxo");

        // The model changes afterwards: the complaint is reworded and the routine question leaves.
        call(put("/api/questionnaires/" + modelId), token, """
                {"name":"Anamnese padrão",
                 "questions":[
                   {"statement":"Queixa que trouxe o paciente","type":"TEXT","highlight":true},
                   {"statement":"Sintomas","type":"MULTIPLE","options":"Azia|Refluxo|Constipação","highlight":true}]}""",
                200);
        JsonNode reread = getJson("/api/anamneses/" + id);
        // The record still says what was asked on the day.
        assertThat(reread.get("answers").get(0).get("statement").asText()).isEqualTo("Queixa principal");
        assertThat(reread.get("answers").get(1).get("statement").asText()).isEqualTo("Como é a rotina alimentar?");

        // Saving it again keeps the answer to the question that left the model.
        call(put("/api/anamneses/" + id), token, """
                {"patientId":%d,"name":"Primeira consulta","date":"2026-09-24",
                 "questionnaireId":%d,
                 "answers":[{"questionId":%d,"value":"Cansaço à tarde e à noite"},
                            {"questionId":%d,"value":"Pula o café da manhã; janta tarde."},
                            {"questionId":%d,"value":"Azia"}]}"""
                .formatted(patient, modelId, complaint, routine, symptoms), 200);
        JsonNode saved = getJson("/api/anamneses/" + id);
        assertThat(saved.get("answers")).hasSize(3);
        assertThat(saved.get("answers").get(1).get("statement").asText()).isEqualTo("Como é a rotina alimentar?");
        assertThat(saved.get("answers").get(1).get("value").asText()).isEqualTo("Pula o café da manhã; janta tarde.");

        mvc.perform(get("/api/anamneses/" + id + "/pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("a pré-consulta respondida vira anamnese uma vez só; a pendente não vira")
    void importFromSending() throws Exception {
        JsonNode model = createModel();
        long modelId = model.get("id").asLong();

        JsonNode pending = call(post("/api/patients/" + patient + "/questionnaires"), token,
                "{\"questionnaireId\":" + modelId + "}", 201);
        call(post("/api/anamneses/from-sending/" + pending.get("id").asLong()), token, null, 422);

        // The patient answers by the link, with no credential.
        String link = pending.get("publicIdentifier").asText();
        JsonNode form = call(get("/api/public/questionnaires/" + link), null, null, 200);
        long complaint = questionId(form, "Queixa principal");
        long symptoms = questionId(form, "Sintomas");
        long activity = questionId(form, "Pratica atividade física?");
        call(post("/api/public/questionnaires/" + link), null, """
                {"answers":[{"questionId":%d,"value":"Dor de cabeça frequente"},
                            {"questionId":%d,"value":"Refluxo; Constipação"},
                            {"questionId":%d,"value":"Não"}]}"""
                .formatted(complaint, symptoms, activity), 204);

        JsonNode anamnesis = call(post("/api/anamneses/from-sending/" + pending.get("id").asLong()),
                token, null, 201);
        assertThat(anamnesis.get("name").asText()).isEqualTo("Anamnese padrão (pré-consulta)");
        assertThat(anamnesis.get("sendingId").asLong()).isEqualTo(pending.get("id").asLong());
        assertThat(anamnesis.get("answers")).hasSize(3);
        assertThat(anamnesis.get("answers").get(1).get("type").asText()).isEqualTo("MULTIPLE");
        assertThat(anamnesis.get("answers").get(1).get("highlight").asBoolean()).isTrue();

        JsonNode listing = getJson("/api/patients/" + patient + "/anamneses");
        assertThat(listing.get(0).get("sendingId").asLong()).isEqualTo(pending.get("id").asLong());

        // Twice would be two records claiming to be the same visit.
        call(post("/api/anamneses/from-sending/" + pending.get("id").asLong()), token, null, 422);
    }

    @Test
    @DisplayName("múltipla escolha soma os pontos das alternativas marcadas")
    void multipleChoiceAddsPoints() throws Exception {
        JsonNode scorable = call(post("/api/questionnaires"), token, """
                {"name":"Sintomas pontuados","scorable":true,"cutoffRange":"0-2=Leve|3-9=Atenção",
                 "questions":[
                   {"statement":"Marque o que sente","type":"MULTIPLE","required":true,
                    "options":"Azia=1|Refluxo=2|Constipação=4"}]}""", 201);
        JsonNode pending = call(post("/api/patients/" + patient + "/questionnaires"), token,
                "{\"questionnaireId\":" + scorable.get("id").asLong() + "}", 201);
        String link = pending.get("publicIdentifier").asText();
        long question = scorable.get("questions").get(0).get("id").asLong();
        call(post("/api/public/questionnaires/" + link), null, """
                {"answers":[{"questionId":%d,"value":"Azia; Constipação"}]}""".formatted(question), 204);

        JsonNode answered = getJson("/api/patients/" + patient + "/questionnaires").get(0);
        assertThat(answered.get("score").asInt()).isEqualTo(5);
        assertThat(answered.get("classification").asText()).isEqualTo("Atenção");
    }
}
