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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrescriptionTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long patient;
    private long arrozId;
    private long arrozMeasureId;
    private BigDecimal arrozKcalPor100;
    private BigDecimal arrozGramsBySpoon;

    @BeforeEach
    void prepare() throws Exception {
        tokenA = registerNutri("presA");
        tokenB = registerNutri("presB");
        patient = createPatient(tokenA, "Marina Duarte");

        JsonNode search = getJson(tokenA, "/api/foods?term=arroz,%20tipo%201,%20cozido&source=TACO");
        // fallback: a hand-built parameter may not decode; redo it by param
        if (search.get("content").isEmpty()) {
            search = findFood(tokenA, "arroz, tipo 1, cozido");
        }
        arrozId = search.get("content").get(0).get("id").asLong();

        JsonNode detail = getJson(tokenA, "/api/foods/" + arrozId);
        arrozKcalPor100 = detail.get("composition").get("energyKcal").decimalValue();
        for (JsonNode m : detail.get("measures")) {
            if (m.get("description").asText().startsWith("colher de sopa")) {
                arrozMeasureId = m.get("id").asLong();
                arrozGramsBySpoon = m.get("grams").decimalValue();
                break;
            }
        }
        assertThat(arrozMeasureId).as("arroz cozido precisa de colher de sopa").isPositive();
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

    private JsonNode findFood(String token, String term) throws Exception {
        String body = mvc.perform(get("/api/foods")
                        .param("term", term).param("source", "TACO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode getJson(String token, String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode postJson(String token, String url, String body, int expected) throws Exception {
        String responseBody = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return responseBody.isBlank() ? null : json.readTree(responseBody);
    }

    /** A plan with one meal and one rice item measured in spoons. */
    private String planComArroz(int spoons) {
        return """
               {"title":"Plano de emagrecimento","patientId":%d,"method":"FOODS",
                "targetEnergyKcal":2000,"template":false,
                "handouts":"Beba dois litros de água por dia.",
                "internalNotes":"Paciente relatou ansiedade noturna.",
                "meals":[
                  {"name":"Almoço","time":"12:30","items":[
                    {"foodId":%d,"measureId":%d,"quantity":%d}
                  ]}
                ]}""".formatted(patient, arrozId, arrozMeasureId, spoons);
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("converte a porção em gramas e totaliza a refeição")
    void calculatesTotalsServingPartir() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);

        JsonNode item = plan.get("meals").get(0).get("items").get(0);
        BigDecimal gramsExpected = arrozGramsBySpoon.multiply(BigDecimal.valueOf(4));

        assertThat(item.get("grams").decimalValue()).isEqualByComparingTo(gramsExpected);
        // The measure agrees with the quantity: see PluralMeasureTest.
        assertThat(item.get("serving").asText()).isEqualTo("4 colheres de sopa cheias");

        BigDecimal kcalExpected = arrozKcalPor100
                .multiply(gramsExpected)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);

        assertThat(plan.get("meals").get(0).get("total").get("composition")
                .get("energyKcal").decimalValue()).isEqualByComparingTo(kcalExpected);
        assertThat(plan.get("dayTotal").get("composition")
                .get("energyKcal").decimalValue()).isEqualByComparingTo(kcalExpected);
        assertThat(plan.get("dayTotal").get("itemsInCalculation").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("o total informa quais nutrientes ficaram incompletos")
    void flagsTotalCoverage() throws Exception {
        // An own food with energy only: no other nutrient reported.
        String own = """
                {"description":"Suplemento X","composition":{"energyKcal":300}}""";
        long supplementId = postJson(tokenA, "/api/foods", own, 201).get("id").asLong();

        String body = """
               {"title":"Plano misto","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"Café","items":[
                   {"foodId":%d,"measureId":%d,"quantity":2},
                   {"foodId":%d,"quantity":50}
                ]}]}""".formatted(patient, arrozId, arrozMeasureId, supplementId);

        JsonNode plan = postJson(tokenA, "/api/prescriptions", body, 201);
        JsonNode total = plan.get("dayTotal");

        assertThat(total.get("itemsInCalculation").asInt()).isEqualTo(2);
        // Protein came only from the rice: the total exists, but it underestimates.
        assertThat(total.get("nutrientsIncomplete").findValuesAsText("")).isNotNull();
        var incomplete = json.convertValue(total.get("nutrientsIncomplete"), java.util.List.class);
        assertThat(incomplete).contains("proteinG");
        assertThat(total.get("reliable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("plano qualitativo não inventa quantidade")
    void planQualitativeNotQuantifies() throws Exception {
        String body = """
               {"title":"Orientação inicial","patientId":%d,"method":"QUALITATIVE","template":false,
                "meals":[{"name":"Jantar","items":[
                   {"description":"Salada de folhas à vontade"},
                   {"foodId":%d,"measureId":%d,"quantity":3}
                ]}]}""".formatted(patient, arrozId, arrozMeasureId);

        JsonNode plan = postJson(tokenA, "/api/prescriptions", body, 201);
        JsonNode items = plan.get("meals").get(0).get("items");

        assertThat(items.get(0).get("serving").asText()).isEqualTo("a vontade");
        // Even though a quantity arrived, the qualitative method does not record it.
        assertThat(items.get(1).has("grams")).isFalse();
        assertThat(plan.get("dayTotal").get("itemsInCalculation").asInt()).isZero();
        assertThat(plan.get("dayTotal").get("itemsOutsideCalculation").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("substituições só existem no método por equivalentes")
    void substitutionsRequireMethodCompatible() throws Exception {
        String body = """
               {"title":"Plano com troca","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"Café","items":[
                   {"foodId":%d,"measureId":%d,"quantity":2,
                    "substitutions":[{"description":"1 tapioca média","quantity":60}]}
                ]}]}""".formatted(patient, arrozId, arrozMeasureId);

        postJson(tokenA, "/api/prescriptions", body, 422);

        JsonNode ok = postJson(tokenA, "/api/prescriptions",
                body.replace("\"method\":\"FOODS\"", "\"method\":\"SUBSTITUTIONS\""), 201);
        JsonNode substitutions = ok.get("meals").get(0).get("items").get(0).get("substitutions");
        assertThat(substitutions).hasSize(1);
        assertThat(substitutions.get(0).get("serving").asText()).isEqualTo("60 g");
    }

    @Test
    @DisplayName("o link só serve plano publicado")
    void publicLinkRequiresPublication() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        String link = plan.get("publicIdentifier").asText();

        // A draft answers as nonexistent, without revealing work in progress.
        mvc.perform(get("/api/public/plans/" + link)).andExpect(status().isNotFound());

        postJson(tokenA, "/api/prescriptions/" + plan.get("id").asLong() + "/publish", "", 200);

        mvc.perform(get("/api/public/plans/" + link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Plano de emagrecimento"))
                .andExpect(jsonPath("$.patientName").value("Marina"))
                .andExpect(jsonPath("$.nutritionistCrn").value("CRN-3 99999"))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.meals[0].items[0].serving").value("4 colheres de sopa cheias"));
    }

    @Test
    @DisplayName("o link do paciente nunca expõe anotação interna")
    void publicLinkNotLeaksDatumInternal() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        postJson(tokenA, "/api/prescriptions/" + plan.get("id").asLong() + "/publish", "", 200);

        String body = mvc.perform(get("/api/public/plans/"
                        + plan.get("publicIdentifier").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("ansiedade noturna");
        assertThat(body).doesNotContain("internalNotes");
        assertThat(body).doesNotContain("accountId");
        // The guidance to the patient, that one does appear.
        assertThat(body).contains("dois litros de água");
    }

    @Test
    @DisplayName("regerar o link invalida o endereço já entregue")
    void regenerateInvalidatesLinkPrevious() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        long id = plan.get("id").asLong();
        postJson(tokenA, "/api/prescriptions/" + id + "/publish", "", 200);

        String old = plan.get("publicIdentifier").asText();
        mvc.perform(get("/api/public/plans/" + old)).andExpect(status().isOk());

        String novo = postJson(tokenA, "/api/prescriptions/" + id + "/regenerate-link", "", 200)
                .get("publicIdentifier").asText();

        assertThat(novo).isNotEqualTo(old);
        mvc.perform(get("/api/public/plans/" + old)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/plans/" + novo)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("corrigir a porção não reescreve plano já prescrito")
    void prescribedWeightStaysFrozen() throws Exception {
        // An own food, so the portion can be changed later.
        String own = """
                {"description":"Granola da casa","composition":{"energyKcal":400},
                 "measures":[{"description":"colher de sopa","grams":15,"standard":true}]}""";
        JsonNode food = postJson(tokenA, "/api/foods", own, 201);
        long foodId = food.get("id").asLong();
        long measureId = food.get("measures").get(0).get("id").asLong();

        String body = """
               {"title":"Plano granola","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"Café","items":[
                   {"foodId":%d,"measureId":%d,"quantity":2}]}]}"""
                .formatted(patient, foodId, measureId);

        JsonNode plan = postJson(tokenA, "/api/prescriptions", body, 201);
        long planId = plan.get("id").asLong();
        assertThat(plan.get("meals").get(0).get("items").get(0).get("grams").decimalValue())
                .isEqualByComparingTo("30");

        // The practice starts treating the spoon as 20 g.
        mvc.perform(put("/api/foods/" + foodId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Granola da casa","composition":{"energyKcal":400},
                                 "measures":[{"description":"colher de sopa","grams":20,"standard":true}]}"""))
                .andExpect(status().isOk());

        // The plan already prescribed keeps the original 30 g.
        JsonNode after = getJson(tokenA, "/api/prescriptions/" + planId);
        assertThat(after.get("meals").get(0).get("items").get(0).get("grams").decimalValue())
                .isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("um consultório não acessa plano de outro")
    void isolatesPlansBetweenAccounts() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        long id = plan.get("id").asLong();

        mvc.perform(get("/api/prescriptions/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/prescriptions/" + id + "/publish")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/prescriptions").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("não publica plano vazio")
    void notPublicPlanWithoutItem() throws Exception {
        String empty = """
               {"title":"Ainda vazio","patientId":%d,"method":"FOODS","template":false,
                "meals":[]}""".formatted(patient);
        long id = postJson(tokenA, "/api/prescriptions", empty, 201).get("id").asLong();

        postJson(tokenA, "/api/prescriptions/" + id + "/publish", "", 422);
    }

    @Test
    @DisplayName("modelo não tem paciente e vira plano ao ser duplicado")
    void templateBecamePlanAoDuplicate() throws Exception {
        String template = """
               {"title":"Modelo low carb","method":"FOODS","template":true,
                "meals":[{"name":"Almoço","items":[
                   {"foodId":%d,"measureId":%d,"quantity":3}]}]}"""
                .formatted(arrozId, arrozMeasureId);

        JsonNode saved = postJson(tokenA, "/api/prescriptions", template, 201);
        assertThat(saved.get("template").asBoolean()).isTrue();
        assertThat(saved.has("patientId")).isFalse();

        JsonNode copies = postJson(tokenA,
                "/api/prescriptions/" + saved.get("id").asLong() + "/duplicate?patientId=" + patient,
                "", 200);

        assertThat(copies.get("patientId").asLong()).isEqualTo(patient);
        assertThat(copies.get("status").asText()).isEqualTo("DRAFT");
        assertThat(copies.get("meals").get(0).get("items")).hasSize(1);
    }

    @Test
    @DisplayName("plano encerrado não aceita edição")
    void planClosedNotEditable() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        long id = plan.get("id").asLong();
        postJson(tokenA, "/api/prescriptions/" + id + "/publish", "", 200);
        postJson(tokenA, "/api/prescriptions/" + id + "/close", "", 200);

        mvc.perform(put("/api/prescriptions/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(planComArroz(6)))
                .andExpect(status().isUnprocessableEntity());

        // It stays visible to the patient, marked as closed.
        mvc.perform(get("/api/public/plans/" + plan.get("publicIdentifier").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.current").value(false));
    }

    @Test
    @DisplayName("calcula a distribuição de macronutrientes")
    void calculatesMacrosDistribution() throws Exception {
        // 100 g com 10 g P, 20 g C, 10 g L -> 40 + 80 + 90 = 210 kcal
        String own = """
                {"description":"Refeição controlada",
                 "composition":{"energyKcal":210,"proteinG":10,"carbohydrateG":20,"fatG":10}}""";
        long id = postJson(tokenA, "/api/foods", own, 201).get("id").asLong();

        String body = """
               {"title":"Plano macros","patientId":%d,"method":"FOODS","template":false,
                "targetEnergyKcal":420,
                "meals":[{"name":"Almoço","items":[{"foodId":%d,"quantity":100}]}]}"""
                .formatted(patient, id);

        JsonNode total = postJson(tokenA, "/api/prescriptions", body, 201).get("dayTotal");
        JsonNode dist = total.get("distribution");

        assertThat(dist.get("proteinPct").decimalValue()).isEqualByComparingTo("19.0");
        assertThat(dist.get("carbohydratePct").decimalValue()).isEqualByComparingTo("38.1");
        assertThat(dist.get("lipidPct").decimalValue()).isEqualByComparingTo("42.9");
        assertThat(dist.get("calculatedEnergyKcal").decimalValue()).isEqualByComparingTo("210.0");
        // 210 kcal prescribed against a goal of 420: half.
        assertThat(total.get("adequacyEnergyPct").decimalValue()).isEqualByComparingTo("50.0");
    }
}
