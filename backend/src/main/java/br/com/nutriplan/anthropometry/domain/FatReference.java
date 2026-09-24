package br.com.nutriplan.anthropometry.domain;

import br.com.nutriplan.patient.domain.Sex;

import java.math.BigDecimal;
import java.util.List;

/**
 * A tabela de Pollock e Wilmore (1993) para classificar o percentual de
 * gordura, por sexo e faixa etária.
 *
 * Cada linha guarda o piso das sete faixas, de "Excelente" a "Muito ruim". O
 * valor cai na última faixa cujo piso ele alcança; abaixo do piso de
 * "Excelente" é "Muito baixo". A faixa ideal é a que o WebDiet chama de ideal
 * e o cliente reconhece: do piso de "Excelente" até logo antes de "Acima da
 * média" — para uma mulher de 36 anos, 16 a 23%.
 *
 * A tabela foi publicada para adultos de 18 a 65 anos. Acima de 65 vale a
 * última linha, e a tela diz de onde o número veio; abaixo de 18 não há
 * classificação, porque a referência não cobre.
 *
 * Os números vieram da tabela como ela circula no ensino brasileiro de
 * avaliação nutricional; se o consultório usa outra edição, é aqui que se
 * ajusta, e só aqui.
 */
public final class FatReference {

    public static final String SOURCE = "Pollock e Wilmore, 1993";

    private static final int MINIMUM_AGE = 18;

    /** Pisos de Excelente, Bom, Acima da média, Média, Abaixo da média, Ruim, Muito ruim. */
    private record Row(int ageFrom, int ageTo, double[] floors) {}

    private static final List<Row> MEN = List.of(
            new Row(18, 25, new double[] {4, 8, 12, 14, 17, 20, 26}),
            new Row(26, 35, new double[] {8, 12, 15, 18, 21, 24, 27}),
            new Row(36, 45, new double[] {10, 15, 18, 21, 24, 26, 30}),
            new Row(46, 55, new double[] {12, 17, 20, 23, 25, 28, 32}),
            new Row(56, Integer.MAX_VALUE, new double[] {15, 19, 22, 24, 26, 28, 32}));

    private static final List<Row> WOMEN = List.of(
            new Row(18, 25, new double[] {13, 17, 20, 23, 26, 29, 33}),
            new Row(26, 35, new double[] {14, 18, 21, 24, 27, 31, 36}),
            new Row(36, 45, new double[] {16, 20, 24, 27, 30, 33, 38}),
            new Row(46, 55, new double[] {17, 23, 26, 29, 32, 35, 39}),
            new Row(56, Integer.MAX_VALUE, new double[] {18, 24, 27, 30, 33, 36, 39}));

    private static final FatClassification[] BANDS = {
            FatClassification.EXCELLENT, FatClassification.GOOD, FatClassification.ABOVE_AVERAGE,
            FatClassification.AVERAGE, FatClassification.BELOW_AVERAGE, FatClassification.POOR,
            FatClassification.VERY_POOR};

    private FatReference() {
    }

    /** A faixa ideal (mínimo e máximo, em %), ou nula fora da cobertura da tabela. */
    public record IdealRange(BigDecimal minimum, BigDecimal maximum) {}

    public static boolean covers(Integer age) {
        return age != null && age >= MINIMUM_AGE;
    }

    public static FatClassification classify(BigDecimal percentage, Sex sex, Integer age) {
        Row row = row(sex, age);
        if (row == null || percentage == null) {
            return null;
        }
        double value = percentage.doubleValue();
        if (value < row.floors()[0]) {
            return FatClassification.VERY_LOW;
        }
        FatClassification found = BANDS[0];
        for (int i = 0; i < BANDS.length; i++) {
            if (value >= row.floors()[i]) {
                found = BANDS[i];
            }
        }
        return found;
    }

    public static IdealRange ideal(Sex sex, Integer age) {
        Row row = row(sex, age);
        if (row == null) {
            return null;
        }
        return new IdealRange(
                BigDecimal.valueOf(row.floors()[0]).setScale(0),
                BigDecimal.valueOf(row.floors()[2] - 1).setScale(0));
    }

    private static Row row(Sex sex, Integer age) {
        if (sex == null || !covers(age)) {
            return null;
        }
        for (Row row : sex == Sex.MALE ? MEN : WOMEN) {
            if (age >= row.ageFrom() && age <= row.ageTo()) {
                return row;
            }
        }
        return null;
    }
}
