package br.com.nutriplan.shared;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the API answers when the request is wrong.
 *
 * These three cases all fell into the generic handler and became 500 "Ocorreu
 * um erro inesperado". An internal error is a statement about the server; a
 * malformed request is a statement about the request, and only the second helps
 * the caller fix what they did.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void prepare() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Erros",
                                "email", "errors" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("rota que não existe responde 404, e não 500")
    void routeNonexistentResponde404() throws Exception {
        String body = mvc.perform(get("/api/not-exists-esta-route")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("message").asText())
                .contains("não existe");
    }

    @Test
    @DisplayName("método HTTP errado responde 405 dizendo o que a rota aceita")
    void methodWrongResponde405() throws Exception {
        // A GET on a route that only accepts POST and DELETE. It used to answer
        // 500, and the log received a whole stack for a caller's mistake.
        String body = mvc.perform(get("/api/foods/1/measures")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getContentAsString();

        // 405 and not 404: the path exists, and saying "not found" would send
        // the caller to check exactly the part that is right.
        assertThat(json.readTree(body).get("message").asText())
                .contains("POST");
    }

    @Test
    @DisplayName("valor fora do enum diz qual é o campo e o que ele aceita")
    void invalidEnumDizFieldEValues() throws Exception {
        String body = mvc.perform(post("/api/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plano","method":"QUANTITATIVE","meals":[]}"""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        var no = json.readTree(body);
        // The message solves it on its own: it names the field and lists what serves.
        assertThat(no.get("message").asText())
                .contains("method")
                .contains("FOODS")
                .contains("QUALITATIVE")
                .contains("SUBSTITUTIONS");
        // And it comes per field too, so the screen can stick it to the right field.
        assertThat(no.get("fields").get(0).get("field").asText()).isEqualTo("method");
    }

    @Test
    @DisplayName("o caminho do campo inválido inclui a posição na lista")
    void fieldIncludesIndexPath() throws Exception {
        String body = mvc.perform(post("/api/prescriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plano","method":"FOODS","meals":[
                                  {"name":"Almoço","items":[{"quantity":"very"}]}]}"""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Without the index, "quantity" does not say which item of which meal.
        assertThat(json.readTree(body).get("fields").get(0).get("field").asText())
                .isEqualTo("meals[0].items[0].quantity");
    }

    @Test
    @DisplayName("identificador que não é número responde 400 nomeando o parâmetro")
    void typeWrongParameter() throws Exception {
        String body = mvc.perform(get("/api/patients/abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("message").asText()).contains("id");
    }

    @Test
    @DisplayName("JSON quebrado responde 400 dizendo que o conteúdo não pôde ser lido")
    void jsonBroken() throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("message").asText()).contains("não pôde ser lido");
    }
}
