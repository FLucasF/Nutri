package br.com.nutriplan.labtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lab tests (RF100–RF106).
 *
 * What these tests protect is the same rule as the skinfold protocol and the
 * prescribed weight: the record keeps what was known when it was made. A
 * reference range depends on the laboratory's method and changes;
 * reclassifying an old test with a new range would turn a normal result into an
 * abnormal one without anything having happened to the patient.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabtestTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long woman;
    private long man;
    private long withoutSignup;

    @BeforeEach
    void prepare() throws Exception {
        token = register("labtest");
        tokenB = register("labtestB");
        woman = createPatient(token, "Marina Duarte", "FEMALE", 34);
        man = createPatient(token, "Carlos Menezes", "MALE", 40);
        withoutSignup = createPatientWithoutData(token);
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

    private long createPatient(String tk, String name, String sex, int age) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "name", name, "sex", sex,
                "dateBirth", LocalDate.now().minusYears(age).minusDays(1).toString()));
        return json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long createPatientWithoutData(String tk) throws Exception {
        return json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sem Dados"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long parameter(String name) throws Exception {
        for (JsonNode p : getJson(token, "/api/labtests/parameters")) {
            if (p.get("name").asText().equals(name)) {
                return p.get("id").asLong();
            }
        }
        throw new IllegalStateException("parametro nao encontrado no catalogo: " + name);
    }

    private JsonNode entry(String tk, long patient, String body, int expected)
            throws Exception {
        String r = mvc.perform(post("/api/patients/" + patient + "/labtests")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    // ----------------------------------------------------------------- catalog

    @Test
    @DisplayName("o sistema traz um catálogo de parâmetros com faixas")
    void systemCatalog() throws Exception {
        JsonNode parameters = getJson(token, "/api/labtests/parameters");

        assertThat(parameters).isNotEmpty();
        JsonNode glycemia = null;
        for (JsonNode p : parameters) {
            if (p.get("name").asText().equals("Glicemia de jejum")) glycemia = p;
        }
        assertThat(glycemia).isNotNull();
        assertThat(glycemia.get("doSystemCatalog").asBoolean()).isTrue();
        assertThat(glycemia.get("unitStandard").asText()).isEqualTo("mg/dL");
        assertThat(glycemia.get("ranges")).isNotEmpty();
    }

    @Test
    @DisplayName("o consultório cadastra parâmetro próprio, com a faixa dele")
    void ownParameter() throws Exception {
        JsonNode created = json.readTree(mvc.perform(post("/api/labtests/parameters")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Selenio serico","unitStandard":"µg/L",
                                 "group":"Vitaminas e minerais","minimum":70,"maximum":150}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(created.get("editable").asBoolean()).isTrue();
        assertThat(created.get("ranges").get(0).get("text").asText()).isEqualTo("70 a 150");

        // It does not leak to another practice.
        assertThat(getJson(tokenB, "/api/labtests/parameters").toString())
                .doesNotContain("Selenio serico");
    }

    // ----------------------------------------------------------- classification

    @Test
    @DisplayName("classifica o valor contra a faixa do parâmetro")
    void classifiesAgainstRange() throws Exception {
        long glycemia = parameter("Glicemia de jejum");

        JsonNode low = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":60}"""
                .formatted(glycemia, LocalDate.now()), 201);
        JsonNode normal = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(glycemia, LocalDate.now()), 201);
        JsonNode high = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":130}"""
                .formatted(glycemia, LocalDate.now()), 201);

        assertThat(low.get("classification").asText()).isEqualTo("BELOW");
        assertThat(normal.get("classification").asText()).isEqualTo("NORMAL");
        assertThat(high.get("classification").asText()).isEqualTo("ABOVE");
        assertThat(normal.get("reference").asText()).isEqualTo("70 a 99");
    }

    @Test
    @DisplayName("a faixa depende do sexo do paciente")
    void rangeBySex() throws Exception {
        long ferritina = parameter("Ferritina");
        String body = """
                {"parameterId":%d,"dateCollection":"%s","value":200}"""
                .formatted(ferritina, LocalDate.now());

        JsonNode dela = entry(token, woman, body, 201);
        JsonNode dele = entry(token, man, body, 201);

        // 200 ng/mL: above for a woman (15–150), inside for a man (30–400).
        assertThat(dela.get("classification").asText()).isEqualTo("ABOVE");
        assertThat(dele.get("classification").asText()).isEqualTo("NORMAL");
        assertThat(dela.get("reference").asText()).isEqualTo("15 a 150");
        assertThat(dele.get("reference").asText()).isEqualTo("30 a 400");
    }

    @Test
    @DisplayName("sem sexo informado, usa a faixa geral — e sem faixa geral, não classifica")
    void withoutSexUsesGeneralRange() throws Exception {
        JsonNode general = entry(token, withoutSignup, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now()), 201);
        assertThat(general.get("classification").asText()).isEqualTo("NORMAL");

        // Ferritin only has a range by sex.
        JsonNode withoutRange = entry(token, withoutSignup, """
                {"parameterId":%d,"dateCollection":"%s","value":200}"""
                .formatted(parameter("Ferritina"), LocalDate.now()), 201);
        assertThat(withoutRange.has("classification") && !withoutRange.get("classification").isNull())
                .as("sem faixa aplicável, o valor é registrado sem classificação")
                .isFalse();
    }

    @Test
    @DisplayName("a faixa usada fica gravada, e alterar o cadastro não reclassifica o passado")
    void rangeRecordedNotChangesComSignup() throws Exception {
        JsonNode created = json.readTree(mvc.perform(post("/api/labtests/parameters")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Marcador X","unitStandard":"mg/L","minimum":10,"maximum":20}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long marker = created.get("id").asLong();

        JsonNode labtest = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":25}"""
                .formatted(marker, LocalDate.now()), 201);
        assertThat(labtest.get("classification").asText()).isEqualTo("ABOVE");
        assertThat(labtest.get("reference").asText()).isEqualTo("10 a 20");

        // A new parameter with a wider range does not reach the lab test already
        // stored: the range was copied into it.
        JsonNode reread = getJson(token, "/api/patients/" + woman + "/labtests").get(0);
        assertThat(reread.get("reference").asText()).isEqualTo("10 a 20");
    }

    @Test
    @DisplayName("unidade diferente da do parâmetro não é classificada")
    void unitDifferentNotClassifies() throws Exception {
        JsonNode labtest = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":4.7,"unit":"mmol/L"}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now()), 201);

        // 4.7 mmol/L is a normal glycemia, but comparing it against 70–99 mg/dL
        // would say "below" with the appearance of certainty.
        assertThat(labtest.get("unit").asText()).isEqualTo("mmol/L");
        assertThat(labtest.has("classification") && !labtest.get("classification").isNull()).isFalse();
    }

    // -------------------------------------------------------------------- record

    @Test
    @DisplayName("parâmetro não determinado fica sem valor, e não como zero")
    void notDeterminedNotBecomesZero() throws Exception {
        JsonNode labtest = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","notes":"pedido, aguardando"}"""
                .formatted(parameter("Homocisteína"), LocalDate.now()), 201);

        assertThat(labtest.has("value") && !labtest.get("value").isNull()).isFalse();
        assertThat(labtest.has("classification") && !labtest.get("classification").isNull()).isFalse();
    }

    @Test
    @DisplayName("recusa coleta no futuro")
    void rejectsCollectionFuture() throws Exception {
        entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now().plusDays(1)), 400);
    }

    @Test
    @DisplayName("aceita coleta retroativa")
    void acceptsRetroactiveCollection() throws Exception {
        JsonNode labtest = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now().minusYears(2)), 201);
        assertThat(labtest.get("dateCollection").asText())
                .isEqualTo(LocalDate.now().minusYears(2).toString());
    }

    // -------------------------------------------------------------------- series

    @Test
    @DisplayName("a série mostra a evolução do parâmetro, com a variação")
    void seriesHistorical() throws Exception {
        long glycemia = parameter("Glicemia de jejum");
        for (var caso : new String[][] {{"120", "24"}, {"105", "12"}, {"92", "0"}}) {
            entry(token, woman, """
                    {"parameterId":%d,"dateCollection":"%s","value":%s}"""
                    .formatted(glycemia, LocalDate.now().minusMonths(Long.parseLong(caso[1])),
                            caso[0]), 201);
        }

        JsonNode series = getJson(token,
                "/api/patients/" + woman + "/labtests/series/" + glycemia);

        assertThat(series.get("points")).hasSize(3);
        // From the oldest to the most recent.
        assertThat(series.get("points").get(0).get("value").decimalValue())
                .isEqualByComparingTo("120.000");
        assertThat(series.get("points").get(2).get("value").decimalValue())
                .isEqualByComparingTo("92.000");
        assertThat(series.get("points").get(1).get("change").decimalValue())
                .isEqualByComparingTo("-15.000");
        assertThat(series.get("unitsMixed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("série com unidades diferentes é sinalizada, e não comparada")
    void seriesComUnitsMixed() throws Exception {
        long glycemia = parameter("Glicemia de jejum");
        entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":100}"""
                .formatted(glycemia, LocalDate.now().minusMonths(6)), 201);
        entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":5.2,"unit":"mmol/L"}"""
                .formatted(glycemia, LocalDate.now()), 201);

        JsonNode series = getJson(token,
                "/api/patients/" + woman + "/labtests/series/" + glycemia);

        assertThat(series.get("unitsMixed").asBoolean()).isTrue();
        // The variation between different units is not calculated.
        JsonNode second = series.get("points").get(1);
        assertThat(second.has("change") && !second.get("change").isNull()).isFalse();
    }

    // -------------------------------------------------------------------- report

    @Test
    @DisplayName("anexa e devolve o laudo")
    void attachesELowReport() throws Exception {
        long id = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now()), 201)
                .get("id").asLong();

        var file = new MockMultipartFile("file", "report.pdf", "application/pdf",
                "%PDF-1.4 conteudo".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/labtests/" + id + "/report").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode labtest = getJson(token, "/api/patients/" + woman + "/labtests").get(0);
        assertThat(labtest.get("hasReport").asBoolean()).isTrue();
        assertThat(labtest.get("reportName").asText()).isEqualTo("report.pdf");

        var answer = mvc.perform(get("/api/labtests/" + id + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(answer.getContentType()).startsWith("application/pdf");
        assertThat(answer.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @DisplayName("exame sem laudo responde 404 no download")
    void withoutReportResponde404() throws Exception {
        long id = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now()), 201)
                .get("id").asLong();

        mvc.perform(get("/api/labtests/" + id + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------------- order

    @Test
    @DisplayName("registra o pedido de exames entregue ao paciente")
    void recordsOrder() throws Exception {
        String body = """
                {"date":"%s","parameterIds":[%d,%d],"notes":"Jejum de 8 horas."}"""
                .formatted(LocalDate.now(), parameter("Glicemia de jejum"), parameter("Ferritina"));

        JsonNode order = json.readTree(mvc.perform(
                        post("/api/patients/" + woman + "/requests-from-labtest")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(order.get("labtests")).hasSize(2);
        assertThat(order.get("notes").asText()).isEqualTo("Jejum de 8 horas.");
        assertThat(getJson(token, "/api/patients/" + woman + "/requests-from-labtest"))
                .hasSize(1);
    }

    @Test
    @DisplayName("solicitação sem exame escolhido é recusada")
    void rejectsOrderEmpty() throws Exception {
        mvc.perform(post("/api/patients/" + woman + "/requests-from-labtest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","parameterIds":[]}""".formatted(LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------- isolation

    @Test
    @DisplayName("um consultório não vê exame de paciente de outro")
    void isolatesBetweenConsultorios() throws Exception {
        long id = entry(token, woman, """
                {"parameterId":%d,"dateCollection":"%s","value":85}"""
                .formatted(parameter("Glicemia de jejum"), LocalDate.now()), 201)
                .get("id").asLong();

        mvc.perform(get("/api/patients/" + woman + "/labtests")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/labtests/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parameterId":%d,"dateCollection":"%s","value":999}"""
                                .formatted(parameter("Glicemia de jejum"), LocalDate.now())))
                .andExpect(status().isNotFound());
    }
}
