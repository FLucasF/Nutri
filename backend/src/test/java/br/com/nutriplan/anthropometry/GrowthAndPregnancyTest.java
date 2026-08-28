package br.com.nutriplan.anthropometry;

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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assessment of children, adolescents and pregnant women (RF69–RF69b).
 *
 * Until here the system refused to classify anyone under 20 — correct, because
 * the adult BMI band does not apply to someone still growing, and it solved the
 * problem halfway.
 *
 * The numbers checked are those of the curves published by the WHO: a value
 * equal to the median has a z-score of zero, and that is what proves the LMS
 * parameters were loaded and applied at the right age.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthAndPregnancyTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Crescimento",
                                "email", "cresc" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    /** A patient with an exact age in months as of today. */
    private long patient(String name, String sex, int months) throws Exception {
        var data = new java.util.HashMap<String, Object>();
        data.put("name", name);
        if (sex != null) {
            data.put("sex", sex);
        }
        data.put("dateBirth", LocalDate.now().minusMonths(months).minusDays(1).toString());
        return json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(data)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long patientWithoutBirth(String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode assess(long patient, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/patients/" + patient + "/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode indicator(JsonNode assessment, String name) {
        JsonNode growth = assessment.get("childGrowth");
        assertThat(growth).isNotNull();
        assertThat(growth.get("value"))
                .as("crescimento indisponivel: %s", growth.get("unavailableBecause"))
                .isNotNull();
        for (JsonNode i : growth.get("value").get("indicators")) {
            if (i.get("indicator").asText().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("indicador ausente: " + name);
    }

    // -------------------------------------------------------------- z-score

    @Test
    @DisplayName("valor igual à mediana da WHO tem escore-z zero")
    void medianHasScoreZero() throws Exception {
        // A 120-month-old boy: the median BMI is 16.4433 kg/m².
        // At 1.40 m, the weight producing that BMI is 16.4433 × 1.96 = 32.229 kg.
        long pedro = patient("Pedro", "MALE", 120);
        JsonNode a = assess(pedro, """
                {"date":"%s","weightKg":32.229,"heightCm":140}""".formatted(LocalDate.now()));

        JsonNode bmi = indicator(a, "BMI_TO_AGE");
        assertThat(bmi.get("scoreZ").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(bmi.get("classification").asText()).isEqualTo("NORMAL");
        assertThat(bmi.get("reference").asText()).contains("2007");
    }

    @Test
    @DisplayName("a estatura mediana da WHO também dá escore zero")
    void heightMedian() throws Exception {
        // A 60-month-old girl: the median height is 109.4189 cm.
        long ana = patient("Ana", "FEMALE", 60);
        JsonNode a = assess(ana, """
                {"date":"%s","weightKg":18,"heightCm":109.4189}""".formatted(LocalDate.now()));

        JsonNode height = indicator(a, "HEIGHT_TO_AGE");
        assertThat(height.get("scoreZ").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(height.get("classification").asText()).isEqualTo("HEIGHT_ADEQUATE");
        // At 60 months the 2006 standard holds, and not the 2007 reference.
        assertThat(height.get("reference").asText()).contains("2006");
    }

    @Test
    @DisplayName("baixa estatura é reconhecida")
    void lowHeight() throws Exception {
        long ana = patient("Ana", "FEMALE", 60);
        // Well below the median of 109.4 cm.
        JsonNode a = assess(ana, """
                {"date":"%s","weightKg":14,"heightCm":97}""".formatted(LocalDate.now()));

        JsonNode height = indicator(a, "HEIGHT_TO_AGE");
        assertThat(height.get("scoreZ").decimalValue()).isLessThan(new java.math.BigDecimal("-2"));
        assertThat(height.get("classification").asText()).contains("LOW_HEIGHT");
        assertThat(height.get("requiresAttention").asBoolean()).isTrue();
    }

    // ------------------------------------------------------- bands by age

    @ParameterizedTest(name = "escore-z {1} aos {0} meses -> {2}")
    @CsvSource({
            // Up to five years, above +1 is risk of overweight; after that, it is
            // already overweight. Classifying an adolescent by the child band
            // would underestimate the picture by a whole grade.
            "48,  1.5, OVERWEIGHT_RISK",
            "48,  2.5, OVERWEIGHT",
            "48,  3.5, OBESITY",
            "120, 1.5, OVERWEIGHT",
            "120, 2.5, OBESITY",
            "120, 3.5, OBESITY_SEVERE",
    })
    @DisplayName("a faixa de corte do BMI muda aos cinco anos")
    void rangeChangesAtFiveYears(int months, double scoreTarget, String expected) throws Exception {
        long child = patient("Crianca " + months + "-" + scoreTarget, "MALE", months);

        // Height fixed; the weight is chosen to hit the target z-score.
        double heightM = 1.10;
        double bmiTarget = bmiToScore(months, scoreTarget);
        double weight = bmiTarget * heightM * heightM;

        JsonNode a = assess(child, """
                {"date":"%s","weightKg":%.3f,"heightCm":%.1f}"""
                .formatted(LocalDate.now(), weight, heightM * 100));

        assertThat(indicator(a, "BMI_TO_AGE").get("classification").asText())
                .isEqualTo(expected);
    }

    /**
     * BMI that produces the requested z-score, by the inverted LMS formula.
     *
     * The parameters come from the WHO table for boys, at the two ages used
     * above. They are written out here on purpose: if the test read the same
     * table as the code, the two would be wrong together.
     */
    private double bmiToScore(int months, double z) {
        double l;
        double m;
        double s;
        if (months == 48) {           // WHO 2006, boys, 48 months
            l = -0.7387;
            m = 15.7133;
            s = 0.07914;
        } else {                     // WHO 2007, boys, 120 months
            l = -1.0630;
            m = 16.4433;
            s = 0.12988;
        }
        if (z > 3) {
            double sd3 = m * Math.pow(1 + l * s * 3, 1 / l);
            double sd2 = m * Math.pow(1 + l * s * 2, 1 / l);
            return sd3 + (z - 3) * (sd3 - sd2);
        }
        return m * Math.pow(1 + l * s * z, 1 / l);
    }

    // ------------------------------------------------------------ recusas

    @Test
    @DisplayName("acima de 19 anos não há curva, e o motivo é dito")
    void adultNotHasChart() throws Exception {
        long adult = patient("Adulto", "MALE", 300);
        JsonNode a = assess(adult, """
                {"date":"%s","weightKg":70,"heightCm":175}""".formatted(LocalDate.now()));

        JsonNode growth = a.get("childGrowth");
        assertThat(growth.has("value") && !growth.get("value").isNull()).isFalse();
        assertThat(growth.get("unavailableBecause").asText()).contains("19 anos");
    }

    @Test
    @DisplayName("sem sexo informado não há curva: elas são específicas por sexo")
    void withoutSexNotClassifies() throws Exception {
        long anonymous = patient("Sem sexo", null, 120);
        JsonNode a = assess(anonymous, """
                {"date":"%s","weightKg":32,"heightCm":140}""".formatted(LocalDate.now()));

        assertThat(a.get("childGrowth").get("unavailableBecause").asText())
                .contains("sex");
    }

    @Test
    @DisplayName("sem data de nascimento não há idade, e sem idade não há curva")
    void withoutBirthNotClassifies() throws Exception {
        long anonymous = patientWithoutBirth("Sem nascimento");
        JsonNode a = assess(anonymous, """
                {"date":"%s","weightKg":32,"heightCm":140}""".formatted(LocalDate.now()));

        assertThat(a.get("childGrowth").get("unavailableBecause").asText())
                .contains("nascimento");
    }

    // ---------------------------------------------------------- pregnancy

    @Test
    @DisplayName("ganho dentro do esperado para a semana")
    void gainAdequate() throws Exception {
        long marina = patient("Marina", "FEMALE", 34 * 12);
        // Pre-pregnancy BMI 22.5 (normal weight): 61.2 kg at 1.65 m.
        // At week 20: 0.5 to 2.0 kg from the 1st trimester + 7 weeks × 0.35–0.50.
        // Expected band: 2.95 to 5.50 kg. A gain of 4 kg falls inside.
        JsonNode a = assess(marina, """
                {"date":"%s","weightKg":65.2,"heightCm":165,
                 "gestationalWeek":20,"weightGestationalPreKg":61.2}"""
                .formatted(LocalDate.now()));

        JsonNode g = a.get("pregnancy").get("value");
        assertThat(g.get("range").asText()).isEqualTo("NORMAL");
        assertThat(g.get("gainAteNow").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(g.get("expectedMin").decimalValue()).isEqualByComparingTo("2.95");
        assertThat(g.get("expectedMax").decimalValue()).isEqualByComparingTo("5.50");
        assertThat(g.get("status").asText()).isEqualTo("ADEQUATE");
    }

    @Test
    @DisplayName("ganho acima do esperado é sinalizado")
    void gainAbove() throws Exception {
        long marina = patient("Marina", "FEMALE", 34 * 12);
        JsonNode a = assess(marina, """
                {"date":"%s","weightKg":74.2,"heightCm":165,
                 "gestationalWeek":20,"weightGestationalPreKg":61.2}"""
                .formatted(LocalDate.now()));

        assertThat(a.get("pregnancy").get("value").get("status").asText()).isEqualTo("ABOVE");
    }

    @Test
    @DisplayName("a faixa vem do BMI pré-gestacional, e não do atual")
    void rangePreviousBmiComes() throws Exception {
        long marina = patient("Marina", "FEMALE", 34 * 12);
        // Pre-pregnancy 82 kg at 1.65 m: BMI 30.1, obesity band — a recommended
        // gain of 5 to 9 kg, and not the 11.5 to 16 of a normal weight.
        JsonNode a = assess(marina, """
                {"date":"%s","weightKg":86,"heightCm":165,
                 "gestationalWeek":30,"weightGestationalPreKg":82}"""
                .formatted(LocalDate.now()));

        JsonNode g = a.get("pregnancy").get("value");
        assertThat(g.get("range").asText()).isEqualTo("OBESITY");
        assertThat(g.get("totalGainRecommendedMin").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(g.get("totalGainRecommendedMax").decimalValue()).isEqualByComparingTo("9.0");
    }

    @Test
    @DisplayName("sem o peso pré-gestacional não classifica, e diz por quê")
    void withoutWeightGestationalPre() throws Exception {
        long marina = patient("Marina", "FEMALE", 34 * 12);
        JsonNode a = assess(marina, """
                {"date":"%s","weightKg":65,"heightCm":165,"gestationalWeek":20}"""
                .formatted(LocalDate.now()));

        JsonNode g = a.get("pregnancy");
        assertThat(g.has("value") && !g.get("value").isNull()).isFalse();
        assertThat(g.get("unavailableBecause").asText()).contains("pre-gestacional");
    }

    @Test
    @DisplayName("avaliação sem semana gestacional não traz o bloco")
    void withoutPregnancyNotBringsBlock() throws Exception {
        long marina = patient("Marina", "FEMALE", 34 * 12);
        JsonNode a = assess(marina, """
                {"date":"%s","weightKg":65,"heightCm":165}""".formatted(LocalDate.now()));

        assertThat(a.has("pregnancy") && !a.get("pregnancy").isNull()).isFalse();
    }

    @Test
    @DisplayName("semana gestacional fora de 1 a 42 é recusada")
    void invalidatesWeek() throws Exception {
        long marina = patient("Marina", "FEMALE", 34 * 12);
        mvc.perform(post("/api/patients/" + marina + "/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","weightKg":65,"heightCm":165,"gestationalWeek":45}"""
                                .formatted(LocalDate.now())))
                .andExpect(status().isBadRequest());
    }
}
