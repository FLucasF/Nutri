package br.com.nutriplan.finance;

import br.com.nutriplan.finance.service.ValueByWords;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Implements the scenarios of section 7 of docs/04-cenarios-bdd.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceTest {

    /**
     * Accrual fixed and in the past, for two reasons: the settlement cannot
     * depend on the month the test runs in, and payment on a future date is
     * refused by the system's own rule.
     */
    private static final LocalDate ACCRUAL = LocalDate.of(2026, 6, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 6, 30);
    private static final String DATE_PAYMENT = "2026-06-12";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long marina;

    @BeforeEach
    void prepare() throws Exception {
        tokenA = registerNutri("finA");
        tokenB = registerNutri("finB");
        marina = createPatient(tokenA, "Marina Duarte");
    }

    // ------------------------------------------------------------------ apoio

    private String registerNutri(String prefix) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 11111"))))
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

    private JsonNode book(String token, String body, int expected) throws Exception {
        String answer = mvc.perform(post("/api/finance/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return answer.isBlank() ? null : json.readTree(answer);
    }

    private long income(String token, String value, String status) throws Exception {
        long id = book(token, """
                {"type":"INCOME","value":%s,"accrual":"%s","category":"Consulta"}"""
                .formatted(value, ACCRUAL), 201).get("id").asLong();
        if ("PAID".equals(status)) {
            pay(token, id, ACCRUAL.toString(), 200);
        }
        return id;
    }

    private long expense(String token, String value, String status) throws Exception {
        long id = book(token, """
                {"type":"EXPENSE","value":%s,"accrual":"%s","category":"Aluguel"}"""
                .formatted(value, ACCRUAL), 201).get("id").asLong();
        if ("PAID".equals(status)) {
            pay(token, id, ACCRUAL.toString(), 200);
        }
        return id;
    }

    private JsonNode pay(String token, long id, String date, int expected) throws Exception {
        String body = date == null ? "{}" : """
                {"datePayment":"%s"}""".formatted(date);
        String answer = mvc.perform(post("/api/finance/transactions/" + id + "/pay")
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

    // ------------------------------------------------------------- transactions

    @Test
    @DisplayName("registra receita vinculada a paciente, como pendente")
    void recordsRecipe() throws Exception {
        JsonNode l = book(tokenA, """
                {"type":"INCOME","value":250.00,"accrual":"%s","due":"%s",
                 "category":"Consulta","patientId":%d,"description":"Primeira consulta"}"""
                .formatted(ACCRUAL, ACCRUAL.plusDays(10), marina), 201);

        assertThat(l.get("status").asText()).isEqualTo("PENDING");
        assertThat(l.get("value").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(l.get("patientName").asText()).isEqualTo("Marina Duarte");
        assertThat(l.has("datePayment")).isFalse();
    }

    @Test
    @DisplayName("recusa valor não positivo")
    void rejectsValueNotPositive() throws Exception {
        // The sign does not tell income from expense — what does that is the type.
        mvc.perform(post("/api/finance/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"INCOME","value":0,"accrual":"%s","category":"Consulta"}"""
                                .formatted(ACCRUAL)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("registra o recebimento com a data")
    void recordsReceipt() throws Exception {
        long id = income(tokenA, "250.00", "PENDING");

        JsonNode paid = pay(tokenA, id, DATE_PAYMENT, 200);

        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("datePayment").asText()).isEqualTo(DATE_PAYMENT);
    }

    @Test
    @DisplayName("recusa pagamento com data futura")
    void rejectsPaymentFuture() throws Exception {
        long id = income(tokenA, "250.00", "PENDING");
        pay(tokenA, id, LocalDate.now().plusDays(1).toString(), 422);
    }

    @Test
    @DisplayName("estorno devolve o lançamento a pendente")
    void refundReturnsPending() throws Exception {
        long id = income(tokenA, "250.00", "PAID");

        String body = mvc.perform(post("/api/finance/transactions/" + id + "/refund")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode refunded = json.readTree(body);
        assertThat(refunded.get("status").asText()).isEqualTo("PENDING");
        assertThat(refunded.has("datePayment")).isFalse();
    }

    @Test
    @DisplayName("não cancela lançamento já pago")
    void notCancelsTransactionPaid() throws Exception {
        long id = income(tokenA, "250.00", "PAID");

        mvc.perform(post("/api/finance/transactions/" + id + "/cancel")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    // --------------------------------------------------------- overdue accounts

    @Test
    @DisplayName("lista apenas os vencidos na data de referência")
    void listOverdue() throws Exception {
        book(tokenA, """
                {"type":"INCOME","value":100,"accrual":"%s","due":"2026-06-01",
                 "category":"Consulta"}""".formatted(ACCRUAL), 201);
        book(tokenA, """
                {"type":"INCOME","value":200,"accrual":"%s","due":"2026-12-30",
                 "category":"Consulta"}""".formatted(ACCRUAL), 201);

        JsonNode overdue = getJson(tokenA, "/api/finance/overdue?reference=2026-06-15");

        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).get("value").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(overdue.get(0).get("overdue").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("lançamento pago não aparece como vencido")
    void paidNotEhOverdue() throws Exception {
        long id = book(tokenA, """
                {"type":"INCOME","value":100,"accrual":"%s","due":"2026-06-01",
                 "category":"Consulta"}""".formatted(ACCRUAL), 201).get("id").asLong();
        pay(tokenA, id, "2026-06-05", 200);

        assertThat(getJson(tokenA, "/api/finance/overdue?reference=2026-06-15")).isEmpty();
    }

    // ---------------------------------------------------------------- settlement

    @Test
    @DisplayName("apura separando efetivado de previsto")
    void settlesPeriod() throws Exception {
        income(tokenA, "1000.00", "PAID");
        income(tokenA, "300.00", "PENDING");
        expense(tokenA, "400.00", "PAID");

        JsonNode a = getJson(tokenA,
                "/api/finance/summary?from=" + ACCRUAL + "&to=" + PERIOD_END);

        assertThat(a.get("totalReceived").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(a.get("totalReceive").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(a.get("expensesPaid").decimalValue()).isEqualByComparingTo("400.00");
        // Settled counts only what came in; expected includes what is still pending.
        assertThat(a.get("resultEfetivado").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(a.get("resultExpected").decimalValue()).isEqualByComparingTo("900.00");
        assertThat(a.get("transactions").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("lançamento cancelado sai da apuração")
    void canceledNotEntersAtSummary() throws Exception {
        income(tokenA, "1000.00", "PAID");
        long canceled = income(tokenA, "500.00", "PENDING");

        mvc.perform(post("/api/finance/transactions/" + canceled + "/cancel")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        JsonNode a = getJson(tokenA,
                "/api/finance/summary?from=" + ACCRUAL + "&to=" + PERIOD_END);

        assertThat(a.get("totalReceive").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(a.get("resultExpected").decimalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("apuração de um consultório ignora o outro")
    void isolatesSummaryBetweenAccounts() throws Exception {
        income(tokenB, "5000.00", "PAID");
        income(tokenA, "1000.00", "PAID");

        JsonNode a = getJson(tokenA,
                "/api/finance/summary?from=" + ACCRUAL + "&to=" + PERIOD_END);

        assertThat(a.get("totalReceived").decimalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("um consultório não acessa lançamento de outro")
    void isolatesTransactionsBetweenAccounts() throws Exception {
        long id = income(tokenA, "250.00", "PENDING");

        mvc.perform(get("/api/finance/transactions/" + id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------- receipt

    @Test
    @DisplayName("emite recibo de lançamento pago")
    void issuesReceipt() throws Exception {
        long id = book(tokenA, """
                {"type":"INCOME","value":250.00,"accrual":"%s","category":"Consulta",
                 "patientId":%d,"description":"Atendimento nutricional"}"""
                .formatted(ACCRUAL, marina), 201).get("id").asLong();
        pay(tokenA, id, DATE_PAYMENT, 200);

        JsonNode receipt = getJson(tokenA, "/api/finance/transactions/" + id + "/receipt");

        assertThat(receipt.get("value").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(receipt.get("valueByWords").asText()).isEqualTo("duzentos e cinquenta reais");
        assertThat(receipt.get("payerName").asText()).isEqualTo("Marina Duarte");
        assertThat(receipt.get("profissionalCrn").asText()).isEqualTo("CRN-3 11111");
        assertThat(receipt.get("related").asText()).isEqualTo("Atendimento nutricional");
    }

    @Test
    @DisplayName("não emite recibo de lançamento pendente")
    void notIssuesPendingReceipt() throws Exception {
        long id = income(tokenA, "250.00", "PENDING");

        // A receipt for an amount not received would be a false statement.
        mvc.perform(get("/api/finance/transactions/" + id + "/receipt")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("não emite recibo sobre despesa")
    void notIssuesExpenseReceipt() throws Exception {
        long id = expense(tokenA, "400.00", "PAID");

        mvc.perform(get("/api/finance/transactions/" + id + "/receipt")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "1.00,       um real",
            "2.00,       dois reais",
            "0.50,       cinquenta centavos",
            "100.00,     cem reais",
            "250.00,     duzentos e cinquenta reais",
            "1000.00,    mil reais",
            "1200.00,    mil e duzentos reais",
            "1250.50,    mil duzentos e cinquenta reais e cinquenta centavos",
            "1000000.00, um milhão de reais",
    })
    @DisplayName("escreve o valor por extenso")
    void writesValueByWords(String value, String expected) {
        assertThat(ValueByWords.inBrl(new BigDecimal(value))).isEqualTo(expected);
    }
}
