package br.com.nutriplan.prescription.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.domain.HouseholdMeasure;
import br.com.nutriplan.food.dto.CompositionDto;
import br.com.nutriplan.food.repository.FoodRepository;
import br.com.nutriplan.food.repository.HouseholdMeasureRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.prescription.domain.ItemSubstitution;
import br.com.nutriplan.prescription.domain.MealItem;
import br.com.nutriplan.prescription.domain.PrescriptionMethod;
import br.com.nutriplan.prescription.domain.MealPlan;
import br.com.nutriplan.prescription.domain.Meal;
import br.com.nutriplan.prescription.domain.PlanStatus;
import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.repository.MealPlanRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MealPlanService {

    private final MealPlanRepository planRepository;
    private final FoodRepository foodRepository;
    private final HouseholdMeasureRepository measureRepository;
    private final PatientRepository patientRepository;
    private final NutritionalCalculator calculator;
    private final CurrentContext contextCurrent;

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public Page<PrescriptionDtos.PlanSummary> list(Long patientId, Boolean template,
                                                   String term, Pageable pageable) {
        Long accountId = contextCurrent.accountId();
        String search = StringUtils.hasText(term) ? term.trim() : null;

        Page<MealPlan> page = planRepository.find(accountId, patientId, template, search, pageable);
        Map<Long, String> names = patientsNames(page.getContent());

        return page.map(plan -> {
            var foods = loadPlanFoods(plan, accountId);
            var total = calculator.totalMeals(plan.getMeals(), foods);
            return new PrescriptionDtos.PlanSummary(
                    plan.getId(), plan.getTitle(), plan.getPatientId(),
                    names.get(plan.getPatientId()), plan.getMethod(), plan.getStatus(),
                    plan.getValidityStart(), plan.getValidityEnd(), plan.isTemplate(),
                    plan.getMeals().size(), plan.itemsTotal(),
                    total.composition().getEnergyKcal(), plan.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public PrescriptionDtos.PlanResponse detail(Long id) {
        Long accountId = contextCurrent.accountId();
        MealPlan plan = accountRequire(id);
        return buildAnswer(plan, accountId);
    }

    @Transactional(readOnly = true)
    public MealPlan accountRequire(Long id) {
        return planRepository.loadComplete(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Plano alimentar", id));
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public PrescriptionDtos.PlanResponse create(PrescriptionDtos.PlanRequest req) {
        Long accountId = contextCurrent.accountId();
        validateLink(req, accountId);

        var plan = new MealPlan(accountId, req.title());
        applyHeader(req, plan);
        replaceMeals(plan, req.meals(), accountId);

        planRepository.save(plan);
        log.info("Plano criado: id={} paciente={} conta={}", plan.getId(), plan.getPatientId(), accountId);
        return buildAnswer(plan, accountId);
    }

    @Transactional
    public PrescriptionDtos.PlanResponse update(Long id, PrescriptionDtos.PlanRequest req) {
        Long accountId = contextCurrent.accountId();
        MealPlan plan = accountRequire(id);

        if (!plan.podeSerEdited()) {
            throw new BusinessRuleException(
                    "Plano encerrado não pode ser alterado. Duplique-o para criar uma nova versão.");
        }
        validateLink(req, accountId);

        plan.setTitle(req.title());
        applyHeader(req, plan);
        replaceMeals(plan, req.meals(), accountId);

        return buildAnswer(plan, accountId);
    }

    /**
     * Publishes the plan, making it visible through the patient's link.
     *
     * A plan with no items at all cannot be published: the patient would open
     * the link and find an empty page, which is worse than having no link.
     */
    @Transactional
    public PrescriptionDtos.PlanResponse publish(Long id) {
        Long accountId = contextCurrent.accountId();
        MealPlan plan = accountRequire(id);

        if (plan.isTemplate()) {
            throw new BusinessRuleException(
                    "Modelo não e publicado. Aplique-o a um paciente para gerar um plano.");
        }
        if (plan.itemsTotal() == 0) {
            throw new BusinessRuleException("Inclua ao menos um item antes de publicar o plano");
        }
        plan.setStatus(PlanStatus.ACTIVE);
        if (plan.getValidityStart() == null) {
            plan.setValidityStart(LocalDate.now());
        }
        log.info("Plano publicado: id={} link={}", plan.getId(), plan.getPublicIdentifier());
        return buildAnswer(plan, accountId);
    }

    @Transactional
    public PrescriptionDtos.PlanResponse close(Long id) {
        Long accountId = contextCurrent.accountId();
        MealPlan plan = accountRequire(id);
        plan.setStatus(PlanStatus.CLOSED);
        if (plan.getValidityEnd() == null) {
            plan.setValidityEnd(LocalDate.now());
        }
        return buildAnswer(plan, accountId);
    }

    @Transactional
    public PrescriptionDtos.PlanResponse backToDraft(Long id) {
        Long accountId = contextCurrent.accountId();
        MealPlan plan = accountRequire(id);
        plan.setStatus(PlanStatus.DRAFT);
        return buildAnswer(plan, accountId);
    }

    /** Invalidates the link handed out and generates another — used if the address leaks. */
    @Transactional
    public PrescriptionDtos.PlanResponse regenerateLink(Long id) {
        Long accountId = contextCurrent.accountId();
        MealPlan plan = accountRequire(id);
        plan.regeneratePublicIdentifier();
        log.info("Link do plano regerado: id={}", plan.getId());
        return buildAnswer(plan, accountId);
    }

    @Transactional
    public void remove(Long id) {
        MealPlan plan = accountRequire(id);
        planRepository.delete(plan);
        log.info("Plano removido: id={}", id);
    }

    /** Copies a plan — to revise without losing what the patient already received. */
    @Transactional
    public PrescriptionDtos.PlanResponse duplicate(Long id, Long patientId, String title) {
        Long accountId = contextCurrent.accountId();
        MealPlan origin = accountRequire(id);

        Long destination = patientId != null ? patientId : origin.getPatientId();
        if (destination == null) {
            throw new BusinessRuleException("Informe o paciente que receberá a cópia");
        }
        requirePatient(destination, accountId);

        var copies = new MealPlan(accountId,
                StringUtils.hasText(title) ? title : origin.getTitle() + " (cópia)");
        copies.setPatientId(destination);
        copies.setMethod(origin.getMethod());
        copies.setHandouts(origin.getHandouts());
        copies.setInternalNotes(origin.getInternalNotes());
        copies.setTargetEnergyKcal(origin.getTargetEnergyKcal());
        copies.setStatus(PlanStatus.DRAFT);

        for (Meal meal : origin.getMeals()) {
            var nova = new Meal(meal.getName(), meal.getTime());
            nova.setOrder(meal.getOrder());
            nova.setNotes(meal.getNotes());

            for (MealItem item : meal.getItems()) {
                var novoItem = new MealItem(item.getDescription());
                novoItem.setFoodId(item.getFoodId());
                novoItem.setMeasureId(item.getMeasureId());
                novoItem.setDescriptionMeasure(item.getDescriptionMeasure());
                novoItem.setQuantity(item.getQuantity());
                novoItem.setGrams(item.getGrams());
                novoItem.setOrder(item.getOrder());
                novoItem.setNotes(item.getNotes());

                for (ItemSubstitution substitution : item.getSubstitutions()) {
                    var novo = new ItemSubstitution(substitution.getDescription());
                    novo.setFoodId(substitution.getFoodId());
                    novo.setMeasureId(substitution.getMeasureId());
                    novo.setDescriptionMeasure(substitution.getDescriptionMeasure());
                    novo.setQuantity(substitution.getQuantity());
                    novo.setGrams(substitution.getGrams());
                    novoItem.addSubstitution(novo);
                }
                nova.addItem(novoItem);
            }
            copies.addMeal(nova);
        }

        planRepository.save(copies);
        return buildAnswer(copies, accountId);
    }

    // ------------------------------------------------------------------ montagem

    private void applyHeader(PrescriptionDtos.PlanRequest req, MealPlan plan) {
        plan.setMethod(req.method());
        plan.setTemplate(req.template());
        plan.setPatientId(req.template() ? null : req.patientId());
        plan.setValidityStart(req.validityStart());
        plan.setValidityEnd(req.validityEnd());
        plan.setHandouts(req.handouts());
        plan.setInternalNotes(req.internalNotes());
        plan.setTargetEnergyKcal(req.targetEnergyKcal());

        if (req.validityStart() != null && req.validityEnd() != null
                && req.validityEnd().isBefore(req.validityStart())) {
            throw new BusinessRuleException("A vigência não pode terminar antes de começar");
        }
    }

    private void validateLink(PrescriptionDtos.PlanRequest req, Long accountId) {
        if (req.template()) {
            return;
        }
        if (req.patientId() == null) {
            throw new BusinessRuleException(
                    "Informe o paciente, ou marque o plano como modelo se ele não for de ninguém");
        }
        requirePatient(req.patientId(), accountId);
    }

    private Patient requirePatient(Long patientId, Long accountId) {
        return patientRepository.findByIdAndAccountId(patientId, accountId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }

    /**
     * Rebuilds the meals from the request.
     *
     * The replacement is wholesale instead of incremental: the prescription
     * screen sends the whole plan on every save, and matching item by item to
     * find out what changed would bring complexity without gain — the volume of
     * a plan is small, and rebuilding is less error-prone than synchronizing.
     */
    private void replaceMeals(MealPlan plan,
                                     List<PrescriptionDtos.MealRequest> requests,
                                     Long accountId) {
        plan.getMeals().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }

        var foods = loadFoodsRequested(requests, accountId);
        var measures = loadMeasuresRequests(requests, accountId);

        for (PrescriptionDtos.MealRequest request : requests) {
            var meal = new Meal(request.name(), request.time());
            meal.setNotes(request.notes());

            if (request.items() != null) {
                for (PrescriptionDtos.ItemRequest itemRequest : request.items()) {
                    meal.addItem(buildItem(itemRequest, plan.getMethod(), foods, measures));
                }
            }
            plan.addMeal(meal);
        }
        plan.renumberMeals();
        plan.getMeals().forEach(Meal::renumberItems);
    }

    private MealItem buildItem(PrescriptionDtos.ItemRequest request,
                                    PrescriptionMethod method,
                                    Map<Long, Food> foods,
                                    Map<Long, HouseholdMeasure> measures) {

        Food food = request.foodId() == null ? null : foods.get(request.foodId());
        if (request.foodId() != null && food == null) {
            throw new NotFoundException("Alimento", request.foodId());
        }

        String description = StringUtils.hasText(request.description())
                ? request.description()
                : (food != null ? food.getDescription() : null);
        if (!StringUtils.hasText(description)) {
            throw new BusinessRuleException(
                    "Cada item precisa de um alimento ou de uma descrição livre");
        }

        var item = new MealItem(description);
        item.setFoodId(request.foodId());
        item.setNotes(request.notes());

        // A qualitative plan does not quantify: "salada a vontade" has no number,
        // and inventing one would be creating clinical data nobody prescribed.
        if (method.isQuantificado()) {
            var serving = resolveServing(request.foodId(), request.measureId(),
                    request.quantity(), measures);
            item.setMeasureId(serving.measureId());
            item.setDescriptionMeasure(serving.descriptionMeasure());
            item.setQuantity(serving.quantity());
            item.setGrams(serving.grams());
        }

        if (request.substitutions() != null && !request.substitutions().isEmpty()) {
            if (!method.admitsSubstitutions()) {
                throw new BusinessRuleException(
                        "Substituições só existem no método por equivalentes");
            }
            for (var substitutionRequest : request.substitutions()) {
                var substitution = new ItemSubstitution(substitutionRequest.description());
                substitution.setFoodId(substitutionRequest.foodId());
                var serving = resolveServing(substitutionRequest.foodId(),
                        substitutionRequest.measureId(), substitutionRequest.quantity(), measures);
                substitution.setMeasureId(serving.measureId());
                substitution.setDescriptionMeasure(serving.descriptionMeasure());
                substitution.setQuantity(serving.quantity());
                substitution.setGrams(serving.grams());
                item.addSubstitution(substitution);
            }
        }
        return item;
    }

    private record Serving(Long measureId, String descriptionMeasure,
                          BigDecimal quantity, BigDecimal grams) {}

    /**
     * Converts "3 tablespoons" into the weight the calculation uses.
     *
     * With no measure reported, the quantity already is the weight in grams.
     */
    private Serving resolveServing(Long foodId, Long measureId,
                                  BigDecimal quantity, Map<Long, HouseholdMeasure> measures) {
        if (quantity == null) {
            return new Serving(null, null, null, null);
        }
        if (measureId == null) {
            return new Serving(null, null, quantity, quantity);
        }

        HouseholdMeasure measure = measures.get(measureId);
        if (measure == null) {
            throw new NotFoundException("Medida caseira", measureId);
        }
        if (foodId != null && !measure.getFood().getId().equals(foodId)) {
            throw new BusinessRuleException(
                    "A porção informada não pertence ao alimento escolhido");
        }
        return new Serving(measureId, measure.getDescription(), quantity, measure.gramsTo(quantity));
    }

    // ------------------------------------------------------------------ apoio

    private Map<Long, Food> loadFoodsRequested(
            List<PrescriptionDtos.MealRequest> meals, Long accountId) {

        List<Long> ids = new ArrayList<>();
        for (var meal : meals) {
            if (meal.items() == null) {
                continue;
            }
            for (var item : meal.items()) {
                if (item.foodId() != null) {
                    ids.add(item.foodId());
                }
                if (item.substitutions() != null) {
                    item.substitutions().stream()
                            .map(PrescriptionDtos.SubstitutionRequest::foodId)
                            .filter(java.util.Objects::nonNull)
                            .forEach(ids::add);
                }
            }
        }
        return ids.isEmpty() ? Map.of() : index(foodRepository.visibleFind(ids, accountId));
    }

    private Map<Long, HouseholdMeasure> loadMeasuresRequests(
            List<PrescriptionDtos.MealRequest> meals, Long accountId) {

        List<Long> ids = new ArrayList<>();
        for (var meal : meals) {
            if (meal.items() == null) {
                continue;
            }
            for (var item : meal.items()) {
                if (item.measureId() != null) {
                    ids.add(item.measureId());
                }
                if (item.substitutions() != null) {
                    item.substitutions().stream()
                            .map(PrescriptionDtos.SubstitutionRequest::measureId)
                            .filter(java.util.Objects::nonNull)
                            .forEach(ids::add);
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        // Filters by visibility: another practice's portion does not serve here.
        return measureRepository.findAllById(ids).stream()
                .filter(m -> m.getAccountId() == null || m.getAccountId().equals(accountId))
                .collect(Collectors.toMap(HouseholdMeasure::getId, Function.identity(), (a, b) -> a));
    }

    /** Foods cited by an already-saved plan, for totalling. */
    private Map<Long, Food> loadPlanFoods(MealPlan plan, Long accountId) {
        List<Long> ids = plan.getMeals().stream()
                .flatMap(r -> r.getItems().stream())
                .map(MealItem::getFoodId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : index(foodRepository.visibleFind(ids, accountId));
    }

    private Map<Long, Food> index(List<Food> foods) {
        return foods.stream()
                .collect(Collectors.toMap(Food::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> patientsNames(List<MealPlan> plans) {
        List<Long> ids = plans.stream()
                .map(MealPlan::getPatientId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        patientRepository.findAllById(ids).forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    // -------------------------------------------------------------------- answer

    PrescriptionDtos.PlanResponse buildAnswer(MealPlan plan, Long accountId) {
        var foods = loadPlanFoods(plan, accountId);

        List<PrescriptionDtos.MealResponse> meals = plan.getMeals().stream()
                .map(meal -> {
                    var total = calculator.total(meal.getItems(), foods);
                    return new PrescriptionDtos.MealResponse(
                            meal.getId(), meal.getName(), meal.getTime(),
                            meal.getOrder(), meal.getNotes(),
                            meal.getItems().stream().map(this::buildItemAnswer).toList(),
                            totalBuild(total, null));
                })
                .toList();

        var dayTotal = calculator.totalMeals(plan.getMeals(), foods);

        String patientName = plan.getPatientId() == null ? null
                : patientRepository.findById(plan.getPatientId())
                        .map(Patient::getName).orElse(null);

        return new PrescriptionDtos.PlanResponse(
                plan.getId(), plan.getTitle(), plan.getPatientId(), patientName,
                plan.getMethod(), plan.getMethod().getDescription(),
                plan.getStatus(), plan.getStatus().getDescription(),
                plan.getPublicIdentifier(),
                plan.getValidityStart(), plan.getValidityEnd(),
                plan.getHandouts(), plan.getInternalNotes(),
                plan.getTargetEnergyKcal(), plan.isTemplate(),
                meals, totalBuild(dayTotal, plan.getTargetEnergyKcal()),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }

    private PrescriptionDtos.ItemResponse buildItemAnswer(MealItem item) {
        return new PrescriptionDtos.ItemResponse(
                item.getId(), item.getFoodId(), item.getMeasureId(),
                item.getDescription(), item.servingFormatted(),
                item.getQuantity(), item.getGrams(), item.getOrder(), item.getNotes(),
                item.getSubstitutions().stream()
                        .map(e -> new PrescriptionDtos.SubstitutionResponse(
                                e.getId(), e.getFoodId(), e.getDescription(),
                                e.servingFormatted(), e.getGrams()))
                        .toList());
    }

    private PrescriptionDtos.TotalResponse totalBuild(NutritionalCalculator.Total total,
                                                     BigDecimal targetKcal) {
        var distribution = calculator.macrosDistribution(total.composition());
        return new PrescriptionDtos.TotalResponse(
                CompositionDto.from(total.composition()),
                total.itemsInCalculation(), total.itemsOutsideCalculation(),
                total.nutrientsIncomplete(), total.nutrientsWithoutDatum(),
                total.reliable(),
                distribution == null ? null : new PrescriptionDtos.DistributionResponse(
                        distribution.proteinPct(), distribution.carbohydratePct(),
                        distribution.lipidPct(), distribution.calculatedEnergyKcal()),
                calculator.adequacyEnergy(total.composition(), targetKcal));
    }
}
