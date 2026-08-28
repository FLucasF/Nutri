package br.com.nutriplan.prescription.service;

import br.com.nutriplan.handout.repository.PlanImageRepository;
import br.com.nutriplan.prescription.domain.MealPlan;
import br.com.nutriplan.prescription.domain.PlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues the plan as a PDF.
 *
 * It exists as a service of its own, and not as a method in the controller,
 * because the whole path has to run inside a single transaction: load the plan,
 * walk the meals and items — which are lazily loaded collections — and only
 * then draw the sheet. Assembling it outside the transaction fails at the first
 * meal, with an error that mentions no PDF at all.
 */
@Service
@RequiredArgsConstructor
public class PlanPrintingService {

    private final MealPlanService planService;
    private final PublicPlanService publicPlanService;
    private final PlanPdfGenerator generator;
    private final PlanImageRepository planImageRepository;

    public record PlanPdf(String fileName, byte[] content) {}

    @Transactional(readOnly = true)
    public PlanPdf issue(Long id) {
        MealPlan plan = planService.accountRequire(id);
        // A draft prints too: checking the sheet before publishing is part of
        // the work. And it is the sheet that identifies itself, rather than the
        // route refusing.
        boolean draft = plan.getStatus() == PlanStatus.DRAFT;
        var view = publicPlanService.build(plan);
        byte[] content = generator.generate(view, draft, figuresDe(view));
        return new PlanPdf(fileName(plan.getTitle()), content);
    }

    /**
     * Loads the figures of this plan's handouts.
     *
     * One query per figure, and not a single one with all of them: there are
     * few per plan, and the alternative would be dragging the files of no plan
     * at all into memory.
     */
    private Map<Long, byte[]> figuresDe(
            br.com.nutriplan.prescription.dto.PrescriptionDtos.PublicPlanResponse view) {
        var figures = new HashMap<Long, byte[]>();
        for (var attachment : view.handoutsAttached()) {
            if (attachment.image() != null) {
                planImageRepository.findById(attachment.id())
                        .ifPresent(i -> figures.put(attachment.id(), i.getContent()));
            }
        }
        return figures;
    }

    /**
     * A predictable filename, without accents.
     *
     * Accents and spaces in an attachment name break in an old email client and
     * in a file system that does not speak UTF-8 — and the file reaches the
     * patient by exactly those paths.
     */
    private String fileName(String title) {
        String clean = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
        return (clean.isEmpty() ? "plan" : clean) + ".pdf";
    }
}
