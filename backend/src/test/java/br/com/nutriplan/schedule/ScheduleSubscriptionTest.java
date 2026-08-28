package br.com.nutriplan.schedule;

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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Schedule subscription in an external calendar (RF76).
 *
 * It is not an integration with the Google API: it is an iCalendar feed, the
 * format Google Calendar, Apple Calendar and Outlook subscribe to natively.
 * What is left out is the way back — creating an appointment here from an event
 * created there.
 *
 * What these tests protect is what breaks in a different reader: timezone,
 * stable UID and escaping of characters that mean something in the format.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleSubscriptionTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri Agenda",
                                "email", "ics" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        patient = json.readTree(mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Marina, Duarte e Silva"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String generateSubscription() throws Exception {
        return json.readTree(mvc.perform(post("/api/schedule/subscription")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private long schedule(String hour) throws Exception {
        return json.readTree(mvc.perform(post("/api/schedule")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":%d,"start":"%sT%s:00","durationMinutes":60,
                                 "type":"FIRST_CONSULTATION","notes":"Trazer exames"}"""
                                .formatted(patient, LocalDate.now().plusDays(2), hour)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String downloadCalendar(String subscription, int expected) throws Exception {
        return mvc.perform(get("/api/public/schedule/" + subscription + ".ics"))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a assinatura só existe depois de pedida")
    void subscriptionSoExistsRequestAfter() throws Exception {
        String before = mvc.perform(get("/api/schedule/subscription")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tokenNode = json.readTree(before).get("token");
        assertThat(tokenNode == null || tokenNode.isNull())
                .as("sem assinatura pedida, o endereco nao existe")
                .isTrue();

        assertThat(generateSubscription()).isNotBlank();
    }

    @Test
    @DisplayName("o feed sai no formato iCalendar, com os atendimentos")
    void feedComAppointments() throws Exception {
        schedule("09:00");
        String subscription = generateSubscription();

        String ics = downloadCalendar(subscription, 200);

        assertThat(ics).startsWith("BEGIN:VCALENDAR");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("BEGIN:VEVENT").contains("END:VEVENT");
        assertThat(ics).contains("SUMMARY:");
        assertThat(ics).contains("Trazer exames");
        // The format's line break is CRLF, and not LF.
        assertThat(ics).contains("\r\n");
    }

    @Test
    @DisplayName("o horário sai em UTC, convertido do fuso de Brasília")
    void timeAtUtc() throws Exception {
        schedule("09:00");
        String ics = downloadCalendar(generateSubscription(), 200);

        // 09:00 in Brasília (UTC−3) is 12:00 UTC. Without the conversion, the
        // appointment would show up three hours off in the user's calendar.
        assertThat(ics).contains("T120000Z");
    }

    @Test
    @DisplayName("o UID é estável entre buscas")
    void uidStable() throws Exception {
        long id = schedule("09:00");
        String subscription = generateSubscription();

        String first = downloadCalendar(subscription, 200);
        String second = downloadCalendar(subscription, 200);

        // If the UID changed, the calendar would delete and recreate the event on
        // every update, and the alert would ring again.
        assertThat(first).contains("UID:appointment-" + id + "@nutriplan");
        assertThat(second).contains("UID:appointment-" + id + "@nutriplan");
    }

    @Test
    @DisplayName("vírgula no nome do paciente é escapada")
    void escapesCharacterComSignificado() throws Exception {
        schedule("09:00");
        String ics = downloadCalendar(generateSubscription(), 200);

        // "Marina, Duarte e Silva": the comma separates values in the format, and
        // without escaping it the reader would cut the name right there.
        assertThat(ics).contains("Marina\\, Duarte e Silva");
    }

    @Test
    @DisplayName("nenhuma linha passa de 75 octetos")
    void rowsDobradas() throws Exception {
        schedule("09:00");
        String ics = downloadCalendar(generateSubscription(), 200);

        for (String row : ics.split("\r\n")) {
            assertThat(row.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .as("linha: %s", row)
                    .isLessThanOrEqualTo(75);
        }
    }

    @Test
    @DisplayName("regerar invalida o endereço anterior")
    void invalidatesRegeneratePrevious() throws Exception {
        String first = generateSubscription();
        downloadCalendar(first, 200);

        String second = generateSubscription();
        assertThat(second).isNotEqualTo(first);

        downloadCalendar(first, 404);
        downloadCalendar(second, 200);
    }

    @Test
    @DisplayName("desligar a assinatura derruba o feed")
    void turnOffDropsFeed() throws Exception {
        String subscription = generateSubscription();
        downloadCalendar(subscription, 200);

        mvc.perform(delete("/api/schedule/subscription")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        downloadCalendar(subscription, 404);
    }

    @Test
    @DisplayName("endereço inventado responde 404")
    void addressInvented() throws Exception {
        downloadCalendar("nao-existe", 404);
    }

    @Test
    @DisplayName("o feed traz só a agenda daquele consultório")
    void feedNotLeaksScheduleOther() throws Exception {
        schedule("09:00");
        String minhaSubscription = generateSubscription();

        String other = json.readTree(mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Bruna",
                                "email", "bruna" + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        String otherSubscription = json.readTree(mvc.perform(post("/api/schedule/subscription")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        assertThat(downloadCalendar(otherSubscription, 200)).doesNotContain("Marina");
        assertThat(downloadCalendar(minhaSubscription, 200)).contains("Marina");
    }
}
