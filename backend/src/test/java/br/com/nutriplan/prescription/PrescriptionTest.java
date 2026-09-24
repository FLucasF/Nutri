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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

        assertThat(items.get(0).get("serving").asText()).isEqualTo("à vontade");
        // Even though a quantity arrived, the qualitative method does not record it.
        assertThat(items.get(1).has("grams")).isFalse();
        assertThat(plan.get("dayTotal").get("itemsInCalculation").asInt()).isZero();
        assertThat(plan.get("dayTotal").get("itemsOutsideCalculation").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("a foto da refeição é anexada, lida e sai no PDF")
    void mealPhotoIsAttachedReadAndPrinted() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        long planId = plan.get("id").asLong();
        long mealId = plan.get("meals").get(0).get("id").asLong();
        assertThat(plan.get("meals").get(0).get("hasPhoto").asBoolean()).isFalse();

        int withoutPhoto = pdfOf(planId).length;

        var file = new MockMultipartFile("file", "prato.png", "image/png", PNG_1X1);
        mvc.perform(multipart("/api/prescriptions/" + planId + "/meals/" + mealId + "/photo")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        JsonNode reread = getJson(tokenA, "/api/prescriptions/" + planId);
        assertThat(reread.get("meals").get(0).get("hasPhoto").asBoolean()).isTrue();
        assertThat(reread.get("meals").get(0).get("photoName").asText()).isEqualTo("prato.png");

        // Sai no PDF: é onde ele quer ver a foto, não só na tela.
        assertThat(pdfOf(planId).length).isGreaterThan(withoutPhoto);

        mvc.perform(get("/api/prescriptions/" + planId + "/meals/" + mealId + "/photo")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a foto precisa ser imagem")
    void thePhotoHasToBeAnImage() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        long planId = plan.get("id").asLong();
        long mealId = plan.get("meals").get(0).get("id").asLong();

        var file = new MockMultipartFile("file", "planilha.csv", "text/csv", "a,b".getBytes());
        mvc.perform(multipart("/api/prescriptions/" + planId + "/meals/" + mealId + "/photo")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().is(422));
    }

    /** PNG 1x1 válido, o menor arquivo que o OpenPDF aceita desenhar. */
    private static final byte[] PNG_1X1 = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private byte[] pdfOf(long planId) throws Exception {
        return mvc.perform(get("/api/prescriptions/" + planId + "/pdf")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    @DisplayName("favorita guarda uma cópia, que sobrevive ao plano de origem")
    void favouriteSurvivesItsSourcePlan() throws Exception {
        // A favorita é cópia e não referência: apagar o plano de onde ela veio
        // não pode levar junto a refeição que ele salvou.
        JsonNode plan = postJson(tokenA, "/api/prescriptions", planComArroz(4), 201);
        long planId = plan.get("id").asLong();

        JsonNode saved = postJson(tokenA, "/api/meal-favorites", """
               {"name":"Almoço padrão","meal":{"name":"Almoço","items":[
                  {"foodId":%d,"measureId":%d,"quantity":4}]}}"""
                .formatted(arrozId, arrozMeasureId), 201);

        assertThat(saved.get("name").asText()).isEqualTo("Almoço padrão");
        assertThat(saved.get("mealName").asText()).isEqualTo("Almoço");
        assertThat(saved.get("itemsTotal").asInt()).isEqualTo(1);
        assertThat(saved.get("energyKcal").decimalValue().signum()).isPositive();

        mvc.perform(delete("/api/prescriptions/" + planId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        JsonNode still = getJson(tokenA, "/api/meal-favorites");
        assertThat(still).hasSize(1);
        assertThat(still.get(0).get("name").asText()).isEqualTo("Almoço padrão");
    }

    @Test
    @DisplayName("favoritar funciona antes de o plano existir")
    void favouritingWorksBeforeThePlanIsSaved() throws Exception {
        // No editor a refeição pode ainda não ter sido salva. Exigir que ele
        // salvasse o plano antes seria burocracia para guardar o próprio
        // trabalho.
        JsonNode saved = postJson(tokenA, "/api/meal-favorites", """
               {"name":"Café rápido","meal":{"name":"Café da Manhã","items":[
                  {"foodId":%d,"measureId":%d,"quantity":2},
                  {"kind":"SEPARATOR"},
                  {"foodId":%d,"measureId":%d,"quantity":1}]}}"""
                .formatted(arrozId, arrozMeasureId, arrozId, arrozMeasureId), 201);

        // O separador vem junto: ele faz parte de como a refeição foi montada.
        JsonNode items = getJson(tokenA, "/api/meal-favorites/" + saved.get("id").asLong())
                .get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(1).get("kind").asText()).isEqualTo("SEPARATOR");
    }

    @Test
    @DisplayName("refeição vazia não vira favorita")
    void anEmptyMealIsNotWorthSaving() throws Exception {
        postJson(tokenA, "/api/meal-favorites",
                "{\"name\":\"Nada\",\"meal\":{\"name\":\"Almoço\",\"items\":[]}}", 422);
    }

    @Test
    @DisplayName("a favorita de um consultório não existe para o outro")
    void favouritesDoNotCrossAccounts() throws Exception {
        JsonNode saved = postJson(tokenA, "/api/meal-favorites", """
               {"name":"Almoço padrão","meal":{"name":"Almoço","items":[
                  {"foodId":%d,"measureId":%d,"quantity":4}]}}"""
                .formatted(arrozId, arrozMeasureId), 201);

        assertThat(getJson(tokenB, "/api/meal-favorites")).isEmpty();
        mvc.perform(get("/api/meal-favorites/" + saved.get("id").asLong())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("compara o prescrito com o teórico e classifica a faixa")
    void comparesPrescribedAgainstPlanned() throws Exception {
        // A aba de distribuição da página 32. Meta de 2000 kcal com 30/50/20:
        // o teórico de proteína é 2000 × 0,30 ÷ 4 = 150 g.
        JsonNode plan = postJson(tokenA, "/api/prescriptions", """
               {"title":"Plano com meta","patientId":%d,"method":"FOODS","template":false,
                "targetEnergyKcal":2000,
                "targetProteinPct":30,"targetCarbohydratePct":50,"targetFatPct":20,
                "targetWeightKg":80,
                "meals":[{"name":"Almoço","items":[
                   {"foodId":%d,"measureId":%d,"quantity":4}]}]}"""
                .formatted(patient, arrozId, arrozMeasureId), 201);

        JsonNode rows = plan.get("dayTotal").get("comparison");
        assertThat(rows).hasSize(3);

        JsonNode protein = rows.get(0);
        assertThat(protein.get("macro").asText()).isEqualTo("protein");
        assertThat(protein.get("theoreticalG").decimalValue()).isEqualByComparingTo("150.0");
        // g/kg sobre o peso programado, que foi a resposta dele à pergunta 1.
        assertThat(protein.get("theoreticalPerKg").decimalValue()).isEqualByComparingTo("1.88");

        // Quatro colheres de arroz ficam muito abaixo de 150 g de proteína.
        assertThat(protein.get("band").asText()).isEqualTo("BELOW");
        assertThat(protein.get("differenceG").decimalValue().signum()).isNegative();
    }

    @Test
    @DisplayName("a faixa de 95 a 105% é o que decide a cor")
    void theBandIsWhatDecidesTheColour() throws Exception {
        // Meta de 100 kcal só de proteína: 25 g de teórico. O arroz entra com
        // o que entra; o que se verifica aqui é o corte, não o alimento.
        JsonNode plan = postJson(tokenA, "/api/prescriptions", """
               {"title":"Faixa","patientId":%d,"method":"FOODS","template":false,
                "targetEnergyKcal":2000,
                "targetProteinPct":0,"targetCarbohydratePct":100,"targetFatPct":0,
                "meals":[{"name":"Almoço","items":[
                   {"foodId":%d,"measureId":%d,"quantity":4}]}]}"""
                .formatted(patient, arrozId, arrozMeasureId), 201);

        JsonNode carb = plan.get("dayTotal").get("comparison").get(1);
        assertThat(carb.get("macro").asText()).isEqualTo("carbohydrate");
        // 2000 kcal ÷ 4 = 500 g de teórico; quatro colheres ficam bem abaixo.
        assertThat(carb.get("theoreticalG").decimalValue()).isEqualByComparingTo("500.0");
        assertThat(carb.get("band").asText()).isEqualTo("BELOW");
    }

    @Test
    @DisplayName("distribuição que não soma 100% é recusada com a soma na mensagem")
    void refusesADistributionThatDoesNotAddUp() throws Exception {
        String answer = mvc.perform(post("/api/prescriptions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                               {"title":"Torta","patientId":%d,"method":"FOODS","template":false,
                                "targetEnergyKcal":2000,
                                "targetProteinPct":30,"targetCarbohydratePct":50,
                                "targetFatPct":30,
                                "meals":[]}""".formatted(patient)))
                .andExpect(status().is(422))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(answer).get("message").asText()).contains("110");
    }

    @Test
    @DisplayName("sem meta energética não há comparação para mostrar")
    void noTargetMeansNoComparison() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/prescriptions", """
               {"title":"Sem meta","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"Almoço","items":[
                   {"foodId":%d,"measureId":%d,"quantity":4}]}]}"""
                .formatted(patient, arrozId, arrozMeasureId), 201);

        // Comparar contra zero mostraria uma diferença que ninguém planejou.
        assertThat(plan.get("dayTotal").get("comparison")).isEmpty();
    }

    @Test
    @DisplayName("refeição fora da contabilização não soma no dia, mas soma em si")
    void mealOutOfTheCalculationStillTotalsItself() throws Exception {
        // É como se prescreve refeição substituta: duas opções de almoço não
        // contam como dois almoços, e ele precisa saber quanto vale cada uma.
        JsonNode plan = postJson(tokenA, "/api/prescriptions", """
               {"title":"Almoço com opção","patientId":%d,"method":"FOODS","template":false,
                "meals":[
                  {"name":"Almoço","items":[{"foodId":%d,"measureId":%d,"quantity":4}]},
                  {"name":"Almoço (opção 2)","inCalculation":false,
                   "items":[{"foodId":%d,"measureId":%d,"quantity":4}]}
                ]}""".formatted(patient, arrozId, arrozMeasureId, arrozId, arrozMeasureId), 201);

        JsonNode meals = plan.get("meals");
        assertThat(meals.get(0).get("inCalculation").asBoolean()).isTrue();
        assertThat(meals.get(1).get("inCalculation").asBoolean()).isFalse();

        BigDecimal ofFirst = meals.get(0).get("total").get("composition")
                .get("energyKcal").decimalValue();
        BigDecimal ofSecond = meals.get(1).get("total").get("composition")
                .get("energyKcal").decimalValue();
        // A segunda continua sendo calculada: ela vale o mesmo que a primeira.
        assertThat(ofSecond).isEqualByComparingTo(ofFirst);

        // Mas o dia soma só uma vez.
        assertThat(plan.get("dayTotal").get("composition").get("energyKcal").decimalValue())
                .isEqualByComparingTo(ofFirst);
    }

    @Test
    @DisplayName("o separador guarda a posição e não entra na conta")
    void separatorHoldsAPositionAndDoesNotCount() throws Exception {
        // A barra que ele descreve: "eu quero que o paciente coma o farelo de
        // aveia com o mamão... isso iria separar".
        JsonNode plan = postJson(tokenA, "/api/prescriptions", """
               {"title":"Café separado","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"Café da Manhã","items":[
                   {"foodId":%d,"measureId":%d,"quantity":2},
                   {"kind":"SEPARATOR"},
                   {"foodId":%d,"measureId":%d,"quantity":2}
                ]}]}""".formatted(patient, arrozId, arrozMeasureId, arrozId, arrozMeasureId), 201);

        JsonNode items = plan.get("meals").get(0).get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(1).get("kind").asText()).isEqualTo("SEPARATOR");
        // Ele fica entre os dois alimentos, que é a única coisa que ele faz.
        assertThat(items.get(0).get("kind").asText()).isEqualTo("FOOD");
        assertThat(items.get(2).get("kind").asText()).isEqualTo("FOOD");

        // E não conta: dois itens somam, o separador não.
        assertThat(plan.get("dayTotal").get("itemsInCalculation").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("o nome da refeição cabe a frase que ele escreve")
    void mealNameFitsHisSentence() throws Exception {
        String longName = "Café da Manhã – Mamão c/ Farelo de Aveia, Pão c/ Ovos Fritos "
                + "e Café c/ Açúcar";
        JsonNode plan = postJson(tokenA, "/api/prescriptions", """
               {"title":"Plano","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"%s","items":[{"foodId":%d,"measureId":%d,"quantity":2}]}]}"""
                .formatted(patient, longName, arrozId, arrozMeasureId), 201);

        assertThat(plan.get("meals").get(0).get("name").asText()).isEqualTo(longName);
    }

    @Test
    @DisplayName("o plano por alimentos aceita substituição no item")
    void foodPlanAcceptsSubstitutions() throws Exception {
        String body = """
               {"title":"Plano com troca","patientId":%d,"method":"FOODS","template":false,
                "meals":[{"name":"Café","items":[
                   {"foodId":%d,"measureId":%d,"quantity":2,
                    "substitutions":[{"description":"1 tapioca média","quantity":60}]}
                ]}]}""".formatted(patient, arrozId, arrozMeasureId);

        JsonNode plan = postJson(tokenA, "/api/prescriptions", body, 201);
        JsonNode substitutions = plan.get("meals").get(0).get("items").get(0).get("substitutions");
        assertThat(substitutions).hasSize(1);
        assertThat(substitutions.get(0).get("serving").asText()).isEqualTo("60 g");
    }

    @Test
    @DisplayName("substituição pede porção, então o plano qualitativo recusa")
    void substitutionsRequireAQuantifiedMethod() throws Exception {
        // The swap is portion for portion; a qualitative plan has no portion to
        // put on either side of it.
        String body = """
               {"title":"Plano sem porção","patientId":%d,"method":"QUALITATIVE","template":false,
                "meals":[{"name":"Café","items":[
                   {"description":"fruta da estação",
                    "substitutions":[{"description":"1 tapioca média","quantity":60}]}
                ]}]}""".formatted(patient);

        postJson(tokenA, "/api/prescriptions", body, 422);
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
