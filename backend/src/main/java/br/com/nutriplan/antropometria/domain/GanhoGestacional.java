package br.com.nutriplan.antropometria.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ganho de peso esperado na gestação, pelas faixas do Institute of Medicine
 * (2009), que são as adotadas pelo Ministério da Saúde.
 *
 * A faixa depende do **IMC pré-gestacional**, e não do IMC atual: quem começou
 * a gestação com sobrepeso deve ganhar menos que quem começou eutrófica, e o
 * IMC medido hoje já embute o ganho que se quer avaliar. Sem o peso anterior à
 * gestação o sistema não classifica — e dizer isso é mais útil que estimar.
 */
public enum GanhoGestacional {

    BAIXO_PESO("Baixo peso", null, new BigDecimal("18.5"),
            new BigDecimal("12.5"), new BigDecimal("18.0"),
            new BigDecimal("0.44"), new BigDecimal("0.58")),

    EUTROFIA("Eutrofia", new BigDecimal("18.5"), new BigDecimal("25.0"),
            new BigDecimal("11.5"), new BigDecimal("16.0"),
            new BigDecimal("0.35"), new BigDecimal("0.50")),

    SOBREPESO("Sobrepeso", new BigDecimal("25.0"), new BigDecimal("30.0"),
            new BigDecimal("7.0"), new BigDecimal("11.5"),
            new BigDecimal("0.23"), new BigDecimal("0.33")),

    OBESIDADE("Obesidade", new BigDecimal("30.0"), null,
            new BigDecimal("5.0"), new BigDecimal("9.0"),
            new BigDecimal("0.17"), new BigDecimal("0.27"));

    /** Ganho esperado no primeiro trimestre, igual para todas as faixas. */
    public static final BigDecimal PRIMEIRO_TRIMESTRE_MIN = new BigDecimal("0.5");
    public static final BigDecimal PRIMEIRO_TRIMESTRE_MAX = new BigDecimal("2.0");
    private static final int FIM_DO_PRIMEIRO_TRIMESTRE = 13;

    private final String descricao;
    private final BigDecimal imcMin;
    private final BigDecimal imcMax;
    private final BigDecimal ganhoTotalMin;
    private final BigDecimal ganhoTotalMax;
    private final BigDecimal porSemanaMin;
    private final BigDecimal porSemanaMax;

    GanhoGestacional(String descricao, BigDecimal imcMin, BigDecimal imcMax,
                     BigDecimal ganhoTotalMin, BigDecimal ganhoTotalMax,
                     BigDecimal porSemanaMin, BigDecimal porSemanaMax) {
        this.descricao = descricao;
        this.imcMin = imcMin;
        this.imcMax = imcMax;
        this.ganhoTotalMin = ganhoTotalMin;
        this.ganhoTotalMax = ganhoTotalMax;
        this.porSemanaMin = porSemanaMin;
        this.porSemanaMax = porSemanaMax;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getGanhoTotalMin() {
        return ganhoTotalMin;
    }

    public BigDecimal getGanhoTotalMax() {
        return ganhoTotalMax;
    }

    /** Faixa pelo IMC anterior à gestação. */
    public static GanhoGestacional porImcPreGestacional(BigDecimal imc) {
        if (imc == null) {
            return null;
        }
        for (GanhoGestacional faixa : values()) {
            boolean acimaDoMinimo = faixa.imcMin == null || imc.compareTo(faixa.imcMin) >= 0;
            boolean abaixoDoMaximo = faixa.imcMax == null || imc.compareTo(faixa.imcMax) < 0;
            if (acimaDoMinimo && abaixoDoMaximo) {
                return faixa;
            }
        }
        return null;
    }

    /**
     * Ganho esperado até a semana informada.
     *
     * Primeiro trimestre à parte: o ganho ali é pequeno e igual para todas as
     * faixas. A partir da 14ª semana entra o ritmo semanal da faixa.
     */
    public BigDecimal esperadoMin(int semana) {
        return acumulado(semana, PRIMEIRO_TRIMESTRE_MIN, porSemanaMin);
    }

    public BigDecimal esperadoMax(int semana) {
        return acumulado(semana, PRIMEIRO_TRIMESTRE_MAX, porSemanaMax);
    }

    private BigDecimal acumulado(int semana, BigDecimal primeiroTrimestre, BigDecimal porSemana) {
        if (semana <= FIM_DO_PRIMEIRO_TRIMESTRE) {
            // Dentro do primeiro trimestre o ganho e proporcional ao andamento
            // dele: exigir 0,5 kg na quarta semana seria cobrar cedo demais.
            return primeiroTrimestre
                    .multiply(BigDecimal.valueOf(semana))
                    .divide(BigDecimal.valueOf(FIM_DO_PRIMEIRO_TRIMESTRE), 2, RoundingMode.HALF_UP);
        }
        int semanasDepois = semana - FIM_DO_PRIMEIRO_TRIMESTRE;
        return primeiroTrimestre
                .add(porSemana.multiply(BigDecimal.valueOf(semanasDepois)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
