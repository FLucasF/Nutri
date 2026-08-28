package br.com.nutriplan.agenda;

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
 * Implementa os cenários da seção 6 de docs/04-cenarios-bdd.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgendaTest {

    /** Data fixa no futuro, para o teste não depender do dia em que roda. */
    private static final LocalDate DIA = LocalDate.of(2026, 9, 10);
    private static final LocalDate OUTRO_DIA = LocalDate.of(2026, 9, 11);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long marina;
    private long pacienteDeB;

    @BeforeEach
    void preparar() throws Exception {
        tokenA = cadastrarNutri("agendaA");
        tokenB = cadastrarNutri("agendaB");
        marina = criarPaciente(tokenA, "Marina Duarte");
        pacienteDeB = criarPaciente(tokenB, "Paciente da Bruna");
    }

    // ------------------------------------------------------------------ apoio

    private String cadastrarNutri(String prefixo) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri " + prefixo,
                                "email", prefixo + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
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

    private String pedido(long paciente, LocalDate dia, String hora, int duracao) {
        return """
               {"pacienteId":%d,"inicio":"%sT%s:00","duracaoMinutos":%d,"tipo":"RETORNO"}"""
                .formatted(paciente, dia, hora, duracao);
    }

    private JsonNode agendar(String token, String corpo, int esperado) throws Exception {
        String resposta = mvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    private JsonNode mudarSituacao(String token, long id, String situacao, int esperado)
            throws Exception {
        String resposta = mvc.perform(post("/api/agenda/" + id + "/situacao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"situacao":"%s"}""".formatted(situacao)))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isBlank() ? null : json.readTree(resposta);
    }

    // ------------------------------------------------------------- agendamento

    @Test
    @DisplayName("agenda uma consulta e deriva o horário de fim")
    void agendaConsulta() throws Exception {
        JsonNode a = agendar(tokenA, """
                {"pacienteId":%d,"inicio":"%sT14:00:00","duracaoMinutos":60,
                 "tipo":"PRIMEIRA_CONSULTA"}""".formatted(marina, DIA), 201);

        assertThat(a.get("inicio").asText()).startsWith(DIA + "T14:00");
        assertThat(a.get("fim").asText()).startsWith(DIA + "T15:00");
        assertThat(a.get("situacao").asText()).isEqualTo("AGENDADO");
        assertThat(a.get("tipoDescricao").asText()).isEqualTo("Primeira consulta");
        assertThat(a.get("pacienteNome").asText()).isEqualTo("Marina Duarte");
    }

    @Test
    @DisplayName("recusa atendimento sobreposto")
    void recusaSobreposicao() throws Exception {
        agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201);

        String erro = mvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido(marina, DIA, "14:30", 60)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(erro).contains("Conflito de horário").contains("14:00").contains("15:00");
    }

    @Test
    @DisplayName("atendimentos encostados não são conflito")
    void encostadosNaoConflitam() throws Exception {
        agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201);
        // Começa exatamente quando o anterior termina: agenda cheia, não erro.
        JsonNode segundo = agendar(tokenA, pedido(marina, DIA, "15:00", 60), 201);

        assertThat(segundo.get("inicio").asText()).startsWith(DIA + "T15:00");
    }

    @Test
    @DisplayName("cancelamento libera o horário")
    void cancelamentoLiberaHorario() throws Exception {
        long id = agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201).get("id").asLong();

        // Enquanto ocupa, o horário está bloqueado.
        agendar(tokenA, pedido(marina, DIA, "14:00", 60), 422);

        mudarSituacao(tokenA, id, "CANCELADO", 200);

        agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201);
    }

    @Test
    @DisplayName("agendas de consultórios diferentes não conflitam")
    void agendasIndependentesPorConsultorio() throws Exception {
        agendar(tokenB, pedido(pacienteDeB, DIA, "14:00", 60), 201);
        // Mesmo horário, outro consultório: sem conflito.
        agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201);
    }

    @Test
    @DisplayName("remarcar não conflita consigo mesmo")
    void remarcarNaoConflitaConsigoMesmo() throws Exception {
        long id = agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201).get("id").asLong();

        // Mantém o mesmo horário, mudando só a duração.
        String corpo = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/agenda/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido(marina, DIA, "14:00", 90)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(corpo).get("fim").asText()).startsWith(DIA + "T15:30");
    }

    // ---------------------------------------------------------------- listagem

    @Test
    @DisplayName("lista a agenda do dia em ordem de horário")
    void listaAgendaDoDia() throws Exception {
        agendar(tokenA, pedido(marina, DIA, "16:00", 30), 201);
        agendar(tokenA, pedido(marina, DIA, "09:00", 30), 201);
        agendar(tokenA, pedido(marina, DIA, "11:00", 30), 201);
        agendar(tokenA, pedido(marina, OUTRO_DIA, "10:00", 30), 201);

        String corpo = mvc.perform(get("/api/agenda/dia").param("data", DIA.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode dia = json.readTree(corpo);

        assertThat(dia.get("totalDeAtendimentos").asInt()).isEqualTo(3);
        assertThat(dia.get("atendimentos").get(0).get("inicio").asText()).contains("T09:00");
        assertThat(dia.get("atendimentos").get(1).get("inicio").asText()).contains("T11:00");
        assertThat(dia.get("atendimentos").get(2).get("inicio").asText()).contains("T16:00");
    }

    // ---------------------------------------------------------------- desfecho

    @ParameterizedTest(name = "{0} -> {1} : {2}")
    @CsvSource({
            "AGENDADO,   CONFIRMADO, 200",
            "AGENDADO,   CANCELADO,  200",
            "AGENDADO,   FALTOU,     200",
            "CONFIRMADO, REALIZADO,  200",
            "REALIZADO,  AGENDADO,   422",
            "CANCELADO,  REALIZADO,  422",
    })
    @DisplayName("respeita as transições válidas de situação")
    void respeitaTransicoes(String de, String para, int esperado) throws Exception {
        long id = agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201).get("id").asLong();

        // Leva o atendimento até a situação de partida.
        if (!"AGENDADO".equals(de)) {
            if ("REALIZADO".equals(de)) {
                mudarSituacao(tokenA, id, "CONFIRMADO", 200);
                mudarSituacao(tokenA, id, "REALIZADO", 200);
            } else {
                mudarSituacao(tokenA, id, de, 200);
            }
        }

        mudarSituacao(tokenA, id, para, esperado);
    }

    @Test
    @DisplayName("falta fica no histórico do paciente")
    void faltaFicaNoHistorico() throws Exception {
        long id = agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201).get("id").asLong();
        mudarSituacao(tokenA, id, "FALTOU", 200);

        String corpo = mvc.perform(get("/api/agenda/paciente/" + marina)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode historico = json.readTree(corpo);
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).get("situacao").asText()).isEqualTo("FALTOU");
    }

    @Test
    @DisplayName("atendimento terminal não é remarcado")
    void terminalNaoRemarca() throws Exception {
        long id = agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201).get("id").asLong();
        mudarSituacao(tokenA, id, "CANCELADO", 200);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/agenda/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido(marina, DIA, "16:00", 60)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------ isolamento

    @Test
    @DisplayName("um consultório não acessa atendimento de outro")
    void isolaAgendaEntreContas() throws Exception {
        long id = agendar(tokenA, pedido(marina, DIA, "14:00", 60), 201).get("id").asLong();

        mvc.perform(get("/api/agenda/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        String corpo = mvc.perform(get("/api/agenda/dia").param("data", DIA.toString())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(corpo).get("totalDeAtendimentos").asInt()).isZero();
    }

    @Test
    @DisplayName("recusa duração inválida")
    void recusaDuracaoInvalida() throws Exception {
        mvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido(marina, DIA, "14:00", 0)))
                .andExpect(status().isBadRequest());
    }
}
