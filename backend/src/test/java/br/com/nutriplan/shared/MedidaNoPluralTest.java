package br.com.nutriplan.shared;

import br.com.nutriplan.shared.util.MedidaNoPlural;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A medida caseira e o maior texto da pagina do paciente, entao a concordancia
 * dela deixou de ser detalhe: "2 unidade" salta aos olhos.
 */
class MedidaNoPluralTest {

    private String duas(String descricao) {
        return MedidaNoPlural.concordar(new BigDecimal("2"), descricao);
    }

    @ParameterizedTest(name = "2 x \"{0}\" -> \"{1}\"")
    @CsvSource({
            "unidade,               unidades",
            "copo,                  copos",
            "pote,                  potes",
            "fatia,                 fatias",
            // Terminadas em -r pedem -es.
            "colher,                colheres",
            // O qualificador colado ao utensilio concorda junto.
            "concha média,          conchas médias",
            "unidade pequena,       unidades pequenas",
            "porção grande,         porções grandes",
            // O que vem depois de preposicao nao e o utensilio e nao flexiona.
            "colher de sopa,        colheres de sopa",
            "colher de chá,         colheres de chá",
            "pedaço de pizza,       pedaços de pizza",
            // Utensilio, preposicao e qualificador na mesma descricao.
            "colher de sopa cheia,  colheres de sopa cheias",
    })
    @DisplayName("a medida concorda com a quantidade")
    void concorda(String descricao, String esperado) {
        assertThat(duas(descricao)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("uma unidade ou menos nao vai para o plural")
    void singularPermanece() {
        assertThat(MedidaNoPlural.concordar(new BigDecimal("1"), "colher de sopa"))
                .isEqualTo("colher de sopa");
        assertThat(MedidaNoPlural.concordar(new BigDecimal("0.5"), "unidade"))
                .isEqualTo("unidade");
    }

    @Test
    @DisplayName("fracao maior que um vai para o plural")
    void fracaoAcimaDeUm() {
        assertThat(MedidaNoPlural.concordar(new BigDecimal("1.5"), "colher de sopa"))
                .isEqualTo("colheres de sopa");
    }

    @Test
    @DisplayName("anotacao da pesquisa do IBGE fica intacta")
    void anotacaoDoIbgeNaoEhFlexionada() {
        // Nestas descricoes o que vem depois nao e texto corrente: e a marcacao
        // do entrevistador. Flexionar produziria "e.)s".
        assertThat(duas("lata (n. e.)")).isEqualTo("lata (n. e.)");
        assertThat(duas("garrafa (1,5 l)")).isEqualTo("garrafa (1,5 l)");
        assertThat(duas("colher de arroz/servir")).isEqualTo("colher de arroz/servir");
    }

    @Test
    @DisplayName("descricao ausente nao quebra")
    void semDescricao() {
        assertThat(MedidaNoPlural.concordar(new BigDecimal("2"), null)).isNull();
        assertThat(MedidaNoPlural.concordar(null, "unidade")).isEqualTo("unidade");
    }
}
