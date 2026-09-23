package br.com.nutriplan.handout.service;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.handout.domain.HandoutImage;
import br.com.nutriplan.handout.domain.PlanImage;
import br.com.nutriplan.handout.domain.Handout;
import br.com.nutriplan.handout.domain.PlanHandout;
import br.com.nutriplan.handout.dto.HandoutDtos;
import br.com.nutriplan.handout.repository.HandoutImageRepository;
import br.com.nutriplan.handout.repository.PlanImageRepository;
import br.com.nutriplan.handout.repository.PlanHandoutRepository;
import br.com.nutriplan.handout.repository.HandoutRepository;
import br.com.nutriplan.prescription.service.MealPlanService;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * The library of handouts and what reaches the plan from it.
 *
 * The system ships a few templates as a starting point. They are not editable —
 * for the same reason the food reference tables are not: they are a shared
 * catalog, and editing on everybody's behalf would be deciding for somebody
 * else's practice. Whoever wants to change one, copies it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandoutService {

    private final HandoutRepository handoutRepository;
    private final PlanHandoutRepository forPlanRepository;
    private final HandoutImageRepository libraryImage;
    private final PlanImageRepository planImage;
    private final MealPlanService planService;
    private final CurrentContext contextCurrent;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    // ---------------------------------------------------------------- library

    @Transactional(readOnly = true)
    public Page<HandoutDtos.HandoutResponse> list(String term, Pageable pageable) {
        String search = StringUtils.hasText(term) ? term.trim().toLowerCase() : null;
        return handoutRepository.visibleTo(contextCurrent.accountId(), search, pageable)
                .map(HandoutDtos.HandoutResponse::from);
    }

    @Transactional(readOnly = true)
    public HandoutDtos.HandoutResponse detail(Long id) {
        return HandoutDtos.HandoutResponse.from(visibleRequire(id));
    }

    @Transactional
    public HandoutDtos.HandoutResponse create(HandoutDtos.HandoutRequest req) {
        var handout = new Handout(contextCurrent.accountId(), req.title(),
                RichTextDocument.ofTextOrDocument(req.body(), mapper).json());
        handoutRepository.save(handout);
        log.info("Orientação criada: id={} conta={}", handout.getId(), handout.getAccountId());
        return HandoutDtos.HandoutResponse.from(handout);
    }

    /**
     * Copies a template into the practice's library, already editable.
     *
     * It is the way to adapt a system template without changing the original —
     * the same design as "register a food of your own starting from this one".
     */
    @Transactional
    public HandoutDtos.HandoutResponse duplicate(Long id) {
        Handout origin = visibleRequire(id);
        var copies = new Handout(contextCurrent.accountId(),
                trim(origin.getTitle() + " (minha versão)", 150), origin.getBody());
        handoutRepository.save(copies);
        return HandoutDtos.HandoutResponse.from(copies);
    }

    @Transactional
    public HandoutDtos.HandoutResponse update(Long id, HandoutDtos.HandoutRequest req) {
        Handout handout = accountRequire(id);
        handout.setTitle(req.title());
        handout.setBody(RichTextDocument.ofTextOrDocument(req.body(), mapper).json());
        return HandoutDtos.HandoutResponse.from(handout);
    }

    @Transactional
    public void remove(Long id) {
        Handout handout = accountRequire(id);
        // Deactivated: plans already delivered reference this handout as
        // provenance, and the text delivered goes on holding.
        handout.setActive(false);
    }

    // ------------------------------------------------------- on the plan

    @Transactional(readOnly = true)
    public List<HandoutDtos.PlanHandoutResponse> forPlan(Long planId) {
        planService.accountRequire(planId);
        return forPlanRepository.findByPlanIdOrderByOrderAsc(planId).stream()
                .map(HandoutDtos.PlanHandoutResponse::from)
                .toList();
    }

    /**
     * Attaches a handout to the plan, copying the text.
     *
     * The copy happens now, and not at publication: that way the nutritionist
     * can adapt the text for this patient without dirtying the template, and no
     * later edit of the library reaches what was attached.
     */
    @Transactional
    public HandoutDtos.PlanHandoutResponse attach(Long planId,
                                                          HandoutDtos.AttachmentRequest req) {
        planService.accountRequire(planId);

        String title = req.title();
        String body = req.body();
        Long origin = req.handoutId();

        if (origin != null) {
            Handout template = visibleRequire(origin);
            if (!StringUtils.hasText(title)) {
                title = template.getTitle();
            }
            if (!StringUtils.hasText(body)) {
                body = template.getBody();
            }
        }
        if (!StringUtils.hasText(title) || !StringUtils.hasText(body)) {
            throw new BusinessRuleException(
                    "Informe o texto da orientação, ou escolha uma da biblioteca");
        }

        int order = forPlanRepository.findByPlanIdOrderByOrderAsc(planId).size();
        var attachment = new PlanHandout(planId, trim(title, 150),
                trim(body, 8000), order);
        attachment.setHandoutId(origin);
        forPlanRepository.save(attachment);

        // The image is copied along, and not referenced: changing the figure in
        // the template later must not change what the patient already
        // received.
        if (origin != null) {
            Handout template = visibleRequire(origin);
            if (template.hasImage()) {
                libraryImage.findById(origin).ifPresent(image -> {
                    attachment.setImageName(template.getImageName());
                    attachment.setImageType(template.getImageType());
                    planImage.save(new PlanImage(attachment.getId(), image.getContent()));
                });
            }
        }

        log.info("Orientação anexada ao plano {}: origem={}", planId, origin);
        return HandoutDtos.PlanHandoutResponse.from(attachment);
    }

    @Transactional
    public HandoutDtos.PlanHandoutResponse editNoPlan(
            Long planId, Long attachmentId, HandoutDtos.HandoutRequest req) {
        PlanHandout attachment = requireAttachment(planId, attachmentId);
        attachment.setTitle(req.title());
        attachment.setBody(RichTextDocument.ofTextOrDocument(req.body(), mapper).json());
        return HandoutDtos.PlanHandoutResponse.from(attachment);
    }

    @Transactional
    public void detach(Long planId, Long attachmentId) {
        forPlanRepository.delete(requireAttachment(planId, attachmentId));
    }

    // ------------------------------------------------------------------ image

    /** Name, type and content, ready for the HTTP response. */
    public record Image(String name, String type, byte[] content) {}

    /** A picture of a divided plate is worth more than the paragraph describing it. */
    private static final int IMAGE_LIMIT = 2 * 1024 * 1024;

    @Transactional
    public void attachImage(Long id, String name, String type, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Envie um arquivo não vazio");
        }
        if (content.length > IMAGE_LIMIT) {
            throw new BusinessRuleException("A imagem pode ter no máximo 2 MB");
        }
        if (type == null || !type.startsWith("image/")) {
            throw new BusinessRuleException("O arquivo precisa ser uma imagem");
        }
        Handout handout = accountRequire(id);
        handout.setImageName(name);
        handout.setImageType(type);
        libraryImage.save(new HandoutImage(id, content));
    }

    @Transactional(readOnly = true)
    public Image image(Long id) {
        Handout handout = visibleRequire(id);
        var file = libraryImage.findById(id)
                .orElseThrow(() -> new NotFoundException("Imagem da orientação", id));
        return new Image(handout.getImageName(), handout.getImageType(),
                file.getContent());
    }

    /** The image delivered in a plan — the copy, and not the library's. */
    @Transactional(readOnly = true)
    public Image planImage(Long planId, Long attachmentId) {
        PlanHandout attachment = requireAttachment(planId, attachmentId);
        var file = planImage.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException(
                        "Imagem da orientação do plano", attachmentId));
        return new Image(attachment.getImageName(), attachment.getImageType(), file.getContent());
    }

    // ------------------------------------------------------------------ apoio

    private PlanHandout requireAttachment(Long planId, Long attachmentId) {
        planService.accountRequire(planId);
        return forPlanRepository.findById(attachmentId)
                .filter(a -> a.getPlanId().equals(planId))
                .orElseThrow(() -> new NotFoundException(
                        "Orientação do plano " + planId, attachmentId));
    }

    private Handout visibleRequire(Long id) {
        return handoutRepository.visibleFind(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Orientação", id));
    }

    private Handout accountRequire(Long id) {
        Handout handout = visibleRequire(id);
        if (handout.isSystemTemplate()) {
            throw new BusinessRuleException(
                    "Modelos do sistema não podem ser alterados. Duplique para criar a sua versão.");
        }
        return handout;
    }

    private String trim(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
