package br.com.nutriplan.schedule;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Implements the scenarios of section 6 of docs/04-cenarios-bdd.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleTest {

    /** A fixed date in the future, so the test does not depend on the day it runs. */
    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);
    private static final LocalDate OTHER_DAY = LocalDate.of(2026, 9, 11);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long marina;
    private long bPatient;

    @BeforeEach
    void prepare() throws Exception {
        tokenA = registerNutri("schedule");
        tokenB = registerNutri("scheduleB");
        marina = createPatient(tokenA, "Marina Duarte");
        bPatient = createPatient(tokenB, "Paciente da Bruna");
    }

    // ------------------------------------------------------------------ apoio

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

    private long createPatient(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String request(long patient, LocalDate day, String hour, int duration) {
        return """
               {"patientId":%d,"start":"%sT%s:00","durationMinutes":%d,"type":"FOLLOWUP"}"""
                .formatted(patient, day, hour, duration);
    }

    private JsonNode schedule(String token, String body, int expected) throws Exception {
        String answer = mvc.perform(post("/api/schedule")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isBlank() ? null : json.readTree(answer);
    }

    private JsonNode changeStatus(String token, long id, String status, int expected)
            throws Exception {
        String answer = mvc.perform(post("/api/schedule/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}""".formatted(status)))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isBlank() ? null : json.readTree(answer);
    }

    // ----------------------------------------------------------------- booking

    @Test
    @DisplayName("agenda uma consulta e deriva o horário de fim")
    void scheduleConsultation() throws Exception {
        JsonNode a = schedule(tokenA, """
                {"patientId":%d,"start":"%sT14:00:00","durationMinutes":60,
                 "type":"FIRST_CONSULTATION"}""".formatted(marina, DAY), 201);

        assertThat(a.get("start").asText()).startsWith(DAY + "T14:00");
        assertThat(a.get("end").asText()).startsWith(DAY + "T15:00");
        assertThat(a.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(a.get("typeDescription").asText()).isEqualTo("Primeira consulta");
        assertThat(a.get("patientName").asText()).isEqualTo("Marina Duarte");
    }

    @Test
    @DisplayName("recusa atendimento sobreposto")
    void rejectsOverlap() throws Exception {
        schedule(tokenA, request(marina, DAY, "14:00", 60), 201);

        String error = mvc.perform(post("/api/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(marina, DAY, "14:30", 60)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(error).contains("Conflito de horário").contains("14:00").contains("15:00");
    }

    @Test
    @DisplayName("atendimentos encostados não são conflito")
    void adjacentNotConflict() throws Exception {
        schedule(tokenA, request(marina, DAY, "14:00", 60), 201);
        // It starts exactly when the previous one ends: a full schedule, not an error.
        JsonNode second = schedule(tokenA, request(marina, DAY, "15:00", 60), 201);

        assertThat(second.get("start").asText()).startsWith(DAY + "T15:00");
    }

    @Test
    @DisplayName("cancelamento libera o horário")
    void cancellationFreesTime() throws Exception {
        long id = schedule(tokenA, request(marina, DAY, "14:00", 60), 201).get("id").asLong();

        // While it occupies, the slot is blocked.
        schedule(tokenA, request(marina, DAY, "14:00", 60), 422);

        changeStatus(tokenA, id, "CANCELED", 200);

        schedule(tokenA, request(marina, DAY, "14:00", 60), 201);
    }

    @Test
    @DisplayName("agendas de consultórios diferentes não conflitam")
    void agendasIndependentesByPractice() throws Exception {
        schedule(tokenB, request(bPatient, DAY, "14:00", 60), 201);
        // Same time, another practice: no conflict.
        schedule(tokenA, request(marina, DAY, "14:00", 60), 201);
    }

    @Test
    @DisplayName("remarcar não conflita consigo mesmo")
    void rescheduleNotConflitaConsigoSame() throws Exception {
        long id = schedule(tokenA, request(marina, DAY, "14:00", 60), 201).get("id").asLong();

        // It keeps the same time, changing only the duration.
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/schedule/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(marina, DAY, "14:00", 90)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body).get("end").asText()).startsWith(DAY + "T15:30");
    }

    // ---------------------------------------------------------------- listagem

    @Test
    @DisplayName("lista a agenda do dia em ordem de horário")
    void listDaySchedule() throws Exception {
        schedule(tokenA, request(marina, DAY, "16:00", 30), 201);
        schedule(tokenA, request(marina, DAY, "09:00", 30), 201);
        schedule(tokenA, request(marina, DAY, "11:00", 30), 201);
        schedule(tokenA, request(marina, OTHER_DAY, "10:00", 30), 201);

        String body = mvc.perform(get("/api/schedule/day").param("date", DAY.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode day = json.readTree(body);

        assertThat(day.get("appointmentsTotal").asInt()).isEqualTo(3);
        assertThat(day.get("appointments").get(0).get("start").asText()).contains("T09:00");
        assertThat(day.get("appointments").get(1).get("start").asText()).contains("T11:00");
        assertThat(day.get("appointments").get(2).get("start").asText()).contains("T16:00");
    }

    // ----------------------------------------------------------------- outcome

    @ParameterizedTest(name = "{0} -> {1} : {2}")
    @CsvSource({
            "SCHEDULED,   CONFIRMED, 200",
            "SCHEDULED,   CANCELED,  200",
            "SCHEDULED,   NOSHOW,     200",
            "CONFIRMED, COMPLETED,  200",
            "COMPLETED,  SCHEDULED,   422",
            "CANCELED,  COMPLETED,  422",
    })
    @DisplayName("respeita as transições válidas de situação")
    void respectsTransitions(String from, String to, int expected) throws Exception {
        long id = schedule(tokenA, request(marina, DAY, "14:00", 60), 201).get("id").asLong();

        // It takes the appointment to the starting status.
        if (!"SCHEDULED".equals(from)) {
            if ("COMPLETED".equals(from)) {
                changeStatus(tokenA, id, "CONFIRMED", 200);
                changeStatus(tokenA, id, "COMPLETED", 200);
            } else {
                changeStatus(tokenA, id, from, 200);
            }
        }

        changeStatus(tokenA, id, to, expected);
    }

    @Test
    @DisplayName("falta fica no histórico do paciente")
    void noshowStaysNoHistory() throws Exception {
        long id = schedule(tokenA, request(marina, DAY, "14:00", 60), 201).get("id").asLong();
        changeStatus(tokenA, id, "NOSHOW", 200);

        String body = mvc.perform(get("/api/schedule/patient/" + marina)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode history = json.readTree(body);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("status").asText()).isEqualTo("NOSHOW");
    }

    @Test
    @DisplayName("atendimento terminal não é remarcado")
    void terminalNotReschedules() throws Exception {
        long id = schedule(tokenA, request(marina, DAY, "14:00", 60), 201).get("id").asLong();
        changeStatus(tokenA, id, "CANCELED", 200);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/schedule/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(marina, DAY, "16:00", 60)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------- isolation

    @Test
    @DisplayName("um consultório não acessa atendimento de outro")
    void isolatesScheduleBetweenAccounts() throws Exception {
        long id = schedule(tokenA, request(marina, DAY, "14:00", 60), 201).get("id").asLong();

        mvc.perform(get("/api/schedule/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        String body = mvc.perform(get("/api/schedule/day").param("date", DAY.toString())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("appointmentsTotal").asInt()).isZero();
    }

    @Test
    @DisplayName("recusa duração inválida")
    void rejectsInvalidatesDuration() throws Exception {
        mvc.perform(post("/api/schedule")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(marina, DAY, "14:00", 0)))
                .andExpect(status().isBadRequest());
    }
}
