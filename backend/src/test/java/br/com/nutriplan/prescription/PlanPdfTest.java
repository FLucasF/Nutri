package br.com.nutriplan.prescription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The meal plan as a PDF (RF51).
 *
 * The patient has no account in the system: they read the plan through the link
 * or take it printed. Since the patient app is out of scope, the printout is
 * the delivery channel — and a PDF that comes out empty, or that delivers a
 * draft without warning, fails exactly where nobody checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanPdfTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long patient;
    private long food;

    @BeforeEach
    void prepare() throws Exception {
        token = register("pdf");
        tokenB = register("pdfB");
        patient = createPatient(token, "Marina Duarte");
        food = createFood(token);
    }

    private String register(String prefix) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Dra. Helena " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 45678"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createPatient(String tk, String name) throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createFood(String tk) throws Exception {
        String body = mvc.perform(post("/api/foods")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Arroz integral cozido",
                                 "composition":{"energyKcal":124,"proteinG":2.6,
                                               "carbohydrateG":25.8,"fatG":1}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createPlan() throws Exception {
        String body = """
                {"title":"Plano de reeducacao","patientId":%d,"method":"FOODS",
                 "handouts":"Beba dois litros de agua por dia.","template":false,
                 "meals":[{"name":"Almoco","time":"12:30",
                   "notes":"Metade do prato de salada.",
                   "items":[{"foodId":%d,"quantity":150}]}]}"""
                .formatted(patient, food);
        String answer = mvc.perform(post("/api/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(answer).get("id").asLong();
    }

    private void publish(long id) throws Exception {
        mvc.perform(post("/api/prescriptions/" + id + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private byte[] pdf(String tk, long id) throws Exception {
        return mvc.perform(get("/api/prescriptions/" + id + "/pdf")
                        .header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /**
     * Extracts the readable text of the PDF without depending on another
     * library.
     *
     * The page content comes compressed, so what is left in plain text is the
     * metadata and the uncompressed strings. For what these tests need to check
     * — that the file is a valid PDF, with pages and a size consistent with the
     * content — that is enough; checking every word would need an extractor,
     * and the layout of the sheet is checked by looking at it.
     */
    private String readable(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("gera um PDF válido, com o cabeçalho que todo leitor exige")
    void generatesPdfValid() throws Exception {
        long id = createPlan();
        publish(id);

        byte[] file = pdf(token, id);

        assertThat(new String(file, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(readable(file)).endsWith("%%EOF\n");
        assertThat(file.length)
                .as("um PDF com uma refeição e um resumo não cabe em 800 bytes")
                .isGreaterThan(800);
    }

    @Test
    @DisplayName("responde como PDF e sugere um nome de arquivo sem acento")
    void respondsComTypeEName() throws Exception {
        long id = createPlan();
        publish(id);

        var answer = mvc.perform(get("/api/prescriptions/" + id + "/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(answer.getContentType()).startsWith(MediaType.APPLICATION_PDF_VALUE);
        String disposition = answer.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).isNotNull();
        assertThat(disposition).contains("plano-de-reeducacao.pdf");
        // A filename with an accent or a space breaks in an email client and
        // in an old file system.
        assertThat(disposition).matches(".*filename=\"[a-z0-9.\\-]+\".*");
    }

    @Test
    @DisplayName("o rascunho também imprime, e a folha se identifica como rascunho")
    void draftPrintsIdentified() throws Exception {
        long id = createPlan();

        // Without publishing: checking the sheet before handing it over is part of the work.
        byte[] draft = pdf(token, id);
        assertThat(new String(draft, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        publish(id);
        byte[] published = pdf(token, id);

        // The draft band is extra content on the page.
        assertThat(draft.length)
                .as("a folha de rascunho carrega a tarja que a publicada não tem")
                .isGreaterThan(published.length);
    }

    @Test
    @DisplayName("um consultório não imprime o plano de outro")
    void notPrintsOtherPracticePlan() throws Exception {
        long id = createPlan();
        publish(id);

        mvc.perform(get("/api/prescriptions/" + id + "/pdf")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("exige autenticação: o PDF é do profissional, não do link público")
    void requiresAuthentication() throws Exception {
        long id = createPlan();
        publish(id);

        mvc.perform(get("/api/prescriptions/" + id + "/pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("plano sem refeição ainda gera folha, em vez de falhar")
    void planEmptyNotBreaks() throws Exception {
        String body = """
                {"title":"Plano vazio","patientId":%d,"method":"QUALITATIVE",
                 "template":false,"meals":[]}""".formatted(patient);
        long id = json.readTree(mvc.perform(post("/api/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        byte[] file = pdf(token, id);
        assertThat(new String(file, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
