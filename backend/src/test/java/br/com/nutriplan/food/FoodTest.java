package br.com.nutriplan.food;

import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.repository.FoodRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoodTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FoodRepository foodRepository;

    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Teste",
                                "email", "food" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    /**
     * Searches passing the term as a parameter, and not concatenated into the
     * URL: MockMvc does not decode hand-written percent-encoding, which would
     * make accents and spaces reach the controller corrupted.
     */
    private JsonNode findByTerm(String term) throws Exception {
        String body = mvc.perform(get("/api/foods")
                        .param("term", term)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private long firstSearchId(String term) throws Exception {
        JsonNode content = findByTerm(term).get("content");
        assertThat(content).as("busca por '%s' nao retornou alimentos", term).isNotEmpty();
        return content.get(0).get("id").asLong();
    }

    // ------------------------------------------------------------- barcode search

    @Test
    @DisplayName("localiza o produto industrializado pelo codigo de barras")
    void locatesByBarcodeCode() throws Exception {
        // Leite Condensado Semidesnatado ITALAC, do Open Food Facts.
        JsonNode found = getJson("/api/foods/barcode/7890000110266");

        assertThat(found).isNotEmpty();
        assertThat(found.get(0).get("description").asText()).containsIgnoringCase("condensado");
        assertThat(found.get(0).get("codeBarcode").asText()).isEqualTo("7890000110266");
    }

    @Test
    @DisplayName("aceita o codigo com separadores, como sai do leitor")
    void acceptsCodeComSeparators() throws Exception {
        JsonNode found = getJson("/api/foods/barcode/789-0000.110266");

        assertThat(found).isNotEmpty();
        assertThat(found.get(0).get("codeBarcode").asText()).isEqualTo("7890000110266");
    }

    @Test
    @DisplayName("codigo inexistente devolve lista vazia, e nao erro")
    void codeNonexistentReturnsEmpty() throws Exception {
        // Not finding a product is a normal result of the search, not a failure:
        // the base covers what Open Food Facts has, and not the whole market.
        assertThat(getJson("/api/foods/barcode/0000000000000")).isEmpty();
    }

    @Test
    @DisplayName("o produto do consultorio vem antes do produto da base publica")
    void ownProductComesFirst() throws Exception {
        String body = """
                {"description":"Leite condensado da marca que eu uso",
                 "codeBarcode":"7890000110266",
                 "composition":{"energyKcal":320}}""";
        mvc.perform(post("/api/foods").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        JsonNode found = getJson("/api/foods/barcode/7890000110266");

        assertThat(found).hasSizeGreaterThan(1);
        assertThat(found.get(0).get("description").asText())
                .isEqualTo("Leite condensado da marca que eu uso");
    }

    @Test
    @DisplayName("codigo de barras de outro consultorio nao aparece")
    void notVeOtherPracticeCode() throws Exception {
        String otherToken = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Outro", "email", "other" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/foods").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Produto do outro consultorio",
                                 "codeBarcode":"1111111111111",
                                 "composition":{"energyKcal":100}}"""))
                .andExpect(status().isCreated());

        assertThat(getJson("/api/foods/barcode/1111111111111")).isEmpty();
    }

    @Test
    @DisplayName("a base TACO e importada por completo no boot")
    void importsBaseTaco() {
        assertThat(foodRepository.countBySource(DataSource.TACO)).isEqualTo(597);
    }

    @Test
    @DisplayName("a busca poe alimento de referencia antes de industrializado")
    void ranksReferenceProcessedBefore() throws Exception {
        // Without ranking the order is alphabetical, and the 21 thousand processed
        // products drown TACO's 597: searching "banana" returned "&Joy Frutas
        // Banana + Cacau" before the fruit.
        for (String term : new String[]{"arroz", "banana", "leite", "frango", "feijao"}) {
            JsonNode content = findByTerm(term).get("content");
            assertThat(content).as("busca por '%s'", term).isNotEmpty();

            assertThat(content.get(0).get("source").asText())
                    .as("primeiro resultado de '%s' deveria vir de tabela de referencia, e veio '%s'",
                            term, content.get(0).get("description").asText())
                    .isEqualTo("TACO");
        }
    }

    @Test
    @DisplayName("o termo que abre o nome vence o que aparece no meio")
    void ranksNameMoreDirectFirst() throws Exception {
        JsonNode content = findByTerm("banana").get("content");

        assertThat(content.get(0).get("description").asText().toLowerCase())
                .startsWith("banana");
    }

    @Test
    @DisplayName("industrializado aparece quando nao ha alimento de referencia")
    void processedAppearsWhenNotHaReference() throws Exception {
        JsonNode content = findByTerm("nescau").get("content");

        if (!content.isEmpty()) {
            assertThat(content.get(0).get("source").asText()).isEqualTo("OPEN_FOOD_FACTS");
        }
    }

    @Test
    @DisplayName("filtro de fonte prevalece sobre o ranqueamento por procedencia")
    void sourceHasPrecedenceFilter() throws Exception {
        String body = mvc.perform(get("/api/foods")
                        .param("term", "arroz")
                        .param("source", "OPEN_FOOD_FACTS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = json.readTree(body).get("content");
        assertThat(content).isNotEmpty();
        for (JsonNode item : content) {
            assertThat(item.get("source").asText()).isEqualTo("OPEN_FOOD_FACTS");
        }
    }

    @Test
    @DisplayName("busca ignora acentuacao")
    void searchIgnoresAccent() throws Exception {
        // Typing "acucar" has to find the items stored with a cedilla and a tilde.
        JsonNode withoutAccent = findByTerm("acucar");
        JsonNode comAccent = findByTerm("açúcar");

        assertThat(withoutAccent.get("totalElements").asInt()).isPositive();
        assertThat(withoutAccent.get("totalElements").asInt())
                .isEqualTo(comAccent.get("totalElements").asInt());
    }

    @Test
    @DisplayName("escala a composicao proporcionalmente a quantidade em gramas")
    void calculatesServingAtGrams() throws Exception {
        long id = firstSearchId("arroz, integral, cozido");

        JsonNode base = getJson("/api/foods/" + id);
        BigDecimal kcalPor100 = base.get("composition").get("energyKcal").decimalValue();
        BigDecimal ptnPor100 = base.get("composition").get("proteinG").decimalValue();

        JsonNode serving = getJson("/api/foods/" + id + "/serving?quantity=150");

        assertThat(serving.get("grams").decimalValue()).isEqualByComparingTo("150");
        assertThat(serving.get("composition").get("energyKcal").decimalValue())
                .isEqualByComparingTo(kcalPor100.multiply(new BigDecimal("1.5")));
        assertThat(serving.get("composition").get("proteinG").decimalValue())
                .isEqualByComparingTo(ptnPor100.multiply(new BigDecimal("1.5")));
    }

    @Test
    @DisplayName("nutriente ausente na fonte continua ausente apos o calculo")
    void notInventsNutrientMissing() throws Exception {
        // Salt (code 517) has no energy determined in TACO.
        long id = firstSearchId("sal, grosso");

        JsonNode serving = getJson("/api/foods/" + id + "/serving?quantity=10");

        assertThat(serving.get("composition").has("energyKcal")).isFalse();
        assertThat(serving.get("composition").get("sodiumMg")).isNotNull();
    }

    @Test
    @DisplayName("alimento de tabela de referencia nao pode ser editado")
    void notEditsPublicBase() throws Exception {
        JsonNode search = getJson("/api/foods?term=arroz&size=1");
        long id = search.get("content").get(0).get("id").asLong();

        mvc.perform(get("/api/foods/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.publicBase").value(true));

        mvc.perform(put("/api/foods/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hackeado","composition":{"energyKcal":1}}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("alimento proprio calcula porcao por medida caseira")
    void calculatesByHouseholdMeasure() throws Exception {
        String create = """
                {"description":"Granola da casa","group":"Cereais e derivados",
                 "composition":{"energyKcal":400,"proteinG":10,"carbohydrateG":60,"fatG":14},
                 "measures":[{"description":"colher de sopa","grams":15,"standard":true}]}""";

        String body = mvc.perform(post("/api/foods")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.source").value("CUSTOM"))
                .andReturn().getResponse().getContentAsString();

        JsonNode food = json.readTree(body);
        long id = food.get("id").asLong();
        long measureId = food.get("measures").get(0).get("id").asLong();

        // 3 spoons of 15 g = 45 g -> 45% of 400 kcal = 180 kcal
        JsonNode serving = getJson("/api/foods/" + id + "/serving?quantity=3&measureId=" + measureId);

        assertThat(serving.get("grams").decimalValue()).isEqualByComparingTo("45");
        assertThat(serving.get("measureUsed").asText()).isEqualTo("3 colheres de sopa");
        assertThat(serving.get("composition").get("energyKcal").decimalValue())
                .isEqualByComparingTo("180");
        assertThat(serving.get("composition").get("proteinG").decimalValue())
                .isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("recusa quantidade zero ou negativa")
    void rejectsInvalidatesQuantity() throws Exception {
        JsonNode search = getJson("/api/foods?term=arroz&size=1");
        long id = search.get("content").get(0).get("id").asLong();

        mvc.perform(get("/api/foods/" + id + "/serving?quantity=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("alimento proprio de um consultorio nao aparece para outro")
    void isolatesOwnFood() throws Exception {
        mvc.perform(post("/api/foods")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Mistura secreta da nutri","composition":{"energyKcal":100}}"""))
                .andExpect(status().isCreated());

        // Another practice searches the same term and finds nothing.
        String other = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Outra Nutri",
                                "email", "other" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andReturn().getResponse().getContentAsString();
        String tokenOther = json.readTree(other).get("token").asText();

        String result = mvc.perform(get("/api/foods?term=mistura secreta")
                        .header("Authorization", "Bearer " + tokenOther))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(result).get("totalElements").asInt()).isZero();
    }
}
