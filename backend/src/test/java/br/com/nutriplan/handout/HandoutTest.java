package br.com.nutriplan.handout;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nutrition handouts (RF110–RF113).
 *
 * The rule these tests protect is the copy: the text delivered to the patient
 * must not change because somebody corrected the template afterwards. It is the
 * same rule as the weight stored in the meal item, applied to text.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoutTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long plan;

    @BeforeEach
    void prepare() throws Exception {
        token = register("orient");
        tokenB = register("orientB");
        plan = createPlan();
    }

    private String register(String prefix) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createPlan() throws Exception {
        long patient = json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Marina Duarte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String body = """
                {"title":"Plano","patientId":%d,"method":"QUALITATIVE","template":false,
                 "meals":[{"name":"Almoco","items":[{"description":"Salada a vontade"}]}]}"""
                .formatted(patient);
        return json.readTree(mvc.perform(post("/api/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String tk, String url, String body, int expected) throws Exception {
        String r = mvc.perform(post(url).header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    private long ownCreate(String tk, String title, String body) throws Exception {
        return postJson(tk, "/api/handouts",
                json.writeValueAsString(Map.of("title", title, "body", body)), 201)
                .get("id").asLong();
    }

    // ---------------------------------------------------------------- library

    /**
     * O texto da orientação, sem a formatação.
     *
     * O corpo passou a ser documento do editor. Estes testes se importam com o
     * que está escrito, e não com a representação — comparar o JSON cru faria
     * eles quebrarem a cada recurso novo do editor, sem nada ter piorado.
     */
    private String textOf(JsonNode body) throws Exception {
        String raw = body.asText();
        if (!raw.startsWith("{")) {
            return raw;
        }
        var out = new StringBuilder();
        json.readTree(raw).findValues("text").forEach(node -> out.append(node.asText()));
        return out.toString();
    }

    @Test
    @DisplayName("o sistema traz modelos, e eles não são editáveis")
    void systemNotAreEditableTemplates() throws Exception {
        JsonNode list = getJson(token, "/api/handouts").get("content");
        assertThat(list).isNotEmpty();

        JsonNode template = list.get(0);
        assertThat(template.get("systemTemplate").asBoolean()).isTrue();
        assertThat(template.get("editable").asBoolean()).isFalse();

        mvc.perform(put("/api/handouts/" + template.get("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Meu jeito","body":"Texto alterado"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("duplicar um modelo cria a minha versão, sem tocar no original")
    void duplicateCreatesOwnVersion() throws Exception {
        JsonNode template = getJson(token, "/api/handouts").get("content").get(0);
        long templateId = template.get("id").asLong();
        String titleOriginal = template.get("title").asText();

        JsonNode copies = postJson(token, "/api/handouts/" + templateId + "/duplicate", "", 201);

        assertThat(copies.get("editable").asBoolean()).isTrue();
        assertThat(copies.get("body").asText()).isEqualTo(template.get("body").asText());
        assertThat(getJson(token, "/api/handouts/" + templateId).get("title").asText())
                .isEqualTo(titleOriginal);
    }

    @Test
    @DisplayName("a orientação própria não aparece para outro consultório")
    void handoutOwnPracticeEh() throws Exception {
        ownCreate(token, "Minha conduta", "Texto interno");

        String list = getJson(tokenB, "/api/handouts").toString();
        assertThat(list).doesNotContain("Minha conduta");
    }

    @Test
    @DisplayName("a minha orientação vem antes dos modelos do sistema")
    void ownComesFirst() throws Exception {
        ownCreate(token, "AAA minha", "Texto");

        JsonNode first = getJson(token, "/api/handouts").get("content").get(0);
        assertThat(first.get("systemTemplate").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------- on the plan

    @Test
    @DisplayName("anexar copia o texto da biblioteca para o plano")
    void attachCopiesText() throws Exception {
        long id = ownCreate(token, "Como montar o prato", "Metade de vegetais.");

        JsonNode attachment = postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201);

        assertThat(attachment.get("title").asText()).isEqualTo("Como montar o prato");
        assertThat(textOf(attachment.get("body"))).isEqualTo("Metade de vegetais.");
        assertThat(attachment.get("handoutId").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("editar a biblioteca não altera o que já foi anexado")
    void editAtLibraryNotReachesPlan() throws Exception {
        long id = ownCreate(token, "Hidratacao", "Beba 2 litros.");
        postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201);

        mvc.perform(put("/api/handouts/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hidratacao","body":"Beba 3 litros."}"""))
                .andExpect(status().isOk());

        JsonNode attached = getJson(token, "/api/prescriptions/" + plan + "/handouts").get(0);
        assertThat(textOf(attached.get("body")))
                .as("o paciente recebeu 2 litros; corrigir o modelo depois nao reescreve isso")
                .isEqualTo("Beba 2 litros.");
    }

    @Test
    @DisplayName("o texto anexado pode ser adaptado ao paciente sem sujar o modelo")
    void textAttachedEhEditable() throws Exception {
        long id = ownCreate(token, "Hidratacao", "Beba 2 litros.");
        long attachmentId = postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201).get("id").asLong();

        mvc.perform(put("/api/prescriptions/" + plan + "/handouts/" + attachmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hidratacao","body":"Beba 2,5 litros — voce treina."}"""))
                .andExpect(status().isOk());

        assertThat(textOf(getJson(token, "/api/handouts/" + id).get("body")))
                .isEqualTo("Beba 2 litros.");
    }

    @Test
    @DisplayName("dá para anexar um texto escrito na hora, sem biblioteca")
    void attachesTextStandalone() throws Exception {
        JsonNode attachment = postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"title":"Recado","body":"Trazer o exame na proxima consulta."}""", 201);

        assertThat(attachment.get("title").asText()).isEqualTo("Recado");
        // A field with no value comes out omitted from the response, and not as null.
        assertThat(attachment.has("handoutId") && !attachment.get("handoutId").isNull())
                .as("texto avulso nao tem origem na biblioteca")
                .isFalse();
    }

    @Test
    @DisplayName("anexo sem texto e sem origem é recusado")
    void rejectsAttachmentEmpty() throws Exception {
        postJson(token, "/api/prescriptions/" + plan + "/handouts", "{}", 422);
    }

    @Test
    @DisplayName("as orientações chegam ao paciente pelo link do plano")
    void handoutReachesAoPatient() throws Exception {
        postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"title":"Como montar o prato","body":"Metade de vegetais."}""", 201);
        mvc.perform(post("/api/prescriptions/" + plan + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String link = getJson(token, "/api/prescriptions/" + plan)
                .get("publicIdentifier").asText();
        String isPublic = mvc.perform(get("/api/public/plans/" + link))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(isPublic).contains("Como montar o prato");
        assertThat(isPublic).contains("Metade de vegetais.");
    }

    @Test
    @DisplayName("um consultório não anexa orientação no plano de outro")
    void notAttachesAtPlanOther() throws Exception {
        postJson(tokenB, "/api/prescriptions/" + plan + "/handouts",
                """
                {"title":"Invasao","body":"Texto"}""", 404);
    }

    @Test
    @DisplayName("desanexar tira do plano e mantém a biblioteca")
    void detachKeepsLibrary() throws Exception {
        long id = ownCreate(token, "Hidratacao", "Beba 2 litros.");
        long attachmentId = postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201).get("id").asLong();

        mvc.perform(delete("/api/prescriptions/" + plan + "/handouts/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(getJson(token, "/api/prescriptions/" + plan + "/handouts")).isEmpty();
        assertThat(getJson(token, "/api/handouts/" + id).get("title").asText())
                .isEqualTo("Hidratacao");
    }

    // ------------------------------------------------------------------ image

    @Test
    @DisplayName("a orientação aceita uma imagem, e ela vai junto para o plano")
    void imageFollowsAttachment() throws Exception {
        long id = ownCreate(token, "Como montar o prato", "Metade de vegetais.");

        var figure = new org.springframework.mock.web.MockMultipartFile(
                "file", "prato.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/handouts/" + id + "/image").file(figure)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(getJson(token, "/api/handouts/" + id).get("hasImage").asBoolean()).isTrue();

        long attachmentId = postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201).get("id").asLong();

        // The copy went along: the plan has its own image.
        var answer = mvc.perform(get("/api/prescriptions/" + plan + "/handouts/"
                        + attachmentId + "/image").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(answer.getContentType()).startsWith("image/png");
        assertThat(answer.getContentAsByteArray()).hasSize(6);
    }

    @Test
    @DisplayName("a imagem entregue chega ao paciente pelo link do plano")
    void imageReachesAoPatientByLink() throws Exception {
        long id = ownCreate(token, "Como montar o prato", "Metade de vegetais.");
        var figure = new org.springframework.mock.web.MockMultipartFile(
                "file", "prato.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/handouts/" + id + "/image").file(figure)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201);
        mvc.perform(post("/api/prescriptions/" + plan + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String link = getJson(token, "/api/prescriptions/" + plan)
                .get("publicIdentifier").asText();
        var isPublic = json.readTree(mvc.perform(get("/api/public/plans/" + link))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // The address comes assembled from the response: the patient's page does
        // not need to know the shape of the route.
        String address = isPublic.get("handoutsAttached").get(0).get("image").asText();
        assertThat(address).contains(link);

        var file = mvc.perform(get(address))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(file.getContentType()).startsWith("image/png");
        assertThat(file.getContentAsByteArray()).hasSize(6);
    }

    @Test
    @DisplayName("o link de um plano não abre a imagem de outro")
    void linkNotOpensOtherPlanImage() throws Exception {
        long id = ownCreate(token, "Como montar o prato", "Metade de vegetais.");
        var figure = new org.springframework.mock.web.MockMultipartFile(
                "file", "prato.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/handouts/" + id + "/image").file(figure)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        long attachmentId = postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201).get("id").asLong();
        mvc.perform(post("/api/prescriptions/" + plan + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long other = createPlan();
        mvc.perform(post("/api/prescriptions/" + other + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String otherLink = getJson(token, "/api/prescriptions/" + other)
                .get("publicIdentifier").asText();

        // It answers as nonexistent, and not as forbidden: whoever holds this link
        // does not need to know that the other plan exists.
        mvc.perform(get("/api/public/plans/" + otherLink
                        + "/handouts/" + attachmentId + "/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("arquivo que não é imagem é recusado")
    void rejectsFileQueNotEhImage() throws Exception {
        long id = ownCreate(token, "Texto", "Corpo");
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "planilha.csv", "text/csv", "a,b".getBytes());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/handouts/" + id + "/image").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("orientação sem imagem responde 404 no download")
    void withoutImageResponde404() throws Exception {
        long id = ownCreate(token, "Texto", "Corpo");
        mvc.perform(get("/api/handouts/" + id + "/image")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("remover a orientação da biblioteca não apaga o que foi entregue")
    void removeNotErasesDelivered() throws Exception {
        long id = ownCreate(token, "Hidratacao", "Beba 2 litros.");
        postJson(token, "/api/prescriptions/" + plan + "/handouts",
                """
                {"handoutId":%d}""".formatted(id), 201);

        mvc.perform(delete("/api/handouts/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode attached = getJson(token, "/api/prescriptions/" + plan + "/handouts").get(0);
        assertThat(textOf(attached.get("body"))).isEqualTo("Beba 2 litros.");
    }
}
