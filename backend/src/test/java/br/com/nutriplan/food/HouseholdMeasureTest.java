package br.com.nutriplan.food;

import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.repository.HouseholdMeasureRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The feature exists because the plan has to be readable by the patient: nobody
 * serves 5 g of salt nor weighs 25 g of rice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HouseholdMeasureTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired HouseholdMeasureRepository householdMeasureRepository;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void authenticate() throws Exception {
        tokenA = register("measure");
        tokenB = register("measureB");
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

    /** Search restricted to one source, so the test does not depend on which base matches first. */
    private JsonNode find(String token, String term, String source) throws Exception {
        var req = get("/api/foods").param("term", term)
                .header("Authorization", "Bearer " + token);
        if (source != null) {
            req = req.param("source", source);
        }
        String body = mvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode find(String token, String term) throws Exception {
        return find(token, term, "TACO");
    }

    private long idDe(String token, String term) throws Exception {
        JsonNode content = find(token, term).get("content");
        assertThat(content).as("nenhum alimento da TACO para '%s'", term).isNotEmpty();
        return content.get(0).get("id").asLong();
    }

    private JsonNode detail(String token, long id) throws Exception {
        String body = mvc.perform(get("/api/foods/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("o acervo base de porcoes da TACO e carregado no boot")
    void loadsCatalogBase() {
        assertThat(householdMeasureRepository.countNoCatalogBySource(DataSource.TACO))
                .isEqualTo(1117);
    }

    @Test
    @DisplayName("nenhum alimento da TACO fica sem porcao usual")
    void tacoHasCoverageServingsTotal() {
        assertThat(householdMeasureRepository.countWithoutNoneServing(DataSource.TACO))
                .as("alimentos da TACO sem nenhuma medida caseira")
                .isZero();
    }

    @Test
    @DisplayName("produto industrializado ganha a porcao da embalagem do rotulo")
    void productProcessedHasPackageServing() throws Exception {
        JsonNode content = find(tokenA, "leite condensado", "OPEN_FOOD_FACTS").get("content");
        assertThat(content).as("nenhum produto industrializado encontrado").isNotEmpty();

        // It looks for one that has a portion derived from the package.
        boolean foundPackage = false;
        for (JsonNode item : content) {
            JsonNode detail = detail(tokenA, item.get("id").asLong());
            for (JsonNode measure : detail.get("measures")) {
                if (measure.get("description").asText().startsWith("embalagem")) {
                    assertThat(measure.get("grams").decimalValue()).isPositive();
                    assertThat(measure.get("standard").asBoolean()).isTrue();
                    foundPackage = true;
                    break;
                }
            }
            if (foundPackage) {
                break;
            }
        }
        assertThat(foundPackage)
                .as("nenhum produto trouxe a porcao da embalagem")
                .isTrue();
    }

    @Test
    @DisplayName("sal e prescrito em pitada, nao em gramas")
    void saltHasUsualServing() throws Exception {
        long id = idDe(tokenA, "sal, grosso");
        JsonNode measures = detail(tokenA, id).get("measures");

        assertThat(measures).isNotEmpty();

        var descriptions = measures.findValuesAsText("description");
        assertThat(descriptions).contains("pitada", "colher de chá", "colher de sopa");

        JsonNode pitada = null;
        for (JsonNode m : measures) {
            if ("pitada".equals(m.get("description").asText())) {
                pitada = m;
            }
        }
        assertThat(pitada).isNotNull();
        assertThat(pitada.get("standard").asBoolean()).isTrue();
        assertThat(pitada.get("grams").decimalValue()).isEqualByComparingTo("0.4");
        assertThat(pitada.get("forCatalogBase").asBoolean()).isTrue();
        // A catalog portion is common to everyone: no practice edits it.
        assertThat(pitada.get("editable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("todo alimento da TACO tem ao menos uma porcao usual")
    void allFoodHasServing() throws Exception {
        // A sample of foods from different groups, including those that got
        // only the group's generic portions.
        for (String term : new String[]{"arroz, tipo 1, cozido", "feijão, carioca, cozido",
                "banana, nanica", "leite, de vaca, integral", "ovo, de galinha, inteiro, cru",
                "azeite, de oliva", "sardinha, assada", "castanha"}) {
            JsonNode content = find(tokenA, term).get("content");
            if (content.isEmpty()) {
                continue;
            }
            long id = content.get(0).get("id").asLong();
            assertThat(detail(tokenA, id).get("measures"))
                    .as("alimento '%s' ficou sem porcao usual", term)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("calcula a porcao a partir da medida caseira do acervo")
    void calculatesByCatalogMeasure() throws Exception {
        long id = idDe(tokenA, "arroz, tipo 1, cozido");
        JsonNode detail = detail(tokenA, id);

        JsonNode spoon = null;
        for (JsonNode m : detail.get("measures")) {
            if (m.get("description").asText().startsWith("colher de sopa")) {
                spoon = m;
            }
        }
        assertThat(spoon).as("arroz cozido deveria ter colher de sopa").isNotNull();

        BigDecimal gramsBySpoon = spoon.get("grams").decimalValue();
        BigDecimal kcalPor100 = detail.get("composition").get("energyKcal").decimalValue();

        String body = mvc.perform(get("/api/foods/" + id + "/serving")
                        .param("quantity", "3")
                        .param("measureId", spoon.get("id").asText())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode serving = json.readTree(body);

        BigDecimal gramsExpected = gramsBySpoon.multiply(new BigDecimal("3"));
        assertThat(serving.get("grams").decimalValue()).isEqualByComparingTo(gramsExpected);
        assertThat(serving.get("measureUsed").asText()).isEqualTo("3 colheres de sopa cheias");

        BigDecimal kcalExpected = kcalPor100
                .multiply(gramsExpected)
                .divide(new BigDecimal("100"), 3, java.math.RoundingMode.HALF_UP);
        assertThat(serving.get("composition").get("energyKcal").decimalValue())
                .isEqualByComparingTo(kcalExpected);
    }

    @Test
    @DisplayName("nutricionista cadastra a propria porcao sobre alimento da TACO")
    void registersServingOwnAboutPublicBase() throws Exception {
        long id = idDe(tokenA, "arroz, tipo 1, cozido");

        String body = mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"colher de servir da clínica","grams":45,"standard":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = json.readTree(body);
        assertThat(created.get("forCatalogBase").asBoolean()).isFalse();
        assertThat(created.get("editable").asBoolean()).isTrue();

        // It appears to whoever created it...
        assertThat(detail(tokenA, id).get("measures").findValuesAsText("description"))
                .contains("colher de servir da clínica");

        // ...and does not leak to another practice.
        assertThat(detail(tokenB, id).get("measures").findValuesAsText("description"))
                .doesNotContain("colher de servir da clínica");
    }

    @Test
    @DisplayName("a porcao propria aparece antes das do acervo")
    void ownServingHasPrecedence() throws Exception {
        long id = idDe(tokenA, "feijão, carioca, cozido");

        mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"concha da casa","grams":95,"standard":false}"""))
                .andExpect(status().isCreated());

        JsonNode measures = detail(tokenA, id).get("measures");
        assertThat(measures.get(0).get("description").asText()).isEqualTo("concha da casa");
        assertThat(measures.get(0).get("forCatalogBase").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("recusa duas porcoes com o mesmo nome no mesmo consultorio")
    void rejectsServingDuplicated() throws Exception {
        long id = idDe(tokenA, "banana, prata");
        String req = """
                {"description":"unidade grande","grams":90,"standard":false}""";

        mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnprocessableEntity());

        // But another practice can use the same name.
        mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("nao remove porcao do acervo base")
    void notRemoveCatalogServing() throws Exception {
        long id = idDe(tokenA, "sal, grosso");
        long catalogMeasure = detail(tokenA, id).get("measures").get(0).get("id").asLong();

        mvc.perform(delete("/api/foods/" + id + "/measures/" + catalogMeasure)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("nao usa porcao cadastrada por outro consultorio no calculo")
    void notCalculatesComServingOther() throws Exception {
        long id = idDe(tokenA, "abacate, cru");

        String body = mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"porção do consultório A","grams":70,"standard":false}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long measureDe = json.readTree(body).get("id").asLong();

        mvc.perform(get("/api/foods/" + id + "/serving")
                        .param("quantity", "1")
                        .param("measureId", String.valueOf(measureDe))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("recusa porcao com peso zero")
    void rejectsInvalidWeight() throws Exception {
        long id = idDe(tokenA, "tomate, salada");

        mvc.perform(post("/api/foods/" + id + "/measures")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"nada","grams":0,"standard":false}"""))
                .andExpect(status().isBadRequest());
    }
}
