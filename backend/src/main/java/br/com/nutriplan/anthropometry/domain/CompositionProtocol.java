package br.com.nutriplan.anthropometry.domain;

import br.com.nutriplan.patient.domain.Sex;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Protocols for estimating body composition from skinfolds.
 *
 * Each protocol declares which skinfolds it requires and whether it depends on
 * sex and age. That lets the system refuse the estimate while naming what is
 * missing, instead of completing the calculation with an absent skinfold —
 * which would invent body composition.
 *
 * The protocols that estimate body density convert to a fat percentage through
 * the Siri equation. Faulkner returns the percentage directly.
 *
 * The protocol applied is stored with the result: assessments estimated by
 * different protocols are not comparable with each other, because each has its
 * own standard error, and the difference between them would be read as a change
 * in the patient.
 */
public enum CompositionProtocol {

    /**
     * Faulkner — four skinfolds, direct percentage, with no dependence on age.
     * Simple and widely used in Brazilian clinical practice.
     */
    FAULKNER(
            "Faulkner",
            EnumSet.of(Skinfold.TRICEPS, Skinfold.SUBSCAPULAR, Skinfold.SUPRAILIAC, Skinfold.ABDOMINAL),
            false, false) {
        @Override
        public double fatPercentage(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            return sum(skinfolds) * 0.153 + 5.783;
        }
    },

    /**
     * Jackson and Pollock — three skinfolds, sex-specific, with age correction.
     */
    POLLOCK_3(
            "Pollock 3 dobras",
            EnumSet.noneOf(Skinfold.class), // depends on the sex; see skinfoldsRequired
            true, true) {
        @Override
        public Set<Skinfold> skinfoldsRequired(Sex sex) {
            return sex == Sex.MALE
                    ? EnumSet.of(Skinfold.CHEST, Skinfold.ABDOMINAL, Skinfold.THIGH)
                    : EnumSet.of(Skinfold.TRICEPS, Skinfold.SUPRAILIAC, Skinfold.THIGH);
        }

        @Override
        public double fatPercentage(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double sums = sumOnly(skinfolds, skinfoldsRequired(sex));
            double density = sex == Sex.MALE
                    ? 1.10938 - 0.0008267 * sums + 0.0000016 * sums * sums - 0.0002574 * age
                    : 1.0994921 - 0.0009929 * sums + 0.0000023 * sums * sums - 0.0001392 * age;
            return siri(density);
        }
    },

    /**
     * Jackson and Pollock — seven skinfolds. More measurements, smaller
     * standard error.
     */
    POLLOCK_7(
            "Pollock 7 dobras",
            EnumSet.of(Skinfold.CHEST, Skinfold.MEAN_AXILLARY, Skinfold.TRICEPS, Skinfold.SUBSCAPULAR,
                    Skinfold.ABDOMINAL, Skinfold.SUPRAILIAC, Skinfold.THIGH),
            true, true) {
        @Override
        public double fatPercentage(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double sums = sumOnly(skinfolds, skinfoldsRequired(sex));
            double density = sex == Sex.MALE
                    ? 1.112 - 0.00043499 * sums + 0.00000055 * sums * sums - 0.00028826 * age
                    : 1.097 - 0.00046971 * sums + 0.00000056 * sums * sums - 0.00012828 * age;
            return siri(density);
        }
    },

    /**
     * Durnin and Womersley — four skinfolds, with no age correction on the sum.
     * The coefficients vary by age band; the adult band is the one used here.
     */
    DURNIN_WOMERSLEY(
            "Durnin e Womersley",
            EnumSet.of(Skinfold.BICEPS, Skinfold.TRICEPS, Skinfold.SUBSCAPULAR, Skinfold.SUPRAILIAC),
            true, false) {
        @Override
        public double fatPercentage(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double log = Math.log10(sum(skinfolds));
            double density = sex == Sex.MALE
                    ? 1.1765 - 0.0744 * log
                    : 1.1567 - 0.0717 * log;
            return siri(density);
        }
    };

    private final String description;
    private final Set<Skinfold> skinfoldsFixed;
    private final boolean requiresSex;
    private final boolean requiresAge;

    CompositionProtocol(String description, Set<Skinfold> skinfoldsFixed,
                        boolean requiresSex, boolean requiresAge) {
        this.description = description;
        this.skinfoldsFixed = skinfoldsFixed;
        this.requiresSex = requiresSex;
        this.requiresAge = requiresAge;
    }

    public String getDescription() {
        return description;
    }

    public boolean requiresSex() {
        return requiresSex;
    }

    public boolean requiresAge() {
        return requiresAge;
    }

    /** Required skinfolds. Some protocols vary the set according to sex. */
    public Set<Skinfold> skinfoldsRequired(Sex sex) {
        return skinfoldsFixed;
    }

    /** Which of the required skinfolds were not measured. */
    public List<Skinfold> skinfoldsMissing(Map<Skinfold, Double> skinfolds, Sex sex) {
        return skinfoldsRequired(sex).stream()
                .filter(d -> skinfolds.get(d) == null || skinfolds.get(d) <= 0)
                .toList();
    }

    public abstract double fatPercentage(Map<Skinfold, Double> skinfolds, Sex sex, Integer age);

    /**
     * Siri — converts body density into a percentage of fat.
     */
    protected static double siri(double density) {
        return (4.95 / density - 4.50) * 100;
    }

    protected static double sum(Map<Skinfold, Double> skinfolds) {
        return skinfolds.values().stream().filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }

    protected static double sumOnly(Map<Skinfold, Double> skinfolds, Set<Skinfold> which) {
        return which.stream()
                .map(skinfolds::get)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }
}
