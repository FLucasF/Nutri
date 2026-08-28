package br.com.nutriplan.shared;

import br.com.nutriplan.shared.util.PluralMeasure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The household measure is the largest text on the patient's page, so its
 * agreement stopped being a detail: "2 unidade" jumps out at you.
 */
class PluralMeasureTest {

    private String two(String description) {
        return PluralMeasure.agree(new BigDecimal("2"), description);
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
            // What comes after a preposition is not the utensil and does not inflect.
            "colher de sopa,        colheres de sopa",
            "colher de chá,         colheres de chá",
            "pedaço de pizza,       pedaços de pizza",
            // Utensil, preposition and qualifier in the same description.
            "colher de sopa cheia,  colheres de sopa cheias",
    })
    @DisplayName("a medida concorda com a quantidade")
    void concorda(String description, String expected) {
        assertThat(two(description)).isEqualTo(expected);
    }

    @Test
    @DisplayName("uma unidade ou menos nao vai para o plural")
    void singularRemains() {
        assertThat(PluralMeasure.agree(new BigDecimal("1"), "colher de sopa"))
                .isEqualTo("colher de sopa");
        assertThat(PluralMeasure.agree(new BigDecimal("0.5"), "unit"))
                .isEqualTo("unit");
    }

    @Test
    @DisplayName("fracao maior que um vai para o plural")
    void fractionAboveDe() {
        assertThat(PluralMeasure.agree(new BigDecimal("1.5"), "colher de sopa"))
                .isEqualTo("colheres de sopa");
    }

    @Test
    @DisplayName("anotacao da pesquisa do IBGE fica intacta")
    void ibgeNotEhInflectedAnnotation() {
        // In these descriptions what comes after is not running text: it is the
        // interviewer's annotation. Inflecting it would produce "e.)s".
        assertThat(two("lata (n. e.)")).isEqualTo("lata (n. e.)");
        assertThat(two("garrafa (1,5 l)")).isEqualTo("garrafa (1,5 l)");
        assertThat(two("colher de arroz/servir")).isEqualTo("colher de arroz/servir");
    }

    @Test
    @DisplayName("descricao ausente nao quebra")
    void withoutDescription() {
        assertThat(PluralMeasure.agree(new BigDecimal("2"), null)).isNull();
        assertThat(PluralMeasure.agree(null, "unit")).isEqualTo("unit");
    }
}
