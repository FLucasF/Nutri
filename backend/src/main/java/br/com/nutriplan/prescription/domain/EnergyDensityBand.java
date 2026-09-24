package br.com.nutriplan.prescription.domain;

import java.math.BigDecimal;

/**
 * A densidade calórica de uma refeição, em kcal por grama, nas faixas que o
 * cliente lê no WebDiet: muito baixa até 0,6; baixa até 1,5; média até 4; alta
 * acima disso. É a leitura de Rolls sobre volume e saciedade, e serve para
 * ver de relance se um almoço de 600 kcal é um prato cheio ou um lanche denso.
 */
public enum EnergyDensityBand {
    VERY_LOW("Muito baixa"),
    LOW("Baixa"),
    MEDIUM("Média"),
    HIGH("Alta");

    private final String description;

    EnergyDensityBand(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** @return nula sem densidade */
    public static EnergyDensityBand of(BigDecimal kcalPerGram) {
        if (kcalPerGram == null) {
            return null;
        }
        double value = kcalPerGram.doubleValue();
        if (value < 0.6) return VERY_LOW;
        if (value < 1.5) return LOW;
        if (value < 4.0) return MEDIUM;
        return HIGH;
    }
}
