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

    @Transactional(readOnly = true)
    public PrescriptionDtos.PublicPlanResponse byIdentifier(String identifier) {
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

        return build(plan);
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
                        meal.getName(), meal.getTime(), meal.getNotes(),
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
                new PrescriptionDtos.PublicSummaryResponse(
                        composition.getEnergyKcal(),
                        composition.getProteinG(),
                        composition.getCarbohydrateG(),
                        composition.getFatG(),
                        meals.size()));
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
    public PublicImage handoutImage(String identifier, Long attachmentId) {
        MealPlan plan = planRepository.findByPublicIdentifier(identifier)
                .filter(MealPlan::isVisibleByLink)
                .orElseThrow(() -> new NotFoundException(
                        "Plano não encontrado. Confira o link recebido."));

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

    private PrescriptionDtos.PublicItemResponse buildItem(MealItem item) {
        return new PrescriptionDtos.PublicItemResponse(
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
