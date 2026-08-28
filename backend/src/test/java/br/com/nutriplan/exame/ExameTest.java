package br.com.nutriplan.exame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exames laboratoriais (RF100–RF106).
 *
 * O que estes testes protegem é a mesma regra do protocolo de dobras e do peso
 * prescrito: o registro guarda o que se sabia quando foi feito. Faixa de
 * referência depende do método do laboratório e muda; reclassificar exame
 * antigo com faixa nova faria um resultado normal virar alterado sem que nada
 * tivesse acontecido com o paciente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExameTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long mulher;
    private long homem;
    private long semCadastro;

    @BeforeEach
    void preparar() throws Exception {
        token = cadastrar("exame");
        tokenB = cadastrar("exameB");
        mulher = criarPaciente(token, "Marina Duarte", "FEMININO", 34);
        homem = criarPaciente(token, "Carlos Menezes", "MASCULINO", 40);
        semCadastro = criarPacienteSemDados(token);
    }

    private String cadastrar(String prefixo) throws Exception {
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

    private long criarPaciente(String tk, String nome, String sexo, int idade) throws Exception {
        String corpo = json.writeValueAsString(Map.of(
                "nome", nome, "sexo", sexo,
                "dataNascimento", LocalDate.now().minusYears(idade).minusDays(1).toString()));
        return json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long criarPacienteSemDados(String tk) throws Exception {
        return json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Sem Dados"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long parametro(String nome) throws Exception {
        for (JsonNode p : getJson(token, "/api/exames/parametros")) {
            if (p.get("nome").asText().equals(nome)) {
                return p.get("id").asLong();
            }
        }
        throw new IllegalStateException("parametro nao encontrado no catalogo: " + nome);
    }

    private JsonNode registrar(String tk, long paciente, String corpo, int esperado)
            throws Exception {
        String r = mvc.perform(post("/api/pacientes/" + paciente + "/exames")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    // ---------------------------------------------------------------- catálogo

    @Test
    @DisplayName("o sistema traz um catálogo de parâmetros com faixas")
    void catalogoDoSistema() throws Exception {
        JsonNode parametros = getJson(token, "/api/exames/parametros");

        assertThat(parametros).isNotEmpty();
        JsonNode glicemia = null;
        for (JsonNode p : parametros) {
            if (p.get("nome").asText().equals("Glicemia de jejum")) glicemia = p;
        }
        assertThat(glicemia).isNotNull();
        assertThat(glicemia.get("doCatalogoDoSistema").asBoolean()).isTrue();
        assertThat(glicemia.get("unidadePadrao").asText()).isEqualTo("mg/dL");
        assertThat(glicemia.get("faixas")).isNotEmpty();
    }

    @Test
    @DisplayName("o consultório cadastra parâmetro próprio, com a faixa dele")
    void parametroProprio() throws Exception {
        JsonNode criado = json.readTree(mvc.perform(post("/api/exames/parametros")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Selenio serico","unidadePadrao":"µg/L",
                                 "grupo":"Vitaminas e minerais","minimo":70,"maximo":150}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(criado.get("editavel").asBoolean()).isTrue();
        assertThat(criado.get("faixas").get(0).get("texto").asText()).isEqualTo("70 a 150");

        // Não vaza para outro consultório.
        assertThat(getJson(tokenB, "/api/exames/parametros").toString())
                .doesNotContain("Selenio serico");
    }

    // ------------------------------------------------------------ classificação

    @Test
    @DisplayName("classifica o valor contra a faixa do parâmetro")
    void classificaContraAFaixa() throws Exception {
        long glicemia = parametro("Glicemia de jejum");

        JsonNode baixa = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":60}"""
                .formatted(glicemia, LocalDate.now()), 201);
        JsonNode normal = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(glicemia, LocalDate.now()), 201);
        JsonNode alta = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":130}"""
                .formatted(glicemia, LocalDate.now()), 201);

        assertThat(baixa.get("classificacao").asText()).isEqualTo("ABAIXO");
        assertThat(normal.get("classificacao").asText()).isEqualTo("NORMAL");
        assertThat(alta.get("classificacao").asText()).isEqualTo("ACIMA");
        assertThat(normal.get("referencia").asText()).isEqualTo("70 a 99");
    }

    @Test
    @DisplayName("a faixa depende do sexo do paciente")
    void faixaPorSexo() throws Exception {
        long ferritina = parametro("Ferritina");
        String corpo = """
                {"parametroId":%d,"dataColeta":"%s","valor":200}"""
                .formatted(ferritina, LocalDate.now());

        JsonNode dela = registrar(token, mulher, corpo, 201);
        JsonNode dele = registrar(token, homem, corpo, 201);

        // 200 ng/mL: acima para mulher (15–150), dentro para homem (30–400).
        assertThat(dela.get("classificacao").asText()).isEqualTo("ACIMA");
        assertThat(dele.get("classificacao").asText()).isEqualTo("NORMAL");
        assertThat(dela.get("referencia").asText()).isEqualTo("15 a 150");
        assertThat(dele.get("referencia").asText()).isEqualTo("30 a 400");
    }

    @Test
    @DisplayName("sem sexo informado, usa a faixa geral — e sem faixa geral, não classifica")
    void semSexoUsaFaixaGeral() throws Exception {
        JsonNode geral = registrar(token, semCadastro, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now()), 201);
        assertThat(geral.get("classificacao").asText()).isEqualTo("NORMAL");

        // Ferritina só tem faixa por sexo.
        JsonNode semFaixa = registrar(token, semCadastro, """
                {"parametroId":%d,"dataColeta":"%s","valor":200}"""
                .formatted(parametro("Ferritina"), LocalDate.now()), 201);
        assertThat(semFaixa.has("classificacao") && !semFaixa.get("classificacao").isNull())
                .as("sem faixa aplicável, o valor é registrado sem classificação")
                .isFalse();
    }

    @Test
    @DisplayName("a faixa usada fica gravada, e alterar o cadastro não reclassifica o passado")
    void faixaGravadaNaoMudaComOCadastro() throws Exception {
        JsonNode criado = json.readTree(mvc.perform(post("/api/exames/parametros")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Marcador X","unidadePadrao":"mg/L","minimo":10,"maximo":20}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long marcador = criado.get("id").asLong();

        JsonNode exame = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":25}"""
                .formatted(marcador, LocalDate.now()), 201);
        assertThat(exame.get("classificacao").asText()).isEqualTo("ACIMA");
        assertThat(exame.get("referencia").asText()).isEqualTo("10 a 20");

        // Um parâmetro novo com faixa mais larga não alcança o exame já gravado:
        // a faixa foi copiada para dentro dele.
        JsonNode relido = getJson(token, "/api/pacientes/" + mulher + "/exames").get(0);
        assertThat(relido.get("referencia").asText()).isEqualTo("10 a 20");
    }

    @Test
    @DisplayName("unidade diferente da do parâmetro não é classificada")
    void unidadeDiferenteNaoClassifica() throws Exception {
        JsonNode exame = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":4.7,"unidade":"mmol/L"}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now()), 201);

        // 4,7 mmol/L é uma glicemia normal, mas comparar com 70–99 mg/dL diria
        // "abaixo" com aparência de certeza.
        assertThat(exame.get("unidade").asText()).isEqualTo("mmol/L");
        assertThat(exame.has("classificacao") && !exame.get("classificacao").isNull()).isFalse();
    }

    // ------------------------------------------------------------------ registro

    @Test
    @DisplayName("parâmetro não determinado fica sem valor, e não como zero")
    void naoDeterminadoNaoViraZero() throws Exception {
        JsonNode exame = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","observacao":"pedido, aguardando"}"""
                .formatted(parametro("Homocisteína"), LocalDate.now()), 201);

        assertThat(exame.has("valor") && !exame.get("valor").isNull()).isFalse();
        assertThat(exame.has("classificacao") && !exame.get("classificacao").isNull()).isFalse();
    }

    @Test
    @DisplayName("recusa coleta no futuro")
    void recusaColetaFutura() throws Exception {
        registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now().plusDays(1)), 400);
    }

    @Test
    @DisplayName("aceita coleta retroativa")
    void aceitaColetaRetroativa() throws Exception {
        JsonNode exame = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now().minusYears(2)), 201);
        assertThat(exame.get("dataColeta").asText())
                .isEqualTo(LocalDate.now().minusYears(2).toString());
    }

    // --------------------------------------------------------------------- série

    @Test
    @DisplayName("a série mostra a evolução do parâmetro, com a variação")
    void serieHistorica() throws Exception {
        long glicemia = parametro("Glicemia de jejum");
        for (var caso : new String[][] {{"120", "24"}, {"105", "12"}, {"92", "0"}}) {
            registrar(token, mulher, """
                    {"parametroId":%d,"dataColeta":"%s","valor":%s}"""
                    .formatted(glicemia, LocalDate.now().minusMonths(Long.parseLong(caso[1])),
                            caso[0]), 201);
        }

        JsonNode serie = getJson(token,
                "/api/pacientes/" + mulher + "/exames/serie/" + glicemia);

        assertThat(serie.get("pontos")).hasSize(3);
        // Do mais antigo para o mais recente.
        assertThat(serie.get("pontos").get(0).get("valor").decimalValue())
                .isEqualByComparingTo("120.000");
        assertThat(serie.get("pontos").get(2).get("valor").decimalValue())
                .isEqualByComparingTo("92.000");
        assertThat(serie.get("pontos").get(1).get("variacao").decimalValue())
                .isEqualByComparingTo("-15.000");
        assertThat(serie.get("unidadesMisturadas").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("série com unidades diferentes é sinalizada, e não comparada")
    void serieComUnidadesMisturadas() throws Exception {
        long glicemia = parametro("Glicemia de jejum");
        registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":100}"""
                .formatted(glicemia, LocalDate.now().minusMonths(6)), 201);
        registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":5.2,"unidade":"mmol/L"}"""
                .formatted(glicemia, LocalDate.now()), 201);

        JsonNode serie = getJson(token,
                "/api/pacientes/" + mulher + "/exames/serie/" + glicemia);

        assertThat(serie.get("unidadesMisturadas").asBoolean()).isTrue();
        // A variação entre unidades diferentes não é calculada.
        JsonNode segundo = serie.get("pontos").get(1);
        assertThat(segundo.has("variacao") && !segundo.get("variacao").isNull()).isFalse();
    }

    // --------------------------------------------------------------------- laudo

    @Test
    @DisplayName("anexa e devolve o laudo")
    void anexaEBaixaLaudo() throws Exception {
        long id = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now()), 201)
                .get("id").asLong();

        var arquivo = new MockMultipartFile("arquivo", "laudo.pdf", "application/pdf",
                "%PDF-1.4 conteudo".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/exames/" + id + "/laudo").file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode exame = getJson(token, "/api/pacientes/" + mulher + "/exames").get(0);
        assertThat(exame.get("temLaudo").asBoolean()).isTrue();
        assertThat(exame.get("laudoNome").asText()).isEqualTo("laudo.pdf");

        var resposta = mvc.perform(get("/api/exames/" + id + "/laudo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(resposta.getContentType()).startsWith("application/pdf");
        assertThat(resposta.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @DisplayName("exame sem laudo responde 404 no download")
    void semLaudoResponde404() throws Exception {
        long id = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now()), 201)
                .get("id").asLong();

        mvc.perform(get("/api/exames/" + id + "/laudo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- solicitação

    @Test
    @DisplayName("registra o pedido de exames entregue ao paciente")
    void registraSolicitacao() throws Exception {
        String corpo = """
                {"data":"%s","parametroIds":[%d,%d],"observacao":"Jejum de 8 horas."}"""
                .formatted(LocalDate.now(), parametro("Glicemia de jejum"), parametro("Ferritina"));

        JsonNode solicitacao = json.readTree(mvc.perform(
                        post("/api/pacientes/" + mulher + "/solicitacoes-de-exame")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(solicitacao.get("exames")).hasSize(2);
        assertThat(solicitacao.get("observacao").asText()).isEqualTo("Jejum de 8 horas.");
        assertThat(getJson(token, "/api/pacientes/" + mulher + "/solicitacoes-de-exame"))
                .hasSize(1);
    }

    @Test
    @DisplayName("solicitação sem exame escolhido é recusada")
    void recusaSolicitacaoVazia() throws Exception {
        mvc.perform(post("/api/pacientes/" + mulher + "/solicitacoes-de-exame")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"%s","parametroIds":[]}""".formatted(LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ isolamento

    @Test
    @DisplayName("um consultório não vê exame de paciente de outro")
    void isolaEntreConsultorios() throws Exception {
        long id = registrar(token, mulher, """
                {"parametroId":%d,"dataColeta":"%s","valor":85}"""
                .formatted(parametro("Glicemia de jejum"), LocalDate.now()), 201)
                .get("id").asLong();

        mvc.perform(get("/api/pacientes/" + mulher + "/exames")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/exames/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parametroId":%d,"dataColeta":"%s","valor":999}"""
                                .formatted(parametro("Glicemia de jejum"), LocalDate.now())))
                .andExpect(status().isNotFound());
    }
}
