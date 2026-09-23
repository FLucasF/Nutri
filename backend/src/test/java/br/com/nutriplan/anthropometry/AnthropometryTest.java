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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Implements the scenarios of section 5 of docs/04-cenarios-bdd.md.
 *
 * The numbers come from there and are not illustrative: a scenario that merely
 * asserts "it calculates correctly" would pass with any result.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnthropometryTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long marina;      // 34 years old, female
    private long pedro;       // 15 years old, male
    private long carlos;      // 40 years old, male
    private long withoutSignup; // no sex and no birth date

    @BeforeEach
    void prepare() throws Exception {
        tokenA = registerNutri("antroA");
        tokenB = registerNutri("antroB");

        marina = createPatient(tokenA, Map.of(
                "name", "Marina Duarte",
                "dateBirth", birthToAge(34),
                "sex", "FEMALE"));
        pedro = createPatient(tokenA, Map.of(
                "name", "Pedro Lima",
                "dateBirth", birthToAge(15),
                "sex", "MALE"));
        // An adult male exists apart from Pedro: the risk cutoffs are for
        // adults, and applying them to a 15-year-old boy would be testing a
        // use the clinic does not make.
        carlos = createPatient(tokenA, Map.of(
                "name", "Carlos Menezes",
                "dateBirth", birthToAge(40),
                "sex", "MALE"));
        withoutSignup = createPatient(tokenA, Map.of("name", "Sem Dados"));
    }

    // ------------------------------------------------------------------ apoio

    /** A birth date that produces exactly the requested age today. */
    private String birthToAge(int age) {
        return LocalDate.now().minusYears(age).minusDays(1).toString();
    }

    private String registerNutri(String prefix) throws Exception {
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

    private long createPatient(String token, Map<String, Object> data) throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(data)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode assess(String token, long patient, String body, int expected) throws Exception {
        String answer = mvc.perform(post("/api/patients/" + patient + "/assessments")
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

    private String weightEHeight(double weight, double height) {
        return """
               {"date":"%s","weightKg":%s,"heightCm":%s}"""
                .formatted(LocalDate.now(), weight, height);
    }

    /** The four skinfolds of the Faulkner protocol: they sum to 85 mm. */
    private static final String SKINFOLDS_FAULKNER = """
            "skinfolds":{"TRICEPS":20,"SUBSCAPULAR":18,"SUPRAILIAC":22,"ABDOMINAL":25}""";

    // ------------------------------------------------------- record and BMI

    @Test
    @DisplayName("registra avaliação com o mínimo e calcula o BMI")
    void recordsAssessmentComMinimum() throws Exception {
        JsonNode a = assess(tokenA, marina, weightEHeight(70, 170), 201);

        assertThat(a.get("date").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(a.get("bmi").decimalValue()).isEqualByComparingTo("24.22");
        assertThat(a.get("classificationBmi").get("value").asText()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("recusa avaliação com data futura")
    void rejectsAssessmentFuture() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170}""".formatted(LocalDate.now().plusDays(1));

        mvc.perform(post("/api/patients/" + marina + "/assessments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("date"));
    }

    @Test
    @DisplayName("aceita avaliação retroativa")
    void acceptsRetroactiveAssessment() throws Exception {
        String body = """
                {"date":"%s","weightKg":72,"heightCm":170}""".formatted(LocalDate.now().minusDays(30));
        JsonNode a = assess(tokenA, marina, body, 201);
        assertThat(a.get("date").asText()).isEqualTo(LocalDate.now().minusDays(30).toString());
    }

    @ParameterizedTest(name = "{0} kg -> BMI {1} -> {2}")
    @CsvSource({
            "50.0,  17.30, LOW_WEIGHT",
            "60.0,  20.76, NORMAL",
            "75.0,  25.95, OVERWEIGHT",
            "90.0,  31.14, OBESITY_I",
            "105.0, 36.33, OBESITY_II",
            "120.0, 41.52, OBESITY_III",
    })
    @DisplayName("classifica o BMI pelas faixas da WHO")
    void classifiesBmiByWhoRanges(double weight, String bmi, String classification) throws Exception {
        JsonNode a = assess(tokenA, marina, weightEHeight(weight, 170), 201);

        assertThat(a.get("bmi").decimalValue()).isEqualByComparingTo(bmi);
        assertThat(a.get("classificationBmi").get("value").asText()).isEqualTo(classification);
    }

    @Test
    @DisplayName("não classifica adolescente pela faixa adulta")
    void notClassifiesAdolescentByRangeAdult() throws Exception {
        JsonNode a = assess(tokenA, pedro, weightEHeight(55, 165), 201);

        // The BMI is calculated as usual...
        assertThat(a.get("bmi").decimalValue()).isEqualByComparingTo("20.20");
        // ...but the adult band is not applied, and the reason is stated.
        assertThat(a.get("classificationBmi").has("value")).isFalse();
        assertThat(a.get("classificationBmi").get("unavailableBecause").asText())
                .contains("percentil");
    }

    // ---------------------------------------------------- body composition

    @Test
    @DisplayName("estima gordura pelo protocolo de Faulkner")
    void estimatesFatByFaulkner() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,%s,"protocolComposition":"FAULKNER"}"""
                .formatted(LocalDate.now(), SKINFOLDS_FAULKNER);

        JsonNode composition = assess(tokenA, marina, body, 201).get("composition");

        // Faulkner: 85 mm x 0.153 + 5.783 = 18.788
        assertThat(composition.get("percentageFat").decimalValue()).isEqualByComparingTo("18.79");
        assertThat(composition.get("massFatKg").decimalValue()).isEqualByComparingTo("13.15");
        assertThat(composition.get("massLeanKg").decimalValue()).isEqualByComparingTo("56.85");
        assertThat(composition.get("protocol").asText()).isEqualTo("FAULKNER");
    }

    @Test
    @DisplayName("recusa estimativa com dobra faltando, nomeando quais")
    void rejectsEstimateComSkinfoldMissing() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,
                 "skinfolds":{"TRICEPS":20,"ABDOMINAL":25},
                 "protocolComposition":"FAULKNER"}""".formatted(LocalDate.now());

        String error = mvc.perform(post("/api/patients/" + marina + "/assessments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(error).contains("Subescapular").contains("Supra-ilíaca");
    }

    @Test
    @DisplayName("registra dobras sem estimar composição")
    void recordsSkinfoldsWithoutEstimate() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,%s}"""
                .formatted(LocalDate.now(), SKINFOLDS_FAULKNER);

        JsonNode a = assess(tokenA, marina, body, 201);

        assertThat(a.get("skinfolds").get("TRICEPS").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(a.has("composition")).isFalse();
    }

    @Test
    @DisplayName("protocolo que depende de idade exige nascimento cadastrado")
    void protocolAgeRequiresBirthDependent() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,
                 "skinfolds":{"TRICEPS":20,"SUPRAILIAC":22,"THIGH":30},
                 "protocolComposition":"POLLOCK_3"}""".formatted(LocalDate.now());

        String error = mvc.perform(post("/api/patients/" + withoutSignup + "/assessments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(error).contains("sex");
    }

    // ----------------------------------------------------- waist-to-hip ratio

    @Test
    @DisplayName("calcula a relação cintura-quadril e classifica o risco")
    void calculatesRatioWaistHip() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,
                 "circumferences":[{"site":"WAIST","side":"SINGLE","valueCm":80},{"site":"HIP","side":"SINGLE","valueCm":100}]}""".formatted(LocalDate.now());

        JsonNode a = assess(tokenA, marina, body, 201);

        assertThat(a.get("ratioWaistHip").decimalValue()).isEqualByComparingTo("0.80");
        // A woman with a WHR of 0.80 sits at the limit: 0.80 is already not low risk.
        assertThat(a.get("riskCardiometabolico").get("value").asText()).isEqualTo("MODERATE");
    }

    @ParameterizedTest(name = "{0} com cintura {1} cm -> {2}")
    @CsvSource({
            // The hip is always 100 cm, so the waist in centimeters is the ratio
            // itself in hundredths. It covers both cutoff bands of each sex,
            // including the first value of each band.
            "FEMALE,   75, LOW",
            "FEMALE,   82, MODERATE",
            "FEMALE,   88, HIGH",
            "MALE,  88, LOW",
            "MALE,  95, MODERATE",
            "MALE, 102, HIGH",
    })
    @DisplayName("classifica o risco pelos cortes de cada sexo")
    void classifiesRiskBySex(String sex, int waist, String risk) throws Exception {
        long patient = "FEMALE".equals(sex) ? marina : carlos;
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,
                 "circumferences":[{"site":"WAIST","side":"SINGLE","valueCm":%d},{"site":"HIP","side":"SINGLE","valueCm":100}]}"""
                .formatted(LocalDate.now(), waist);

        JsonNode a = assess(tokenA, patient, body, 201);

        assertThat(a.get("riskCardiometabolico").get("value").asText()).isEqualTo(risk);
    }

    @Test
    @DisplayName("não classifica risco sem sexo informado")
    void notClassifiesRiskWithoutSex() throws Exception {
        String body = """
                {"date":"%s","circumferences":[{"site":"WAIST","side":"SINGLE","valueCm":80},{"site":"HIP","side":"SINGLE","valueCm":100}]}"""
                .formatted(LocalDate.now());

        JsonNode a = assess(tokenA, withoutSignup, body, 201);

        assertThat(a.get("ratioWaistHip").decimalValue()).isEqualByComparingTo("0.80");
        assertThat(a.get("riskCardiometabolico").has("value")).isFalse();
        assertThat(a.get("riskCardiometabolico").get("unavailableBecause").asText())
                .contains("sex");
    }

    @Test
    @DisplayName("guarda a circunferência dos dois lados")
    void keepsBothSides() throws Exception {
        // Sete dos treze locais que o cliente lista são medidos dos dois lados.
        // É o que fez as circunferências saírem das colunas planas.
        String body = """
                {"date":"%s","circumferences":[
                   {"site":"ARM_RELAXED","side":"RIGHT","valueCm":31.5},
                   {"site":"ARM_RELAXED","side":"LEFT","valueCm":30.8},
                   {"site":"WAIST","side":"SINGLE","valueCm":78}]}""".formatted(LocalDate.now());

        JsonNode a = assess(tokenA, marina, body, 201);
        JsonNode measures = a.get("circumferences");
        assertThat(measures).hasSize(3);

        // Cintura vem antes do braço: a ordem é a do enum, que é a anatômica.
        assertThat(measures.get(0).get("site").asText()).isEqualTo("WAIST");
        assertThat(measures.get(1).get("site").asText()).isEqualTo("ARM_RELAXED");
        assertThat(measures.get(1).get("side").asText()).isEqualTo("RIGHT");
        assertThat(measures.get(1).get("description").asText()).isEqualTo("Braço relaxado");
        assertThat(measures.get(2).get("side").asText()).isEqualTo("LEFT");
    }

    @Test
    @DisplayName("recusa lado em local que não tem lado")
    void refusesASideOnAUnilateralSite() throws Exception {
        // Uma cintura não tem lado direito. Aceitar isso guardaria um dado que
        // não descreve nada e que a tela depois não saberia desenhar.
        String body = """
                {"date":"%s","circumferences":[
                   {"site":"WAIST","side":"RIGHT","valueCm":78}]}""".formatted(LocalDate.now());

        assess(tokenA, marina, body, 422);
    }

    @Test
    @DisplayName("recusa a mesma circunferência duas vezes")
    void refusesADuplicateSite() throws Exception {
        String body = """
                {"date":"%s","circumferences":[
                   {"site":"WAIST","side":"SINGLE","valueCm":78},
                   {"site":"WAIST","side":"SINGLE","valueCm":80}]}""".formatted(LocalDate.now());

        assess(tokenA, marina, body, 422);
    }

    @Test
    @DisplayName("registra a dobra supraespinhal e a bioimpedância")
    void recordsSupraspinalAndBioimpedance() throws Exception {
        String body = """
                {"date":"%s","weightKg":62,"heightCm":165,
                 "skinfolds":{"SUPRASPINAL":14.5},
                 "heightSittingCm":86,"heightKneeCm":49,
                 "diameterHumerus":6.2,"diameterWrist":5.1,"diameterFemur":9.0,
                 "biaFatPercentage":22.4,"biaMuscleMassKg":41.2,
                 "biaVisceralFat":4,"biaMetabolicAge":27}""".formatted(LocalDate.now());

        JsonNode a = assess(tokenA, marina, body, 201);

        assertThat(a.get("skinfolds").get("SUPRASPINAL").decimalValue())
                .isEqualByComparingTo("14.50");
        assertThat(a.get("heightKneeCm").decimalValue()).isEqualByComparingTo("49");
        assertThat(a.get("diameterFemur").decimalValue()).isEqualByComparingTo("9.0");
        // A bioimpedância entra como o aparelho informou, sem recálculo.
        assertThat(a.get("biaFatPercentage").decimalValue()).isEqualByComparingTo("22.4");
        assertThat(a.get("biaMetabolicAge").asInt()).isEqualTo(27);
    }

    @Test
    @DisplayName("não calcula a relação com apenas uma medida")
    void notCalculatesRatioComMeasure() throws Exception {
        String body = """
                {"date":"%s","circumferences":[{"site":"WAIST","side":"SINGLE","valueCm":80}]}""".formatted(LocalDate.now());

        JsonNode a = assess(tokenA, marina, body, 201);

        assertThat(a.get("circumferences").get(0).get("site").asText()).isEqualTo("WAIST");
        assertThat(a.get("circumferences").get(0).get("valueCm").decimalValue())
                .isEqualByComparingTo("80");
        assertThat(a.has("ratioWaistHip")).isFalse();
    }

    // ----------------------------------------------------- energy expenditure

    @Test
    @DisplayName("estima o gasto energético por Mifflin-St Jeor")
    void estimatesExpenditureEnergy() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,
                 "equationExpenditure":"MIFFLIN_ST_JEOR","factorActivity":1.55}"""
                .formatted(LocalDate.now());

        JsonNode expenditure = assess(tokenA, marina, body, 201).get("expenditureEnergy");

        // Mulheres: 10 x 70 + 6,25 x 170 - 5 x 34 - 161 = 1431,5
        assertThat(expenditure.get("basalKcal").decimalValue()).isEqualByComparingTo("1431.50");
        assertThat(expenditure.get("totalKcal").decimalValue()).isEqualByComparingTo("2218.83");
        assertThat(expenditure.get("equation").asText()).isEqualTo("MIFFLIN_ST_JEOR");
    }

    @Test
    @DisplayName("não estima gasto sem idade cadastrada")
    void notEstimatesExpenditureWithoutAge() throws Exception {
        String body = """
                {"date":"%s","weightKg":70,"heightCm":170,"equationExpenditure":"MIFFLIN_ST_JEOR"}"""
                .formatted(LocalDate.now());

        mvc.perform(post("/api/patients/" + withoutSignup + "/assessments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------- progress

    @Test
    @DisplayName("compara com a avaliação anterior e com a primeira")
    void comparesPreviousComEComFirst() throws Exception {
        recordWeight(75, 60);
        recordWeight(72, 30);
        recordWeight(70, 0);

        JsonNode progress = getJson(tokenA, "/api/patients/" + marina + "/progress");
        assertThat(progress.get("assessmentsTotal").asInt()).isEqualTo(3);

        JsonNode last = progress.get("points").get(2);
        assertThat(change(last, "changesPreviousFront", "weightKg"))
                .isEqualByComparingTo("-2.00");
        assertThat(change(last, "changesFrontFirst", "weightKg"))
                .isEqualByComparingTo("-5.00");
    }

    @Test
    @DisplayName("não compara medida ausente em uma das avaliações")
    void notComparesMeasureMissing() throws Exception {
        assess(tokenA, marina, """
                {"date":"%s","weightKg":72,"heightCm":170,"circumferences":[{"site":"WAIST","side":"SINGLE","valueCm":82}]}"""
                .formatted(LocalDate.now().minusDays(30)), 201);
        assess(tokenA, marina, weightEHeight(70, 170), 201);

        JsonNode last = getJson(tokenA, "/api/patients/" + marina + "/progress")
                .get("points").get(1);

        JsonNode waist = findChange(last, "changesPreviousFront", "circumferenceWaist");
        assertThat(waist.get("comparable").asBoolean()).isFalse();
        assertThat(waist.has("difference")).isFalse();
        assertThat(waist.get("notes").asText()).contains("ausente");
    }

    @Test
    @DisplayName("não compara composição estimada por protocolos diferentes")
    void notComparesProtocolsDifferent() throws Exception {
        // January: Faulkner
        assess(tokenA, marina, """
                {"date":"%s","weightKg":72,"heightCm":170,%s,"protocolComposition":"FAULKNER"}"""
                .formatted(LocalDate.now().minusDays(60), SKINFOLDS_FAULKNER), 201);

        // March: Pollock with 3 skinfolds
        assess(tokenA, marina, """
                {"date":"%s","weightKg":70,"heightCm":170,
                 "skinfolds":{"TRICEPS":18,"SUPRAILIAC":20,"THIGH":28},
                 "protocolComposition":"POLLOCK_3"}"""
                .formatted(LocalDate.now()), 201);

        JsonNode last = getJson(tokenA, "/api/patients/" + marina + "/progress")
                .get("points").get(1);

        JsonNode fat = findChange(last, "changesPreviousFront", "percentageFat");
        assertThat(fat.get("comparable").asBoolean()).isFalse();
        assertThat(fat.has("difference")).isFalse();
        assertThat(fat.get("notes").asText()).contains("erro-padrão");

        // But the weight, which does not depend on the protocol, stays comparable.
        assertThat(change(last, "changesPreviousFront", "weightKg"))
                .isEqualByComparingTo("-2.00");
    }

    // ------------------------------------------------------------- isolation

    @Test
    @DisplayName("um consultório não acessa avaliação de outro")
    void isolatesAssessmentsBetweenAccounts() throws Exception {
        long id = assess(tokenA, marina, weightEHeight(70, 170), 201).get("id").asLong();

        mvc.perform(get("/api/assessments/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/patients/" + marina + "/progress")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lista os protocolos com as dobras que cada um exige")
    void listProtocols() throws Exception {
        JsonNode protocols = getJson(tokenA, "/api/anthropometry/protocols");

        assertThat(protocols).isNotEmpty();
        JsonNode faulkner = null;
        for (JsonNode p : protocols) {
            if ("FAULKNER".equals(p.get("protocol").asText())) {
                faulkner = p;
            }
        }
        assertThat(faulkner).isNotNull();
        assertThat(faulkner.get("skinfoldsFemale").findValuesAsText("").size()).isZero();
        assertThat(json.convertValue(faulkner.get("skinfoldsFemale"), java.util.List.class))
                .containsExactlyInAnyOrder("TRICEPS", "SUBSCAPULAR", "SUPRAILIAC", "ABDOMINAL");
    }

    // ----------------------------------------------------------------- helpers

    private void recordWeight(double weight, int daysBack) throws Exception {
        assess(tokenA, marina, """
                {"date":"%s","weightKg":%s,"heightCm":170}"""
                .formatted(LocalDate.now().minusDays(daysBack), weight), 201);
    }

    private JsonNode findChange(JsonNode point, String list, String measure) {
        for (JsonNode v : point.get(list)) {
            if (measure.equals(v.get("measure").asText())) {
                return v;
            }
        }
        throw new AssertionError("variação não encontrada: " + measure);
    }

    private java.math.BigDecimal change(JsonNode point, String list, String measure) {
        return findChange(point, list, measure).get("difference").decimalValue();
    }
}
