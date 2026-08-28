package br.com.nutriplan.food.dto;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.domain.HouseholdMeasure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** DTOs of the food module, grouped because they are small and cohesive. */
public final class FoodDtos {

    private FoodDtos() {
    }

    /** A listing row: enough to pick a food in the search. */
    public record Summary(
            Long id,
            String description,
            String group,
            DataSource source,
            String brand,
            BigDecimal energyKcal,
            BigDecimal proteinG,
            BigDecimal carbohydrateG,
            BigDecimal fatG,
            boolean publicBase
    ) {
        public static Summary from(Food a) {
            var c = a.getComposition();
            return new Summary(a.getId(), a.getDescription(), a.getGroup(), a.getSource(), a.getBrand(),
                    c.getEnergyKcal(), c.getProteinG(), c.getCarbohydrateG(), c.getFatG(),
                    a.isPublicBase());
        }
    }

    /**
     * @param forCatalogBase portion that came with the system, shared by every practice
     * @param editable       false for portions of the base catalog: a practice can create
     *                       its own version, but not change everybody's
     */
    public record MeasureResponse(
            Long id,
            String description,
            BigDecimal grams,
            boolean standard,
            boolean forCatalogBase,
            boolean editable
    ) {
        public static MeasureResponse from(HouseholdMeasure m, Long accountId) {
            return new MeasureResponse(m.getId(), m.getDescription(), m.getGrams(), m.isStandard(),
                    m.isForCatalogBase(), m.editableBy(accountId));
        }
    }

    /** The complete detail, with the composition and the usual portions. */
    public record Detail(
            Long id,
            String description,
            String group,
            DataSource source,
            String sourceDescription,
            String codeSource,
            /** EAN of the processed product. Null in the reference tables. */
            String codeBarcode,
            String brand,
            boolean publicBase,
            boolean editable,
            CompositionDto composition,
            List<MeasureResponse> measures
    ) {
        /**
         * The measures come from outside, and not from a.getMeasures(): the JPA
         * association would bring the portions of every practice. The list
         * visible to this account is resolved by a filtered repository query.
         */
        public static Detail from(Food a, List<HouseholdMeasure> visibleMeasures, Long accountId) {
            return new Detail(
                    a.getId(), a.getDescription(), a.getGroup(),
                    a.getSource(), a.getSource().getDescription(), a.getCodeSource(),
                    a.getCodeBarcode(), a.getBrand(),
                    a.isPublicBase(),
                    // A public base is a shared reference: nobody edits it.
                    !a.isPublicBase(),
                    CompositionDto.from(a.getComposition()),
                    visibleMeasures.stream().map(m -> MeasureResponse.from(m, accountId)).toList());
        }
    }

    public record MeasureRequest(
            @NotBlank @Size(max = 120) String description,
            @NotNull @DecimalMin(value = "0.001", message = "O peso da medida deve ser maior que zero")
            BigDecimal grams,
            boolean standard
    ) {}

    /** Registration of a food of the practice's own. */
    public record FoodRequest(
            @NotBlank @Size(max = 250) String description,
            @Size(max = 100) String group,
            /**
             * Product EAN, when there is one. It lets the practice find its own
             * product by code — the one the collaborative base does not have,
             * or brings with data the nutritionist does not accept.
             */
            @Size(max = 20) String codeBarcode,
            @Size(max = 100) String brand,
            @NotNull CompositionDto composition,
            @Valid List<MeasureRequest> measures
    ) {}

    /**
     * Summary of a table import, to show the nutritionist what came in, what
     * was discarded and why.
     */
    public record ResultImport(int imported, int ignored, List<String> warnings) {}

    /**
     * Result of calculating a portion: how much of each nutrient there is in
     * the requested quantity.
     */
    public record CalculatedServing(
            Long foodId,
            String description,
            BigDecimal grams,
            String measureUsed,
            CompositionDto composition
    ) {}
}
