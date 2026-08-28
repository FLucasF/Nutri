package br.com.nutriplan.alimento;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Receitas compostas (RF28).
 *
 * Os números aqui são conferidos contra a conta feita à mão, e não contra o que
 * o código devolve: uma receita errada por um fator de dois é exatamente o tipo
 * de defeito que passa despercebido num teste que só verifica se "calculou".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceitaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;

    @BeforeEach
    void autenticar() throws Exception {
        token = cadastrar("receita");
        tokenB = cadastrar("receitaB");
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

    /** Alimento próprio com composição conhecida, para a conta fechar exata. */
    private long alimento(String tk, String descricao, int kcal, int proteina, int carboidrato)
            throws Exception {
        String corpo = """
                {"descricao":"%s","composicao":{"energiaKcal":%d,"proteinaG":%d,"carboidratoG":%d}}"""
                .formatted(descricao, kcal, proteina, carboidrato);
        String resposta = mvc.perform(post("/api/alimentos")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asLong();
    }

    private JsonNode criar(String tk, String corpo, int esperado) throws Exception {
        String resposta = mvc.perform(post("/api/receitas")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return resposta.isEmpty() ? null : json.readTree(resposta);
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        String corpo = mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    // ------------------------------------------------------------------ cálculo

    @Test
    @DisplayName("a composição da receita é por 100 g da preparação pronta")
    void composicaoPor100gDaPreparacao() throws Exception {
        long arroz = alimento(token, "Arroz cru", 360, 7, 78);

        // 100 g de arroz cru rendendo 250 g cozido: os 360 kcal continuam
        // existindo, mas agora diluídos em 250 g — 144 kcal por 100 g.
        JsonNode r = criar(token, """
                {"nome":"Arroz cozido da casa","rendimentoGramas":250,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}"""
                .formatted(arroz), 201);

        assertThat(r.get("composicaoPor100g").get("energiaKcal").decimalValue())
                .isEqualByComparingTo("144.000");
        assertThat(r.get("rendimentoEstimado").asBoolean()).isFalse();
        assertThat(r.get("pesoDosIngredientes").decimalValue()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("sem rendimento informado, a soma dos ingredientes é usada — e o resultado diz isso")
    void rendimentoEstimadoQuandoNaoInformado() throws Exception {
        long a = alimento(token, "Ingrediente A", 100, 10, 0);
        long b = alimento(token, "Ingrediente B", 300, 0, 50);

        JsonNode r = criar(token, """
                {"nome":"Mistura","ingredientes":[
                  {"alimentoId":%d,"quantidade":100},
                  {"alimentoId":%d,"quantidade":100}]}"""
                .formatted(a, b), 201);

        // 100 + 300 kcal em 200 g = 200 kcal por 100 g.
        assertThat(r.get("composicaoPor100g").get("energiaKcal").decimalValue())
                .isEqualByComparingTo("200.000");
        assertThat(r.get("rendimentoEstimado").asBoolean())
                .as("o peso final foi presumido, e a resposta precisa admitir isso")
                .isTrue();
        assertThat(r.get("rendimentoGramas").decimalValue()).isEqualByComparingTo("200.000");
    }

    @Test
    @DisplayName("o ingrediente pode entrar por medida caseira")
    void ingredientePorMedidaCaseira() throws Exception {
        long oleo = alimento(token, "Oleo", 900, 0, 0);
        String medida = mvc.perform(post("/api/alimentos/" + oleo + "/medidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"colher de sopa","gramas":8,"padrao":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long medidaId = json.readTree(medida).get("id").asLong();

        JsonNode r = criar(token, """
                {"nome":"Refogado","rendimentoGramas":100,
                 "ingredientes":[{"alimentoId":%d,"medidaId":%d,"quantidade":2}]}"""
                .formatted(oleo, medidaId), 201);

        JsonNode ingrediente = r.get("ingredientes").get(0);
        assertThat(ingrediente.get("gramas").decimalValue()).isEqualByComparingTo("16.000");
        // A medida concorda com a quantidade, como em toda porção do sistema.
        assertThat(ingrediente.get("quantidade").asText()).isEqualTo("2 colheres de sopa");
    }

    @Test
    @DisplayName("nutriente ausente em parte dos ingredientes vira piso, e é sinalizado")
    void nutrienteIncompletoEhSinalizado() throws Exception {
        long comFibra = alimento(token, "Farinha integral", 340, 10, 70);
        mvc.perform(put("/api/alimentos/" + comFibra)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"Farinha integral",
                                 "composicao":{"energiaKcal":340,"proteinaG":10,"carboidratoG":70,
                                               "fibraG":9}}"""))
                .andExpect(status().isOk());
        long semFibra = alimento(token, "Fermento", 100, 0, 20);

        JsonNode r = criar(token, """
                {"nome":"Massa","rendimentoGramas":200,
                 "ingredientes":[
                   {"alimentoId":%d,"quantidade":100},
                   {"alimentoId":%d,"quantidade":100}]}"""
                .formatted(comFibra, semFibra), 201);

        assertThat(r.get("nutrientesIncompletos").toString()).contains("fibraG");
        // A fibra da farinha continua contando: somar tratando ausente como
        // zero seria pior, porque produziria um número que aparenta exatidão.
        assertThat(r.get("composicaoPor100g").get("fibraG").decimalValue())
                .isEqualByComparingTo("4.500");
    }

    @Test
    @DisplayName("nutriente que nenhum ingrediente determina permanece ausente")
    void nutrienteAusenteEmTodosNaoViraZero() throws Exception {
        long a = alimento(token, "Simples", 100, 5, 10);

        JsonNode r = criar(token, """
                {"nome":"So um ingrediente","rendimentoGramas":100,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}""".formatted(a), 201);

        JsonNode composicao = r.get("composicaoPor100g");
        assertThat(composicao.has("sodioMg") && !composicao.get("sodioMg").isNull())
                .as("sodio nao determinado em nenhum ingrediente nao pode virar zero")
                .isFalse();
        assertThat(r.get("nutrientesIncompletos").toString())
                .as("ausente em todos nao e incompleto: e simplesmente nao determinado")
                .doesNotContain("sodioMg");
    }

    // -------------------------------------------------------------- porções

    @Test
    @DisplayName("a receita nasce com as porções derivadas do rendimento")
    void geraPorcoesDerivadas() throws Exception {
        long a = alimento(token, "Base", 200, 5, 30);

        JsonNode r = criar(token, """
                {"nome":"Bolo","rendimentoGramas":800,"porcoes":8,
                 "ingredientes":[{"alimentoId":%d,"quantidade":500}]}""".formatted(a), 201);

        assertThat(r.get("gramasPorPorcao").decimalValue()).isEqualByComparingTo("100.000");

        JsonNode detalhe = getJson(token, "/api/alimentos/" + r.get("id").asLong());
        var descricoes = detalhe.get("medidas").findValuesAsText("descricao");
        assertThat(descricoes).contains("porção", "receita inteira");
    }

    @Test
    @DisplayName("mudar o rendimento refaz as porções em vez de acumular")
    void porcoesNaoAcumulam() throws Exception {
        long a = alimento(token, "Base", 200, 5, 30);
        long id = criar(token, """
                {"nome":"Bolo","rendimentoGramas":800,"porcoes":8,
                 "ingredientes":[{"alimentoId":%d,"quantidade":500}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(put("/api/receitas/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Bolo","rendimentoGramas":600,"porcoes":6,
                                 "ingredientes":[{"alimentoId":%d,"quantidade":500}]}"""
                                .formatted(a)))
                .andExpect(status().isOk());

        JsonNode detalhe = getJson(token, "/api/alimentos/" + id);
        var descricoes = detalhe.get("medidas").findValuesAsText("descricao");
        assertThat(descricoes).containsOnlyOnce("porção");
        assertThat(descricoes).containsOnlyOnce("receita inteira");
    }

    // ------------------------------------------------------- integração ao acervo

    @Test
    @DisplayName("a receita aparece na busca de alimentos, com a fonte que a identifica")
    void receitaEntraNoAcervo() throws Exception {
        long a = alimento(token, "Base", 200, 5, 30);
        criar(token, """
                {"nome":"Panqueca de aveia","rendimentoGramas":300,
                 "ingredientes":[{"alimentoId":%d,"quantidade":300}]}""".formatted(a), 201);

        JsonNode busca = mvc.perform(get("/api/alimentos")
                        .param("termo", "panqueca").param("fonte", "RECEITA")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .transform(this::ler);

        assertThat(busca.get("content")).isNotEmpty();
        assertThat(busca.get("content").get(0).get("descricao").asText())
                .isEqualTo("Panqueca de aveia");
    }

    private JsonNode ler(String corpo) {
        try {
            return json.readTree(corpo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("uma receita pode ser ingrediente de outra")
    void receitaDentroDeReceita() throws Exception {
        long a = alimento(token, "Base", 400, 10, 50);
        long refogado = criar(token, """
                {"nome":"Refogado","rendimentoGramas":100,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}""".formatted(a), 201)
                .get("id").asLong();

        JsonNode torta = criar(token, """
                {"nome":"Torta","rendimentoGramas":200,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100},
                                 {"alimentoId":%d,"quantidade":100}]}"""
                .formatted(refogado, a), 201);

        // 400 kcal do refogado + 400 kcal da base, em 200 g = 400 por 100 g.
        assertThat(torta.get("composicaoPor100g").get("energiaKcal").decimalValue())
                .isEqualByComparingTo("400.000");
    }

    @Test
    @DisplayName("uma receita não pode ser ingrediente dela mesma")
    void recusaCicloDireto() throws Exception {
        long a = alimento(token, "Base", 200, 5, 30);
        long id = criar(token, """
                {"nome":"Sopa","rendimentoGramas":300,
                 "ingredientes":[{"alimentoId":%d,"quantidade":300}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(put("/api/receitas/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Sopa","rendimentoGramas":300,
                                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}"""
                                .formatted(id)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- validação

    @Test
    @DisplayName("receita sem ingrediente é recusada")
    void recusaReceitaSemIngrediente() throws Exception {
        criar(token, """
                {"nome":"Vazia","ingredientes":[]}""", 400);
    }

    @Test
    @DisplayName("ingrediente de outro consultório não é encontrado")
    void naoUsaAlimentoDeOutroConsultorio() throws Exception {
        long doOutro = alimento(tokenB, "Segredo do outro", 100, 1, 1);

        criar(token, """
                {"nome":"Tentativa","rendimentoGramas":100,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}""".formatted(doOutro), 404);
    }

    @Test
    @DisplayName("um consultório não abre a receita de outro")
    void isolaReceitasEntreContas() throws Exception {
        long a = alimento(token, "Base", 200, 5, 30);
        long id = criar(token, """
                {"nome":"Minha receita","rendimentoGramas":100,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(get("/api/receitas/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        assertThat(getJson(tokenB, "/api/receitas").get("content")).isEmpty();
    }

    @Test
    @DisplayName("remover inativa: o plano que a prescreveu continua de pé")
    void removerInativa() throws Exception {
        long a = alimento(token, "Base", 200, 5, 30);
        long id = criar(token, """
                {"nome":"Descartavel","rendimentoGramas":100,
                 "ingredientes":[{"alimentoId":%d,"quantidade":100}]}""".formatted(a), 201)
                .get("id").asLong();

        mvc.perform(delete("/api/receitas/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(getJson(token, "/api/receitas").get("content")).isEmpty();
    }
}
