package br.com.nutriplan.food;

import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.repository.FoodRepository;
import br.com.nutriplan.food.repository.HouseholdMeasureRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guarantees about the composition of the food base and about search relevance.
 *
 * The ranking is the difference between a usable base and 24 thousand useless
 * records: without it, "banana" returned "&Joy Frutas Banana + Cacau" before
 * the fruit, because the processed products are 36 times more numerous than
 * TACO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoodsBaseTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FoodRepository foodRepository;
    @Autowired HouseholdMeasureRepository householdMeasureRepository;

    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Base",
                                "email", "base" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    private JsonNode find(String term) throws Exception {
        String body = mvc.perform(get("/api/foods")
                        .param("term", term).param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // ----------------------------------------------- composition of the base

    @Test
    @DisplayName("as tres fontes sao carregadas no boot")
    void loadsThreeSources() {
        assertThat(foodRepository.countBySource(DataSource.TACO)).isEqualTo(597);
        assertThat(foodRepository.countBySource(DataSource.IBGE)).isEqualTo(1971);
        assertThat(foodRepository.countBySource(DataSource.OPEN_FOOD_FACTS)).isEqualTo(21377);
    }

    @Test
    @DisplayName("as medidas do IBGE vem da pesquisa, nao de estimativa")
    void loadsIbgeMeasures() {
        // 11,801 portions recorded in the field by the POF interviewer, with
        // the utensil the family actually used.
        assertThat(householdMeasureRepository.countNoCatalogBySource(DataSource.IBGE))
                .isEqualTo(11801);
        assertThat(householdMeasureRepository.countWithoutNoneServing(DataSource.IBGE))
                .as("alimentos do IBGE sem nenhuma porcao")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("o IBGE traz nutrientes que a TACO nao determina")
    void ibgeBringsNutrientsNovos() throws Exception {
        JsonNode content = find("feijoada").get("content");
        assertThat(content).isNotEmpty();

        long id = content.get(0).get("id").asLong();
        String body = mvc.perform(get("/api/foods/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode composition = json.readTree(body).get("composition");
        // B12, folate and vitamin D exist in no row of TACO.
        assertThat(composition.has("vitaminB12Mcg")).isTrue();
        assertThat(composition.has("folateMcg")).isTrue();
    }

    // ------------------------------------------------------ source cascade

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            // A basic food: TACO answers.
            "arroz,        TACO",
            "banana,       TACO",
            "leite,        TACO",
            "frango,       TACO",
            "feijao,       TACO",
            // A preparation TACO does not have: IBGE answers.
            "feijoada,     IBGE",
            "carne suina,  IBGE",
            // It only exists as a manufacturer's product.
            "nescau,       OPEN_FOOD_FACTS",
    })
    @DisplayName("a busca cai de fonte em fonte ate encontrar")
    void sourcesCascade(String term, String sourceExpected) throws Exception {
        JsonNode content = find(term).get("content");
        assertThat(content).as("busca por '%s'", term).isNotEmpty();

        assertThat(content.get(0).get("source").asText())
                .as("primeiro resultado de '%s' foi '%s'",
                        term, content.get(0).get("description").asText())
                .isEqualTo(sourceExpected);
    }

    @Test
    @DisplayName("o termo precisa ser palavra inteira, nao prefixo de outra")
    void ranksWordWholePrefixBefore() throws Exception {
        // "Arrozina" is a children's cereal whose name merely starts with the five
        // letters of "arroz". Before this rule, it beat plain rice by having
        // the shorter name.
        JsonNode content = find("arroz").get("content");

        String first = content.get(0).get("description").asText().toLowerCase();
        assertThat(first)
                .as("primeiro resultado de 'arroz'")
                .doesNotStartWith("arrozina");

        // The term opens the name and ends there — it does not run into another word.
        assertThat(first).matches("^arroz([\\s,\\-].*)?$");
    }

    @Test
    @DisplayName("sem termo, a listagem volta a ser alfabetica")
    void withoutTermSortsAlphabetically() throws Exception {
        String body = mvc.perform(get("/api/foods")
                        .param("size", "5").param("source", "TACO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = json.readTree(body).get("content");
        assertThat(content).isNotEmpty();

        String previous = null;
        for (JsonNode item : content) {
            String current = item.get("description").asText();
            if (previous != null) {
                assertThat(current).isGreaterThanOrEqualTo(previous);
            }
            previous = current;
        }
    }
}
