package br.com.nutriplan.energy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.nutriplan.energy.domain.ActivityLevel;
import br.com.nutriplan.energy.domain.EnergyEquation;
import br.com.nutriplan.patient.domain.Sex;

/**
 * The arithmetic of the equations, checked against the published coefficients.
 *
 * These numbers were worked out by hand from the source formulas rather than
 * copied from the implementation — a test that runs the code to find out what
 * to expect confirms nothing. A wrong coefficient here becomes a wrong
 * prescription, which is the one kind of bug this project cannot ship.
 */
@DisplayName("equações de gasto energético")
class EnergyEquationTest {

    private static EnergyEquation.Input adultMale(ActivityLevel level) {
        return new EnergyEquation.Input(80, 180, Sex.MALE, 30, level);
    }

    @Test
    @DisplayName("Harris-Benedict 1984 é a revisão de Roza e Shizgal, não a de 1919")
    void harrisBenedictIsTheRevision() {
        // 88,362 + 13,397×80 + 4,799×180 − 5,677×30
        double expected = 88.362 + 13.397 * 80 + 4.799 * 180 - 5.677 * 30;
        assertThat(expected).isCloseTo(1853.632, within(0.001));
        assertThat(EnergyEquation.HARRIS_BENEDICT_1984.basal(adultMale(ActivityLevel.INACTIVE)))
                .isCloseTo(1853.632, within(0.01));
    }

    @Test
    @DisplayName("FAO/WHO 2004 escolhe a faixa etária e ignora a altura")
    void faoWhoPicksTheAgeBand() {
        // Homem de 30 anos cai na faixa 30–60: 11,472×80 + 873,1
        assertThat(EnergyEquation.FAO_WHO_2004.basal(adultMale(ActivityLevel.INACTIVE)))
                .isCloseTo(11.472 * 80 + 873.1, within(0.01));

        // A altura não entra: mudá-la não pode mexer no resultado.
        var taller = new EnergyEquation.Input(80, 210, Sex.MALE, 30, ActivityLevel.INACTIVE);
        assertThat(EnergyEquation.FAO_WHO_2004.basal(taller))
                .isCloseTo(EnergyEquation.FAO_WHO_2004.basal(adultMale(ActivityLevel.INACTIVE)),
                        within(0.01));

        // Uma criança de 8 anos usa outra faixa da mesma publicação.
        var child = new EnergyEquation.Input(25, 128, Sex.MALE, 8, ActivityLevel.ACTIVE);
        assertThat(EnergyEquation.FAO_WHO_2004.basal(child))
                .isCloseTo(22.706 * 25 + 504.3, within(0.01));
    }

    @Test
    @DisplayName("EER 2005 usa a altura em metros")
    void eerTwoThousandFiveUsesMetres() {
        // 662 − 9,53×30 + 1,00 × (15,91×80 + 539,6×1,80)
        double expected = 662 - 9.53 * 30 + 1.00 * (15.91 * 80 + 539.6 * 1.80);
        assertThat(expected).isCloseTo(2620.18, within(0.01));
        assertThat(EnergyEquation.EER_IOM_2005.totalExpenditure(adultMale(ActivityLevel.INACTIVE)))
                .isCloseTo(2620.18, within(0.01));
    }

    @Test
    @DisplayName("EER 2023 usa a altura em centímetros e troca de equação por nível")
    void eerTwentyTwentyThreeSwapsEquationPerLevel() {
        // Inativo: 753,07 − 10,83×30 + 6,50×180 + 14,10×80
        assertThat(EnergyEquation.EER_2023.totalExpenditure(adultMale(ActivityLevel.INACTIVE)))
                .isCloseTo(753.07 - 10.83 * 30 + 6.50 * 180 + 14.10 * 80, within(0.01));

        // Pouco ativo tem outro intercepto e outros coeficientes, não um multiplicador.
        assertThat(EnergyEquation.EER_2023.totalExpenditure(adultMale(ActivityLevel.LOW_ACTIVE)))
                .isCloseTo(581.47 - 10.83 * 30 + 8.30 * 180 + 14.94 * 80, within(0.01));
    }

    @Test
    @DisplayName("o fator de atividade só multiplica equação basal")
    void activityOnlyMultipliesBasalEquations() {
        var input = adultMale(ActivityLevel.ACTIVE);

        // Basal: o gasto do dia é o basal vezes o fator da tabela.
        assertThat(EnergyEquation.HARRIS_BENEDICT_1984.totalExpenditure(input))
                .isCloseTo(EnergyEquation.HARRIS_BENEDICT_1984.basal(input) * 1.55, within(0.01));

        // Total: o nível já está nos coeficientes; multiplicar de novo contaria
        // o mesmo movimento duas vezes.
        assertThat(EnergyEquation.EER_2023.basal(input)).isZero();
        assertThat(EnergyEquation.EER_2023.totalExpenditure(input))
                .isCloseTo(1004.82 - 10.83 * 30 + 6.52 * 180 + 15.91 * 80, within(0.01));
    }

    @Test
    @DisplayName("o EER infantil soma a deposição de energia do crescimento")
    void childEerAddsEnergyDeposition() {
        var boy = new EnergyEquation.Input(30, 135, Sex.MALE, 10, ActivityLevel.ACTIVE);
        // 88,5 − 61,9×10 + 1,26 × (26,7×30 + 903×1,35) + 20
        double expected = 88.5 - 61.9 * 10 + 1.26 * (26.7 * 30 + 903 * 1.35) + 20;
        assertThat(EnergyEquation.EER_IOM_2005.totalExpenditure(boy))
                .isCloseTo(expected, within(0.01));
    }

    @Test
    @DisplayName("as equações de EER não atendem menores de 3 anos")
    void eerDoesNotServeUnderThrees() {
        assertThat(EnergyEquation.EER_2023.servesAge(2)).isFalse();
        assertThat(EnergyEquation.EER_IOM_2005.servesAge(2)).isFalse();
        // Schofield tem faixa para menores de 3, então atende.
        assertThat(EnergyEquation.FAO_WHO_2004.servesAge(2)).isTrue();
    }
}
