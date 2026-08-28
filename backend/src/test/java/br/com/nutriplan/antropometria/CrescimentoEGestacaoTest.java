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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avaliação de criança, adolescente e gestante (RF69–RF69b).
 *
 * Até aqui o sistema recusava classificar quem tem menos de 20 anos — correto,
 * porque a faixa adulta do IMC não se aplica a quem ainda cresce, e resolvia
 * pela metade.
 *
 * Os números conferidos são os das curvas publicadas pela OMS: um valor igual à
 * mediana tem escore-z zero, e é isso que prova que os parâmetros LMS foram
 * carregados e aplicados na idade certa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrescimentoEGestacaoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void autenticar() throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Crescimento",
                                "email", "cresc" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(corpo).get("token").asText();
    }

    /** Paciente com idade exata em meses na data de hoje. */
    private long paciente(String nome, String sexo, int meses) throws Exception {
        var dados = new java.util.HashMap<String, Object>();
        dados.put("nome", nome);
        if (sexo != null) {
            dados.put("sexo", sexo);
        }
        dados.put("dataNascimento", LocalDate.now().minusMonths(meses).minusDays(1).toString());
        return json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(dados)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long pacienteSemNascimento(String nome) throws Exception {
        return json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", nome))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode avaliar(long paciente, String corpo) throws Exception {
        return json.readTree(mvc.perform(post("/api/pacientes/" + paciente + "/avaliacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode indicador(JsonNode avaliacao, String nome) {
        JsonNode crescimento = avaliacao.get("crescimentoInfantil");
        assertThat(crescimento).isNotNull();
        assertThat(crescimento.get("valor"))
                .as("crescimento indisponivel: %s", crescimento.get("indisponivelPorque"))
                .isNotNull();
        for (JsonNode i : crescimento.get("valor").get("indicadores")) {
            if (i.get("indicador").asText().equals(nome)) {
                return i;
            }
        }
        throw new AssertionError("indicador ausente: " + nome);
    }

    // ------------------------------------------------------------- escore-z

    @Test
    @DisplayName("valor igual à mediana da OMS tem escore-z zero")
    void medianaTemEscoreZero() throws Exception {
        // Menino de 120 meses: a mediana do IMC é 16,4433 kg/m².
        // Com 1,40 m, o peso que produz esse IMC é 16,4433 × 1,96 = 32,229 kg.
        long pedro = paciente("Pedro", "MASCULINO", 120);
        JsonNode a = avaliar(pedro, """
                {"data":"%s","pesoKg":32.229,"alturaCm":140}""".formatted(LocalDate.now()));

        JsonNode imc = indicador(a, "IMC_PARA_IDADE");
        assertThat(imc.get("escoreZ").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(imc.get("classificacao").asText()).isEqualTo("EUTROFIA");
        assertThat(imc.get("referencia").asText()).contains("2007");
    }

    @Test
    @DisplayName("a estatura mediana da OMS também dá escore zero")
    void estaturaMediana() throws Exception {
        // Menina de 60 meses: a estatura mediana é 109,4189 cm.
        long ana = paciente("Ana", "FEMININO", 60);
        JsonNode a = avaliar(ana, """
                {"data":"%s","pesoKg":18,"alturaCm":109.4189}""".formatted(LocalDate.now()));

        JsonNode estatura = indicador(a, "ESTATURA_PARA_IDADE");
        assertThat(estatura.get("escoreZ").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(estatura.get("classificacao").asText()).isEqualTo("ESTATURA_ADEQUADA");
        // Aos 60 meses vale o padrão de 2006, e não a referência de 2007.
        assertThat(estatura.get("referencia").asText()).contains("2006");
    }

    @Test
    @DisplayName("baixa estatura é reconhecida")
    void baixaEstatura() throws Exception {
        long ana = paciente("Ana", "FEMININO", 60);
        // Bem abaixo da mediana de 109,4 cm.
        JsonNode a = avaliar(ana, """
                {"data":"%s","pesoKg":14,"alturaCm":97}""".formatted(LocalDate.now()));

        JsonNode estatura = indicador(a, "ESTATURA_PARA_IDADE");
        assertThat(estatura.get("escoreZ").decimalValue()).isLessThan(new java.math.BigDecimal("-2"));
        assertThat(estatura.get("classificacao").asText()).contains("BAIXA_ESTATURA");
        assertThat(estatura.get("exigeAtencao").asBoolean()).isTrue();
    }

    // --------------------------------------------------- faixas por idade

    @ParameterizedTest(name = "escore-z {1} aos {0} meses -> {2}")
    @CsvSource({
            // Até cinco anos, acima de +1 é risco de sobrepeso; depois, já é
            // sobrepeso. Classificar um adolescente pela faixa de criança
            // subestimaria o quadro em um grau inteiro.
            "48,  1.5, RISCO_DE_SOBREPESO",
            "48,  2.5, SOBREPESO",
            "48,  3.5, OBESIDADE",
            "120, 1.5, SOBREPESO",
            "120, 2.5, OBESIDADE",
            "120, 3.5, OBESIDADE_GRAVE",
    })
    @DisplayName("a faixa de corte do IMC muda aos cinco anos")
    void faixaMudaAosCincoAnos(int meses, double escoreAlvo, String esperado) throws Exception {
        long crianca = paciente("Crianca " + meses + "-" + escoreAlvo, "MASCULINO", meses);

        // Altura fixa; o peso é escolhido para atingir o escore-z alvo.
        double alturaM = 1.10;
        double imcAlvo = imcParaEscore(meses, escoreAlvo);
        double peso = imcAlvo * alturaM * alturaM;

        JsonNode a = avaliar(crianca, """
                {"data":"%s","pesoKg":%.3f,"alturaCm":%.1f}"""
                .formatted(LocalDate.now(), peso, alturaM * 100));

        assertThat(indicador(a, "IMC_PARA_IDADE").get("classificacao").asText())
                .isEqualTo(esperado);
    }

    /**
     * IMC que produz o escore-z pedido, pela fórmula LMS invertida.
     *
     * Os parâmetros vêm da tabela da OMS para meninos, nas duas idades usadas
     * acima. Estão escritos aqui de propósito: se o teste lesse a mesma tabela
     * que o código, os dois errariam juntos.
     */
    private double imcParaEscore(int meses, double z) {
        double l;
        double m;
        double s;
        if (meses == 48) {           // OMS 2006, meninos, 48 meses
            l = -0.7387;
            m = 15.7133;
            s = 0.07914;
        } else {                     // OMS 2007, meninos, 120 meses
            l = -1.0630;
            m = 16.4433;
            s = 0.12988;
        }
        if (z > 3) {
            double sd3 = m * Math.pow(1 + l * s * 3, 1 / l);
            double sd2 = m * Math.pow(1 + l * s * 2, 1 / l);
            return sd3 + (z - 3) * (sd3 - sd2);
        }
        return m * Math.pow(1 + l * s * z, 1 / l);
    }

    // ------------------------------------------------------------ recusas

    @Test
    @DisplayName("acima de 19 anos não há curva, e o motivo é dito")
    void adultoNaoTemCurva() throws Exception {
        long adulto = paciente("Adulto", "MASCULINO", 300);
        JsonNode a = avaliar(adulto, """
                {"data":"%s","pesoKg":70,"alturaCm":175}""".formatted(LocalDate.now()));

        JsonNode crescimento = a.get("crescimentoInfantil");
        assertThat(crescimento.has("valor") && !crescimento.get("valor").isNull()).isFalse();
        assertThat(crescimento.get("indisponivelPorque").asText()).contains("19 anos");
    }

    @Test
    @DisplayName("sem sexo informado não há curva: elas são específicas por sexo")
    void semSexoNaoClassifica() throws Exception {
        long anonimo = paciente("Sem sexo", null, 120);
        JsonNode a = avaliar(anonimo, """
                {"data":"%s","pesoKg":32,"alturaCm":140}""".formatted(LocalDate.now()));

        assertThat(a.get("crescimentoInfantil").get("indisponivelPorque").asText())
                .contains("sexo");
    }

    @Test
    @DisplayName("sem data de nascimento não há idade, e sem idade não há curva")
    void semNascimentoNaoClassifica() throws Exception {
        long anonimo = pacienteSemNascimento("Sem nascimento");
        JsonNode a = avaliar(anonimo, """
                {"data":"%s","pesoKg":32,"alturaCm":140}""".formatted(LocalDate.now()));

        assertThat(a.get("crescimentoInfantil").get("indisponivelPorque").asText())
                .contains("nascimento");
    }

    // ----------------------------------------------------------- gestação

    @Test
    @DisplayName("ganho dentro do esperado para a semana")
    void ganhoAdequado() throws Exception {
        long marina = paciente("Marina", "FEMININO", 34 * 12);
        // IMC pré-gestacional 22,5 (eutrofia): 61,2 kg com 1,65 m.
        // Na 20ª semana: 0,5 a 2,0 kg do 1º trimestre + 7 semanas × 0,35–0,50.
        // Faixa esperada: 2,95 a 5,50 kg. Ganho de 4 kg cai dentro.
        JsonNode a = avaliar(marina, """
                {"data":"%s","pesoKg":65.2,"alturaCm":165,
                 "semanaGestacional":20,"pesoPreGestacionalKg":61.2}"""
                .formatted(LocalDate.now()));

        JsonNode g = a.get("gestacao").get("valor");
        assertThat(g.get("faixa").asText()).isEqualTo("EUTROFIA");
        assertThat(g.get("ganhoAteAgora").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(g.get("esperadoMin").decimalValue()).isEqualByComparingTo("2.95");
        assertThat(g.get("esperadoMax").decimalValue()).isEqualByComparingTo("5.50");
        assertThat(g.get("situacao").asText()).isEqualTo("ADEQUADO");
    }

    @Test
    @DisplayName("ganho acima do esperado é sinalizado")
    void ganhoAcima() throws Exception {
        long marina = paciente("Marina", "FEMININO", 34 * 12);
        JsonNode a = avaliar(marina, """
                {"data":"%s","pesoKg":74.2,"alturaCm":165,
                 "semanaGestacional":20,"pesoPreGestacionalKg":61.2}"""
                .formatted(LocalDate.now()));

        assertThat(a.get("gestacao").get("valor").get("situacao").asText()).isEqualTo("ACIMA");
    }

    @Test
    @DisplayName("a faixa vem do IMC pré-gestacional, e não do atual")
    void faixaVemDoImcAnterior() throws Exception {
        long marina = paciente("Marina", "FEMININO", 34 * 12);
        // Pré-gestacional 82 kg em 1,65 m: IMC 30,1, faixa de obesidade —
        // ganho recomendado de 5 a 9 kg, e não os 11,5 a 16 da eutrofia.
        JsonNode a = avaliar(marina, """
                {"data":"%s","pesoKg":86,"alturaCm":165,
                 "semanaGestacional":30,"pesoPreGestacionalKg":82}"""
                .formatted(LocalDate.now()));

        JsonNode g = a.get("gestacao").get("valor");
        assertThat(g.get("faixa").asText()).isEqualTo("OBESIDADE");
        assertThat(g.get("ganhoTotalRecomendadoMin").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(g.get("ganhoTotalRecomendadoMax").decimalValue()).isEqualByComparingTo("9.0");
    }

    @Test
    @DisplayName("sem o peso pré-gestacional não classifica, e diz por quê")
    void semPesoPreGestacional() throws Exception {
        long marina = paciente("Marina", "FEMININO", 34 * 12);
        JsonNode a = avaliar(marina, """
                {"data":"%s","pesoKg":65,"alturaCm":165,"semanaGestacional":20}"""
                .formatted(LocalDate.now()));

        JsonNode g = a.get("gestacao");
        assertThat(g.has("valor") && !g.get("valor").isNull()).isFalse();
        assertThat(g.get("indisponivelPorque").asText()).contains("pre-gestacional");
    }

    @Test
    @DisplayName("avaliação sem semana gestacional não traz o bloco")
    void semGestacaoNaoTrazOBloco() throws Exception {
        long marina = paciente("Marina", "FEMININO", 34 * 12);
        JsonNode a = avaliar(marina, """
                {"data":"%s","pesoKg":65,"alturaCm":165}""".formatted(LocalDate.now()));

        assertThat(a.has("gestacao") && !a.get("gestacao").isNull()).isFalse();
    }

    @Test
    @DisplayName("semana gestacional fora de 1 a 42 é recusada")
    void semanaInvalida() throws Exception {
        long marina = paciente("Marina", "FEMININO", 34 * 12);
        mvc.perform(post("/api/pacientes/" + marina + "/avaliacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"%s","pesoKg":65,"alturaCm":165,"semanaGestacional":45}"""
                                .formatted(LocalDate.now())))
                .andExpect(status().isBadRequest());
    }
}
