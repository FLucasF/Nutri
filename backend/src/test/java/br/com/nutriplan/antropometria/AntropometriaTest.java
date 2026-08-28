package br.com.nutriplan.antropometria;

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
 * Implementa os cenários da seção 5 de docs/04-cenarios-bdd.md.
 *
 * Os números vêm de lá e não são ilustrativos: um cenário que apenas afirma
 * "calcula corretamente" passaria com qualquer resultado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AntropometriaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long marina;      // 34 anos, feminino
    private long pedro;       // 15 anos, masculino
    private long carlos;      // 40 anos, masculino
    private long semCadastro; // sem sexo nem nascimento

    @BeforeEach
    void preparar() throws Exception {
        tokenA = cadastrarNutri("antroA");
        tokenB = cadastrarNutri("antroB");

        marina = criarPaciente(tokenA, Map.of(
                "nome", "Marina Duarte",
                "dataNascimento", nascimentoParaIdade(34),
                "sexo", "FEMININO"));
        pedro = criarPaciente(tokenA, Map.of(
                "nome", "Pedro Lima",
                "dataNascimento", nascimentoParaIdade(15),
                "sexo", "MASCULINO"));
        // Adulto masculino existe separado de Pedro: os cortes de risco sao
        // de adulto, e aplica-los a um menino de 15 anos seria testar um uso
        // que a clinica nao faz.
        carlos = criarPaciente(tokenA, Map.of(
                "nome", "Carlos Menezes",
                "dataNascimento", nascimentoParaIdade(40),
                "sexo", "MASCULINO"));
        semCadastro = criarPaciente(tokenA, Map.of("nome", "Sem Dados"));
    }

    // ------------------------------------------------------------------ apoio

    /** Nascimento que produz exatamente a idade pedida hoje. */
    private String nascimentoParaIdade(int idade) {
        return LocalDate.now().minusYears(idade).minusDays(1).toString();
    }

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

    private long criarPaciente(String token, Map<String, Object> dados) throws Exception {
        String corpo = mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dados)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    private JsonNode avaliar(String token, long paciente, String corpo, int esperado) throws Exception {
        String resposta = mvc.perform(post("/api/pacientes/" + paciente + "/avaliacoes")
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

    private String pesoEAltura(double peso, double altura) {
        return """
               {"data":"%s","pesoKg":%s,"alturaCm":%s}"""
                .formatted(LocalDate.now(), peso, altura);
    }

    /** As quatro dobras do protocolo de Faulkner: soma 85 mm. */
    private static final String DOBRAS_FAULKNER = """
            "dobras":{"TRICIPITAL":20,"SUBESCAPULAR":18,"SUPRAILIACA":22,"ABDOMINAL":25}""";

    // ------------------------------------------------------- registro e IMC

    @Test
    @DisplayName("registra avaliação com o mínimo e calcula o IMC")
    void registraAvaliacaoComOMinimo() throws Exception {
        JsonNode a = avaliar(tokenA, marina, pesoEAltura(70, 170), 201);

        assertThat(a.get("data").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(a.get("imc").decimalValue()).isEqualByComparingTo("24.22");
        assertThat(a.get("classificacaoImc").get("valor").asText()).isEqualTo("EUTROFIA");
    }

    @Test
    @DisplayName("recusa avaliação com data futura")
    void recusaAvaliacaoFutura() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170}""".formatted(LocalDate.now().plusDays(1));

        mvc.perform(post("/api/pacientes/" + marina + "/avaliacoes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("data"));
    }

    @Test
    @DisplayName("aceita avaliação retroativa")
    void aceitaAvaliacaoRetroativa() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":72,"alturaCm":170}""".formatted(LocalDate.now().minusDays(30));
        JsonNode a = avaliar(tokenA, marina, corpo, 201);
        assertThat(a.get("data").asText()).isEqualTo(LocalDate.now().minusDays(30).toString());
    }

    @ParameterizedTest(name = "{0} kg -> IMC {1} -> {2}")
    @CsvSource({
            "50.0,  17.30, BAIXO_PESO",
            "60.0,  20.76, EUTROFIA",
            "75.0,  25.95, SOBREPESO",
            "90.0,  31.14, OBESIDADE_I",
            "105.0, 36.33, OBESIDADE_II",
            "120.0, 41.52, OBESIDADE_III",
    })
    @DisplayName("classifica o IMC pelas faixas da OMS")
    void classificaImcPelasFaixasDaOms(double peso, String imc, String classificacao) throws Exception {
        JsonNode a = avaliar(tokenA, marina, pesoEAltura(peso, 170), 201);

        assertThat(a.get("imc").decimalValue()).isEqualByComparingTo(imc);
        assertThat(a.get("classificacaoImc").get("valor").asText()).isEqualTo(classificacao);
    }

    @Test
    @DisplayName("não classifica adolescente pela faixa adulta")
    void naoClassificaAdolescentePelaFaixaAdulta() throws Exception {
        JsonNode a = avaliar(tokenA, pedro, pesoEAltura(55, 165), 201);

        // O IMC é calculado normalmente...
        assertThat(a.get("imc").decimalValue()).isEqualByComparingTo("20.20");
        // ...mas a faixa adulta não é aplicada, e o motivo é dito.
        assertThat(a.get("classificacaoImc").has("valor")).isFalse();
        assertThat(a.get("classificacaoImc").get("indisponivelPorque").asText())
                .contains("percentil");
    }

    // ------------------------------------------------- composição corporal

    @Test
    @DisplayName("estima gordura pelo protocolo de Faulkner")
    void estimaGorduraPorFaulkner() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,%s,"protocoloComposicao":"FAULKNER"}"""
                .formatted(LocalDate.now(), DOBRAS_FAULKNER);

        JsonNode composicao = avaliar(tokenA, marina, corpo, 201).get("composicao");

        // Faulkner: 85 mm x 0,153 + 5,783 = 18,788
        assertThat(composicao.get("percentualGordura").decimalValue()).isEqualByComparingTo("18.79");
        assertThat(composicao.get("massaGordaKg").decimalValue()).isEqualByComparingTo("13.15");
        assertThat(composicao.get("massaMagraKg").decimalValue()).isEqualByComparingTo("56.85");
        assertThat(composicao.get("protocolo").asText()).isEqualTo("FAULKNER");
    }

    @Test
    @DisplayName("recusa estimativa com dobra faltando, nomeando quais")
    void recusaEstimativaComDobraFaltando() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,
                 "dobras":{"TRICIPITAL":20,"ABDOMINAL":25},
                 "protocoloComposicao":"FAULKNER"}""".formatted(LocalDate.now());

        String erro = mvc.perform(post("/api/pacientes/" + marina + "/avaliacoes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(erro).contains("Subescapular").contains("Supra-ilíaca");
    }

    @Test
    @DisplayName("registra dobras sem estimar composição")
    void registraDobrasSemEstimar() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,%s}"""
                .formatted(LocalDate.now(), DOBRAS_FAULKNER);

        JsonNode a = avaliar(tokenA, marina, corpo, 201);

        assertThat(a.get("dobras").get("TRICIPITAL").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(a.has("composicao")).isFalse();
    }

    @Test
    @DisplayName("protocolo que depende de idade exige nascimento cadastrado")
    void protocoloDependenteDeIdadeExigeNascimento() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,
                 "dobras":{"TRICIPITAL":20,"SUPRAILIACA":22,"COXA":30},
                 "protocoloComposicao":"POLLOCK_3"}""".formatted(LocalDate.now());

        String erro = mvc.perform(post("/api/pacientes/" + semCadastro + "/avaliacoes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat(erro).contains("sexo");
    }

    // ------------------------------------------------ relação cintura-quadril

    @Test
    @DisplayName("calcula a relação cintura-quadril e classifica o risco")
    void calculaRelacaoCinturaQuadril() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,
                 "circunferencias":{"cintura":80,"quadril":100}}""".formatted(LocalDate.now());

        JsonNode a = avaliar(tokenA, marina, corpo, 201);

        assertThat(a.get("relacaoCinturaQuadril").decimalValue()).isEqualByComparingTo("0.80");
        // Mulher com RCQ 0,80 fica no limite: 0,80 já não é baixo risco.
        assertThat(a.get("riscoCardiometabolico").get("valor").asText()).isEqualTo("MODERADO");
    }

    @ParameterizedTest(name = "{0} com cintura {1} cm -> {2}")
    @CsvSource({
            // O quadril e sempre 100 cm, entao a cintura em centimetros e a
            // propria relacao em centesimos. Cobre as duas faixas de corte de
            // cada sexo, incluindo o primeiro valor de cada faixa.
            "FEMININO,   75, BAIXO",
            "FEMININO,   82, MODERADO",
            "FEMININO,   88, ALTO",
            "MASCULINO,  88, BAIXO",
            "MASCULINO,  95, MODERADO",
            "MASCULINO, 102, ALTO",
    })
    @DisplayName("classifica o risco pelos cortes de cada sexo")
    void classificaRiscoPorSexo(String sexo, int cintura, String risco) throws Exception {
        long paciente = "FEMININO".equals(sexo) ? marina : carlos;
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,
                 "circunferencias":{"cintura":%d,"quadril":100}}"""
                .formatted(LocalDate.now(), cintura);

        JsonNode a = avaliar(tokenA, paciente, corpo, 201);

        assertThat(a.get("riscoCardiometabolico").get("valor").asText()).isEqualTo(risco);
    }

    @Test
    @DisplayName("não classifica risco sem sexo informado")
    void naoClassificaRiscoSemSexo() throws Exception {
        String corpo = """
                {"data":"%s","circunferencias":{"cintura":80,"quadril":100}}"""
                .formatted(LocalDate.now());

        JsonNode a = avaliar(tokenA, semCadastro, corpo, 201);

        assertThat(a.get("relacaoCinturaQuadril").decimalValue()).isEqualByComparingTo("0.80");
        assertThat(a.get("riscoCardiometabolico").has("valor")).isFalse();
        assertThat(a.get("riscoCardiometabolico").get("indisponivelPorque").asText())
                .contains("sexo");
    }

    @Test
    @DisplayName("não calcula a relação com apenas uma medida")
    void naoCalculaRelacaoComUmaMedida() throws Exception {
        String corpo = """
                {"data":"%s","circunferencias":{"cintura":80}}""".formatted(LocalDate.now());

        JsonNode a = avaliar(tokenA, marina, corpo, 201);

        assertThat(a.get("circunferencias").get("cintura").decimalValue()).isEqualByComparingTo("80");
        assertThat(a.has("relacaoCinturaQuadril")).isFalse();
    }

    // ------------------------------------------------------- gasto energético

    @Test
    @DisplayName("estima o gasto energético por Mifflin-St Jeor")
    void estimaGastoEnergetico() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,
                 "equacaoGasto":"MIFFLIN_ST_JEOR","fatorAtividade":1.55}"""
                .formatted(LocalDate.now());

        JsonNode gasto = avaliar(tokenA, marina, corpo, 201).get("gastoEnergetico");

        // Mulheres: 10 x 70 + 6,25 x 170 - 5 x 34 - 161 = 1431,5
        assertThat(gasto.get("basalKcal").decimalValue()).isEqualByComparingTo("1431.50");
        assertThat(gasto.get("totalKcal").decimalValue()).isEqualByComparingTo("2218.83");
        assertThat(gasto.get("equacao").asText()).isEqualTo("MIFFLIN_ST_JEOR");
    }

    @Test
    @DisplayName("não estima gasto sem idade cadastrada")
    void naoEstimaGastoSemIdade() throws Exception {
        String corpo = """
                {"data":"%s","pesoKg":70,"alturaCm":170,"equacaoGasto":"MIFFLIN_ST_JEOR"}"""
                .formatted(LocalDate.now());

        mvc.perform(post("/api/pacientes/" + semCadastro + "/avaliacoes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------- evolução

    @Test
    @DisplayName("compara com a avaliação anterior e com a primeira")
    void comparaComAnteriorEComPrimeira() throws Exception {
        registrarPeso(75, 60);
        registrarPeso(72, 30);
        registrarPeso(70, 0);

        JsonNode evolucao = getJson(tokenA, "/api/pacientes/" + marina + "/evolucao");
        assertThat(evolucao.get("totalDeAvaliacoes").asInt()).isEqualTo(3);

        JsonNode ultimo = evolucao.get("pontos").get(2);
        assertThat(variacao(ultimo, "variacoesFrenteAAnterior", "pesoKg"))
                .isEqualByComparingTo("-2.00");
        assertThat(variacao(ultimo, "variacoesFrenteAPrimeira", "pesoKg"))
                .isEqualByComparingTo("-5.00");
    }

    @Test
    @DisplayName("não compara medida ausente em uma das avaliações")
    void naoComparaMedidaAusente() throws Exception {
        avaliar(tokenA, marina, """
                {"data":"%s","pesoKg":72,"alturaCm":170,"circunferencias":{"cintura":82}}"""
                .formatted(LocalDate.now().minusDays(30)), 201);
        avaliar(tokenA, marina, pesoEAltura(70, 170), 201);

        JsonNode ultimo = getJson(tokenA, "/api/pacientes/" + marina + "/evolucao")
                .get("pontos").get(1);

        JsonNode cintura = buscarVariacao(ultimo, "variacoesFrenteAAnterior", "circCintura");
        assertThat(cintura.get("comparavel").asBoolean()).isFalse();
        assertThat(cintura.has("diferenca")).isFalse();
        assertThat(cintura.get("observacao").asText()).contains("ausente");
    }

    @Test
    @DisplayName("não compara composição estimada por protocolos diferentes")
    void naoComparaProtocolosDiferentes() throws Exception {
        // Janeiro: Faulkner
        avaliar(tokenA, marina, """
                {"data":"%s","pesoKg":72,"alturaCm":170,%s,"protocoloComposicao":"FAULKNER"}"""
                .formatted(LocalDate.now().minusDays(60), DOBRAS_FAULKNER), 201);

        // Março: Pollock de 3 dobras
        avaliar(tokenA, marina, """
                {"data":"%s","pesoKg":70,"alturaCm":170,
                 "dobras":{"TRICIPITAL":18,"SUPRAILIACA":20,"COXA":28},
                 "protocoloComposicao":"POLLOCK_3"}"""
                .formatted(LocalDate.now()), 201);

        JsonNode ultimo = getJson(tokenA, "/api/pacientes/" + marina + "/evolucao")
                .get("pontos").get(1);

        JsonNode gordura = buscarVariacao(ultimo, "variacoesFrenteAAnterior", "percentualGordura");
        assertThat(gordura.get("comparavel").asBoolean()).isFalse();
        assertThat(gordura.has("diferenca")).isFalse();
        assertThat(gordura.get("observacao").asText()).contains("erro-padrão");

        // Mas o peso, que não depende de protocolo, continua comparável.
        assertThat(variacao(ultimo, "variacoesFrenteAAnterior", "pesoKg"))
                .isEqualByComparingTo("-2.00");
    }

    // ------------------------------------------------------------ isolamento

    @Test
    @DisplayName("um consultório não acessa avaliação de outro")
    void isolaAvaliacoesEntreContas() throws Exception {
        long id = avaliar(tokenA, marina, pesoEAltura(70, 170), 201).get("id").asLong();

        mvc.perform(get("/api/avaliacoes/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pacientes/" + marina + "/evolucao")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("lista os protocolos com as dobras que cada um exige")
    void listaProtocolos() throws Exception {
        JsonNode protocolos = getJson(tokenA, "/api/antropometria/protocolos");

        assertThat(protocolos).isNotEmpty();
        JsonNode faulkner = null;
        for (JsonNode p : protocolos) {
            if ("FAULKNER".equals(p.get("protocolo").asText())) {
                faulkner = p;
            }
        }
        assertThat(faulkner).isNotNull();
        assertThat(faulkner.get("dobrasFemininas").findValuesAsText("").size()).isZero();
        assertThat(json.convertValue(faulkner.get("dobrasFemininas"), java.util.List.class))
                .containsExactlyInAnyOrder("TRICIPITAL", "SUBESCAPULAR", "SUPRAILIACA", "ABDOMINAL");
    }

    // ------------------------------------------------------------- utilitários

    private void registrarPeso(double peso, int diasAtras) throws Exception {
        avaliar(tokenA, marina, """
                {"data":"%s","pesoKg":%s,"alturaCm":170}"""
                .formatted(LocalDate.now().minusDays(diasAtras), peso), 201);
    }

    private JsonNode buscarVariacao(JsonNode ponto, String lista, String medida) {
        for (JsonNode v : ponto.get(lista)) {
            if (medida.equals(v.get("medida").asText())) {
                return v;
            }
        }
        throw new AssertionError("variação não encontrada: " + medida);
    }

    private java.math.BigDecimal variacao(JsonNode ponto, String lista, String medida) {
        return buscarVariacao(ponto, lista, medida).get("diferenca").decimalValue();
    }
}
