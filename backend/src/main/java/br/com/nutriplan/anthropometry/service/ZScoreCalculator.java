package br.com.nutriplan.anthropometry.service;

import br.com.nutriplan.anthropometry.domain.GrowthChart;
import br.com.nutriplan.anthropometry.domain.GrowthIndicator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Z-score by the WHO LMS method.
 *
 * The distribution of an anthropometric indicator is not normal — it is skewed,
 * and the skew changes with age. The LMS method describes that with three
 * numbers per age: L (the transformation that normalizes), M (the median) and S
 * (the coefficient of variation). With them the z-score comes out by formula,
 * and not by looking up a table of cutoff points:
 *
 * <pre>
 *   z = ((value / M)^L − 1) / (L × S)      when L ≠ 0
 *   z = ln(value / M) / S                  when L = 0
 * </pre>
 *
 * The practical advantage is saying "z-score −2.3" instead of "between −3 and
 * −2".
 */
@Component
public class ZScoreCalculator {

    /** Scale of the result: two decimals is how the z-score is read in the clinic. */
    private static final int SCALE = 2;

    /**
     * @return the z-score, or null when the value does not allow the calculation
     */
    public BigDecimal calculate(GrowthChart chart, BigDecimal value) {
        if (chart == null || value == null || value.signum() <= 0) {
            return null;
        }
        double l = chart.getL().doubleValue();
        double m = chart.getM().doubleValue();
        double s = chart.getS().doubleValue();
        double y = value.doubleValue();

        double z = raw(y, l, m, s);

        // The WHO correction for the tails.
        //
        // Beyond three deviations, the LMS formula produces absurd values in
        // weight-based indicators: the tail of the distribution is long, and
        // the Box-Cox transformation exaggerates what falls outside it. In
        // those cases the WHO prescribes extrapolating linearly, using the
        // distance between the second and the third deviation as the unit.
        if (chart.getIndicator().requiresTailsCorrection()) {
            if (z > 3) {
                double sd3 = valueNoDeviation(3, l, m, s);
                double sd2 = valueNoDeviation(2, l, m, s);
                z = 3 + (y - sd3) / (sd3 - sd2);
            } else if (z < -3) {
                double sd3 = valueNoDeviation(-3, l, m, s);
                double sd2 = valueNoDeviation(-2, l, m, s);
                z = -3 + (y - sd3) / (sd2 - sd3);
            }
        }

        if (Double.isNaN(z) || Double.isInfinite(z)) {
            return null;
        }
        return BigDecimal.valueOf(z).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private double raw(double y, double l, double m, double s) {
        if (l == 0) {
            return Math.log(y / m) / s;
        }
        return (Math.pow(y / m, l) - 1) / (l * s);
    }

    /** The value of the indicator at the requested standard deviation — the inverted LMS formula. */
    private double valueNoDeviation(int deviations, double l, double m, double s) {
        if (l == 0) {
            return m * Math.exp(s * deviations);
        }
        return m * Math.pow(1 + l * s * deviations, 1 / l);
    }

    /**
     * Which curve serves this age.
     *
     * It returns the indicator and the age in months, already clamped to the
     * reach of the tables. Above 228 months — nineteen years — there is no
     * curve: from there on the adult classification is what holds.
     */
    public static boolean ageHasChart(Integer months) {
        return months != null && months >= 0 && months <= 228;
    }

    /** Indicators calculated from weight and height. */
    public static GrowthIndicator[] availableIndicators() {
        return GrowthIndicator.values();
    }
}
