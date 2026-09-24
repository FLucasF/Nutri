package br.com.nutriplan.prescription.service;

import br.com.nutriplan.food.domain.Food;
import br.com.nutriplan.food.repository.FoodRepository;
import br.com.nutriplan.auth.domain.Account;
import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.User;
import br.com.nutriplan.auth.repository.AccountRepository;
import br.com.nutriplan.auth.repository.UserRepository;
import br.com.nutriplan.handout.repository.PlanImageRepository;
import br.com.nutriplan.handout.repository.PlanHandoutRepository;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.prescription.domain.MealItem;
import br.com.nutriplan.prescription.domain.MealPlan;
import br.com.nutriplan.prescription.domain.PlanStatus;
import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.repository.MealPlanRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delivers the plan through the patient's link.
 *
 * This service answers requests **without authentication**, and so it is the
 * only one in the system that does not go through the account context. Two
 * design consequences:
 *
 *  - access is authorized by possession of the identifier, which is a UUID —
 *    which requires that it never appear in a predictable place nor be
 *    derivable from the id;
 *  - the response is assembled by a DTO of its own, which simply has no field
 *    for an internal note or an account identifier. The protection is in the
 *    shape of the contract, and not in remembering to filter on every change.
 *
 * A draft is never served: the patient would see a half-done plan and take it
 * for a prescription.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicPlanService {

    private final MealPlanRepository planRepository;
    private final FoodRepository foodRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PlanHandoutRepository planHandoutRepository;
    private final PlanImageRepository planImageRepository;
    private final NutritionalCalculator calculator;
    private final PlanAccessService accessService;

    @Transactional(readOnly = true)
    public PrescriptionDtos.PublicPlanResponse byIdentifier(String identifier) {
        return byIdentifier(identifier, null);
    }

    /**
     * O plano pelo link, conferindo o passe quando o paciente tem data de
     * nascimento a confirmar. O dono da conta, logado, passa sem ele.
     */
    @Transactional(readOnly = true)
    public PrescriptionDtos.PublicPlanResponse byIdentifier(String identifier, String accessToken) {
        MealPlan plan = planRepository.findByPublicIdentifier(identifier)
                .orElseThrow(() -> new NotFoundException(
                        "Plano não encontrado. Confira o link recebido."));

        if (!plan.isVisibleByLink()) {
            // A draft answers as nonexistent: revealing that the link "exists,
            // but not yet" would hand over information about work in
            // progress.
            throw new NotFoundException(
                    "Plano não encontrado. Confira o link recebido.");
        }

        if (!accessService.requiresBirthDate(plan)) {
            return build(plan, null);
        }
        if (accessService.ownerViewing(plan)) {
            return build(plan, accessService.token(identifier));
        }
        if (!accessService.valid(identifier, accessToken)) {
            throw new br.com.nutriplan.shared.error.AccessConfirmationRequiredException(
                    "Para abrir o plano, confirme a data de nascimento do paciente.");
        }
        return build(plan, accessToken);
    }

    /** Se o link abre direto, pelo dono logado ou pelo passe, ou pede a data. */
    @Transactional(readOnly = true)
    public PrescriptionDtos.GateResponse gate(String identifier, String accessToken) {
        MealPlan plan = planRepository.findByPublicIdentifier(identifier)
                .filter(MealPlan::isVisibleByLink)
                .orElseThrow(() -> new NotFoundException("Plano não encontrado. Confira o link recebido."));
        boolean requires = accessService.requiresBirthDate(plan);
        return new PrescriptionDtos.GateResponse(requires, accessService.allowed(plan, accessToken));
    }

    /** A data de nascimento confirmada vira o passe do link. */
    public PrescriptionDtos.AccessResponse confirm(String identifier, LocalDate birthDate) {
        return new PrescriptionDtos.AccessResponse(accessService.confirm(identifier, birthDate));
    }

    /**
     * Assembles the view of the plan from the already-loaded entity.
     *
     * Extracted so that the PDF printout uses exactly the same contract the
     * patient reads at the link. Were there two assemblers, the paper and the
     * screen would diverge at the first change — and the divergence would show
     * up with the plan in the patient's hand.
     */
    @Transactional(readOnly = true)
    public PrescriptionDtos.PublicPlanResponse build(MealPlan plan) {
        return build(plan, null);
    }

    @Transactional(readOnly = true)
    public PrescriptionDtos.PublicPlanResponse build(MealPlan plan, String accessToken) {
        var foods = loadFoods(plan);
        var total = calculator.totalMeals(plan.getMeals(), foods);
        var composition = total.composition();

        String patientName = plan.getPatientId() == null ? null
                : patientRepository.findById(plan.getPatientId())
                        .map(p -> firstName(p.getName())).orElse(null);

        Account account = accountRepository.findById(plan.getAccountId()).orElse(null);
        User profissional = userRepository
                .findFirstByAccountIdAndRoleAndActiveTrue(plan.getAccountId(), Role.NUTRITIONIST)
                .orElse(null);

        List<PrescriptionDtos.PublicMealResponse> meals = plan.getMeals().stream()
                .map(meal -> new PrescriptionDtos.PublicMealResponse(
                        meal.getId(), meal.getName(), meal.getTime(), meal.getNotes(),
                        meal.getPhotoName(),
                        meal.getItems().stream().map(this::buildItem).toList()))
                .toList();

        return new PrescriptionDtos.PublicPlanResponse(
                plan.getTitle(),
                patientName,
                profissional == null ? null : profissional.getName(),
                profissional == null ? null : profissional.getCrn(),
                account == null ? null : account.getName(),
                account == null ? null : account.getPrimaryColor(),
                account == null ? null : account.getLogoUrl(),
                plan.getMethod(),
                plan.currentAt(LocalDate.now()),
                plan.getStatus() == PlanStatus.CLOSED,
                plan.getValidityStart(),
                plan.getValidityEnd(),
                plan.getHandouts(),
                planHandoutRepository.findByPlanIdOrderByOrderAsc(plan.getId()).stream()
                        .map(o -> new PrescriptionDtos.PublicHandoutResponse(
                                o.getId(), o.getTitle(), o.getBody(),
                                imageAddress(plan.getPublicIdentifier(), o)))
                        .toList(),
                meals,
                recipesOf(plan, foods),
                new PrescriptionDtos.PublicSummaryResponse(
                        composition.getEnergyKcal(),
                        composition.getProteinG(),
                        composition.getCarbohydrateG(),
                        composition.getFatG(),
                        meals.size()),
                accessToken);
    }

    /** Name, type and content of the figure, ready for the HTTP response. */
    public record PublicImage(String name, String type, byte[] content) {}

    private String imageAddress(String identifier,
                                    br.com.nutriplan.handout.domain.PlanHandout o) {
        return o.hasImage()
                ? "/api/public/plans/" + identifier + "/handouts/" + o.getId() + "/image"
                : null;
    }

    /**
     * The figure of a delivered handout, served by the same link as the plan.
     *
     * It goes through the same door as the plan — valid identifier and visible
     * plan — because the figure is part of what the patient received. An
     * attachment from another plan answers as nonexistent, and not as
     * forbidden: whoever holds the link does not need to learn that the other
     * plan exists.
     */
    @Transactional(readOnly = true)
    public PublicImage handoutImage(String identifier, Long attachmentId, String accessToken) {
        MealPlan plan = planRepository.findByPublicIdentifier(identifier)
                .filter(MealPlan::isVisibleByLink)
                .orElseThrow(() -> new NotFoundException(
                        "Plano não encontrado. Confira o link recebido."));
        if (!accessService.allowed(plan, accessToken)) {
            throw new br.com.nutriplan.shared.error.AccessConfirmationRequiredException(
                    "Para abrir a figura, confirme a data de nascimento do paciente.");
        }

        var attachment = planHandoutRepository.findById(attachmentId)
                .filter(a -> a.getPlanId().equals(plan.getId()))
                .orElseThrow(() -> new NotFoundException(
                        "Orientação deste plano", attachmentId));

        var file = planImageRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException(
                        "Imagem da orientação", attachmentId));

        return new PublicImage(attachment.getImageName(), attachment.getImageType(),
                file.getContent());
    }

    /**
     * As receitas usadas no cardapio, com o preparo que o paciente precisa ler.
     *
     * Na ordem em que aparecem, e sem repetir: a mesma receita costuma entrar
     * no almoco e no jantar, e imprimi-la duas vezes dobraria a folha sem dizer
     * nada de novo. Entram so as que tem preparo escrito — uma receita sem
     * instrucao nenhuma viraria um titulo solto embaixo de "Modo de preparo".
     */
    private List<PrescriptionDtos.PublicRecipeResponse> recipesOf(
            MealPlan plan, java.util.Map<Long, br.com.nutriplan.food.domain.Food> foods) {

        var output = new java.util.ArrayList<PrescriptionDtos.PublicRecipeResponse>();
        var seen = new java.util.HashSet<Long>();

        for (var meal : plan.getMeals()) {
            for (var item : meal.getItems()) {
                Long foodId = item.getFoodId();
                if (foodId == null || !seen.add(foodId)) {
                    continue;
                }
                var food = foods.get(foodId);
                if (food == null || !food.isRecipe()) {
                    continue;
                }
                String preparation = food.getModeInstructions();
                if (preparation == null || preparation.isBlank()) {
                    continue;
                }
                output.add(new PrescriptionDtos.PublicRecipeResponse(
                        foodId, food.getDescription(), preparation));
            }
        }
        return output;
    }

    private PrescriptionDtos.PublicItemResponse buildItem(MealItem item) {
        return new PrescriptionDtos.PublicItemResponse(
                item.getKind(),
                item.getDescription(),
                item.servingFormatted(),
                item.getGrams(),
                item.getNotes(),
                item.getSubstitutions().stream()
                        .map(e -> new PrescriptionDtos.PublicSubstitutionResponse(
                                e.getDescription(), e.servingFormatted()))
                        .toList());
    }

    private Map<Long, Food> loadFoods(MealPlan plan) {
        List<Long> ids = plan.getMeals().stream()
                .flatMap(r -> r.getItems().stream())
                .map(MealItem::getFoodId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        // No account context here: the plan already delimits which foods
        // matter, and they are the ones it references itself.
        return foodRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * The patient is greeted by first name. A full name on a page reachable by
     * link is more personal data exposed than necessary.
     */
    private String firstName(String nameComplete) {
        if (nameComplete == null || nameComplete.isBlank()) {
            return null;
        }
        return nameComplete.trim().split("\\s+")[0];
    }
}
