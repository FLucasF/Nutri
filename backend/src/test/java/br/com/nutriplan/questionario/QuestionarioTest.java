package br.com.nutriplan.questionario;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Questionários pré-consulta (RF90–RF95).
 *
 * O paciente responde por link, sem conta — o mesmo mecanismo pelo qual ele lê
 * o plano. O que estes testes protegem é o mesmo conjunto de regras de sempre:
 * o registro guarda o que aconteceu naquele dia, e o cálculo não finge exatidão
 * quando falta dado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionarioTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long paciente;

    @BeforeEach
    void preparar() throws Exception {
        token = cadastrar("quest");
        tokenB = cadastrar("questB");
        paciente = json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Marina Duarte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String cadastrar(String prefixo) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri " + prefixo,
                                "email", prefixo + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String tk, String url, String corpo, int esperado) throws Exception {
        var pedido = post(url).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (tk != null) {
            pedido = pedido.header("Authorization", "Bearer " + tk);
        }
        String r = mvc.perform(pedido).andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    /** Questionário pontuável com três perguntas de escolha. */
    private long criarPontuavel() throws Exception {
        return postJson(token, "/api/questionarios", """
                {"nome":"Triagem alimentar","pontuavel":true,
                 "faixaDeCorte":"0-2=Baixo|3-4=Moderado|5-9=Alto",
                 "perguntas":[
                   {"enunciado":"Come fora de casa?","tipo":"ESCOLHA_UNICA","obrigatoria":true,
                    "opcoes":"Raramente=0|As vezes=1|Todo dia=2"},
                   {"enunciado":"Bebe refrigerante?","tipo":"ESCOLHA_UNICA","obrigatoria":true,
                    "opcoes":"Nunca=0|As vezes=1|Todo dia=2"},
                   {"enunciado":"Algo mais?","tipo":"TEXTO","obrigatoria":false}]}""", 201)
                .get("id").asLong();
    }

    private String enviar(long questionarioId) throws Exception {
        return postJson(token, "/api/pacientes/" + paciente + "/questionarios",
                """
                {"questionarioId":%d}""".formatted(questionarioId), 201)
                .get("identificadorPublico").asText();
    }

    // ------------------------------------------------------------- biblioteca

    @Test
    @DisplayName("o sistema traz um modelo de pré-consulta, não editável")
    void modeloDoSistema() throws Exception {
        JsonNode lista = getJson(token, "/api/questionarios");
        assertThat(lista).isNotEmpty();

        JsonNode modelo = lista.get(0);
        assertThat(modelo.get("modeloDoSistema").asBoolean()).isTrue();
        assertThat(modelo.get("nome").asText()).isEqualTo("Pré-consulta");
        assertThat(modelo.get("perguntas")).hasSize(10);

        mvc.perform(put("/api/questionarios/" + modelo.get("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Meu","perguntas":[{"enunciado":"X","tipo":"TEXTO"}]}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("duplicar o modelo traz as perguntas junto")
    void duplicarTrazAsPerguntas() throws Exception {
        long modelo = getJson(token, "/api/questionarios").get(0).get("id").asLong();

        JsonNode copia = postJson(token, "/api/questionarios/" + modelo + "/duplicar", "", 201);

        assertThat(copia.get("editavel").asBoolean()).isTrue();
        assertThat(copia.get("perguntas")).hasSize(10);
    }

    @Test
    @DisplayName("pergunta de escolha sem alternativa é recusada")
    void escolhaSemAlternativa() throws Exception {
        postJson(token, "/api/questionarios", """
                {"nome":"Torto","perguntas":[
                  {"enunciado":"Escolha","tipo":"ESCOLHA_UNICA","obrigatoria":true}]}""", 422);
    }

    @Test
    @DisplayName("questionário sem pergunta é recusado")
    void questionarioVazio() throws Exception {
        postJson(token, "/api/questionarios", """
                {"nome":"Vazio","perguntas":[]}""", 400);
    }

    // ------------------------------------------------------ lado do paciente

    @Test
    @DisplayName("o paciente abre e responde sem conta")
    void pacienteRespondeSemConta() throws Exception {
        long questionario = criarPontuavel();
        String link = enviar(questionario);

        // Sem cabeçalho de autenticação nenhum.
        JsonNode formulario = json.readTree(mvc.perform(
                        get("/api/publico/questionarios/" + link))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(formulario.get("titulo").asText()).isEqualTo("Triagem alimentar");
        assertThat(formulario.get("jaRespondido").asBoolean()).isFalse();
        assertThat(formulario.get("perguntas")).hasSize(3);
        // O contrato público não tem campo para paciente nem para conta.
        assertThat(formulario.toString()).doesNotContain("Marina");
    }

    @Test
    @DisplayName("o escore sai da soma das alternativas, e a classificação da faixa")
    void escoreEClassificacao() throws Exception {
        long questionario = criarPontuavel();
        String link = enviar(questionario);
        JsonNode formulario = json.readTree(mvc.perform(
                        get("/api/publico/questionarios/" + link))
                .andReturn().getResponse().getContentAsString());

        long p1 = formulario.get("perguntas").get(0).get("id").asLong();
        long p2 = formulario.get("perguntas").get(1).get("id").asLong();

        // "Todo dia" vale 2 nas duas: escore 4, faixa "3-4=Moderado".
        postJson(null, "/api/publico/questionarios/" + link, """
                {"respostas":[{"perguntaId":%d,"valor":"Todo dia"},
                              {"perguntaId":%d,"valor":"Todo dia"}]}"""
                .formatted(p1, p2), 204);

        JsonNode resposta = getJson(token, "/api/pacientes/" + paciente + "/questionarios").get(0);
        assertThat(resposta.get("escore").asInt()).isEqualTo(4);
        assertThat(resposta.get("classificacao").asText()).isEqualTo("Moderado");
        assertThat(resposta.get("pendente").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("falta de resposta obrigatória é recusada dizendo qual falta")
    void faltaObrigatoria() throws Exception {
        long questionario = criarPontuavel();
        String link = enviar(questionario);
        JsonNode formulario = json.readTree(mvc.perform(
                        get("/api/publico/questionarios/" + link))
                .andReturn().getResponse().getContentAsString());
        long p1 = formulario.get("perguntas").get(0).get("id").asLong();

        String erro = mvc.perform(post("/api/publico/questionarios/" + link)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"respostas":[{"perguntaId":%d,"valor":"Raramente"}]}"""
                                .formatted(p1)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        // Diz qual falta, em vez de pontuar pela metade.
        assertThat(erro).contains("Bebe refrigerante");
    }

    @Test
    @DisplayName("o link aceita resposta uma vez só")
    void respostaUnica() throws Exception {
        long questionario = criarPontuavel();
        String link = enviar(questionario);
        JsonNode formulario = json.readTree(mvc.perform(
                        get("/api/publico/questionarios/" + link))
                .andReturn().getResponse().getContentAsString());
        long p1 = formulario.get("perguntas").get(0).get("id").asLong();
        long p2 = formulario.get("perguntas").get(1).get("id").asLong();
        String corpo = """
                {"respostas":[{"perguntaId":%d,"valor":"Nunca"},
                              {"perguntaId":%d,"valor":"Nunca"}]}""".formatted(p1, p2);

        postJson(null, "/api/publico/questionarios/" + link, corpo, 204);
        postJson(null, "/api/publico/questionarios/" + link, corpo, 422);
    }

    @Test
    @DisplayName("link inventado responde 404")
    void linkInventado() throws Exception {
        mvc.perform(get("/api/publico/questionarios/nao-existe"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- versão

    @Test
    @DisplayName("editar o questionário não altera a resposta já recebida")
    void edicaoNaoAlcancaARespostaRecebida() throws Exception {
        long questionario = criarPontuavel();
        String link = enviar(questionario);
        JsonNode formulario = json.readTree(mvc.perform(
                        get("/api/publico/questionarios/" + link))
                .andReturn().getResponse().getContentAsString());
        long p1 = formulario.get("perguntas").get(0).get("id").asLong();
        long p2 = formulario.get("perguntas").get(1).get("id").asLong();

        postJson(null, "/api/publico/questionarios/" + link, """
                {"respostas":[{"perguntaId":%d,"valor":"As vezes"},
                              {"perguntaId":%d,"valor":"Nunca"}]}""".formatted(p1, p2), 204);

        // O modelo perde uma pergunta e ganha outra.
        mvc.perform(put("/api/questionarios/" + questionario)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Triagem alimentar","pontuavel":true,
                                 "perguntas":[{"enunciado":"Pergunta nova","tipo":"TEXTO"}]}"""))
                .andExpect(status().isOk());

        JsonNode resposta = getJson(token, "/api/pacientes/" + paciente + "/questionarios").get(0);
        assertThat(resposta.get("versaoModelo").asInt())
                .as("a resposta guarda a versao que respondeu")
                .isEqualTo(1);
        assertThat(resposta.get("itens").toString())
                .as("o enunciado respondido continua la, mesmo removido do modelo")
                .contains("Come fora de casa");
    }

    // --------------------------------------------------------- isolamento

    @Test
    @DisplayName("o questionário próprio não aparece para outro consultório")
    void isolaBiblioteca() throws Exception {
        criarPontuavel();
        assertThat(getJson(tokenB, "/api/questionarios").toString())
                .doesNotContain("Triagem alimentar");
    }

    @Test
    @DisplayName("um consultório não lê as respostas do paciente de outro")
    void isolaRespostas() throws Exception {
        long questionario = criarPontuavel();
        enviar(questionario);

        mvc.perform(get("/api/pacientes/" + paciente + "/questionarios")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("as respostas aparecem ligadas ao atendimento")
    void respostaLigadaAoAtendimento() throws Exception {
        long questionario = criarPontuavel();
        long agendamento = json.readTree(mvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"inicio":"%sT09:00:00","duracaoMinutos":60,
                                 "tipo":"PRIMEIRA_CONSULTA"}"""
                                .formatted(paciente, java.time.LocalDate.now().plusDays(3))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        postJson(token, "/api/pacientes/" + paciente + "/questionarios", """
                {"questionarioId":%d,"agendamentoId":%d}""".formatted(questionario, agendamento),
                201);

        assertThat(getJson(token, "/api/agenda/" + agendamento + "/questionarios")).hasSize(1);
    }
}
