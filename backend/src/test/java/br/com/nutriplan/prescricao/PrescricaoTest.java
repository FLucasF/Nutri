package br.com.nutriplan.prescricao;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrescricaoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenA;
    private String tokenB;
    private long pacienteA;
    private long arrozId;
    private long arrozMedidaId;
    private BigDecimal arrozKcalPor100;
    private BigDecimal arrozGramasPorColher;

    @BeforeEach
    void preparar() throws Exception {
        tokenA = cadastrarNutri("presA");
        tokenB = cadastrarNutri("presB");
        pacienteA = criarPaciente(tokenA, "Marina Duarte");

        JsonNode busca = getJson(tokenA, "/api/alimentos?termo=arroz,%20tipo%201,%20cozido&fonte=TACO");
        // fallback: parametro montado à mão pode não decodificar; refaz por param
        if (busca.get("content").isEmpty()) {
            busca = buscarAlimento(tokenA, "arroz, tipo 1, cozido");
        }
        arrozId = busca.get("content").get(0).get("id").asLong();

        JsonNode detalhe = getJson(tokenA, "/api/alimentos/" + arrozId);
        arrozKcalPor100 = detalhe.get("composicao").get("energiaKcal").decimalValue();
        for (JsonNode m : detalhe.get("medidas")) {
            if (m.get("descricao").asText().startsWith("colher de sopa")) {
                arrozMedidaId = m.get("id").asLong();
                arrozGramasPorColher = m.get("gramas").decimalValue();
                break;
            }
        }
        assertThat(arrozMedidaId).as("arroz cozido precisa de colher de sopa").isPositive();
    }

    // ------------------------------------------------------------------ apoio

    private String cadastrarNutri(String prefixo) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri " + prefixo,
                                "email", prefixo + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1",
                                "crn", "CRN-3 99999"))))
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

    private JsonNode buscarAlimento(String token, String termo) throws Exception {
        String corpo = mvc.perform(get("/api/alimentos")
                        .param("termo", termo).param("fonte", "TACO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private JsonNode getJson(String token, String url) throws Exception {
        String corpo = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private JsonNode postJson(String token, String url, String body, int esperado) throws Exception {
        String corpo = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return corpo.isBlank() ? null : json.readTree(corpo);
    }

    /** Plano com uma refeição e um item de arroz medido em colheres. */
    private String planoComArroz(int colheres) {
        return """
               {"titulo":"Plano de emagrecimento","pacienteId":%d,"metodo":"ALIMENTOS",
                "metaEnergiaKcal":2000,"modelo":false,
                "orientacoes":"Beba dois litros de água por dia.",
                "observacoesInternas":"Paciente relatou ansiedade noturna.",
                "refeicoes":[
                  {"nome":"Almoço","horario":"12:30","itens":[
                    {"alimentoId":%d,"medidaId":%d,"quantidade":%d}
                  ]}
                ]}""".formatted(pacienteA, arrozId, arrozMedidaId, colheres);
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("converte a porção em gramas e totaliza a refeição")
    void calculaTotaisAPartirDaPorcao() throws Exception {
        JsonNode plano = postJson(tokenA, "/api/prescricoes", planoComArroz(4), 201);

        JsonNode item = plano.get("refeicoes").get(0).get("itens").get(0);
        BigDecimal gramasEsperadas = arrozGramasPorColher.multiply(BigDecimal.valueOf(4));

        assertThat(item.get("gramas").decimalValue()).isEqualByComparingTo(gramasEsperadas);
        // A medida concorda com a quantidade: ver MedidaNoPluralTest.
        assertThat(item.get("porcao").asText()).isEqualTo("4 colheres de sopa cheias");

        BigDecimal kcalEsperada = arrozKcalPor100
                .multiply(gramasEsperadas)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);

        assertThat(plano.get("refeicoes").get(0).get("total").get("composicao")
                .get("energiaKcal").decimalValue()).isEqualByComparingTo(kcalEsperada);
        assertThat(plano.get("totalDoDia").get("composicao")
                .get("energiaKcal").decimalValue()).isEqualByComparingTo(kcalEsperada);
        assertThat(plano.get("totalDoDia").get("itensNoCalculo").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("o total informa quais nutrientes ficaram incompletos")
    void sinalizaCoberturaDoTotal() throws Exception {
        // Alimento próprio só com energia: nenhum outro nutriente informado.
        String proprio = """
                {"descricao":"Suplemento X","composicao":{"energiaKcal":300}}""";
        long suplementoId = postJson(tokenA, "/api/alimentos", proprio, 201).get("id").asLong();

        String corpo = """
               {"titulo":"Plano misto","pacienteId":%d,"metodo":"ALIMENTOS","modelo":false,
                "refeicoes":[{"nome":"Café","itens":[
                   {"alimentoId":%d,"medidaId":%d,"quantidade":2},
                   {"alimentoId":%d,"quantidade":50}
                ]}]}""".formatted(pacienteA, arrozId, arrozMedidaId, suplementoId);

        JsonNode plano = postJson(tokenA, "/api/prescricoes", corpo, 201);
        JsonNode total = plano.get("totalDoDia");

        assertThat(total.get("itensNoCalculo").asInt()).isEqualTo(2);
        // Proteína veio só do arroz: o total existe, mas subestima.
        assertThat(total.get("nutrientesIncompletos").findValuesAsText("")).isNotNull();
        var incompletos = json.convertValue(total.get("nutrientesIncompletos"), java.util.List.class);
        assertThat(incompletos).contains("proteinaG");
        assertThat(total.get("confiavel").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("plano qualitativo não inventa quantidade")
    void planoQualitativoNaoQuantifica() throws Exception {
        String corpo = """
               {"titulo":"Orientação inicial","pacienteId":%d,"metodo":"QUALITATIVO","modelo":false,
                "refeicoes":[{"nome":"Jantar","itens":[
                   {"descricao":"Salada de folhas à vontade"},
                   {"alimentoId":%d,"medidaId":%d,"quantidade":3}
                ]}]}""".formatted(pacienteA, arrozId, arrozMedidaId);

        JsonNode plano = postJson(tokenA, "/api/prescricoes", corpo, 201);
        JsonNode itens = plano.get("refeicoes").get(0).get("itens");

        assertThat(itens.get(0).get("porcao").asText()).isEqualTo("a vontade");
        // Mesmo tendo vindo quantidade, o método qualitativo não a registra.
        assertThat(itens.get(1).has("gramas")).isFalse();
        assertThat(plano.get("totalDoDia").get("itensNoCalculo").asInt()).isZero();
        assertThat(plano.get("totalDoDia").get("itensForaDoCalculo").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("substituições só existem no método por equivalentes")
    void equivalentesExigemMetodoCompativel() throws Exception {
        String corpo = """
               {"titulo":"Plano com troca","pacienteId":%d,"metodo":"ALIMENTOS","modelo":false,
                "refeicoes":[{"nome":"Café","itens":[
                   {"alimentoId":%d,"medidaId":%d,"quantidade":2,
                    "equivalentes":[{"descricao":"1 tapioca média","quantidade":60}]}
                ]}]}""".formatted(pacienteA, arrozId, arrozMedidaId);

        postJson(tokenA, "/api/prescricoes", corpo, 422);

        JsonNode ok = postJson(tokenA, "/api/prescricoes",
                corpo.replace("\"metodo\":\"ALIMENTOS\"", "\"metodo\":\"EQUIVALENTES\""), 201);
        JsonNode substituicoes = ok.get("refeicoes").get(0).get("itens").get(0).get("equivalentes");
        assertThat(substituicoes).hasSize(1);
        assertThat(substituicoes.get(0).get("porcao").asText()).isEqualTo("60 g");
    }

    @Test
    @DisplayName("o link só serve plano publicado")
    void linkPublicoExigePublicacao() throws Exception {
        JsonNode plano = postJson(tokenA, "/api/prescricoes", planoComArroz(4), 201);
        String link = plano.get("identificadorPublico").asText();

        // Rascunho responde como inexistente, sem revelar trabalho em curso.
        mvc.perform(get("/api/publico/planos/" + link)).andExpect(status().isNotFound());

        postJson(tokenA, "/api/prescricoes/" + plano.get("id").asLong() + "/publicar", "", 200);

        mvc.perform(get("/api/publico/planos/" + link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Plano de emagrecimento"))
                .andExpect(jsonPath("$.pacienteNome").value("Marina"))
                .andExpect(jsonPath("$.nutricionistaCrn").value("CRN-3 99999"))
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.refeicoes[0].itens[0].porcao").value("4 colheres de sopa cheias"));
    }

    @Test
    @DisplayName("o link do paciente nunca expõe anotação interna")
    void linkPublicoNaoVazaDadoInterno() throws Exception {
        JsonNode plano = postJson(tokenA, "/api/prescricoes", planoComArroz(4), 201);
        postJson(tokenA, "/api/prescricoes/" + plano.get("id").asLong() + "/publicar", "", 200);

        String corpo = mvc.perform(get("/api/publico/planos/"
                        + plano.get("identificadorPublico").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("ansiedade noturna");
        assertThat(corpo).doesNotContain("observacoesInternas");
        assertThat(corpo).doesNotContain("contaId");
        // A orientação ao paciente, essa sim, aparece.
        assertThat(corpo).contains("dois litros de água");
    }

    @Test
    @DisplayName("regerar o link invalida o endereço já entregue")
    void regerarLinkInvalidaOAnterior() throws Exception {
        JsonNode plano = postJson(tokenA, "/api/prescricoes", planoComArroz(4), 201);
        long id = plano.get("id").asLong();
        postJson(tokenA, "/api/prescricoes/" + id + "/publicar", "", 200);

        String antigo = plano.get("identificadorPublico").asText();
        mvc.perform(get("/api/publico/planos/" + antigo)).andExpect(status().isOk());

        String novo = postJson(tokenA, "/api/prescricoes/" + id + "/regerar-link", "", 200)
                .get("identificadorPublico").asText();

        assertThat(novo).isNotEqualTo(antigo);
        mvc.perform(get("/api/publico/planos/" + antigo)).andExpect(status().isNotFound());
        mvc.perform(get("/api/publico/planos/" + novo)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("corrigir a porção não reescreve plano já prescrito")
    void pesoPrescritoFicaCongelado() throws Exception {
        // Alimento próprio, para poder alterar a porção depois.
        String proprio = """
                {"descricao":"Granola da casa","composicao":{"energiaKcal":400},
                 "medidas":[{"descricao":"colher de sopa","gramas":15,"padrao":true}]}""";
        JsonNode alimento = postJson(tokenA, "/api/alimentos", proprio, 201);
        long alimentoId = alimento.get("id").asLong();
        long medidaId = alimento.get("medidas").get(0).get("id").asLong();

        String corpo = """
               {"titulo":"Plano granola","pacienteId":%d,"metodo":"ALIMENTOS","modelo":false,
                "refeicoes":[{"nome":"Café","itens":[
                   {"alimentoId":%d,"medidaId":%d,"quantidade":2}]}]}"""
                .formatted(pacienteA, alimentoId, medidaId);

        JsonNode plano = postJson(tokenA, "/api/prescricoes", corpo, 201);
        long planoId = plano.get("id").asLong();
        assertThat(plano.get("refeicoes").get(0).get("itens").get(0).get("gramas").decimalValue())
                .isEqualByComparingTo("30");

        // O consultório passa a considerar a colher com 20 g.
        mvc.perform(put("/api/alimentos/" + alimentoId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"Granola da casa","composicao":{"energiaKcal":400},
                                 "medidas":[{"descricao":"colher de sopa","gramas":20,"padrao":true}]}"""))
                .andExpect(status().isOk());

        // O plano já prescrito continua com as 30 g originais.
        JsonNode depois = getJson(tokenA, "/api/prescricoes/" + planoId);
        assertThat(depois.get("refeicoes").get(0).get("itens").get(0).get("gramas").decimalValue())
                .isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("um consultório não acessa plano de outro")
    void isolaPlanosEntreContas() throws Exception {
        JsonNode plano = postJson(tokenA, "/api/prescricoes", planoComArroz(4), 201);
        long id = plano.get("id").asLong();

        mvc.perform(get("/api/prescricoes/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/prescricoes/" + id + "/publicar")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/prescricoes").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("não publica plano vazio")
    void naoPublicaPlanoSemItem() throws Exception {
        String vazio = """
               {"titulo":"Ainda vazio","pacienteId":%d,"metodo":"ALIMENTOS","modelo":false,
                "refeicoes":[]}""".formatted(pacienteA);
        long id = postJson(tokenA, "/api/prescricoes", vazio, 201).get("id").asLong();

        postJson(tokenA, "/api/prescricoes/" + id + "/publicar", "", 422);
    }

    @Test
    @DisplayName("modelo não tem paciente e vira plano ao ser duplicado")
    void modeloVirouPlanoAoDuplicar() throws Exception {
        String modelo = """
               {"titulo":"Modelo low carb","metodo":"ALIMENTOS","modelo":true,
                "refeicoes":[{"nome":"Almoço","itens":[
                   {"alimentoId":%d,"medidaId":%d,"quantidade":3}]}]}"""
                .formatted(arrozId, arrozMedidaId);

        JsonNode salvo = postJson(tokenA, "/api/prescricoes", modelo, 201);
        assertThat(salvo.get("modelo").asBoolean()).isTrue();
        assertThat(salvo.has("pacienteId")).isFalse();

        JsonNode copia = postJson(tokenA,
                "/api/prescricoes/" + salvo.get("id").asLong() + "/duplicar?pacienteId=" + pacienteA,
                "", 200);

        assertThat(copia.get("pacienteId").asLong()).isEqualTo(pacienteA);
        assertThat(copia.get("status").asText()).isEqualTo("RASCUNHO");
        assertThat(copia.get("refeicoes").get(0).get("itens")).hasSize(1);
    }

    @Test
    @DisplayName("plano encerrado não aceita edição")
    void planoEncerradoNaoEditavel() throws Exception {
        JsonNode plano = postJson(tokenA, "/api/prescricoes", planoComArroz(4), 201);
        long id = plano.get("id").asLong();
        postJson(tokenA, "/api/prescricoes/" + id + "/publicar", "", 200);
        postJson(tokenA, "/api/prescricoes/" + id + "/encerrar", "", 200);

        mvc.perform(put("/api/prescricoes/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(planoComArroz(6)))
                .andExpect(status().isUnprocessableEntity());

        // Continua visível para o paciente, marcado como encerrado.
        mvc.perform(get("/api/publico/planos/" + plano.get("identificadorPublico").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrado").value(true))
                .andExpect(jsonPath("$.vigente").value(false));
    }

    @Test
    @DisplayName("calcula a distribuição de macronutrientes")
    void calculaDistribuicaoDeMacros() throws Exception {
        // 100 g com 10 g P, 20 g C, 10 g L -> 40 + 80 + 90 = 210 kcal
        String proprio = """
                {"descricao":"Refeição controlada",
                 "composicao":{"energiaKcal":210,"proteinaG":10,"carboidratoG":20,"lipideosG":10}}""";
        long id = postJson(tokenA, "/api/alimentos", proprio, 201).get("id").asLong();

        String corpo = """
               {"titulo":"Plano macros","pacienteId":%d,"metodo":"ALIMENTOS","modelo":false,
                "metaEnergiaKcal":420,
                "refeicoes":[{"nome":"Almoço","itens":[{"alimentoId":%d,"quantidade":100}]}]}"""
                .formatted(pacienteA, id);

        JsonNode total = postJson(tokenA, "/api/prescricoes", corpo, 201).get("totalDoDia");
        JsonNode dist = total.get("distribuicao");

        assertThat(dist.get("proteinaPct").decimalValue()).isEqualByComparingTo("19.0");
        assertThat(dist.get("carboidratoPct").decimalValue()).isEqualByComparingTo("38.1");
        assertThat(dist.get("lipideoPct").decimalValue()).isEqualByComparingTo("42.9");
        assertThat(dist.get("energiaCalculadaKcal").decimalValue()).isEqualByComparingTo("210.0");
        // Prescrito 210 kcal para uma meta de 420: metade.
        assertThat(total.get("adequacaoEnergeticaPct").decimalValue()).isEqualByComparingTo("50.0");
    }
}
