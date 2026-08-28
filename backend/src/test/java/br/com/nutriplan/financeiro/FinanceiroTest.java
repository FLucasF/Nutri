package br.com.nutriplan.financeiro;

import br.com.nutriplan.financeiro.service.ValorPorExtenso;
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
 * Implementa os cenários da seção 7 de docs/04-cenarios-bdd.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceiroTest {

    /**
     * Competência fixa e no passado, por dois motivos: a apuração não pode
     * depender do mês em que o teste roda, e pagamento em data futura é
     * recusado pela própria regra do sistema.
     */
    private static final LocalDate COMPETENCIA = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM_DO_PERIODO = LocalDate.of(2026, 6, 30);
    private static final String DATA_PAGAMENTO = "2026-06-12";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long marina;

    @BeforeEach
    void preparar() throws Exception {
        tokenA = cadastrarNutri("finA");
        tokenB = cadastrarNutri("finB");
        marina = criarPaciente(tokenA, "Marina Duarte");
    }

    // ------------------------------------------------------------------ apoio

    private String cadastrarNutri(String prefixo) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri " + prefixo,
                                "email", prefixo + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1",
                                "crn", "CRN-3 11111"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asText();
    }

    private long criarPaciente(String token, String nome) throws Exception {
        String corpo = mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", nome))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    private JsonNode lancar(String token, String corpo, int esperado) throws Exception {
        String resposta = mvc.perform(post("/api/financeiro/lancamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    private long receita(String token, String valor, String situacao) throws Exception {
        long id = lancar(token, """
                {"tipo":"RECEITA","valor":%s,"competencia":"%s","categoria":"Consulta"}"""
                .formatted(valor, COMPETENCIA), 201).get("id").asLong();
        if ("PAGO".equals(situacao)) {
            pagar(token, id, COMPETENCIA.toString(), 200);
        }
        return id;
    }

    private long despesa(String token, String valor, String situacao) throws Exception {
        long id = lancar(token, """
                {"tipo":"DESPESA","valor":%s,"competencia":"%s","categoria":"Aluguel"}"""
                .formatted(valor, COMPETENCIA), 201).get("id").asLong();
        if ("PAGO".equals(situacao)) {
            pagar(token, id, COMPETENCIA.toString(), 200);
        }
        return id;
    }

    private JsonNode pagar(String token, long id, String data, int esperado) throws Exception {
        String corpo = data == null ? "{}" : """
                {"dataPagamento":"%s"}""".formatted(data);
        String resposta = mvc.perform(post("/api/financeiro/lancamentos/" + id + "/pagar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    private JsonNode getJson(String token, String url) throws Exception {
        String corpo = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    // -------------------------------------------------------------- lançamentos

    @Test
    @DisplayName("registra receita vinculada a paciente, como pendente")
    void registraReceita() throws Exception {
        JsonNode l = lancar(tokenA, """
                {"tipo":"RECEITA","valor":250.00,"competencia":"%s","vencimento":"%s",
                 "categoria":"Consulta","pacienteId":%d,"descricao":"Primeira consulta"}"""
                .formatted(COMPETENCIA, COMPETENCIA.plusDays(10), marina), 201);

        assertThat(l.get("situacao").asText()).isEqualTo("PENDENTE");
        assertThat(l.get("valor").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(l.get("pacienteNome").asText()).isEqualTo("Marina Duarte");
        assertThat(l.has("dataPagamento")).isFalse();
    }

    @Test
    @DisplayName("recusa valor não positivo")
    void recusaValorNaoPositivo() throws Exception {
        // O sinal não distingue entrada de saída — quem faz isso é o tipo.
        mvc.perform(post("/api/financeiro/lancamentos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"RECEITA","valor":0,"competencia":"%s","categoria":"Consulta"}"""
                                .formatted(COMPETENCIA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("registra o recebimento com a data")
    void registraRecebimento() throws Exception {
        long id = receita(tokenA, "250.00", "PENDENTE");

        JsonNode pago = pagar(tokenA, id, DATA_PAGAMENTO, 200);

        assertThat(pago.get("situacao").asText()).isEqualTo("PAGO");
        assertThat(pago.get("dataPagamento").asText()).isEqualTo(DATA_PAGAMENTO);
    }

    @Test
    @DisplayName("recusa pagamento com data futura")
    void recusaPagamentoFuturo() throws Exception {
        long id = receita(tokenA, "250.00", "PENDENTE");
        pagar(tokenA, id, LocalDate.now().plusDays(1).toString(), 422);
    }

    @Test
    @DisplayName("estorno devolve o lançamento a pendente")
    void estornoDevolveAPendente() throws Exception {
        long id = receita(tokenA, "250.00", "PAGO");

        String corpo = mvc.perform(post("/api/financeiro/lancamentos/" + id + "/estornar")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode estornado = json.readTree(corpo);
        assertThat(estornado.get("situacao").asText()).isEqualTo("PENDENTE");
        assertThat(estornado.has("dataPagamento")).isFalse();
    }

    @Test
    @DisplayName("não cancela lançamento já pago")
    void naoCancelaLancamentoPago() throws Exception {
        long id = receita(tokenA, "250.00", "PAGO");

        mvc.perform(post("/api/financeiro/lancamentos/" + id + "/cancelar")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------ inadimplência

    @Test
    @DisplayName("lista apenas os vencidos na data de referência")
    void listaVencidos() throws Exception {
        lancar(tokenA, """
                {"tipo":"RECEITA","valor":100,"competencia":"%s","vencimento":"2026-06-01",
                 "categoria":"Consulta"}""".formatted(COMPETENCIA), 201);
        lancar(tokenA, """
                {"tipo":"RECEITA","valor":200,"competencia":"%s","vencimento":"2026-12-30",
                 "categoria":"Consulta"}""".formatted(COMPETENCIA), 201);

        JsonNode vencidos = getJson(tokenA, "/api/financeiro/vencidos?referencia=2026-06-15");

        assertThat(vencidos).hasSize(1);
        assertThat(vencidos.get(0).get("valor").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(vencidos.get(0).get("vencido").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("lançamento pago não aparece como vencido")
    void pagoNaoEhVencido() throws Exception {
        long id = lancar(tokenA, """
                {"tipo":"RECEITA","valor":100,"competencia":"%s","vencimento":"2026-06-01",
                 "categoria":"Consulta"}""".formatted(COMPETENCIA), 201).get("id").asLong();
        pagar(tokenA, id, "2026-06-05", 200);

        assertThat(getJson(tokenA, "/api/financeiro/vencidos?referencia=2026-06-15")).isEmpty();
    }

    // ------------------------------------------------------------------ apuração

    @Test
    @DisplayName("apura separando efetivado de previsto")
    void apuraPeriodo() throws Exception {
        receita(tokenA, "1000.00", "PAGO");
        receita(tokenA, "300.00", "PENDENTE");
        despesa(tokenA, "400.00", "PAGO");

        JsonNode a = getJson(tokenA,
                "/api/financeiro/apuracao?de=" + COMPETENCIA + "&ate=" + FIM_DO_PERIODO);

        assertThat(a.get("totalRecebido").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(a.get("totalAReceber").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(a.get("despesasPagas").decimalValue()).isEqualByComparingTo("400.00");
        // Efetivado só conta o que entrou; previsto inclui o que ainda está pendente.
        assertThat(a.get("resultadoEfetivado").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(a.get("resultadoPrevisto").decimalValue()).isEqualByComparingTo("900.00");
        assertThat(a.get("lancamentos").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("lançamento cancelado sai da apuração")
    void canceladoNaoEntraNaApuracao() throws Exception {
        receita(tokenA, "1000.00", "PAGO");
        long cancelado = receita(tokenA, "500.00", "PENDENTE");

        mvc.perform(post("/api/financeiro/lancamentos/" + cancelado + "/cancelar")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        JsonNode a = getJson(tokenA,
                "/api/financeiro/apuracao?de=" + COMPETENCIA + "&ate=" + FIM_DO_PERIODO);

        assertThat(a.get("totalAReceber").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(a.get("resultadoPrevisto").decimalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("apuração de um consultório ignora o outro")
    void isolaApuracaoEntreContas() throws Exception {
        receita(tokenB, "5000.00", "PAGO");
        receita(tokenA, "1000.00", "PAGO");

        JsonNode a = getJson(tokenA,
                "/api/financeiro/apuracao?de=" + COMPETENCIA + "&ate=" + FIM_DO_PERIODO);

        assertThat(a.get("totalRecebido").decimalValue()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("um consultório não acessa lançamento de outro")
    void isolaLancamentosEntreContas() throws Exception {
        long id = receita(tokenA, "250.00", "PENDENTE");

        mvc.perform(get("/api/financeiro/lancamentos/" + id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------- recibo

    @Test
    @DisplayName("emite recibo de lançamento pago")
    void emiteRecibo() throws Exception {
        long id = lancar(tokenA, """
                {"tipo":"RECEITA","valor":250.00,"competencia":"%s","categoria":"Consulta",
                 "pacienteId":%d,"descricao":"Atendimento nutricional"}"""
                .formatted(COMPETENCIA, marina), 201).get("id").asLong();
        pagar(tokenA, id, DATA_PAGAMENTO, 200);

        JsonNode recibo = getJson(tokenA, "/api/financeiro/lancamentos/" + id + "/recibo");

        assertThat(recibo.get("valor").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(recibo.get("valorPorExtenso").asText()).isEqualTo("duzentos e cinquenta reais");
        assertThat(recibo.get("pagadorNome").asText()).isEqualTo("Marina Duarte");
        assertThat(recibo.get("profissionalCrn").asText()).isEqualTo("CRN-3 11111");
        assertThat(recibo.get("referente").asText()).isEqualTo("Atendimento nutricional");
    }

    @Test
    @DisplayName("não emite recibo de lançamento pendente")
    void naoEmiteReciboDePendente() throws Exception {
        long id = receita(tokenA, "250.00", "PENDENTE");

        // Recibo de valor não recebido seria declaração falsa.
        mvc.perform(get("/api/financeiro/lancamentos/" + id + "/recibo")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("não emite recibo sobre despesa")
    void naoEmiteReciboDeDespesa() throws Exception {
        long id = despesa(tokenA, "400.00", "PAGO");

        mvc.perform(get("/api/financeiro/lancamentos/" + id + "/recibo")
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
    void escreveValorPorExtenso(String valor, String esperado) {
        assertThat(ValorPorExtenso.emReais(new BigDecimal(valor))).isEqualTo(esperado);
    }
}
