package br.com.nutriplan.anthropometry.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A faixa de peso que mantém o adulto em eutrofia, pela altura.
 *
 * É o "limite de peso superior e inferior de acordo com o IMC" que o cliente
 * sentiu falta na antropometria. Os limites são os da própria tabela de
 * classificação do IMC que o sistema usa (18,5 a 25), multiplicados pela
 * altura ao quadrado — e o cálculo mora aqui, num lugar só, porque a faixa
 * aparece na antropometria, no cálculo energético, na ficha do paciente e ao
 * lado do cardápio. Quatro cópias da mesma conta divergiriam na primeira
 * correção feita de um lado só.
 *
 * Só vale para adulto: abaixo de 20 anos a leitura correta é por percentil.
 */
public record HealthyWeightRange(
        BigDecimal minimumKg,
        BigDecimal maximumKg,
        BigDecimal bmiMinimum,
        BigDecimal bmiMaximum
) {

    /** @return nulo sem altura, ou com altura que não faz sentido */
    public static HealthyWeightRange of(BigDecimal heightCm) {
        if (heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        Double minimum = BmiClassification.NORMAL.getLimitInferior();
        Double maximum = BmiClassification.NORMAL.getLimitSuperior();
        if (minimum == null || maximum == null) {
            return null;
        }
        BigDecimal metresSquared = heightCm
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .pow(2);
        return new HealthyWeightRange(
                BigDecimal.valueOf(minimum).multiply(metresSquared).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(maximum).multiply(metresSquared).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(minimum),
                BigDecimal.valueOf(maximum));
    }

    /** A faixa para quem já tem idade de adulto; nula para criança e adolescente. */
    public static HealthyWeightRange forAdult(BigDecimal heightCm, Integer age) {
        if (age != null && age < BmiClassification.MINIMUM_AGE_ADULT) {
            return null;
        }
        return of(heightCm);
    }
}
