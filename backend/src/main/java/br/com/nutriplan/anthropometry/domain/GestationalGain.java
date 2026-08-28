package br.com.nutriplan.anthropometry.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weight gain expected in pregnancy, by the Institute of Medicine bands (2009),
 * which are the ones adopted by the Ministry of Health.
 *
 * The band depends on the **pre-pregnancy BMI**, and not on the current BMI:
 * someone who started the pregnancy overweight should gain less than someone
 * who started at a normal weight, and the BMI measured today already embeds the
 * gain being assessed. Without the weight before pregnancy the system does not
 * classify — and saying so is more useful than estimating.
 */
public enum GestationalGain {

    LOW_WEIGHT("Baixo peso", null, new BigDecimal("18.5"),
            new BigDecimal("12.5"), new BigDecimal("18.0"),
            new BigDecimal("0.44"), new BigDecimal("0.58")),

    NORMAL("Eutrofia", new BigDecimal("18.5"), new BigDecimal("25.0"),
            new BigDecimal("11.5"), new BigDecimal("16.0"),
            new BigDecimal("0.35"), new BigDecimal("0.50")),

    OVERWEIGHT("Sobrepeso", new BigDecimal("25.0"), new BigDecimal("30.0"),
            new BigDecimal("7.0"), new BigDecimal("11.5"),
            new BigDecimal("0.23"), new BigDecimal("0.33")),

    OBESITY("Obesidade", new BigDecimal("30.0"), null,
            new BigDecimal("5.0"), new BigDecimal("9.0"),
            new BigDecimal("0.17"), new BigDecimal("0.27"));

    /** Gain expected in the first trimester, the same for every band. */
    public static final BigDecimal FIRST_TRIMESTER_MIN = new BigDecimal("0.5");
    public static final BigDecimal FIRST_TRIMESTER_MAX = new BigDecimal("2.0");
    private static final int FIRST_TRIMESTER_END = 13;

    private final String description;
    private final BigDecimal bmiMin;
    private final BigDecimal bmiMax;
    private final BigDecimal totalGainMin;
    private final BigDecimal totalGainMax;
    private final BigDecimal byWeekMin;
    private final BigDecimal byWeekMax;

    GestationalGain(String description, BigDecimal bmiMin, BigDecimal bmiMax,
                     BigDecimal totalGainMin, BigDecimal totalGainMax,
                     BigDecimal byWeekMin, BigDecimal byWeekMax) {
        this.description = description;
        this.bmiMin = bmiMin;
        this.bmiMax = bmiMax;
        this.totalGainMin = totalGainMin;
        this.totalGainMax = totalGainMax;
        this.byWeekMin = byWeekMin;
        this.byWeekMax = byWeekMax;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotalGainMin() {
        return totalGainMin;
    }

    public BigDecimal getTotalGainMax() {
        return totalGainMax;
    }

    /** Band by the BMI before pregnancy. */
    public static GestationalGain byBmiGestationalPre(BigDecimal bmi) {
        if (bmi == null) {
            return null;
        }
        for (GestationalGain range : values()) {
            boolean minimumAbove = range.bmiMin == null || bmi.compareTo(range.bmiMin) >= 0;
            boolean maximumBelow = range.bmiMax == null || bmi.compareTo(range.bmiMax) < 0;
            if (minimumAbove && maximumBelow) {
                return range;
            }
        }
        return null;
    }

    /**
     * Gain expected up to the reported week.
     *
     * The first trimester stands apart: the gain there is small and the same
     * for every band. From the 14th week on, the band's weekly rate takes over.
     */
    public BigDecimal expectedMin(int week) {
        return accumulated(week, FIRST_TRIMESTER_MIN, byWeekMin);
    }

    public BigDecimal expectedMax(int week) {
        return accumulated(week, FIRST_TRIMESTER_MAX, byWeekMax);
    }

    private BigDecimal accumulated(int week, BigDecimal firstTrimester, BigDecimal byWeek) {
        if (week <= FIRST_TRIMESTER_END) {
            // Inside the first trimester the gain is proportional to how far
            // along it is: demanding 0.5 kg in the fourth week would be asking
            // too early.
            return firstTrimester
                    .multiply(BigDecimal.valueOf(week))
                    .divide(BigDecimal.valueOf(FIRST_TRIMESTER_END), 2, RoundingMode.HALF_UP);
        }
        int weeksAfter = week - FIRST_TRIMESTER_END;
        return firstTrimester
                .add(byWeek.multiply(BigDecimal.valueOf(weeksAfter)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
