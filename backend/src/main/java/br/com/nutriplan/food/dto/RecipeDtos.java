package br.com.nutriplan.food.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class RecipeDtos {

    private RecipeDtos() {
    }

    // -------------------------------------------------------------------- input

    public record IngredientRequest(
            @NotNull Long foodId,
            /** Household measure chosen. Null when the ingredient was weighed. */
            Long measureId,
            @NotNull
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantity
    ) {}

    public record RecipeRequest(
            @NotBlank @Size(max = 250) String name,
            @Size(max = 100) String group,
            /**
             * Weight of the finished preparation. Optional, and the interface
             * warns when it is left out: cooking changes the weight, and
             * without that number the per-100 g composition is an estimate,
             * not a measurement.
             */
            @DecimalMin(value = "0.001", message = "O rendimento deve ser maior que zero")
            BigDecimal yieldGrams,
            @Min(value = 1, message = "A receita precisa render ao menos uma porção")
            Integer servings,
            @Size(max = 4000) String modeInstructions,
            @NotEmpty(message = "Uma receita precisa de ao menos um ingrediente")
            @Valid List<IngredientRequest> ingredients
    ) {}

    // ------------------------------------------------------------------ output

    public record IngredientResponse(
            Long id,
            Long foodId,
            String description,
            String sourceDescription,
            Long measureId,
            /** Ready-made text: "2 tablespoons" or "150 g". */
            String quantity,
            BigDecimal grams
    ) {}

    /**
     * The complete recipe, as the nutritionist sees it.
     *
     * `estimatedYield` and `nutrientsIncomplete` exist so that the screen can
     * say what the number does not: that the final weight was presumed, and
     * that part of the nutrients is a floor and not a total.
     */
    public record RecipeResponse(
            Long id,
            String name,
            String group,
            String modeInstructions,
            BigDecimal yieldGrams,
            boolean estimatedYield,
            BigDecimal ingredientsWeight,
            Integer servings,
            BigDecimal gramsByServing,
            CompositionDto compositionPor100g,
            CompositionDto servingComposition,
            Set<String> nutrientsIncomplete,
            List<IngredientResponse> ingredients
    ) {}

    public record RecipeSummary(
            Long id,
            String name,
            String group,
            int ingredientsTotal,
            BigDecimal yieldGrams,
            Integer servings,
            BigDecimal energyKcalPor100g
    ) {}
}
