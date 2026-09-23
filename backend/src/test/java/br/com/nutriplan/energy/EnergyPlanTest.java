package br.com.nutriplan.energy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("cálculo energético")
class EnergyPlanTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        tokenA = registerNutri("energiaA");
        tokenB = registerNutri("energiaB");
        // Nasceu em 10/09/1996: completa 30 anos na data usada nos cálculos.
        patient = createPatient(tokenA, "Daniel Lacerda", "MALE", "1996-09-10");
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

    private long createPatient(String token, String name, String sex, String birth)
            throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", name, "sex", sex, "dateBirth", birth))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode postJson(String token, String url, String body, int expected) throws Exception {
        String answer = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isBlank() ? null : json.readTree(answer);
    }

    private JsonNode getJson(String token, String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("a média sai sobre o gasto do dia de cada equação escolhida")
    void averagesTheDailyTotals() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":80,"heightCm":180,
                 "activityLevel":"INACTIVE",
                 "equations":["HARRIS_BENEDICT_1984","EER_2023"]}""".formatted(patient), 201);

        JsonNode equations = plan.get("equations");
        assertThat(equations).hasSize(2);

        // Harris-Benedict é basal: 1853,63 de basal, vezes 1,2 do sedentário.
        assertThat(equations.get(0).get("basalKcal").decimalValue().doubleValue())
                .isEqualTo(1853.63);
        assertThat(equations.get(0).get("totalKcal").decimalValue().doubleValue())
                .isEqualTo(2224.36);

        // EER 2023 já responde o dia inteiro, então não tem basal. O campo pode
        // vir ausente ou nulo conforme a serialização; as duas dizem o mesmo.
        assertThat(equations.get(1).hasNonNull("basalKcal")).isFalse();
        assertThat(equations.get(1).get("totalKcal").decimalValue().doubleValue())
                .isEqualTo(2726.17);

        // A média é dos dois totais, nunca de um basal com um total.
        assertThat(plan.get("averageKcal").decimalValue().doubleValue()).isEqualTo(2475.27);
        assertThat(plan.get("prescribedKcal").decimalValue().doubleValue()).isEqualTo(2475.27);
    }

    @Test
    @DisplayName("a programação de peso confere com o exemplo do próprio cliente")
    void weightProgrammingMatchesTheClientsExample() throws Exception {
        // O documento diz: "para 10 kg em 90 dias desse meu paciente, ele iria
        // retirar 855 calorias da alimentação". É a constante de 7 700 kcal por
        // quilo de tecido adiposo, e serve de conferência independente.
        JsonNode plan = postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":90,"heightCm":180,
                 "activityLevel":"INACTIVE","equations":["EER_2023"],
                 "targetWeightKg":80,"targetDate":"2026-12-09"}""".formatted(patient), 201);

        assertThat(plan.get("adjustmentKcal").decimalValue().doubleValue()).isEqualTo(-855.56);

        double average = plan.get("averageKcal").decimalValue().doubleValue();
        assertThat(plan.get("prescribedKcal").decimalValue().doubleValue())
                .isEqualTo(Math.round((average - 855.56) * 100) / 100.0);
    }

    @Test
    @DisplayName("a faixa de peso saudável vem junto, e o limite inferior é 18,5")
    void healthyWeightComesAlong() throws Exception {
        // O cliente registra a dúvida no documento — "ou é 18,5 ou é 19, não
        // tenho certeza" — e conta que fecha o cardápio para ir consultar isso.
        JsonNode plan = postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":80,"heightCm":180,
                 "activityLevel":"INACTIVE","equations":["EER_2023"]}""".formatted(patient), 201);

        JsonNode healthy = plan.get("healthyWeight");
        assertThat(healthy.get("bmiMinimum").decimalValue().doubleValue()).isEqualTo(18.5);
        assertThat(healthy.get("minimumKg").decimalValue().doubleValue()).isEqualTo(59.9);
        assertThat(healthy.get("maximumKg").decimalValue().doubleValue()).isEqualTo(81.0);
    }

    @Test
    @DisplayName("o fator de injúria e as calorias por MET entram no prescrito")
    void injuryAndMetEnterThePrescription() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":80,"heightCm":180,
                 "activityLevel":"INACTIVE","equations":["EER_2023"],
                 "injuryFactor":1.2,"metKcal":300}""".formatted(patient), 201);

        // 2726,17 × 1,2 + 300
        assertThat(plan.get("prescribedKcal").decimalValue().doubleValue())
                .isEqualTo(Math.round((2726.17 * 1.2 + 300) * 100) / 100.0);
    }

    @Test
    @DisplayName("recusa equação que não foi publicada para a idade do paciente")
    void refusesAnEquationOutsideItsAgeRange() throws Exception {
        long baby = createPatient(tokenA, "Bebê Teste", "MALE", "2025-01-10");
        postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":11,"heightCm":78,
                 "activityLevel":"INACTIVE","equations":["EER_2023"]}""".formatted(baby), 422);

        // A FAO/WHO tem faixa para menores de 3 anos, então aceita.
        postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":11,"heightCm":78,
                 "activityLevel":"INACTIVE","equations":["FAO_WHO_2004"]}""".formatted(baby), 201);
    }

    @Test
    @DisplayName("sem sexo ou sem nascimento o cálculo explica o que falta")
    void explainsWhatIsMissing() throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sem dados"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long incomplete = json.readTree(body).get("id").asLong();

        String answer = mvc.perform(post("/api/energy-plans")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":%d,"date":"2026-09-10","weightKg":80,"heightCm":180,
                                 "activityLevel":"INACTIVE","equations":["EER_2023"]}"""
                                .formatted(incomplete)))
                .andExpect(status().is(422))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(answer).get("message").asText())
                .contains("sexo biológico");
    }

    @Test
    @DisplayName("o cálculo de um consultório não existe para o outro")
    void oneAccountDoesNotSeeAnother() throws Exception {
        JsonNode plan = postJson(tokenA, "/api/energy-plans", """
                {"patientId":%d,"date":"2026-09-10","weightKg":80,"heightCm":180,
                 "activityLevel":"INACTIVE","equations":["EER_2023"]}""".formatted(patient), 201);

        mvc.perform(get("/api/energy-plans/" + plan.get("id").asLong())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("as opções da tela saem dos enums do domínio")
    void optionsComeFromTheDomain() throws Exception {
        JsonNode options = getJson(tokenA, "/api/energy-plans/options");
        assertThat(options.get("equations")).hasSize(5);
        assertThat(options.get("activityLevels")).hasSize(4);

        JsonNode eer = null;
        for (JsonNode option : options.get("equations")) {
            if ("EER_2023".equals(option.get("equation").asText())) eer = option;
        }
        assertThat(eer).isNotNull();
        assertThat(eer.get("total").asBoolean()).isTrue();
    }
}
