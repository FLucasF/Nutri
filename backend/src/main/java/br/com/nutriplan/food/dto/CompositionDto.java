package br.com.nutriplan.food.dto;

import br.com.nutriplan.food.domain.NutritionalComposition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nutritional composition as exposed by the API.
 *
 * It serializes as a flat object — {"energyKcal": 123.5, "proteinG": 2.59} —
 * and not as a record of fixed fields. That way including a new nutrient in the
 * catalog shows up in the API without changing this file, and nutrients not
 * determined in the source simply do not appear in the JSON.
 *
 * An absent key means a nutrient that was not determined. The client should
 * show "não informado", never zero: they are different things in a clinical
 * context.
 */
public record CompositionDto(@JsonValue Map<String, BigDecimal> values) {

    public CompositionDto {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    @JsonCreator
    public static CompositionDto forJson(Map<String, BigDecimal> values) {
        return new CompositionDto(values == null ? Map.of() : new LinkedHashMap<>(values));
    }

    public static CompositionDto from(NutritionalComposition composition) {
        return composition == null ? null : new CompositionDto(composition.asMap());
    }

    /**
     * Unknown keys are ignored in silence: an old client or an imported
     * spreadsheet may bring columns the catalog does not recognize, and that
     * must not bring down the registration of the food.
     */
    public NutritionalComposition toDomain() {
        var composition = new NutritionalComposition();
        values.forEach(composition::define);
        return composition;
    }

    public BigDecimal valueDe(String key) {
        return values.get(key);
    }

    public boolean empty() {
        return values.isEmpty();
    }
}
