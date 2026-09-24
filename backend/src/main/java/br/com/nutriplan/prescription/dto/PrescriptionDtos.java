package br.com.nutriplan.prescription.dto;

import br.com.nutriplan.food.dto.CompositionDto;
import br.com.nutriplan.prescription.domain.AdequacyBand;
import br.com.nutriplan.prescription.domain.EnergyDensityBand;
import br.com.nutriplan.prescription.domain.MealItemKind;
import br.com.nutriplan.prescription.service.NutritionalCalculator;
import br.com.nutriplan.prescription.domain.PrescriptionMethod;
import br.com.nutriplan.prescription.domain.PlanStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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
            /** FOOD por omissão. SEPARATOR desenha a barra entre alimentos. */
            MealItemKind kind,
            Long foodId,
            Long measureId,
            @Size(max = 250) String description,
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantity,
            /** "À vontade": sem quantidade e fora do somatório. Nulo vale como não. */
            Boolean adLibitum,
            @Size(max = 8000) String notes,
            @Valid List<SubstitutionRequest> substitutions
    ) {
        public MealItemKind kindOrFood() {
            return kind == null ? MealItemKind.FOOD : kind;
        }

        public boolean isAdLibitum() {
            return adLibitum != null && adLibitum;
        }
    }

    public record MealRequest(
            @NotBlank @Size(max = 250) String name,
            LocalTime time,
            /** Observações da refeição, no formato do editor. */
            @Size(max = 8000) String notes,
            /**
             * Se a refeição soma no dia. Nulo vale como sim.
             *
             * Desligar é como se prescreve refeição substituta: duas opções de
             * almoço não contam como dois almoços.
             */
            Boolean inCalculation,
            @Valid List<ItemRequest> items
    ) {
        public boolean countsInDay() {
            return inCalculation == null || inCalculation;
        }
    }

    public record PlanRequest(
            @NotBlank @Size(max = 150) String title,
            Long patientId,
            @NotNull PrescriptionMethod method,
            LocalDate validityStart,
            LocalDate validityEnd,
            @Size(max = 20_000) String handouts,
            @Size(max = 20_000) String internalNotes,
            @DecimalMin(value = "0.0", message = "A meta energética não pode ser negativa")
            BigDecimal targetEnergyKcal,
            /** Distribuição planejada, em porcentagem da energia. */
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal targetProteinPct,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal targetCarbohydratePct,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal targetFatPct,
            /** Peso programado, base do g/kg do relatório. */
            @DecimalMin("1.0") @DecimalMax("400.0") BigDecimal targetWeightKg,
            /** Cálculo energético de onde a meta foi importada. */
            Long energyPlanId,
            boolean template,
            @Valid List<MealRequest> meals
    ) {}

    /**
     * Salvar uma refeição para reutilizar.
     *
     * A refeição vai inteira no corpo, e não como o id de uma já salva: ele
     * favorita a refeição que está montando, e no editor ela pode ainda não
     * ter sido salva. Exigir que salvasse o plano antes seria pedir burocracia
     * para ele guardar o próprio trabalho.
     */
    public record FavoriteMealRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @Valid MealRequest meal
    ) {}

    public record FavoriteMealResponse(
            Long id,
            /** O nome sob o qual ele salvou. */
            String name,
            /** O nome da refeição em si — "Café da Manhã". */
            String mealName,
            String notes,
            List<ItemResponse> items,
            BigDecimal energyKcal,
            int itemsTotal
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
            BigDecimal adequacyEnergyPct,
            /** Em que faixa a energia caiu: abaixo, dentro ou acima do planejado. */
            AdequacyBand energyBand,
            /**
             * Prescrito × teórico × diferença, macro a macro.
             *
             * Vazio quando não há meta energética: sem teórico não há o que
             * comparar, e mostrar uma diferença contra zero enganaria.
             */
            List<NutritionalCalculator.MacroComparison> comparison
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
            MealItemKind kind,
            Long foodId,
            Long measureId,
            String description,
            String serving,
            BigDecimal quantity,
            BigDecimal grams,
            /** "À vontade": sem quantidade e fora do somatório. */
            boolean adLibitum,
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
            boolean inCalculation,
            boolean hasPhoto,
            String photoName,
            List<ItemResponse> items,
            TotalResponse total,
            /** Peso do que entra na conta, em gramas. */
            BigDecimal weightGrams,
            /** kcal por grama, e a faixa em que cai. Nulos sem peso ou sem energia. */
            BigDecimal energyDensity,
            EnergyDensityBand energyDensityBand,
            String energyDensityDescription,
            /** Quanto do dia esta refeição representa, em %. Nulo fora da conta ou num dia sem energia. */
            BigDecimal shareOfDayPct
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
            BigDecimal targetProteinPct,
            BigDecimal targetCarbohydratePct,
            BigDecimal targetFatPct,
            BigDecimal targetWeightKg,
            Long energyPlanId,
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
            /**
             * O modo de preparo das receitas usadas no cardápio.
             *
             * "A observação que eu colocar em uma receita deve aparecer quando
             * eu adicionar a receita no cardápio, para o usuário ler como fazer
             * quando receber o PDF."
             *
             * Vem numa seção no fim, e não embaixo de cada item: a mesma
             * receita costuma aparecer no almoço e no jantar, e repeti-la
             * dobraria a folha sem dizer nada de novo.
             */
            List<PublicRecipeResponse> recipes,
            PublicSummaryResponse summary
    ) {}

    /** Uma receita usada no cardápio, com o preparo que a acompanha. */
    public record PublicRecipeResponse(Long foodId, String name, String modeInstructions) {}

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
            /** Id da refeição, para casar com a foto carregada à parte. */
            Long id,
            String name,
            LocalTime time,
            String notes,
            /** Nome do arquivo da foto, ou null quando não há foto. */
            String photoName,
            List<PublicItemResponse> items
    ) {}

    public record PublicItemResponse(
            MealItemKind kind,
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
