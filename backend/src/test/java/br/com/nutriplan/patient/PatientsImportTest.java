package br.com.nutriplan.patient;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importing patients from a spreadsheet (RF14).
 *
 * What these tests protect is the behaviour in the face of a real spreadsheet:
 * it always has one crooked row, one date in a different format and one
 * repeated CPF. Aborting the whole file because of that would force the user to
 * hunt for the defect with no clue at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientsImportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Importacao",
                                "email", "import" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    private JsonNode importAll(String csv) throws Exception {
        var file = new MockMultipartFile(
                "file", "patients.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/patients/import").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode list() throws Exception {
        String body = mvc.perform(get("/api/patients").param("size", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("content");
    }

    @Test
    @DisplayName("importa a planilha e preenche os campos reconhecidos")
    void importsPlanilha() throws Exception {
        JsonNode r = importAll("""
                nome,email,telefone,nascimento,sexo,objetivo
                Marina Duarte,marina@exemplo.com,11999990000,12/03/1992,F,Reeducação alimentar
                Carlos Menezes,carlos@exemplo.com,11988887777,1986-07-05,Masculino,Ganho de massa
                """);

        assertThat(r.get("imported").asInt()).isEqualTo(2);
        assertThat(r.get("ignored").asInt()).isZero();

        JsonNode list = list();
        assertThat(list).hasSize(2);
        JsonNode marina = list.get(1).get("name").asText().startsWith("Marina")
                ? list.get(1) : list.get(0);
        assertThat(marina.get("email").asText()).isEqualTo("marina@exemplo.com");
        // Born in 1992: the age is derived, and proves the date got in.
        assertThat(marina.get("age").isNull()).isFalse();
    }

    @Test
    @DisplayName("aceita os formatos de data usados em planilha brasileira")
    void acceptsDateFormats() throws Exception {
        JsonNode r = importAll("""
                nome,nascimento
                Um,12/03/1992
                Dois,1992-03-12
                Tres,5/7/1986
                Quatro,12-03-1992
                """);

        assertThat(r.get("imported").asInt()).isEqualTo(4);
        for (JsonNode p : list()) {
            assertThat(p.get("age").isNull())
                    .as("idade de %s", p.get("name").asText())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("linha sem nome é ignorada, e as outras entram")
    void rowWithoutNameNotDropsFile() throws Exception {
        JsonNode r = importAll("""
                nome,email
                Marina Duarte,marina@exemplo.com
                ,orfao@exemplo.com
                Carlos Menezes,carlos@exemplo.com
                """);

        assertThat(r.get("imported").asInt()).isEqualTo(2);
        assertThat(r.get("ignored").asInt()).isEqualTo(1);
        assertThat(r.get("warnings").toString()).contains("Linha 3");
    }

    @Test
    @DisplayName("dado ilegivel nao descarta o paciente")
    void datumUnreadableNotDiscardsPatient() throws Exception {
        JsonNode r = importAll("""
                nome,email,nascimento,sexo
                Marina Duarte,nao tenho,ontem,indefinido
                """);

        // The name is what makes the registration useful; the rest is corrected on the record.
        assertThat(r.get("imported").asInt()).isEqualTo(1);
        JsonNode p = list().get(0);
        assertThat(p.get("name").asText()).isEqualTo("Marina Duarte");
        // A field with no value comes out omitted from the response, and not as null or empty.
        assertThat(missing(p, "email")).isTrue();
        assertThat(missing(p, "age")).isTrue();
        assertThat(r.get("warnings").toString()).contains("data de nascimento");
    }

    private boolean missing(JsonNode no, String field) {
        return !no.has(field) || no.get(field).isNull();
    }

    @Test
    @DisplayName("nao importa paciente com CPF ja cadastrado")
    void notDuplicatesByCpf() throws Exception {
        mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Marina Duarte", "cpf", "12345678901"))))
                .andExpect(status().isCreated());

        JsonNode r = importAll("""
                nome,cpf
                Marina Duarte,123.456.789-01
                Carlos Menezes,98765432100
                """);

        // The CPF from the spreadsheet arrives punctuated; the comparison is by digit.
        assertThat(r.get("imported").asInt()).isEqualTo(1);
        assertThat(r.get("ignored").asInt()).isEqualTo(1);
        assertThat(r.get("warnings").toString()).contains("CPF");
        assertThat(list()).hasSize(2);
    }

    @Test
    @DisplayName("o limite do plano vale na importacao, e ela diz onde parou")
    void respectsPlanLimit() throws Exception {
        // The trial plan allows 5 active patients.
        JsonNode r = importAll("""
                nome
                Um
                Dois
                Tres
                Quatro
                Cinco
                Seis
                Sete
                """);

        assertThat(r.get("imported").asInt()).isEqualTo(5);
        assertThat(r.get("warnings").toString()).contains("Importação interrompida");
        assertThat(list()).hasSize(5);
    }

    @Test
    @DisplayName("recusa arquivo vazio")
    void rejectsFileEmpty() throws Exception {
        var empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        mvc.perform(multipart("/api/patients/import").file(empty)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("avisa quando a coluna de nome nao foi encontrada")
    void warnsNameMissingColumn() throws Exception {
        JsonNode r = importAll("""
                email,telefone
                marina@exemplo.com,11999990000
                """);

        assertThat(r.get("imported").asInt()).isZero();
        assertThat(r.get("warnings").toString()).contains("nome do paciente");
    }
}
