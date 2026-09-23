package br.com.nutriplan.prescription.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.prescription.domain.Meal;
import br.com.nutriplan.prescription.domain.PrescriptionMethod;
import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.repository.FavoriteMealRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The meals the practice saved to use again.
 *
 * The favourite arrives as a whole meal body, not as the id of a saved one.
 * That is deliberate: the professional favourites the meal he is assembling,
 * and in the editor that meal may not have been saved yet. Asking him to save
 * the plan first would be making him do paperwork to keep his own work.
 *
 * What is stored is a copy. Deleting the plan it came from must not take the
 * saved meal with it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteMealService {

    private final FavoriteMealRepository favoriteRepository;
    private final MealPlanService planService;
    private final NutritionalCalculator calculator;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public List<PrescriptionDtos.FavoriteMealResponse> list() {
        Long accountId = currentContext.accountId();
        List<Meal> favorites = favoriteRepository.favoritesOf(accountId);
        Map<Long, Food> foods = planService.loadFoodsOfMeals(favorites, accountId);

        return favorites.stream()
                .map(meal -> toAnswer(meal, foods))
                .toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionDtos.FavoriteMealResponse detail(Long id) {
        Long accountId = currentContext.accountId();
        Meal favorite = require(id, accountId);
        return toAnswer(favorite, planService.loadFoodsOfMeals(List.of(favorite), accountId));
    }

    @Transactional
    public PrescriptionDtos.FavoriteMealResponse save(PrescriptionDtos.FavoriteMealRequest request) {
        Long accountId = currentContext.accountId();
        if (!StringUtils.hasText(request.name())) {
            throw new BusinessRuleException(
                    "Dê um nome à refeição para achá-la depois na lista de salvas.");
        }
        if (request.meal().items() == null || request.meal().items().isEmpty()) {
            throw new BusinessRuleException("Não há o que salvar: a refeição está vazia.");
        }

        // Reaproveita a montagem do plano: uma refeição favorita é uma refeição.
        Meal favorite = planService.buildMeal(request.meal(), PrescriptionMethod.FOODS, accountId);
        favorite.setAccountId(accountId);
        favorite.setFavoriteName(request.name().trim());
        favorite.setOrder(0);
        favoriteRepository.save(favorite);

        log.info("Refeição favoritada: id={} conta={} nome={}",
                favorite.getId(), accountId, favorite.getFavoriteName());
        return toAnswer(favorite, planService.loadFoodsOfMeals(List.of(favorite), accountId));
    }

    @Transactional
    public void remove(Long id) {
        Long accountId = currentContext.accountId();
        favoriteRepository.delete(require(id, accountId));
        log.info("Refeição favorita removida: id={}", id);
    }

    // ------------------------------------------------------------------ apoio

    private PrescriptionDtos.FavoriteMealResponse toAnswer(Meal meal, Map<Long, Food> foods) {
        var total = calculator.total(meal.getItems(), foods);
        return new PrescriptionDtos.FavoriteMealResponse(
                meal.getId(),
                meal.getFavoriteName(),
                meal.getName(),
                meal.getNotes(),
                planService.itemsAnswer(meal),
                total.composition().getEnergyKcal(),
                meal.getItems().size());
    }

    private Meal require(Long id, Long accountId) {
        return favoriteRepository.findFavorite(id, accountId)
                .orElseThrow(() -> new NotFoundException("Refeição favorita", id));
    }
}
