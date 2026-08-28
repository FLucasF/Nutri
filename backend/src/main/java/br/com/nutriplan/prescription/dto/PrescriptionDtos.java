package br.com.nutriplan.prescription.dto;

import br.com.nutriplan.food.dto.CompositionDto;
import br.com.nutriplan.prescription.domain.PrescriptionMethod;
import br.com.nutriplan.prescription.domain.PlanStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Input and output contracts of the prescription module. */
public final class PrescriptionDtos {

    private PrescriptionDtos() {
    }

    // -------------------------------------------------------------------- input

    public record SubstitutionRequest(
            Long foodId,
            Long measureId,
            @NotBlank @Size(max = 250) String description,
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantity
    ) {}

    public record ItemRequest(
            Long foodId,
            Long measureId,
            @Size(max = 250) String description,
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantity,
            @Size(max = 500) String notes,
            @Valid List<SubstitutionRequest> substitutions
    ) {}

    public record MealRequest(
            @NotBlank @Size(max = 100) String name,
            LocalTime time,
            @Size(max = 1000) String notes,
            @Valid List<ItemRequest> items
    ) {}

    public record PlanRequest(
            @NotBlank @Size(max = 150) String title,
            Long patientId,
            @NotNull PrescriptionMethod method,
            LocalDate validityStart,
            LocalDate validityEnd,
            @Size(max = 4000) String handouts,
            @Size(max = 4000) String internalNotes,
            @DecimalMin(value = "0.0", message = "A meta energética não pode ser negativa")
            BigDecimal targetEnergyKcal,
            boolean template,
            @Valid List<MealRequest> meals
    ) {}

    /** Creating a plan from an existing template. */
    public record ApplyTemplateRequest(
            @NotNull Long templateId,
            @NotNull Long patientId,
            @Size(max = 150) String title
    ) {}

    // ------------------------------------------------------------------ output

    /**
     * Nutritional total with its own margin of confidence.
     *
     * `nutrientsIncomplete` lists what was summed from only part of the items —
     * in those cases the value is a floor, and the interface has to say so
     * instead of showing a number that looks exact.
     */
    public record TotalResponse(
            CompositionDto composition,
            int itemsInCalculation,
            int itemsOutsideCalculation,
            Set<String> nutrientsIncomplete,
            Set<String> nutrientsWithoutDatum,
            boolean reliable,
            DistributionResponse distribution,
            BigDecimal adequacyEnergyPct
    ) {}

    public record DistributionResponse(
            BigDecimal proteinPct,
            BigDecimal carbohydratePct,
            BigDecimal lipidPct,
            BigDecimal calculatedEnergyKcal
    ) {}

    public record SubstitutionResponse(
            Long id,
            Long foodId,
            String description,
            String serving,
            BigDecimal grams
    ) {}

    public record ItemResponse(
            Long id,
            Long foodId,
            Long measureId,
            String description,
            String serving,
            BigDecimal quantity,
            BigDecimal grams,
            Integer order,
            String notes,
            List<SubstitutionResponse> substitutions
    ) {}

    public record MealResponse(
            Long id,
            String name,
            LocalTime time,
            Integer order,
            String notes,
            List<ItemResponse> items,
            TotalResponse total
    ) {}

    /** The complete plan, as the nutritionist sees it. */
    public record PlanResponse(
            Long id,
            String title,
            Long patientId,
            String patientName,
            PrescriptionMethod method,
            String methodDescription,
            PlanStatus status,
            String statusDescription,
            String publicIdentifier,
            LocalDate validityStart,
            LocalDate validityEnd,
            String handouts,
            String internalNotes,
            BigDecimal targetEnergyKcal,
            boolean template,
            List<MealResponse> meals,
            TotalResponse dayTotal,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    /** A listing row. */
    public record PlanSummary(
            Long id,
            String title,
            Long patientId,
            String patientName,
            PrescriptionMethod method,
            PlanStatus status,
            LocalDate validityStart,
            LocalDate validityEnd,
            boolean template,
            int meals,
            int items,
            BigDecimal energyKcal,
            java.time.Instant updatedAt
    ) {}

    /**
     * The plan as the patient sees it, through the link.
     *
     * Deliberately different from PlanResponse: no internal notes, no account
     * identifiers and none of the technical warnings about data coverage. The
     * patient receives what to eat, not the diagnosis of the nutritional base.
     */
    public record PublicPlanResponse(
            String title,
            String patientName,
            String nutritionistName,
            String nutritionistCrn,
            String practiceName,
            String primaryColor,
            String logoUrl,
            PrescriptionMethod method,
            boolean current,
            boolean closed,
            LocalDate validityStart,
            LocalDate validityEnd,
            String handouts,
            /** Handouts attached to the plan, in the text frozen at attachment time. */
            List<PublicHandoutResponse> handoutsAttached,
            List<PublicMealResponse> meals,
            PublicSummaryResponse summary
    ) {}

    /**
     * A handout delivered in the plan.
     *
     * {@code image} is the address of the figure, ready for the {@code src},
     * null when there is no figure. It is assembled here, and not in the
     * browser, so that the patient's page does not need to know the shape of
     * the route.
     */
    public record PublicHandoutResponse(Long id, String title, String body,
                                            String image) {}

    public record PublicMealResponse(
            String name,
            LocalTime time,
            String notes,
            List<PublicItemResponse> items
    ) {}

    public record PublicItemResponse(
            String description,
            String serving,
            /**
             * Portion weight. It travels together with the household measure
             * text because the patient's page shows the two with different
             * visual weight: the measure large, because it is the instruction,
             * and the grams small, because they are the record. Null when the
             * item has no defined weight ("as desired").
             */
            BigDecimal weightGrams,
            String notes,
            List<PublicSubstitutionResponse> substitutions
    ) {}

    public record PublicSubstitutionResponse(String description, String serving) {}

    /** Summary of the day in the patient's language: only the essentials. */
    public record PublicSummaryResponse(
            BigDecimal energyKcal,
            BigDecimal proteinG,
            BigDecimal carbohydrateG,
            BigDecimal fatG,
            int meals
    ) {}
}
