package br.com.nutriplan.energy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.nutriplan.energy.domain.ActivityLevel;
import br.com.nutriplan.energy.domain.EnergyEquation;
import br.com.nutriplan.energy.domain.EnergyPlan;
import br.com.nutriplan.energy.domain.EnergyPlanEquation;
import br.com.nutriplan.patient.domain.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EnergyDtos {

    private EnergyDtos() {
    }

    public record EnergyPlanRequest(
            @NotNull Long patientId,
            @Size(max = 150) String name,
            @NotNull LocalDate date,
            /** Assessment to copy weight and height from. Optional. */
            Long assessmentId,
            @DecimalMin("10.0") @DecimalMax("400.0") BigDecimal weightKg,
            @DecimalMin("40.0") @DecimalMax("260.0") BigDecimal heightCm,
            @NotNull ActivityLevel activityLevel,
            @DecimalMin("1.0") @DecimalMax("2.5") BigDecimal injuryFactor,
            @DecimalMin("0.0") @DecimalMax("5000.0") BigDecimal metKcal,
            @NotEmpty List<EnergyEquation> equations,
            @DecimalMin("10.0") @DecimalMax("400.0") BigDecimal targetWeightKg,
            LocalDate targetDate,
            @Size(max = 8000) String notes
    ) {}

    public record EquationResult(
            EnergyEquation equation,
            String description,
            /** Null for an equation that answers the daily total directly. */
            BigDecimal basalKcal,
            BigDecimal totalKcal
    ) {
        public static EquationResult from(EnergyPlanEquation applied) {
            return new EquationResult(applied.getEquation(),
                    applied.getEquation().getDescription(),
                    applied.getBasalKcal(), applied.getTotalKcal());
        }
    }

    /**
     * A faixa de peso que mantém o adulto em eutrofia.
     *
     * O cliente conta que fecha a aba do cardápio e vai consultar isso na
     * antropometria toda vez. Vem junto do cálculo por isso.
     */
    public record HealthyWeight(
            BigDecimal minimumKg,
            BigDecimal maximumKg,
            BigDecimal bmiMinimum,
            BigDecimal bmiMaximum
    ) {}

    public record EnergyPlanResponse(
            Long id,
            Long patientId,
            String name,
            LocalDate date,
            Long assessmentId,
            BigDecimal weightKg,
            BigDecimal heightCm,
            Integer ageYears,
            Sex sex,
            ActivityLevel activityLevel,
            String activityDescription,
            BigDecimal injuryFactor,
            BigDecimal metKcal,
            BigDecimal targetWeightKg,
            LocalDate targetDate,
            BigDecimal adjustmentKcal,
            BigDecimal averageKcal,
            BigDecimal prescribedKcal,
            List<EquationResult> equations,
            HealthyWeight healthyWeight,
            /**
             * O que merece um segundo olhar antes de usar o número: a
             * programação de peso que desconta mais de 1 kg por semana, o
             * prescrito abaixo do basal, o resultado zerado. São avisos, não
             * recusas — o cliente pediu "só avisar, nada de limitar".
             */
            List<String> warnings,
            String notes
    ) {
        public static EnergyPlanResponse from(EnergyPlan plan, HealthyWeight healthyWeight,
                                              List<String> warnings) {
            return new EnergyPlanResponse(
                    plan.getId(), plan.getPatientId(), plan.getName(), plan.getDate(),
                    plan.getAssessmentId(), plan.getWeightKg(), plan.getHeightCm(),
                    plan.getAgeYears(), plan.getSex(),
                    plan.getActivityLevel(), plan.getActivityLevel().getDescription(),
                    plan.getInjuryFactor(), plan.getMetKcal(),
                    plan.getTargetWeightKg(), plan.getTargetDate(), plan.getAdjustmentKcal(),
                    plan.getAverageKcal(), plan.getPrescribedKcal(),
                    plan.getEquations().stream().map(EquationResult::from).toList(),
                    healthyWeight, warnings, plan.getNotes());
        }
    }

    public record EnergyPlanSummary(
            Long id,
            String name,
            LocalDate date,
            BigDecimal prescribedKcal,
            List<String> equations
    ) {
        public static EnergyPlanSummary from(EnergyPlan plan) {
            return new EnergyPlanSummary(plan.getId(), plan.getName(), plan.getDate(),
                    plan.getPrescribedKcal(),
                    plan.getEquations().stream()
                            .map(applied -> applied.getEquation().getDescription())
                            .toList());
        }
    }

    /** What the screen offers in the equation and activity pickers. */
    public record OptionsResponse(
            List<EquationOption> equations,
            List<ActivityOption> activityLevels
    ) {}

    public record EquationOption(
            EnergyEquation equation,
            String description,
            /** Whether the activity level is already inside the result. */
            boolean total,
            /** Youngest age the equation was published for. */
            int ageMinimum
    ) {}

    public record ActivityOption(ActivityLevel level, String description) {}
}
