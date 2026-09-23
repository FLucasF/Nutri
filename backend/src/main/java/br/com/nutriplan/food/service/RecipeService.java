package br.com.nutriplan.food.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.DataSource;
import br.com.nutriplan.food.domain.RecipeIngredient;
import br.com.nutriplan.food.domain.HouseholdMeasure;
import br.com.nutriplan.food.dto.CompositionDto;
import br.com.nutriplan.food.dto.RecipeDtos;
import br.com.nutriplan.food.repository.FoodRepository;
import br.com.nutriplan.food.repository.HouseholdMeasureRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The practice's recipes.
 *
 * A recipe is a {@link Food} with source {@code RECIPE} and a composition that
 * is calculated instead of tabulated. The decision pays for itself: because a
 * recipe is a food, it already appears in the search, accepts a household
 * measure, enters a meal and carries its provenance into the prescription —
 * without one extra line of code in any of those places. What this service adds
 * is the calculation and the list of ingredients.
 *
 * One recipe can go into another: a sofrito inside a pie. The only limit is not
 * using a recipe as an ingredient of itself, directly or indirectly — which
 * would produce an infinite composition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private final FoodRepository foodRepository;
    private final HouseholdMeasureRepository householdMeasureRepository;
    private final RecipeCalculator calculator;
    private final CurrentContext contextCurrent;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Page<RecipeDtos.RecipeSummary> list(String term, Pageable pageable) {
        return foodRepository
                .findRecipes(contextCurrent.accountId(), normalize(term), pageable)
                .map(this::summarize);
    }

    @Transactional(readOnly = true)
    public RecipeDtos.RecipeResponse detail(Long id) {
        return buildAnswer(requireRecipe(id));
    }

    @Transactional
    public RecipeDtos.RecipeResponse create(RecipeDtos.RecipeRequest req) {
        Food recipe = new Food(req.name(), DataSource.RECIPE);
        recipe.setAccountId(contextCurrent.accountId());
        apply(req, recipe);
        foodRepository.save(recipe);

        log.info("Receita criada: id={} conta={} ingredientes={}",
                recipe.getId(), recipe.getAccountId(), recipe.getIngredients().size());
        return buildAnswer(recipe);
    }

    @Transactional
    public RecipeDtos.RecipeResponse update(Long id, RecipeDtos.RecipeRequest req) {
        Food recipe = requireRecipe(id);
        recipe.setDescription(req.name());
        apply(req, recipe);
        return buildAnswer(recipe);
    }

    @Transactional
    public void remove(Long id) {
        Food recipe = requireRecipe(id);
        // Deactivates instead of deleting: the recipe may be prescribed in an
        // active plan, and the plan has to go on saying what was prescribed.
        recipe.setActive(false);
        log.info("Receita inativada: id={}", id);
    }

    // ------------------------------------------------------------------- apoio

    private Food requireRecipe(Long id) {
        Food food = foodRepository
                .visibleFind(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Receita", id));
        if (!food.isRecipe()) {
            throw new NotFoundException("Receita", id);
        }
        if (food.getAccountId() == null) {
            throw new BusinessRuleException("Esta receita não pertence ao seu consultório");
        }
        return food;
    }

    private void apply(RecipeDtos.RecipeRequest req, Food recipe) {
        recipe.setGroup(req.group());
        recipe.setModeInstructions(
                RichTextDocument.ofTextOrDocument(req.modeInstructions(), mapper).json());
        recipe.setYieldGrams(req.yieldGrams());
        recipe.setServings(req.servings());

        List<Long> ids = req.ingredients().stream()
                .map(RecipeDtos.IngredientRequest::foodId)
                .distinct()
                .toList();
        Map<Long, Food> byId = new LinkedHashMap<>();
        foodRepository.visibleFind(ids, contextCurrent.accountId())
                .forEach(a -> byId.put(a.getId(), a));

        recipe.getIngredients().clear();
        int order = 0;
        for (var request : req.ingredients()) {
            Food food = byId.get(request.foodId());
            if (food == null) {
                throw new NotFoundException("Alimento", request.foodId());
            }
            rejectCycle(recipe, food);

            var ingredient = new RecipeIngredient(
                    recipe, food, resolveGrams(food, request), order++);
            ingredient.setQuantity(request.quantity());
            ingredient.setMeasureId(request.measureId());
            if (request.measureId() != null) {
                ingredient.setDescriptionMeasure(householdMeasureRepository
                        .visibleTo(request.measureId(), food.getId(), contextCurrent.accountId())
                        .map(HouseholdMeasure::getDescription)
                        .orElse(null));
            }
            recipe.getIngredients().add(ingredient);
        }

        var calculation = calculator.calculate(recipe.getIngredients(), req.yieldGrams());
        recipe.setComposition(calculation.composition());
        syncMeasures(recipe, calculation.yieldUsed());
    }

    private BigDecimal resolveGrams(Food food, RecipeDtos.IngredientRequest request) {
        if (request.measureId() == null) {
            return request.quantity();
        }
        HouseholdMeasure measure = householdMeasureRepository
                .visibleTo(request.measureId(), food.getId(), contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException(
                        "Medida caseira do alimento " + food.getId(), request.measureId()));
        return measure.gramsTo(request.quantity());
    }

    /**
     * Prevents a recipe from using itself as an ingredient, directly or
     * indirectly. Without this, calculating the composition would go into
     * recursion — and the error would only show up on the next save, far from
     * the cause.
     */
    private void rejectCycle(Food recipe, Food candidate) {
        if (recipe.getId() != null && contains(candidate, recipe.getId(), 0)) {
            throw new BusinessRuleException(
                    ("\"%s\" não pode ser ingrediente desta receita: ela já usa esta receita, "
                            + "direta ou indiretamente.").formatted(candidate.getDescription()));
        }
    }

    private boolean contains(Food candidate, Long recipeId, int depth) {
        if (candidate.getId() != null && candidate.getId().equals(recipeId)) {
            return true;
        }
        // A recipe inside another inside another is legitimate; ten levels
        // already indicates a mistake, and the limit avoids walking a crooked
        // graph.
        if (depth > 10 || !candidate.isRecipe()) {
            return false;
        }
        return candidate.getIngredients().stream()
                .anyMatch(i -> contains(i.getFood(), recipeId, depth + 1));
    }

    /**
     * Keeps the portions derived from the yield.
     *
     * A recipe is born with the household measures that make sense for it — the
     * whole preparation and, when the nutritionist reports how many servings it
     * yields, the serving. Without that the recipe would enter the plan in
     * grams, which is exactly what the rest of the system avoids.
     */
    private void syncMeasures(Food recipe, BigDecimal yieldGrams) {
        recipe.getMeasures().removeIf(m -> DERIVED.contains(m.getDescription()));
        if (yieldGrams == null || yieldGrams.signum() <= 0) {
            return;
        }

        var whole = new HouseholdMeasure(RECIPE_WHOLE, yieldGrams.stripTrailingZeros());
        whole.setFood(recipe);
        whole.setAccountId(recipe.getAccountId());
        recipe.getMeasures().add(whole);

        if (recipe.getServings() != null && recipe.getServings() > 0) {
            BigDecimal byServing = yieldGrams.divide(
                    BigDecimal.valueOf(recipe.getServings()), 3, RoundingMode.HALF_UP);
            var serving = new HouseholdMeasure(SERVING, byServing.stripTrailingZeros());
            serving.setFood(recipe);
            serving.setAccountId(recipe.getAccountId());
            serving.setStandard(true);
            recipe.getMeasures().add(serving);
        } else {
            whole.setStandard(true);
        }
    }

    private static final String RECIPE_WHOLE = "receita inteira";
    private static final String SERVING = "porção";
    private static final List<String> DERIVED = List.of(RECIPE_WHOLE, SERVING);

    private RecipeDtos.RecipeResponse buildAnswer(Food recipe) {
        var calculation = calculator.calculate(recipe.getIngredients(), recipe.getYieldGrams());

        BigDecimal gramsByServing = null;
        CompositionDto servingComposition = null;
        if (recipe.getServings() != null && recipe.getServings() > 0
                && calculation.yieldUsed().signum() > 0) {
            gramsByServing = calculation.yieldUsed()
                    .divide(BigDecimal.valueOf(recipe.getServings()), 3, RoundingMode.HALF_UP);
            servingComposition = CompositionDto.from(recipe.compositionTo(gramsByServing));
        }

        List<RecipeDtos.IngredientResponse> ingredients = new ArrayList<>();
        for (RecipeIngredient i : recipe.getIngredients()) {
            ingredients.add(new RecipeDtos.IngredientResponse(
                    i.getId(),
                    i.getFood().getId(),
                    i.getFood().getDescription(),
                    i.getFood().getSource().getDescription(),
                    i.getMeasureId(),
                    i.quantityFormatted(),
                    i.getGrams()));
        }

        return new RecipeDtos.RecipeResponse(
                recipe.getId(),
                recipe.getDescription(),
                recipe.getGroup(),
                recipe.getModeInstructions(),
                calculation.yieldUsed(),
                calculation.estimatedYield(),
                calculation.ingredientsWeight(),
                recipe.getServings(),
                gramsByServing,
                CompositionDto.from(recipe.getComposition()),
                servingComposition,
                calculation.nutrientsIncomplete(),
                ingredients);
    }

    private RecipeDtos.RecipeSummary summarize(Food recipe) {
        return new RecipeDtos.RecipeSummary(
                recipe.getId(),
                recipe.getDescription(),
                recipe.getGroup(),
                recipe.getIngredients().size(),
                recipe.getYieldGrams(),
                recipe.getServings(),
                recipe.getComposition().getEnergyKcal());
    }

    private String normalize(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        return Food.normalizeToSearch(term.trim());
    }
}
