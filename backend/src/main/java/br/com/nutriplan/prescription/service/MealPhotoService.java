package br.com.nutriplan.prescription.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nutriplan.prescription.domain.Meal;
import br.com.nutriplan.prescription.domain.MealPhoto;
import br.com.nutriplan.prescription.repository.MealPhotoRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A foto da refeição.
 *
 * "Adicionar uma foto, eu poderia subir uma/umas foto de como eu quero que
 * fique e aparecerá lá no PDF." É material de cozinha: o paciente entende
 * "meio prato de salada" vendo meio prato de salada.
 *
 * A refeição é alcançada pelo plano, e não pelo id solto, porque é o plano que
 * carrega o consultório. Sem isso, o id de uma refeição de outro consultório
 * serviria para trocar a foto dela.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MealPhotoService {

    /** Foto de prato tirada no celular cabe folgado em 4 MB. */
    private static final int LIMIT = 4 * 1024 * 1024;

    private final MealPlanService planService;
    private final MealPhotoRepository photoRepository;

    @Transactional
    public void attach(Long planId, Long mealId, String name, String type, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Envie um arquivo não vazio.");
        }
        if (content.length > LIMIT) {
            throw new BusinessRuleException("A foto pode ter no máximo 4 MB.");
        }
        if (type == null || !type.startsWith("image/")) {
            throw new BusinessRuleException("O arquivo precisa ser uma imagem.");
        }
        Meal meal = require(planId, mealId);
        meal.setPhotoName(name);
        meal.setPhotoType(type);
        photoRepository.save(new MealPhoto(meal.getId(), content));
        log.info("Foto anexada à refeição: plano={} refeicao={}", planId, mealId);
    }

    @Transactional(readOnly = true)
    public Photo read(Long planId, Long mealId) {
        Meal meal = require(planId, mealId);
        var file = photoRepository.findById(meal.getId())
                .orElseThrow(() -> new NotFoundException("Foto da refeição", mealId));
        return new Photo(meal.getPhotoName(), meal.getPhotoType(), file.getContent());
    }

    @Transactional
    public void remove(Long planId, Long mealId) {
        Meal meal = require(planId, mealId);
        meal.setPhotoName(null);
        meal.setPhotoType(null);
        photoRepository.deleteById(meal.getId());
    }

    private Meal require(Long planId, Long mealId) {
        return planService.accountRequire(planId).getMeals().stream()
                .filter(meal -> mealId.equals(meal.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Refeição", mealId));
    }

    public record Photo(String name, String type, byte[] content) {}
}
