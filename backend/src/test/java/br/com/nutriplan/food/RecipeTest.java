package br.com.nutriplan.food;

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
 * Composite recipes (RF28).
 *
 * The numbers here are checked against the calculation done by hand, and not
 * against what the code returns: a recipe wrong by a factor of two is exactly
 * the kind of defect that goes unnoticed in a test that only checks whether "it
 * calculated".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecipeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;

    @BeforeEach
    void authenticate() throws Exception {
        token = register("recipe");
        tokenB = register("recipeB");
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

    /** An own food with a known composition, so the arithmetic comes out exact. */
    private long food(String tk, String description, int kcal, int protein, int carbohydrate)
            throws Exception {
        String body = """
                {"description":"%s","composition":{"energyKcal":%d,"proteinG":%d,"carbohydrateG":%d}}"""
                .formatted(description, kcal, protein, carbohydrate);
        String answer = mvc.perform(post("/api/foods")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(answer).get("id").asLong();
    }

    private JsonNode create(String tk, String body, int expected) throws Exception {
        String answer = mvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isEmpty() ? null : json.readTree(answer);
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // -------------------------------------------------------------- calculation

    @Test
    @DisplayName("a composição da receita é por 100 g da preparação pronta")
    void compositionPreparationPor100g() throws Exception {
        long arroz = food(token, "Arroz cru", 360, 7, 78);

        // 100 g of raw rice yielding 250 g cooked: the 360 kcal are still there,
        // but now diluted in 250 g — 144 kcal per 100 g.
        JsonNode r = create(token, """
                {"name":"Arroz cozido da casa","yieldGrams":250,
                 "ingredients":[{"foodId":%d,"quantity":100}]}"""
                .formatted(arroz), 201);

        assertThat(r.get("compositionPor100g").get("energyKcal").decimalValue())
                .isEqualByComparingTo("144.000");
        assertThat(r.get("estimatedYield").asBoolean()).isFalse();
        assertThat(r.get("ingredientsWeight").decimalValue()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("sem rendimento informado, a soma dos ingredientes é usada — e o resultado diz isso")
    void estimatedYieldWhenNotReported() throws Exception {
        long a = food(token, "Ingrediente A", 100, 10, 0);
        long b = food(token, "Ingrediente B", 300, 0, 50);

        JsonNode r = create(token, """
                {"name":"Mistura","ingredients":[
                  {"foodId":%d,"quantity":100},
                  {"foodId":%d,"quantity":100}]}"""
                .formatted(a, b), 201);

        // 100 + 300 kcal in 200 g = 200 kcal per 100 g.
        assertThat(r.get("compositionPor100g").get("energyKcal").decimalValue())
                .isEqualByComparingTo("200.000");
        assertThat(r.get("estimatedYield").asBoolean())
                .as("o peso final foi presumido, e a resposta precisa admitir isso")
                .isTrue();
        assertThat(r.get("yieldGrams").decimalValue()).isEqualByComparingTo("200.000");
    }

    @Test
    @DisplayName("o ingrediente pode entrar por medida caseira")
    void ingredientByHouseholdMeasure() throws Exception {
        long oil = food(token, "Oleo", 900, 0, 0);
        String measure = mvc.perform(post("/api/foods/" + oil + "/measures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"colher de sopa","grams":8,"standard":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long measureId = json.readTree(measure).get("id").asLong();

        JsonNode r = create(token, """
                {"name":"Refogado","yieldGrams":100,
                 "ingredients":[{"foodId":%d,"measureId":%d,"quantity":2}]}"""
                .formatted(oil, measureId), 201);

        JsonNode ingredient = r.get("ingredients").get(0);
        assertThat(ingredient.get("grams").decimalValue()).isEqualByComparingTo("16.000");
        // The measure agrees with the quantity, as in every portion in the system.
        assertThat(ingredient.get("quantity").asText()).isEqualTo("2 colheres de sopa");
    }

    @Test
    @DisplayName("nutriente ausente em parte dos ingredientes vira piso, e é sinalizado")
    void nutrientIncompleteEhFlagged() throws Exception {
        long comFiber = food(token, "Farinha integral", 340, 10, 70);
        mvc.perform(put("/api/foods/" + comFiber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Farinha integral",
                                 "composition":{"energyKcal":340,"proteinG":10,"carbohydrateG":70,
                                               "fiberG":9}}"""))
                .andExpect(status().isOk());
        long withoutFiber = food(token, "Fermento", 100, 0, 20);

        JsonNode r = create(token, """
                {"name":"Massa","yieldGrams":200,
                 "ingredients":[
                   {"foodId":%d,"quantity":100},
                   {"foodId":%d,"quantity":100}]}"""
                .formatted(comFiber, withoutFiber), 201);

        assertThat(r.get("nutrientsIncomplete").toString()).contains("fiberG");
        // The flour's fiber still counts: summing while treating absent as
        // zero would be worse, because it would produce a number that looks
        // exact.
        assertThat(r.get("compositionPor100g").get("fiberG").decimalValue())
                .isEqualByComparingTo("4.500");
    }

    @Test
    @DisplayName("nutriente que nenhum ingrediente determina permanece ausente")
    void nutrientMissingAtAllNotBecomesZero() throws Exception {
        long a = food(token, "Simples", 100, 5, 10);

        JsonNode r = create(token, """
                {"name":"So um ingrediente","yieldGrams":100,
                 "ingredients":[{"foodId":%d,"quantity":100}]}""".formatted(a), 201);

        JsonNode composition = r.get("compositionPor100g");
        assertThat(composition.has("sodiumMg") && !composition.get("sodiumMg").isNull())
                .as("sodio nao determinado em nenhum ingrediente nao pode virar zero")
                .isFalse();
        assertThat(r.get("nutrientsIncomplete").toString())
                .as("ausente em todos nao e incompleto: e simplesmente nao determinado")
                .doesNotContain("sodiumMg");
    }

    // ------------------------------------------------------------- portions

    @Test
    @DisplayName("a receita nasce com as porções derivadas do rendimento")
    void generatesServingsDerived() throws Exception {
        long a = food(token, "Base", 200, 5, 30);

        JsonNode r = create(token, """
                {"name":"Bolo","yieldGrams":800,"servings":8,
                 "ingredients":[{"foodId":%d,"quantity":500}]}""".formatted(a), 201);

        assertThat(r.get("gramsByServing").decimalValue()).isEqualByComparingTo("100.000");

        JsonNode detail = getJson(token, "/api/foods/" + r.get("id").asLong());
        var descriptions = detail.get("measures").findValuesAsText("description");
        assertThat(descriptions).contains("porção", "receita inteira");
    }

    @Test
    @DisplayName("mudar o rendimento refaz as porções em vez de acumular")
    void servingsNotAccumulate() throws Exception {
        long a = food(token, "Base", 200, 5, 30);
        long id = create(token, """
                {"name":"Bolo","yieldGrams":800,"servings":8,
                 "ingredients":[{"foodId":%d,"quantity":500}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(put("/api/recipes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bolo","yieldGrams":600,"servings":6,
                                 "ingredients":[{"foodId":%d,"quantity":500}]}"""
                                .formatted(a)))
                .andExpect(status().isOk());

        JsonNode detail = getJson(token, "/api/foods/" + id);
        var descriptions = detail.get("measures").findValuesAsText("description");
        assertThat(descriptions).containsOnlyOnce("porção");
        assertThat(descriptions).containsOnlyOnce("receita inteira");
    }

    // ----------------------------------------------- integration into the catalog

    @Test
    @DisplayName("a receita aparece na busca de alimentos, com a fonte que a identifica")
    void recipeEntersNoCatalog() throws Exception {
        long a = food(token, "Base", 200, 5, 30);
        create(token, """
                {"name":"Panqueca de aveia","yieldGrams":300,
                 "ingredients":[{"foodId":%d,"quantity":300}]}""".formatted(a), 201);

        JsonNode search = mvc.perform(get("/api/foods")
                        .param("term", "panqueca").param("source", "RECIPE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .transform(this::read);

        assertThat(search.get("content")).isNotEmpty();
        assertThat(search.get("content").get(0).get("description").asText())
                .isEqualTo("Panqueca de aveia");
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("uma receita pode ser ingrediente de outra")
    void recipeRecipeInside() throws Exception {
        long a = food(token, "Base", 400, 10, 50);
        long refogado = create(token, """
                {"name":"Refogado","yieldGrams":100,
                 "ingredients":[{"foodId":%d,"quantity":100}]}""".formatted(a), 201)
                .get("id").asLong();

        JsonNode crooked = create(token, """
                {"name":"Torta","yieldGrams":200,
                 "ingredients":[{"foodId":%d,"quantity":100},
                                 {"foodId":%d,"quantity":100}]}"""
                .formatted(refogado, a), 201);

        // 400 kcal from the sofrito + 400 kcal from the base, in 200 g = 400 per 100 g.
        assertThat(crooked.get("compositionPor100g").get("energyKcal").decimalValue())
                .isEqualByComparingTo("400.000");
    }

    @Test
    @DisplayName("uma receita não pode ser ingrediente dela mesma")
    void rejectsCycleDirect() throws Exception {
        long a = food(token, "Base", 200, 5, 30);
        long id = create(token, """
                {"name":"Sopa","yieldGrams":300,
                 "ingredients":[{"foodId":%d,"quantity":300}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(put("/api/recipes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sopa","yieldGrams":300,
                                 "ingredients":[{"foodId":%d,"quantity":100}]}"""
                                .formatted(id)))
                .andExpect(status().isUnprocessableEntity());
    }

    // --------------------------------------------------------------- validation

    @Test
    @DisplayName("receita sem ingrediente é recusada")
    void rejectsRecipeWithoutIngredient() throws Exception {
        create(token, """
                {"name":"Vazia","ingredients":[]}""", 400);
    }

    @Test
    @DisplayName("ingrediente de outro consultório não é encontrado")
    void notUsesOtherPracticeFood() throws Exception {
        long forOther = food(tokenB, "Segredo do outro", 100, 1, 1);

        create(token, """
                {"name":"Tentativa","yieldGrams":100,
                 "ingredients":[{"foodId":%d,"quantity":100}]}""".formatted(forOther), 404);
    }

    @Test
    @DisplayName("um consultório não abre a receita de outro")
    void isolatesRecipesBetweenAccounts() throws Exception {
        long a = food(token, "Base", 200, 5, 30);
        long id = create(token, """
                {"name":"Minha receita","yieldGrams":100,
                 "ingredients":[{"foodId":%d,"quantity":100}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(get("/api/recipes/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        assertThat(getJson(tokenB, "/api/recipes").get("content")).isEmpty();
    }

    @Test
    @DisplayName("remover inativa: o plano que a prescreveu continua de pé")
    void inactiveRemove() throws Exception {
        long a = food(token, "Base", 200, 5, 30);
        long id = create(token, """
                {"name":"Descartavel","yieldGrams":100,
                 "ingredients":[{"foodId":%d,"quantity":100}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(delete("/api/recipes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(getJson(token, "/api/recipes").get("content")).isEmpty();
    }
}
