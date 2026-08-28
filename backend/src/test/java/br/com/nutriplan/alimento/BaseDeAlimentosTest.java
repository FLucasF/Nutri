package br.com.nutriplan.alimento;

import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.repository.AlimentoRepository;
import br.com.nutriplan.alimento.repository.MedidaCaseiraRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garantias sobre a composição da base de alimentos e sobre a relevância da
 * busca.
 *
 * O ranqueamento é a diferença entre uma base utilizável e 24 mil registros
 * inúteis: sem ele, "banana" devolvia "&Joy Frutas Banana + Cacau" antes da
 * fruta, porque os industrializados são 36 vezes mais numerosos que a TACO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BaseDeAlimentosTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AlimentoRepository alimentoRepository;
    @Autowired MedidaCaseiraRepository medidaCaseiraRepository;

    private String token;

    @BeforeEach
    void autenticar() throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Base",
                                "email", "base" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(corpo).get("token").asText();
    }

    private JsonNode buscar(String termo) throws Exception {
        String corpo = mvc.perform(get("/api/alimentos")
                        .param("termo", termo).param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    // ---------------------------------------------------- composição da base

    @Test
    @DisplayName("as tres fontes sao carregadas no boot")
    void carregaAsTresFontes() {
        assertThat(alimentoRepository.countByFonte(FonteDeDados.TACO)).isEqualTo(597);
        assertThat(alimentoRepository.countByFonte(FonteDeDados.IBGE)).isEqualTo(1971);
        assertThat(alimentoRepository.countByFonte(FonteDeDados.OPEN_FOOD_FACTS)).isEqualTo(21377);
    }

    @Test
    @DisplayName("as medidas do IBGE vem da pesquisa, nao de estimativa")
    void carregaMedidasDoIbge() {
        // 11.801 porções registradas em campo pelo entrevistador da POF,
        // com o utensílio que a família de fato usou.
        assertThat(medidaCaseiraRepository.contarNoAcervoPorFonte(FonteDeDados.IBGE))
                .isEqualTo(11801);
        assertThat(medidaCaseiraRepository.contarSemNenhumaPorcao(FonteDeDados.IBGE))
                .as("alimentos do IBGE sem nenhuma porcao")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("o IBGE traz nutrientes que a TACO nao determina")
    void ibgeTrazNutrientesNovos() throws Exception {
        JsonNode conteudo = buscar("feijoada").get("content");
        assertThat(conteudo).isNotEmpty();

        long id = conteudo.get(0).get("id").asLong();
        String corpo = mvc.perform(get("/api/alimentos/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode composicao = json.readTree(corpo).get("composicao");
        // B12, folato e vitamina D nao existem em nenhuma linha da TACO.
        assertThat(composicao.has("vitaminaB12Mcg")).isTrue();
        assertThat(composicao.has("folatoMcg")).isTrue();
    }

    // --------------------------------------------------- cascata de fontes

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            // Alimento básico: a TACO responde.
            "arroz,        TACO",
            "banana,       TACO",
            "leite,        TACO",
            "frango,       TACO",
            "feijao,       TACO",
            // Preparação que a TACO não tem: o IBGE responde.
            "feijoada,     IBGE",
            "carne suina,  IBGE",
            // Só existe como produto de fabricante.
            "nescau,       OPEN_FOOD_FACTS",
    })
    @DisplayName("a busca cai de fonte em fonte ate encontrar")
    void cascataDeFontes(String termo, String fonteEsperada) throws Exception {
        JsonNode conteudo = buscar(termo).get("content");
        assertThat(conteudo).as("busca por '%s'", termo).isNotEmpty();

        assertThat(conteudo.get(0).get("fonte").asText())
                .as("primeiro resultado de '%s' foi '%s'",
                        termo, conteudo.get(0).get("descricao").asText())
                .isEqualTo(fonteEsperada);
    }

    @Test
    @DisplayName("o termo precisa ser palavra inteira, nao prefixo de outra")
    void ranqueiaPalavraInteiraAntesDePrefixo() throws Exception {
        // "Arrozina" e um cereal infantil cujo nome apenas comeca com as cinco
        // letras de "arroz". Antes desta regra, ele vencia o arroz comum por
        // ter o nome mais curto.
        JsonNode conteudo = buscar("arroz").get("content");

        String primeiro = conteudo.get(0).get("descricao").asText().toLowerCase();
        assertThat(primeiro)
                .as("primeiro resultado de 'arroz'")
                .doesNotStartWith("arrozina");

        // O termo abre o nome e termina ali — nao emenda em outra palavra.
        assertThat(primeiro).matches("^arroz([\\s,\\-].*)?$");
    }

    @Test
    @DisplayName("sem termo, a listagem volta a ser alfabetica")
    void semTermoOrdenaAlfabeticamente() throws Exception {
        String corpo = mvc.perform(get("/api/alimentos")
                        .param("size", "5").param("fonte", "TACO")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode conteudo = json.readTree(corpo).get("content");
        assertThat(conteudo).isNotEmpty();

        String anterior = null;
        for (JsonNode item : conteudo) {
            String atual = item.get("descricao").asText();
            if (anterior != null) {
                assertThat(atual).isGreaterThanOrEqualTo(anterior);
            }
            anterior = atual;
        }
    }
}
