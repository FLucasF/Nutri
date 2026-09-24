package br.com.nutriplan.energy.domain;

import br.com.nutriplan.patient.domain.Sex;

/**
 * The energy equations the practice can apply.
 *
 * Two families live here, and telling them apart is the whole reason this is
 * an enum with a flag instead of a list of formulas:
 *
 *   - a <b>basal</b> equation answers what the body spends at rest. The
 *     activity factor multiplies it afterwards, and so does the injury factor.
 *   - a <b>total</b> equation answers what the person spends in a day. The
 *     activity level is already inside it, in the coefficients, and
 *     multiplying it again would count the same movement twice.
 *
 * The client asked for four: Harris-Benedict 1984, FAO/WHO 2004, EER 2005 and
 * EER 2023. Mifflin-St Jeor is here because the system already had it and
 * removing it would take a calculation away from records that used it.
 *
 * Every coefficient below was checked against the published source, not
 * recalled. The child bands are part of the equations, not an adaptation.
 */
public enum EnergyEquation {

    /**
     * Harris-Benedict, in the revision by Roza and Shizgal (1984).
     *
     * It is the 1984 one the client asked for: the intercepts 88.362 and
     * 447.593 are the revision's, not the 1919 original's.
     */
    HARRIS_BENEDICT_1984("Harris-Benedict (1984)", false, 0) {
        @Override
        double compute(Input in) {
            return in.male()
                    ? 88.362 + 13.397 * in.weightKg() + 4.799 * in.heightCm() - 5.677 * in.age()
                    : 447.593 + 9.247 * in.weightKg() + 3.098 * in.heightCm() - 4.330 * in.age();
        }
    },

    MIFFLIN_ST_JEOR("Mifflin-St Jeor (1990)", false, 0) {
        @Override
        double compute(Input in) {
            double common = 10 * in.weightKg() + 6.25 * in.heightCm() - 5 * in.age();
            return in.male() ? common + 5 : common - 161;
        }
    },

    /**
     * FAO/WHO/UNU (2004), which adopts the Schofield (1985) equations.
     *
     * Weight and age only: height does not enter. The bands are the
     * publication's, including the paediatric ones, which is why this equation
     * serves a child without a separate entry.
     */
    FAO_WHO_2004("FAO/WHO/UNU (2004)", false, 0) {
        @Override
        double compute(Input in) {
            double w = in.weightKg();
            int age = in.age();
            if (in.male()) {
                if (age < 3) return 59.512 * w - 30.4;
                if (age < 10) return 22.706 * w + 504.3;
                if (age < 18) return 17.686 * w + 658.2;
                if (age < 30) return 15.057 * w + 692.2;
                if (age < 60) return 11.472 * w + 873.1;
                return 11.711 * w + 587.7;
            }
            if (age < 3) return 58.317 * w - 31.1;
            if (age < 10) return 20.315 * w + 485.9;
            if (age < 18) return 13.384 * w + 692.6;
            if (age < 30) return 14.818 * w + 486.6;
            if (age < 60) return 8.126 * w + 845.6;
            return 9.082 * w + 658.5;
        }
    },

    /**
     * EER of the 2005 DRI (IOM).
     *
     * Total expenditure. Height enters in metres here, unlike every other
     * equation in this file — that is the publication's form, and converting
     * it to centimetres "for consistency" is how a factor of a hundred gets
     * into a prescription.
     *
     * For children it adds 20 kcal of energy deposition: a growing body stores
     * energy, and that is part of what it requires.
     */
    EER_IOM_2005("EER/IOM (2005)", true, 3) {
        @Override
        double compute(Input in) {
            double w = in.weightKg();
            double h = in.heightCm() / 100.0;
            int age = in.age();
            double pa = in.activity().physicalActivityCoefficient(in.male());

            if (age < 19) {
                return in.male()
                        ? 88.5 - 61.9 * age + paChild(in, true) * (26.7 * w + 903 * h) + 20
                        : 135.3 - 30.8 * age + paChild(in, false) * (10.0 * w + 934 * h) + 20;
            }
            return in.male()
                    ? 662 - 9.53 * age + pa * (15.91 * w + 539.6 * h)
                    : 354 - 6.91 * age + pa * (9.36 * w + 726 * h);
        }

        /** The 2005 report gives children their own PA coefficients. */
        private double paChild(Input in, boolean male) {
            return switch (in.activity()) {
                case INACTIVE -> 1.00;
                case LOW_ACTIVE -> male ? 1.13 : 1.16;
                case ACTIVE -> male ? 1.26 : 1.31;
                case VERY_ACTIVE -> male ? 1.42 : 1.56;
            };
        }
    },

