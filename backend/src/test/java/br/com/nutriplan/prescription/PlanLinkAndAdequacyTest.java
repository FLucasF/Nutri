package br.com.nutriplan.prescription;

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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O lote 5: a data de nascimento como segundo fator do link do plano, e o
 * cardápio contra as DRI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("link do plano e adequação de micronutrientes")
class PlanLinkAndAdequacyTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private long leite;

    @BeforeEach
    void prepare() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri do link",
                                "email", "link" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 12345"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        JsonNode search = call(get("/api/foods").param("term", "leite, de vaca, integral")
                .param("source", "TACO"), token, null, 200);
        leite = search.get("content").get(0).get("id").asLong();
    }

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

    private long patient(String name, String sex, String birth) throws Exception {
        var data = new HashMap<String, Object>();
        data.put("name", name);
        data.put("sex", sex);
        if (birth != null) {
            data.put("dateBirth", birth);
        }
        return call(post("/api/patients"), token, json.writeValueAsString(data), 201).get("id").asLong();
    }

    /** Um plano publicado com meio litro de leite integral, e o identificador do link. */
    private JsonNode publishedPlan(long patientId) throws Exception {
        JsonNode plan = call(post("/api/prescriptions"), token, """
                {"title":"Plano do link","patientId":%d,"method":"FOODS",
                 "meals":[{"name":"Café da Manhã","time":"07:00","items":[{"foodId":%d,"quantity":500}]}]}"""
                .formatted(patientId, leite), 201);
        call(post("/api/prescriptions/" + plan.get("id").asLong() + "/publish"), token, "{}", 200);
        return plan;
    }

    // ------------------------------------------------------------- o link

    @Test
    @DisplayName("com data de nascimento, o link pede a data, dá o passe com ela e recusa a errada")
    void birthDateGatesTheLink() throws Exception {
        long id = patient("Marina Duarte", "FEMALE", "1990-05-20");
        JsonNode plan = publishedPlan(id);
        String link = call(get("/api/prescriptions/" + plan.get("id").asLong()), token, null, 200)
                .get("publicIdentifier").asText();

        JsonNode gate = call(get("/api/public/plans/" + link + "/gate"), null, null, 200);
        assert gate.get("requiresBirthDate").asBoolean() && !gate.get("allowed").asBoolean();

        // Sem passe, 403 dizendo o que falta — e não 404, que diria "link errado".
        JsonNode refused = call(get("/api/public/plans/" + link), null, null, 403);
        assertThat(refused.get("message").asText()).contains("data de nascimento");

        JsonNode wrong = call(post("/api/public/plans/" + link + "/access"), null,
                "{\"birthDate\":\"1990-05-21\"}", 422);
        assertThat(wrong.get("message").asText()).contains("não confere").contains("Restam 4");

        String pass = call(post("/api/public/plans/" + link + "/access"), null,
                "{\"birthDate\":\"1990-05-20\"}", 200).get("accessToken").asText();
        JsonNode opened = call(get("/api/public/plans/" + link).header("X-Plan-Access", pass), null, null, 200);
        assertThat(call(get("/api/public/plans/" + link + "/gate").header("X-Plan-Access", pass), null, null, 200)
                .get("allowed").asBoolean()).isTrue();
        assertThat(opened.get("title").asText()).isEqualTo("Plano do link");

        // Um passe forjado ou de outro plano não abre.
        call(get("/api/public/plans/" + link).header("X-Plan-Access", "9999999999.forjado"), null, null, 403);

        // O dono logado abre sem passe e recebe um, para as figuras.
        JsonNode owner = call(get("/api/public/plans/" + link), token, null, 200);
        assertThat(owner.get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("cinco datas erradas travam o link, mesmo para a data certa")
    void fiveWrongDatesLockTheLink() throws Exception {
        long id = patient("Paciente Travado", "MALE", "1985-01-15");
        JsonNode plan = publishedPlan(id);
        String link = call(get("/api/prescriptions/" + plan.get("id").asLong()), token, null, 200)
                .get("publicIdentifier").asText();
        for (int i = 1; i <= 5; i++) {
            call(post("/api/public/plans/" + link + "/access"), null, "{\"birthDate\":\"2000-01-0" + i + "\"}", 422);
        }
        JsonNode locked = call(post("/api/public/plans/" + link + "/access"), null,
                "{\"birthDate\":\"1985-01-15\"}", 422);
        assertThat(locked.get("message").asText()).contains("Tente de novo em");
    }

    @Test
    @DisplayName("sem data de nascimento no cadastro, o link abre como antes")
    void noBirthDateNoGate() throws Exception {
        long id = patient("Sem Nascimento", "FEMALE", null);
        JsonNode plan = publishedPlan(id);
        String link = call(get("/api/prescriptions/" + plan.get("id").asLong()), token, null, 200)
                .get("publicIdentifier").asText();
        JsonNode opened = call(get("/api/public/plans/" + link), null, null, 200);
        assertThat(opened.hasNonNull("accessToken")).isFalse();
    }

    // ---------------------------------------------------------- adequação

    @Test
    @DisplayName("o cálcio de meio litro de leite sai contra a RDA da mulher adulta")
    void adequacyAgainstTheDri() throws Exception {
        long id = patient("Adulta", "FEMALE", "1990-05-20");
        long planId = publishedPlan(id).get("id").asLong();

        JsonNode adequacy = call(get("/api/prescriptions/" + planId + "/adequacy"), token, null, 200);
        assertThat(adequacy.get("available").asBoolean()).isTrue();
        assertThat(adequacy.get("reference").asText()).isEqualTo("Mulher, 31 a 50 anos");

        JsonNode calcium = find(adequacy, "calciumMg");
        assertThat(calcium.get("reference").decimalValue()).isEqualByComparingTo("1000");
        assertThat(calcium.get("kind").asText()).isEqualTo("RDA");
        // Meio litro de leite integral dá uns 610 mg de cálcio (TACO): 61% da RDA.
        assertThat(calcium.get("percent").asInt()).isBetween(45, 70);
        assertThat(calcium.get("status").asText()).isEqualTo("BELOW");

        JsonNode sodium = find(adequacy, "sodiumMg");
        assertThat(sodium.get("kind").asText()).isEqualTo("LIMIT");
        assertThat(sodium.get("status").asText()).isEqualTo("WITHIN_LIMIT");

        JsonNode iron = find(adequacy, "ironMg");
        assertThat(iron.get("reference").decimalValue()).isEqualByComparingTo("18");
    }

    @Test
    @DisplayName("gestante e modelo sem paciente respondem sem comparação, dizendo por quê")
    void adequacyUnavailable() throws Exception {
        var data = new HashMap<String, Object>(Map.of("name", "Gestante", "sex", "FEMALE",
                "dateBirth", "1994-02-02", "biologicalCondition", "PREGNANT"));
        long pregnant = call(post("/api/patients"), token, json.writeValueAsString(data), 201).get("id").asLong();
        long planId = publishedPlan(pregnant).get("id").asLong();
        JsonNode adequacy = call(get("/api/prescriptions/" + planId + "/adequacy"), token, null, 200);
        assertThat(adequacy.get("available").asBoolean()).isFalse();
        assertThat(adequacy.get("unavailableBecause").asText()).contains("Gestantes");

        JsonNode template = call(post("/api/prescriptions"), token, """
                {"title":"Modelo","method":"FOODS","template":true,
                 "meals":[{"name":"Almoço","items":[{"foodId":%d,"quantity":200}]}]}""".formatted(leite), 201);
        JsonNode none = call(get("/api/prescriptions/" + template.get("id").asLong() + "/adequacy"), token, null, 200);
        assertThat(none.get("available").asBoolean()).isFalse();
    }

    private static JsonNode find(JsonNode adequacy, String nutrient) {
        for (JsonNode row : adequacy.get("rows")) {
            if (row.get("nutrient").asText().equals(nutrient)) {
                return row;
            }
        }
        throw new AssertionError("sem a linha " + nutrient);
    }
}
