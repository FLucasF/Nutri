package br.com.nutriplan.anthropometry.domain;

import br.com.nutriplan.patient.domain.Sex;

/**
 * Predictive equations for basal energy expenditure.
 *
 * The equation used is stored together with the result. Without that, an old
 * assessment would change value if the system started adopting another equation
 * — and the patient's history would stop being comparable with itself.
 *
 * They all depend on weight, height, sex and age.
 */
public enum EnergyExpenditureEquation {

    /** Mifflin-St Jeor. Good accuracy in the general adult population. */
    MIFFLIN_ST_JEOR("Mifflin-St Jeor") {
        @Override
        public double basal(double weightKg, double heightCm, Sex sex, int age) {
            double common = 10 * weightKg + 6.25 * heightCm - 5 * age;
            return sex == Sex.MALE ? common + 5 : common - 161;
        }
    },

    /** Harris-Benedict, in the revised form. */
    HARRIS_BENEDICT("Harris-Benedict revisada") {
        @Override
        public double basal(double weightKg, double heightCm, Sex sex, int age) {
            return sex == Sex.MALE
                    ? 88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age
                    : 447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.330 * age;
        }
    };

    private final String description;

    EnergyExpenditureEquation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Basal energy expenditure, in kcal per day. */
    public abstract double basal(double weightKg, double heightCm, Sex sex, int age);
}