    /**
     * EER of the 2023 DRI (NASEM).
     *
     * Total expenditure, and a different equation per activity level rather
     * than a multiplier — which is why {@link ActivityLevel} is a category and
     * not a number. Height in centimetres here.
     */
    EER_2023("EER (2023)", true, 3) {
        @Override
        double compute(Input in) {
            double w = in.weightKg();
            double h = in.heightCm();
            int age = in.age();

            if (age < 19) {
                return in.male()
                        ? switch (in.activity()) {
                            case INACTIVE -> -447.51 + 3.68 * age + 13.01 * h + 13.15 * w;
                            case LOW_ACTIVE -> 19.12 + 3.68 * age + 8.62 * h + 20.28 * w;
                            case ACTIVE -> -388.19 + 3.68 * age + 12.66 * h + 20.46 * w;
                            case VERY_ACTIVE -> -671.75 + 3.68 * age + 15.38 * h + 23.25 * w;
                        }
                        : switch (in.activity()) {
                            case INACTIVE -> 55.59 - 22.25 * age + 8.43 * h + 17.07 * w;
                            case LOW_ACTIVE -> -297.54 - 22.25 * age + 12.77 * h + 14.73 * w;
                            case ACTIVE -> -189.55 - 22.25 * age + 11.74 * h + 18.34 * w;
                            case VERY_ACTIVE -> -709.59 - 22.25 * age + 18.22 * h + 14.25 * w;
                        };
            }
            return in.male()
                    ? switch (in.activity()) {
                        case INACTIVE -> 753.07 - 10.83 * age + 6.50 * h + 14.10 * w;
                        case LOW_ACTIVE -> 581.47 - 10.83 * age + 8.30 * h + 14.94 * w;
                        case ACTIVE -> 1004.82 - 10.83 * age + 6.52 * h + 15.91 * w;
                        case VERY_ACTIVE -> -517.88 - 10.83 * age + 15.61 * h + 19.11 * w;
                    }
                    : switch (in.activity()) {
                        case INACTIVE -> 584.90 - 7.01 * age + 5.72 * h + 11.71 * w;
                        case LOW_ACTIVE -> 575.77 - 7.01 * age + 6.60 * h + 12.14 * w;
                        case ACTIVE -> 710.25 - 7.01 * age + 6.54 * h + 12.34 * w;
                        case VERY_ACTIVE -> 511.83 - 7.01 * age + 9.07 * h + 12.56 * w;
                    };
        }
    },

    /**
     * Katch-McArdle: 370 + 21,6 × massa magra.
     *
     * Basal, e pela massa livre de gordura em vez do peso: é a que serve
     * quando o peso engana — muito músculo ou muita gordura para a altura.
     * Não usa sexo nem idade; eles já estão na composição.
     */
    KATCH_MCARDLE("Katch-McArdle (massa magra)", false, 18, true) {
        @Override
        double compute(Input in) {
            return 370 + 21.6 * in.lean();
        }
    },

    /** Cunningham (1980): 500 + 22 × massa magra. Basal. */
    CUNNINGHAM("Cunningham (1980, massa magra)", false, 18, true) {
        @Override
        double compute(Input in) {
            return 500 + 22 * in.lean();
        }
    },

    /**
     * Tinsley et al. (2019), pelo peso: 24,8 × peso + 10.
     *
     * Desenvolvida em atletas de força e fisiculturismo, para quem Harris e
     * Mifflin subestimam o repouso. Basal.
     */
    TINSLEY_WEIGHT("Tinsley (2019, peso)", false, 18, false) {
        @Override
        double compute(Input in) {
            return 24.8 * in.weightKg() + 10;
        }
    },

    /** Tinsley et al. (2019), pela massa magra: 25,9 × massa magra + 284. Basal. */
    TINSLEY_LEAN("Tinsley (2019, massa magra)", false, 18, true) {
        @Override
        double compute(Input in) {
            return 25.9 * in.lean() + 284;
        }
    };

    /** What an equation needs to answer. */
    public record Input(
            double weightKg,
            double heightCm,
            Sex sex,
            int age,
            ActivityLevel activity,
            /** Massa livre de gordura, em kg. Nula quando não há composição. */
            Double leanMassKg
    ) {
        public Input(double weightKg, double heightCm, Sex sex, int age, ActivityLevel activity) {
            this(weightKg, heightCm, sex, age, activity, null);
        }

        public boolean male() {
            return sex == Sex.MALE;
        }

        double lean() {
            if (leanMassKg == null) {
                throw new IllegalStateException("Equação por massa magra sem massa magra.");
            }
            return leanMassKg;
        }
    }

    private final String description;
    private final boolean total;
    private final int ageMinimum;
    private final boolean leanMass;

    EnergyEquation(String description, boolean total, int ageMinimum) {
        this(description, total, ageMinimum, false);
    }

    EnergyEquation(String description, boolean total, int ageMinimum, boolean leanMass) {
        this.description = description;
        this.total = total;
        this.ageMinimum = ageMinimum;
        this.leanMass = leanMass;
    }

    /** Parte da massa magra, e não do peso: precisa de uma composição corporal. */
    public boolean requiresLeanMass() {
        return leanMass;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Whether the result already includes activity.
     *
     * A {@code true} here means the activity factor must not be applied again
     * on the outside.
     */
    public boolean isTotal() {
        return total;
    }

    /** Whether the equation was published for someone of this age. */
    public boolean servesAge(int age) {
        return age >= ageMinimum;
    }

    /** The youngest age the equation was published for. */
    public int getAgeMinimum() {
        return ageMinimum;
    }

    abstract double compute(Input in);

    /** The basal expenditure, for a basal equation. Zero for a total one. */
    public double basal(Input in) {
        return total ? 0 : compute(in);
    }

    /**
     * The total daily expenditure, with activity already counted.
     *
     * A basal equation is multiplied by the level's factor; a total equation
     * answers on its own, because the level is already in its coefficients.
     */
    public double totalExpenditure(Input in) {
        return total ? compute(in) : compute(in) * in.activity().basalFactor();
    }
}
