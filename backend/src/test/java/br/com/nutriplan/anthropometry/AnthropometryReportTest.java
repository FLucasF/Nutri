package br.com.nutriplan.anthropometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * O relatório de evolução antropométrica.
 *
 * "Adicionar funcionalidade de gerar relatórios com gráficos na antropometria."
 *
 * O teste não tenta ler o desenho do gráfico. O que ele fixa é o contrato que
 * quebra em silêncio: que a folha sai, que ela é um PDF de verdade, que uma
 * avaliação só não produz evolução, e que o relatório de um consultório não
 * existe para o outro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("relatório de evolução antropométrica")
class AnthropometryReportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String other;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        token = register("relA");
        other = register("relB");
        patient = createPatient(token);
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

    private long createPatient(String owner) throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Marina Duarte",
                                "dateBirth", "1991-03-14",
                                "sex", "FEMALE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void assess(String date, double weight, double waist) throws Exception {
        mvc.perform(post("/api/patients/" + patient + "/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","weightKg":%s,"heightCm":164,
                                 "skinfolds":{"TRICEPS":26,"SUBSCAPULAR":22},
                                 "circumferences":[
                                   {"site":"WAIST","side":"SINGLE","valueCm":%s}]}"""
                                .formatted(date, weight, waist)))
                .andExpect(status().isCreated());
    }

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("com duas avaliações a folha sai, e sai como PDF")
    void withTwoAssessmentsTheSheetComesOut() throws Exception {
        assess("2026-03-10", 88.4, 94);
        assess("2026-09-15", 80.3, 84.5);

        byte[] content = mvc.perform(
                        get("/api/patients/" + patient + "/anthropometry-report")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).isNotEmpty();
        // O cabeçalho do formato, e não só "veio alguma coisa": um corpo de
        // erro em texto também passaria por "não vazio".
        assertThat(new String(content, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("uma avaliação só não é evolução, e a recusa explica isso")
    void oneAssessmentIsNotProgress() throws Exception {
        assess("2026-03-10", 88.4, 94);

        String body = mvc.perform(
                        get("/api/patients/" + patient + "/anthropometry-report")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().is(422))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("duas avaliações");
    }

    @Test
    @DisplayName("corrigir o peso não esbarra na circunferência que já estava lá")
    void correctingWeightDoesNotCollideWithTheExistingCircumference() throws Exception {
        // Havia um UNIQUE (assessment_id, site, side), e a atualização apagava
        // tudo para reescrever. O Hibernate manda os INSERT antes dos DELETE,
        // então a cintura reenviada colidia com a que ainda estava lá — e
        // corrigir um dígito no peso devolvia 409.
        assess("2026-03-10", 88.4, 94);

        String lista = mvc.perform(get("/api/patients/" + patient + "/assessments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(lista).get(0).get("id").asLong();

        String corrigida = mvc.perform(put("/api/assessments/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-03-10","weightKg":84.8,"heightCm":164,
                                 "circumferences":[
                                   {"site":"WAIST","side":"SINGLE","valueCm":94},
                                   {"site":"HIP","side":"SINGLE","valueCm":110}]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var corpo = json.readTree(corrigida);
        assertThat(corpo.get("weightKg").decimalValue()).isEqualByComparingTo("84.80");
        // A cintura continua uma só, e a nova entrou junto.
        assertThat(corpo.get("circumferences")).hasSize(2);
    }

    @Test
    @DisplayName("a circunferência que sai do pedido é removida")
    void theCircumferenceLeftOutIsRemoved() throws Exception {
        assess("2026-03-10", 88.4, 94);

        String lista = mvc.perform(get("/api/patients/" + patient + "/assessments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(lista).get(0).get("id").asLong();

        String corrigida = mvc.perform(put("/api/assessments/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-03-10","weightKg":88.4,"heightCm":164,
                                 "circumferences":[]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(corrigida).get("circumferences")).isEmpty();
    }

    @Test
    @DisplayName("o paciente de um consultório não tem relatório no outro")
    void reportDoesNotCrossAccounts() throws Exception {
        assess("2026-03-10", 88.4, 94);
        assess("2026-09-15", 80.3, 84.5);

        mvc.perform(get("/api/patients/" + patient + "/anthropometry-report")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }
}
