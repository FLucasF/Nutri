package br.com.nutriplan.schedule;

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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O lote 2 do cliente: pacotes que marcam a série, pagamento a partir da
 * consulta, atestado de comparecimento, parcelas, parceiros e estatísticas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PackagesAndPaymentsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        token = registerNutri("lote2");
        patient = createPatient(token, "Marina Duarte", null);
    }

    // ------------------------------------------------------------------ apoio

    private String registerNutri(String prefix) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 12345"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createPatient(String tokenOf, String name, Long partnerId) throws Exception {
        var data = new java.util.HashMap<String, Object>();
        data.put("name", name);
        if (partnerId != null) {
            data.put("partnerId", partnerId);
        }
        return json.readTree(mvc.perform(post("/api/patients")
                                .header("Authorization", "Bearer " + tokenOf)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(data)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private JsonNode postJson(String tokenOf, String path, Object body, int expected) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .header("Authorization", "Bearer " + tokenOf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expected))
                .andReturn();
        String text = result.getResponse().getContentAsString();
        return text.isBlank() ? null : json.readTree(text);
    }

    private JsonNode getJson(String tokenOf, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + tokenOf))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long createPackage(String name, int amount, Integer sessions, Integer intervalDays) throws Exception {
        var data = new java.util.HashMap<String, Object>();
        data.put("name", name);
        data.put("amount", amount);
        if (sessions != null) {
            data.put("sessions", sessions);
        }
        if (intervalDays != null) {
            data.put("intervalDays", intervalDays);
        }
        return postJson(token, "/api/packages", data, 201).get("id").asLong();
    }

    private JsonNode schedule(LocalDate day, String hour, Map<String, Object> extra) throws Exception {
        var data = new java.util.HashMap<String, Object>(Map.of(
                "patientId", patient,
                "start", day + "T" + hour + ":00",
                "durationMinutes", 60,
                "type", "FOLLOWUP"));
        data.putAll(extra);
        return postJson(token, "/api/schedule", data, 201);
    }

    // ------------------------------------------------------------------ pacotes

    @Test
    @DisplayName("Um pacote de três encontros marca os outros dois, de intervalo em intervalo")
    void packageSeries() throws Exception {
        long pack = createPackage("Trimestral", 900, 3, 7);

        JsonNode first = schedule(DAY, "09:00", Map.of("packageId", pack, "createSeries", true));
        assertThat(first.get("seriesCreated").asInt()).isEqualTo(2);
        assertThat(first.get("packageName").asText()).isEqualTo("Trimestral");
        assertThat(first.get("packageAmount").decimalValue()).isEqualByComparingTo("900");

        JsonNode list = getJson(token, "/api/schedule/patient/" + patient);
        assertThat(list).hasSize(3);
        Set<String> starts = new HashSet<>();
        for (JsonNode a : list) {
            starts.add(a.get("start").asText());
            assertThat(a.get("packageId").asLong()).isEqualTo(pack);
        }
        assertThat(starts).containsExactlyInAnyOrder(
                DAY + "T09:00:00", DAY.plusDays(7) + "T09:00:00", DAY.plusDays(14) + "T09:00:00");
    }

    @Test
    @DisplayName("Um encontro da série cujo horário já está tomado fica de fora, e é dito")
    void seriesSkipsTakenSlot() throws Exception {
        long pack = createPackage("Mensal", 400, 3, 7);
        schedule(DAY.plusDays(7), "09:00", Map.of());

        JsonNode first = schedule(DAY, "09:00", Map.of("packageId", pack, "createSeries", true));
        assertThat(first.get("seriesCreated").asInt()).isEqualTo(1);
        assertThat(first.get("seriesSkipped")).hasSize(1);
        assertThat(first.get("seriesSkipped").get(0).asText()).contains("Encontro 2");
    }

    @Test
    @DisplayName("Um pacote desativado não serve para agendar, e o que já apontava para ele fica")
    void inactivePackageRefused() throws Exception {
        long pack = createPackage("Antigo", 100, null, null);
        long kept = schedule(DAY, "09:00", Map.of("packageId", pack)).get("id").asLong();
        postJson(token, "/api/packages/" + pack + "/deactivate", Map.of(), 200);

        postJson(token, "/api/schedule", Map.of(
                "patientId", patient,
                "start", DAY + "T11:00:00",
                "durationMinutes", 60,
                "type", "FOLLOWUP",
                "packageId", pack), 422);
        assertThat(getJson(token, "/api/schedule/" + kept).get("packageName").asText()).isEqualTo("Antigo");
        // Desativado, some da lista de agendar; pedido explicitamente, aparece.
        assertThat(getJson(token, "/api/packages")).isEmpty();
        assertThat(getJson(token, "/api/packages?includeInactive=true")).hasSize(1);
    }

    // -------------------------------------------------------------- pagamento

    @Test
    @DisplayName("O pagamento registrado pela consulta vira receita paga com o valor do pacote; a segunda vez é recusada")
    void paymentFromAppointment() throws Exception {
        long pack = createPackage("Consulta avulsa", 250, null, null);
        long appointment = schedule(DAY, "10:00", Map.of("packageId", pack)).get("id").asLong();

        JsonNode paid = postJson(token, "/api/finance/appointments/" + appointment + "/payment",
                Map.of("paymentMethod", "PIX", "documentNumber", "REC-1"), 200);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("value").decimalValue()).isEqualByComparingTo("250");
        assertThat(paid.get("packageId").asLong()).isEqualTo(pack);
        assertThat(paid.get("appointmentId").asLong()).isEqualTo(appointment);
        assertThat(paid.get("documentNumber").asText()).isEqualTo("REC-1");
        assertThat(paid.get("category").asText()).isEqualTo("Pacote");

        JsonNode detail = getJson(token, "/api/schedule/" + appointment);
        assertThat(detail.get("payment").get("status").asText()).isEqualTo("PAID");
        assertThat(detail.get("payment").get("transactionId").asLong()).isEqualTo(paid.get("id").asLong());

        postJson(token, "/api/finance/appointments/" + appointment + "/payment", Map.of(), 422);

        JsonNode receipt = getJson(token, "/api/finance/transactions/" + paid.get("id").asLong() + "/receipt");
        assertThat(receipt.get("documentNumber").asText()).isEqualTo("REC-1");
        assertThat(receipt.get("payerName").asText()).isEqualTo("Marina Duarte");
    }

    @Test
    @DisplayName("Sem pacote e sem valor, o pagamento pela consulta é recusado")
    void paymentNeedsValue() throws Exception {
        long appointment = schedule(DAY, "10:00", Map.of()).get("id").asLong();
        postJson(token, "/api/finance/appointments/" + appointment + "/payment", Map.of(), 422);
        JsonNode paid = postJson(token, "/api/finance/appointments/" + appointment + "/payment",
                Map.of("value", 180), 200);
        assertThat(paid.get("category").asText()).isEqualTo("Retorno");
        assertThat(paid.get("value").decimalValue()).isEqualByComparingTo("180");
    }

    // --------------------------------------------------------------- atestado

    @Test
    @DisplayName("O atestado só sai de consulta realizada, e sai em PDF")
    void certificateOnlyWhenCompleted() throws Exception {
        long appointment = schedule(DAY, "14:00", Map.of()).get("id").asLong();

        mvc.perform(get("/api/schedule/" + appointment + "/certificate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        postJson(token, "/api/schedule/" + appointment + "/status", Map.of("status", "COMPLETED"), 200);

        byte[] pdf = mvc.perform(get("/api/schedule/" + appointment + "/certificate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    // ---------------------------------------------------------------- parcelas

    @Test
    @DisplayName("Parcelar em três cria três lançamentos que somam o valor, um por mês")
    void installments() throws Exception {
        JsonNode first = postJson(token, "/api/finance/transactions", Map.of(
                "type", "INCOME",
                "value", 100,
                "accrual", "2026-09-10",
                "category", "Pacote",
                "description", "Pacote parcelado",
                "patientId", patient,
                "documentNumber", "NF 77",
                "installments", 3), 201);
        assertThat(first.get("installmentIndex").asInt()).isEqualTo(1);
        assertThat(first.get("installmentCount").asInt()).isEqualTo(3);
        assertThat(first.get("value").decimalValue()).isEqualByComparingTo("33.34");

        JsonNode page = getJson(token, "/api/finance/transactions?patientId=" + patient + "&size=50");
        JsonNode rows = page.get("content");
        assertThat(rows).hasSize(3);
        BigDecimal total = BigDecimal.ZERO;
        Set<String> months = new HashSet<>();
        for (JsonNode row : rows) {
            total = total.add(row.get("value").decimalValue());
            months.add(row.get("accrual").asText().substring(0, 7));
            assertThat(row.get("installmentGroup").asText()).isEqualTo(first.get("installmentGroup").asText());
            assertThat(row.get("documentNumber").asText()).isEqualTo("NF 77");
        }
        assertThat(total).isEqualByComparingTo("100");
        assertThat(months).containsExactlyInAnyOrder("2026-09", "2026-10", "2026-11");

        postJson(token, "/api/finance/transactions/" + first.get("id").asLong() + "/pay",
                Map.of("datePayment", LocalDate.now().toString()), 200);
        JsonNode receipt = getJson(token, "/api/finance/transactions/" + first.get("id").asLong() + "/receipt");
        assertThat(receipt.get("installment").asText()).isEqualTo("1/3");
        assertThat(receipt.get("related").asText()).contains("parcela 1/3");
    }

    // --------------------------------------------------------------- parceiros

    @Test
    @DisplayName("O relatório de indicações conta o paciente, a consulta e a receita paga do parceiro")
    void partnerReport() throws Exception {
        long partner = postJson(token, "/api/partners",
                Map.of("name", "Academia Corpo Leve", "kind", "Academia"), 201).get("id").asLong();
        long referred = createPatient(token, "Indicada pela Academia", partner);

        JsonNode list = getJson(token, "/api/partners");
        assertThat(list.get(0).get("patientsReferred").asLong()).isEqualTo(1);

        LocalDate today = LocalDate.now();
        JsonNode appointment = postJson(token, "/api/schedule", Map.of(
                "patientId", referred,
                "start", today + "T10:00:00",
                "durationMinutes", 30,
                "type", "FIRST_CONSULTATION"), 201);
        postJson(token, "/api/finance/appointments/" + appointment.get("id").asLong() + "/payment",
                Map.of("value", 200), 200);

        JsonNode report = getJson(token, "/api/partners/report?from=" + today.minusDays(1) + "&to=" + today.plusDays(1));
        JsonNode row = report.get("rows").get(0);
        assertThat(row.get("partnerName").asText()).isEqualTo("Academia Corpo Leve");
        assertThat(row.get("patientsReferred").asLong()).isEqualTo(1);
        assertThat(row.get("appointments").asLong()).isEqualTo(1);
        assertThat(row.get("revenuePaid").decimalValue()).isEqualByComparingTo("200");
        assertThat(report.get("totalRevenue").decimalValue()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Um parceiro de outra conta não serve para indicar paciente")
    void partnerOfAnotherAccountRefused() throws Exception {
        String other = registerNutri("outra");
        long foreign = postJson(other, "/api/partners", Map.of("name", "Parceiro Alheio"), 201).get("id").asLong();

        mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Paciente", "partnerId", foreign))))
                .andExpect(status().isNotFound());
        assertThat(getJson(token, "/api/partners")).isEmpty();
    }

    // ------------------------------------------------------------ estatísticas

    @Test
    @DisplayName("As estatísticas trazem um mês por período e quem está sem consulta")
    void statistics() throws Exception {
        JsonNode stats = getJson(token, "/api/statistics?months=3");
        assertThat(stats.get("months")).hasSize(3);
        assertThat(stats.get("activePatients").asInt()).isEqualTo(1);
        assertThat(stats.get("inactivityDays").asInt()).isEqualTo(60);
        // Cadastrada agora, sem consulta: ainda não conta como sumida.
        assertThat(stats.get("withoutVisit")).isEmpty();
        assertThat(stats.get("months").get(2).get("newPatients").asInt()).isEqualTo(1);
    }
}
