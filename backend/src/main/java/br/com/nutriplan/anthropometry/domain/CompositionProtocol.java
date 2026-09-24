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
            return sumOnly(skinfolds, skinfoldsRequired(sex)) * 0.153 + 5.783;
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
        public Double density(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double sums = sumOnly(skinfolds, skinfoldsRequired(sex));
            return sex == Sex.MALE
                    ? 1.10938 - 0.0008267 * sums + 0.0000016 * sums * sums - 0.0002574 * age
                    : 1.0994921 - 0.0009929 * sums + 0.0000023 * sums * sums - 0.0001392 * age;
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
        public Double density(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double sums = sumOnly(skinfolds, skinfoldsRequired(sex));
            return sex == Sex.MALE
                    ? 1.112 - 0.00043499 * sums + 0.00000055 * sums * sums - 0.00028826 * age
                    : 1.097 - 0.00046971 * sums + 0.00000056 * sums * sums - 0.00012828 * age;
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
        public Double density(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double log = Math.log10(sumOnly(skinfolds, skinfoldsRequired(sex)));
            return sex == Sex.MALE
                    ? 1.1765 - 0.0744 * log
                    : 1.1567 - 0.0717 * log;
        }
    },

    /**
     * Petroski (1995) — quatro dobras, desenvolvido com adultos brasileiros
     * (homens de 18 a 66 anos, mulheres de 18 a 51). As dobras mudam com o
     * sexo, e a idade entra na equação.
     *
     * O cliente pediu junto com Guedes: são os dois protocolos nacionais que o
     * WebDiet oferece e que faltavam aqui.
     */
    PETROSKI(
            "Petroski",
            EnumSet.noneOf(Skinfold.class), // depends on the sex; see skinfoldsRequired
            true, true) {
        @Override
        public Set<Skinfold> skinfoldsRequired(Sex sex) {
            return sex == Sex.MALE
                    ? EnumSet.of(Skinfold.SUBSCAPULAR, Skinfold.TRICEPS, Skinfold.SUPRAILIAC, Skinfold.CALF)
                    : EnumSet.of(Skinfold.MEAN_AXILLARY, Skinfold.SUPRAILIAC, Skinfold.THIGH, Skinfold.CALF);
        }

        @Override
        public Double density(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double sums = sumOnly(skinfolds, skinfoldsRequired(sex));
            return sex == Sex.MALE
                    ? 1.10726863 - 0.00081201 * sums + 0.00000212 * sums * sums - 0.00041761 * age
                    : 1.19547130 - 0.07513507 * Math.log10(sums) - 0.00041072 * age;
        }
    },

    /**
     * Guedes (1985) — três dobras, desenvolvido com adultos jovens brasileiros.
     * As dobras mudam com o sexo; a idade não entra.
     */
    GUEDES(
            "Guedes",
            EnumSet.noneOf(Skinfold.class), // depends on the sex; see skinfoldsRequired
            true, false) {
        @Override
        public Set<Skinfold> skinfoldsRequired(Sex sex) {
            return sex == Sex.MALE
                    ? EnumSet.of(Skinfold.TRICEPS, Skinfold.SUPRAILIAC, Skinfold.ABDOMINAL)
                    : EnumSet.of(Skinfold.THIGH, Skinfold.SUPRAILIAC, Skinfold.SUBSCAPULAR);
        }

        @Override
        public Double density(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
            double log = Math.log10(sumOnly(skinfolds, skinfoldsRequired(sex)));
            return sex == Sex.MALE
                    ? 1.17136 - 0.06706 * log
                    : 1.16650 - 0.07063 * log;
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

    /**
     * The fat percentage. The density protocols pass through Siri; the ones
     * that answer the percentage directly override this instead.
     */
    public double fatPercentage(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
        Double density = density(skinfolds, sex, age);
        if (density == null) {
            throw new IllegalStateException(name() + " neither estimates density nor overrides fatPercentage");
        }
        return siri(density);
    }

    /**
     * Body density in g/cm³, for the protocols that go through it. Null for
     * the ones that answer the percentage directly (Faulkner).
     */
    public Double density(Map<Skinfold, Double> skinfolds, Sex sex, Integer age) {
        return null;
    }

    /** The sum of the skinfolds this protocol reads, in millimetres. */
    public double skinfoldSum(Map<Skinfold, Double> skinfolds, Sex sex) {
        return sumOnly(skinfolds, skinfoldsRequired(sex));
    }

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
