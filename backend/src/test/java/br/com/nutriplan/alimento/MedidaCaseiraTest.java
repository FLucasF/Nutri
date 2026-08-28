package br.com.nutriplan.alimento;

import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.repository.MedidaCaseiraRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O recurso existe porque o plano precisa ser legivel pelo paciente: ninguem
 * serve 5 g de sal nem pesa 25 g de arroz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedidaCaseiraTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MedidaCaseiraRepository medidaCaseiraRepository;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void autenticar() throws Exception {
        tokenA = cadastrar("medidaA");
        tokenB = cadastrar("medidaB");
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

    /** Busca restrita a uma fonte, para o teste nao depender de qual base casa primeiro. */
    private JsonNode buscar(String token, String termo, String fonte) throws Exception {
        var req = get("/api/alimentos").param("termo", termo)
                .header("Authorization", "Bearer " + token);
        if (fonte != null) {
            req = req.param("fonte", fonte);
        }
        String corpo = mvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private JsonNode buscar(String token, String termo) throws Exception {
        return buscar(token, termo, "TACO");
    }

    private long idDe(String token, String termo) throws Exception {
        JsonNode conteudo = buscar(token, termo).get("content");
        assertThat(conteudo).as("nenhum alimento da TACO para '%s'", termo).isNotEmpty();
        return conteudo.get(0).get("id").asLong();
    }

    private JsonNode detalhar(String token, long id) throws Exception {
        String corpo = mvc.perform(get("/api/alimentos/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    @Test
    @DisplayName("o acervo base de porcoes da TACO e carregado no boot")
    void carregaAcervoBase() {
        assertThat(medidaCaseiraRepository.contarNoAcervoPorFonte(FonteDeDados.TACO))
                .isEqualTo(1117);
    }

    @Test
    @DisplayName("nenhum alimento da TACO fica sem porcao usual")
    void tacoTemCoberturaTotalDePorcoes() {
        assertThat(medidaCaseiraRepository.contarSemNenhumaPorcao(FonteDeDados.TACO))
                .as("alimentos da TACO sem nenhuma medida caseira")
                .isZero();
    }

    @Test
    @DisplayName("produto industrializado ganha a porcao da embalagem do rotulo")
    void produtoIndustrializadoTemPorcaoDaEmbalagem() throws Exception {
        JsonNode conteudo = buscar(tokenA, "leite condensado", "OPEN_FOOD_FACTS").get("content");
        assertThat(conteudo).as("nenhum produto industrializado encontrado").isNotEmpty();

        // Procura um que tenha porcao derivada da embalagem.
        boolean achouEmbalagem = false;
        for (JsonNode item : conteudo) {
            JsonNode detalhe = detalhar(tokenA, item.get("id").asLong());
            for (JsonNode medida : detalhe.get("medidas")) {
                if (medida.get("descricao").asText().startsWith("embalagem")) {
                    assertThat(medida.get("gramas").decimalValue()).isPositive();
                    assertThat(medida.get("padrao").asBoolean()).isTrue();
                    achouEmbalagem = true;
                    break;
                }
            }
            if (achouEmbalagem) {
                break;
            }
        }
        assertThat(achouEmbalagem)
                .as("nenhum produto trouxe a porcao da embalagem")
                .isTrue();
    }

    @Test
    @DisplayName("sal e prescrito em pitada, nao em gramas")
    void salTemPorcaoUsual() throws Exception {
        long id = idDe(tokenA, "sal, grosso");
        JsonNode medidas = detalhar(tokenA, id).get("medidas");

        assertThat(medidas).isNotEmpty();

        var descricoes = medidas.findValuesAsText("descricao");
        assertThat(descricoes).contains("pitada", "colher de chá", "colher de sopa");

        JsonNode pitada = null;
        for (JsonNode m : medidas) {
            if ("pitada".equals(m.get("descricao").asText())) {
                pitada = m;
            }
        }
        assertThat(pitada).isNotNull();
        assertThat(pitada.get("padrao").asBoolean()).isTrue();
        assertThat(pitada.get("gramas").decimalValue()).isEqualByComparingTo("0.4");
        assertThat(pitada.get("doAcervoBase").asBoolean()).isTrue();
        // Porcao do acervo e comum a todos: nenhum consultorio a edita.
        assertThat(pitada.get("editavel").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("todo alimento da TACO tem ao menos uma porcao usual")
    void todoAlimentoTemPorcao() throws Exception {
        // Amostra de alimentos de grupos diferentes, incluindo os que receberam
        // apenas as porcoes genericas do grupo.
        for (String termo : new String[]{"arroz, tipo 1, cozido", "feijão, carioca, cozido",
                "banana, nanica", "leite, de vaca, integral", "ovo, de galinha, inteiro, cru",
                "azeite, de oliva", "sardinha, assada", "castanha"}) {
            JsonNode conteudo = buscar(tokenA, termo).get("content");
            if (conteudo.isEmpty()) {
                continue;
            }
            long id = conteudo.get(0).get("id").asLong();
            assertThat(detalhar(tokenA, id).get("medidas"))
                    .as("alimento '%s' ficou sem porcao usual", termo)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("calcula a porcao a partir da medida caseira do acervo")
    void calculaPorMedidaDoAcervo() throws Exception {
        long id = idDe(tokenA, "arroz, tipo 1, cozido");
        JsonNode detalhe = detalhar(tokenA, id);

        JsonNode colher = null;
        for (JsonNode m : detalhe.get("medidas")) {
            if (m.get("descricao").asText().startsWith("colher de sopa")) {
                colher = m;
            }
        }
        assertThat(colher).as("arroz cozido deveria ter colher de sopa").isNotNull();

        BigDecimal gramasPorColher = colher.get("gramas").decimalValue();
        BigDecimal kcalPor100 = detalhe.get("composicao").get("energiaKcal").decimalValue();

        String corpo = mvc.perform(get("/api/alimentos/" + id + "/porcao")
                        .param("quantidade", "3")
                        .param("medidaId", colher.get("id").asText())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode porcao = json.readTree(corpo);

        BigDecimal gramasEsperadas = gramasPorColher.multiply(new BigDecimal("3"));
        assertThat(porcao.get("gramas").decimalValue()).isEqualByComparingTo(gramasEsperadas);
        assertThat(porcao.get("medidaUsada").asText()).isEqualTo("3 colheres de sopa cheias");

        BigDecimal kcalEsperada = kcalPor100
                .multiply(gramasEsperadas)
                .divide(new BigDecimal("100"), 3, java.math.RoundingMode.HALF_UP);
        assertThat(porcao.get("composicao").get("energiaKcal").decimalValue())
                .isEqualByComparingTo(kcalEsperada);
    }

    @Test
    @DisplayName("nutricionista cadastra a propria porcao sobre alimento da TACO")
    void cadastraPorcaoPropriaSobreBasePublica() throws Exception {
        long id = idDe(tokenA, "arroz, tipo 1, cozido");

        String corpo = mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"colher de servir da clínica","gramas":45,"padrao":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode criada = json.readTree(corpo);
        assertThat(criada.get("doAcervoBase").asBoolean()).isFalse();
        assertThat(criada.get("editavel").asBoolean()).isTrue();

        // Aparece para quem criou...
        assertThat(detalhar(tokenA, id).get("medidas").findValuesAsText("descricao"))
                .contains("colher de servir da clínica");

        // ...e nao vaza para outro consultorio.
        assertThat(detalhar(tokenB, id).get("medidas").findValuesAsText("descricao"))
                .doesNotContain("colher de servir da clínica");
    }

    @Test
    @DisplayName("a porcao propria aparece antes das do acervo")
    void porcaoPropriaTemPrecedencia() throws Exception {
        long id = idDe(tokenA, "feijão, carioca, cozido");

        mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"concha da casa","gramas":95,"padrao":false}"""))
                .andExpect(status().isCreated());

        JsonNode medidas = detalhar(tokenA, id).get("medidas");
        assertThat(medidas.get(0).get("descricao").asText()).isEqualTo("concha da casa");
        assertThat(medidas.get(0).get("doAcervoBase").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("recusa duas porcoes com o mesmo nome no mesmo consultorio")
    void recusaPorcaoDuplicada() throws Exception {
        long id = idDe(tokenA, "banana, prata");
        String req = """
                {"descricao":"unidade grande","gramas":90,"padrao":false}""";

        mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnprocessableEntity());

        // Mas outro consultorio pode usar o mesmo nome.
        mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("nao remove porcao do acervo base")
    void naoRemovePorcaoDoAcervo() throws Exception {
        long id = idDe(tokenA, "sal, grosso");
        long medidaDoAcervo = detalhar(tokenA, id).get("medidas").get(0).get("id").asLong();

        mvc.perform(delete("/api/alimentos/" + id + "/medidas/" + medidaDoAcervo)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("nao usa porcao cadastrada por outro consultorio no calculo")
    void naoCalculaComPorcaoAlheia() throws Exception {
        long id = idDe(tokenA, "abacate, cru");

        String corpo = mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"porção do consultório A","gramas":70,"padrao":false}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long medidaDeA = json.readTree(corpo).get("id").asLong();

        mvc.perform(get("/api/alimentos/" + id + "/porcao")
                        .param("quantidade", "1")
                        .param("medidaId", String.valueOf(medidaDeA))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("recusa porcao com peso zero")
    void recusaPesoInvalido() throws Exception {
        long id = idDe(tokenA, "tomate, salada");

        mvc.perform(post("/api/alimentos/" + id + "/medidas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"nada","gramas":0,"padrao":false}"""))
                .andExpect(status().isBadRequest());
    }
}
