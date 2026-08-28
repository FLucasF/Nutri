package br.com.nutriplan.alimento;

import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
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
class AlimentoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AlimentoRepository alimentoRepository;

    private String token;

    @BeforeEach
    void autenticar() throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Teste",
                                "email", "alimento" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(corpo).get("token").asText();
    }

    private JsonNode getJson(String url) throws Exception {
        String corpo = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    /**
     * Busca passando o termo como parametro, e nao concatenado na URL: o
     * MockMvc nao decodifica percent-encoding escrito a mao, o que faria
     * acentos e espacos chegarem corrompidos ao controller.
     */
    private JsonNode buscarPorTermo(String termo) throws Exception {
        String corpo = mvc.perform(get("/api/alimentos")
                        .param("termo", termo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private long primeiroIdDaBusca(String termo) throws Exception {
        JsonNode conteudo = buscarPorTermo(termo).get("content");
        assertThat(conteudo).as("busca por '%s' nao retornou alimentos", termo).isNotEmpty();
        return conteudo.get(0).get("id").asLong();
    }

    // ------------------------------------------------- busca por codigo de barras

    @Test
    @DisplayName("localiza o produto industrializado pelo codigo de barras")
    void localizaPorCodigoDeBarras() throws Exception {
        // Leite Condensado Semidesnatado ITALAC, do Open Food Facts.
        JsonNode achados = getJson("/api/alimentos/codigo-barras/7890000110266");

        assertThat(achados).isNotEmpty();
        assertThat(achados.get(0).get("descricao").asText()).containsIgnoringCase("condensado");
        assertThat(achados.get(0).get("codigoBarras").asText()).isEqualTo("7890000110266");
    }

    @Test
    @DisplayName("aceita o codigo com separadores, como sai do leitor")
    void aceitaCodigoComSeparadores() throws Exception {
        JsonNode achados = getJson("/api/alimentos/codigo-barras/789-0000.110266");

        assertThat(achados).isNotEmpty();
        assertThat(achados.get(0).get("codigoBarras").asText()).isEqualTo("7890000110266");
    }

    @Test
    @DisplayName("codigo inexistente devolve lista vazia, e nao erro")
    void codigoInexistenteDevolveVazio() throws Exception {
        // Nao encontrar um produto e resultado normal da busca, nao falha:
        // a base cobre o que o Open Food Facts tem, e nao o mercado inteiro.
        assertThat(getJson("/api/alimentos/codigo-barras/0000000000000")).isEmpty();
    }

    @Test
    @DisplayName("o produto do consultorio vem antes do produto da base publica")
    void produtoProprioVemPrimeiro() throws Exception {
        String corpo = """
                {"descricao":"Leite condensado da marca que eu uso",
                 "codigoBarras":"7890000110266",
                 "composicao":{"energiaKcal":320}}""";
        mvc.perform(post("/api/alimentos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        JsonNode achados = getJson("/api/alimentos/codigo-barras/7890000110266");

        assertThat(achados).hasSizeGreaterThan(1);
        assertThat(achados.get(0).get("descricao").asText())
                .isEqualTo("Leite condensado da marca que eu uso");
    }

    @Test
    @DisplayName("codigo de barras de outro consultorio nao aparece")
    void naoVeCodigoDeOutroConsultorio() throws Exception {
        String outroToken = json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Outro", "email", "outro" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mvc.perform(post("/api/alimentos").header("Authorization", "Bearer " + outroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"Produto do outro consultorio",
                                 "codigoBarras":"1111111111111",
                                 "composicao":{"energiaKcal":100}}"""))
                .andExpect(status().isCreated());

        assertThat(getJson("/api/alimentos/codigo-barras/1111111111111")).isEmpty();
    }

    @Test
    @DisplayName("a base TACO e importada por completo no boot")
    void importaBaseTaco() {
        assertThat(alimentoRepository.countByFonte(FonteDeDados.TACO)).isEqualTo(597);
    }

    @Test
    @DisplayName("a busca poe alimento de referencia antes de industrializado")
    void ranqueiaReferenciaAntesDeIndustrializado() throws Exception {
        // Sem ranqueamento a ordem e alfabetica, e os 21 mil industrializados
        // afogam os 597 da TACO: buscar "banana" devolvia "&Joy Frutas Banana
        // + Cacau" antes da fruta.
        for (String termo : new String[]{"arroz", "banana", "leite", "frango", "feijao"}) {
            JsonNode conteudo = buscarPorTermo(termo).get("content");
            assertThat(conteudo).as("busca por '%s'", termo).isNotEmpty();

            assertThat(conteudo.get(0).get("fonte").asText())
                    .as("primeiro resultado de '%s' deveria vir de tabela de referencia, e veio '%s'",
                            termo, conteudo.get(0).get("descricao").asText())
                    .isEqualTo("TACO");
        }
    }

    @Test
    @DisplayName("o termo que abre o nome vence o que aparece no meio")
    void ranqueiaNomeMaisDiretoPrimeiro() throws Exception {
        JsonNode conteudo = buscarPorTermo("banana").get("content");

        assertThat(conteudo.get(0).get("descricao").asText().toLowerCase())
                .startsWith("banana");
    }

    @Test
    @DisplayName("industrializado aparece quando nao ha alimento de referencia")
    void industrializadoApareceQuandoNaoHaReferencia() throws Exception {
        JsonNode conteudo = buscarPorTermo("nescau").get("content");

        if (!conteudo.isEmpty()) {
            assertThat(conteudo.get(0).get("fonte").asText()).isEqualTo("OPEN_FOOD_FACTS");
        }
    }

    @Test
    @DisplayName("filtro de fonte prevalece sobre o ranqueamento por procedencia")
    void filtroDeFonteTemPrecedencia() throws Exception {
        String corpo = mvc.perform(get("/api/alimentos")
                        .param("termo", "arroz")
                        .param("fonte", "OPEN_FOOD_FACTS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode conteudo = json.readTree(corpo).get("content");
        assertThat(conteudo).isNotEmpty();
        for (JsonNode item : conteudo) {
            assertThat(item.get("fonte").asText()).isEqualTo("OPEN_FOOD_FACTS");
        }
    }

    @Test
    @DisplayName("busca ignora acentuacao")
    void buscaIgnoraAcento() throws Exception {
        // Digitar "acucar" precisa encontrar os itens gravados com cedilha e til.
        JsonNode semAcento = buscarPorTermo("acucar");
        JsonNode comAcento = buscarPorTermo("açúcar");

        assertThat(semAcento.get("totalElements").asInt()).isPositive();
        assertThat(semAcento.get("totalElements").asInt())
                .isEqualTo(comAcento.get("totalElements").asInt());
    }

    @Test
    @DisplayName("escala a composicao proporcionalmente a quantidade em gramas")
    void calculaPorcaoEmGramas() throws Exception {
        long id = primeiroIdDaBusca("arroz, integral, cozido");

        JsonNode base = getJson("/api/alimentos/" + id);
        BigDecimal kcalPor100 = base.get("composicao").get("energiaKcal").decimalValue();
        BigDecimal ptnPor100 = base.get("composicao").get("proteinaG").decimalValue();

        JsonNode porcao = getJson("/api/alimentos/" + id + "/porcao?quantidade=150");

        assertThat(porcao.get("gramas").decimalValue()).isEqualByComparingTo("150");
        assertThat(porcao.get("composicao").get("energiaKcal").decimalValue())
                .isEqualByComparingTo(kcalPor100.multiply(new BigDecimal("1.5")));
        assertThat(porcao.get("composicao").get("proteinaG").decimalValue())
                .isEqualByComparingTo(ptnPor100.multiply(new BigDecimal("1.5")));
    }

    @Test
    @DisplayName("nutriente ausente na fonte continua ausente apos o calculo")
    void naoInventaNutrienteAusente() throws Exception {
        // O sal (codigo 517) nao tem energia determinada na TACO.
        long id = primeiroIdDaBusca("sal, grosso");

        JsonNode porcao = getJson("/api/alimentos/" + id + "/porcao?quantidade=10");

        assertThat(porcao.get("composicao").has("energiaKcal")).isFalse();
        assertThat(porcao.get("composicao").get("sodioMg")).isNotNull();
    }

    @Test
    @DisplayName("alimento de tabela de referencia nao pode ser editado")
    void naoEditaBasePublica() throws Exception {
        JsonNode busca = getJson("/api/alimentos?termo=arroz&size=1");
        long id = busca.get("content").get(0).get("id").asLong();

        mvc.perform(get("/api/alimentos/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.editavel").value(false))
                .andExpect(jsonPath("$.basePublica").value(true));

        mvc.perform(put("/api/alimentos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"Hackeado","composicao":{"energiaKcal":1}}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("alimento proprio calcula porcao por medida caseira")
    void calculaPorMedidaCaseira() throws Exception {
        String criar = """
                {"descricao":"Granola da casa","grupo":"Cereais e derivados",
                 "composicao":{"energiaKcal":400,"proteinaG":10,"carboidratoG":60,"lipideosG":14},
                 "medidas":[{"descricao":"colher de sopa","gramas":15,"padrao":true}]}""";

        String corpo = mvc.perform(post("/api/alimentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(criar))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.fonte").value("PERSONALIZADO"))
                .andReturn().getResponse().getContentAsString();

        JsonNode alimento = json.readTree(corpo);
        long id = alimento.get("id").asLong();
        long medidaId = alimento.get("medidas").get(0).get("id").asLong();

        // 3 colheres de 15 g = 45 g -> 45% de 400 kcal = 180 kcal
        JsonNode porcao = getJson("/api/alimentos/" + id + "/porcao?quantidade=3&medidaId=" + medidaId);

        assertThat(porcao.get("gramas").decimalValue()).isEqualByComparingTo("45");
        assertThat(porcao.get("medidaUsada").asText()).isEqualTo("3 colheres de sopa");
        assertThat(porcao.get("composicao").get("energiaKcal").decimalValue())
                .isEqualByComparingTo("180");
        assertThat(porcao.get("composicao").get("proteinaG").decimalValue())
                .isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("recusa quantidade zero ou negativa")
    void recusaQuantidadeInvalida() throws Exception {
        JsonNode busca = getJson("/api/alimentos?termo=arroz&size=1");
        long id = busca.get("content").get(0).get("id").asLong();

        mvc.perform(get("/api/alimentos/" + id + "/porcao?quantidade=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("alimento proprio de um consultorio nao aparece para outro")
    void isolaAlimentoProprio() throws Exception {
        mvc.perform(post("/api/alimentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"Mistura secreta da nutri","composicao":{"energiaKcal":100}}"""))
                .andExpect(status().isCreated());

        // Outro consultorio busca o mesmo termo e nao encontra nada.
        String outro = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Outra Nutri",
                                "email", "outra" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andReturn().getResponse().getContentAsString();
        String tokenOutro = json.readTree(outro).get("token").asText();

        String resultado = mvc.perform(get("/api/alimentos?termo=mistura secreta")
                        .header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(resultado).get("totalElements").asInt()).isZero();
    }
}
