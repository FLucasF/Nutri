package br.com.nutriplan.questionnaire.service;

import br.com.nutriplan.auth.repository.AccountRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.service.PatientService;
import br.com.nutriplan.questionnaire.domain.CutoffRange;
import br.com.nutriplan.questionnaire.domain.AnswerItem;
import br.com.nutriplan.questionnaire.domain.Option;
import br.com.nutriplan.questionnaire.domain.Question;
import br.com.nutriplan.questionnaire.domain.Questionnaire;
import br.com.nutriplan.questionnaire.domain.QuestionnaireAnswer;
import br.com.nutriplan.questionnaire.domain.QuestionType;
import br.com.nutriplan.questionnaire.dto.QuestionnaireDtos;
import br.com.nutriplan.questionnaire.repository.QuestionnaireRepository;
import br.com.nutriplan.questionnaire.repository.QuestionnaireAnswerRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-appointment questionnaires.
 *
 * The patient answers by link, without an account — the same mechanism by which
 * they read the plan. It is a feature about the nutritionist's time, and not
 * about the patient's engagement: arriving at the appointment with the reading
 * already done is what they buy here.
 *
 * The service has two sides that barely touch. The practice's side builds the
 * form and reads the answers, always authenticated. The patient's side serves
 * and receives the form with no credential at all, authorized only by
 * possession of the identifier — and so it answers through a contract of its
 * own, which has no field for a patient name or an account identifier.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireService {

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireAnswerRepository answerRepository;
    private final PatientService patientService;
    private final AccountRepository accountRepository;
    private final CurrentContext contextCurrent;

    // ---------------------------------------------------------------- library

    @Transactional(readOnly = true)
    public List<QuestionnaireDtos.QuestionnaireResponse> list() {
        return questionnaireRepository.visibleTo(contextCurrent.accountId()).stream()
                .map(QuestionnaireDtos.QuestionnaireResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionnaireDtos.QuestionnaireResponse detail(Long id) {
        return QuestionnaireDtos.QuestionnaireResponse.from(visibleRequire(id));
    }

    @Transactional
    public QuestionnaireDtos.QuestionnaireResponse create(QuestionnaireDtos.QuestionnaireRequest req) {
        var questionnaire = new Questionnaire(contextCurrent.accountId(), req.name());
        apply(req, questionnaire);
        questionnaireRepository.save(questionnaire);
        log.info("Questionário criado: id={} conta={}",
                questionnaire.getId(), questionnaire.getAccountId());
        return QuestionnaireDtos.QuestionnaireResponse.from(questionnaire);
    }

    /** Copies a system template into the practice's library, already editable. */
    @Transactional
    public QuestionnaireDtos.QuestionnaireResponse duplicate(Long id) {
        Questionnaire origin = visibleRequire(id);
        var copies = new Questionnaire(contextCurrent.accountId(),
                trim(origin.getName() + " (minha versão)", 150));
        copies.setDescription(origin.getDescription());
        copies.setInstrument(origin.getInstrument());
        copies.setVersion(origin.getVersion());
        copies.setScorable(origin.isScorable());
        copies.setCutoffRange(origin.getCutoffRange());

        int order = 1;
        for (Question p : origin.getQuestions()) {
            var nova = new Question(copies, p.getStatement(), p.getType(), order++);
            nova.setRequired(p.isRequired());
            nova.setOptions(p.getOptions());
            nova.setAjuda(p.getAjuda());
            nova.setHighlight(p.isHighlight());
            copies.getQuestions().add(nova);
        }
        questionnaireRepository.save(copies);
        return QuestionnaireDtos.QuestionnaireResponse.from(copies);
    }

    /**
     * Replaces the questionnaire and increments the template version.
     *
     * The version is what makes it possible to know, later, which form the
     * patient saw — the answers already received keep the number they answered.
     */
    @Transactional
    public QuestionnaireDtos.QuestionnaireResponse update(
            Long id, QuestionnaireDtos.QuestionnaireRequest req) {

        Questionnaire questionnaire = accountRequire(id);
        questionnaire.setName(req.name());
        apply(req, questionnaire);
        questionnaire.setTemplateVersion(questionnaire.getTemplateVersion() + 1);
        return QuestionnaireDtos.QuestionnaireResponse.from(questionnaire);
    }

    @Transactional
    public void remove(Long id) {
        Questionnaire questionnaire = accountRequire(id);
        // Deactivated: answers already received point at it as provenance.
        questionnaire.setActive(false);
    }

    private void apply(QuestionnaireDtos.QuestionnaireRequest req, Questionnaire questionnaire) {
        questionnaire.setDescription(req.description());
        questionnaire.setInstrument(req.instrument());
        questionnaire.setVersion(req.version());
        questionnaire.setScorable(req.scorable());
        questionnaire.setCutoffRange(req.cutoffRange());

        questionnaire.getQuestions().clear();
        int order = 1;
        for (var request : req.questions()) {
            if (request.type().hasOptions() && !StringUtils.hasText(request.options())) {
                throw new BusinessRuleException(
                        "A pergunta \"%s\" e de escolha e precisa de alternativas."
                                .formatted(request.statement()));
            }
            var question = new Question(questionnaire, request.statement(), request.type(), order++);
            // A section is a heading: it is never required, never highlighted.
            boolean answerable = request.type().answerable();
            question.setRequired(answerable && request.required());
            question.setOptions(request.type().hasOptions() ? request.options() : null);
            question.setAjuda(request.ajuda());
            question.setHighlight(answerable && request.highlight());
            questionnaire.getQuestions().add(question);
        }
    }

    // ---------------------------------------------------------------- sending

    @Transactional(readOnly = true)
    public List<QuestionnaireDtos.AnswerResponse> forPatient(Long patientId) {
        patientService.accountRequire(patientId);
        return answerRepository.forPatient(patientId, contextCurrent.accountId()).stream()
                .map(QuestionnaireDtos.AnswerResponse::from)
                .toList();
    }

    /** Answers tied to an appointment — what the consultation screen shows. */
    @Transactional(readOnly = true)
    public List<QuestionnaireDtos.AnswerResponse> forAppointment(Long appointmentId) {
        return answerRepository.forAppointment(appointmentId, contextCurrent.accountId()).stream()
                .map(QuestionnaireDtos.AnswerResponse::from)
                .toList();
    }

    @Transactional
    public QuestionnaireDtos.AnswerResponse send(Long patientId,
                                                    QuestionnaireDtos.SendingRequest req) {
        patientService.accountRequire(patientId);
        Questionnaire questionnaire = visibleRequire(req.questionnaireId());

        var answer = new QuestionnaireAnswer(
                contextCurrent.accountId(), patientId, questionnaire);
        answer.setAppointmentId(req.appointmentId());
        answerRepository.save(answer);

        log.info("Questionário enviado: paciente={} questionário={} link={}",
                patientId, questionnaire.getId(), answer.getPublicIdentifier());
        return QuestionnaireDtos.AnswerResponse.from(answer);
    }

    @Transactional
    public void cancelSending(Long id) {
        answerRepository.delete(answerRepository
                .accountFind(id, contextCurrent.accountId())
                .filter(QuestionnaireAnswer::pending)
                .orElseThrow(() -> new NotFoundException("Envio pendente", id)));
    }

    // ----------------------------------------------------------- patient side

    /** The form, served without a credential — authorized by possession of the link. */
    @Transactional(readOnly = true)
    public QuestionnaireDtos.PublicFormResponse form(String identifier) {
        QuestionnaireAnswer sending = requireSending(identifier);
        var account = accountRepository.findById(sending.getAccountId()).orElse(null);

        return new QuestionnaireDtos.PublicFormResponse(
                account == null ? null : account.getName(),
                sending.getQuestionnaire().getName(),
                sending.getQuestionnaire().getDescription(),
                !sending.pending(),
                sending.getQuestionnaire().getQuestions().stream()
                        .map(QuestionnaireDtos.QuestionResponse::from)
                        .toList());
    }

    /**
     * Receives the patient's answers.
     *
     * The score only comes out with every required question answered. Adding up
     * what arrived and presenting it as a score would produce a number that
     * looks exact — it is the same rule as the skinfolds, where with one
     * missing the system does not estimate and says which one is missing.
     */
    @Transactional
    public void preencher(String identifier, QuestionnaireDtos.FillingRequest req) {
        QuestionnaireAnswer sending = requireSending(identifier);
        if (!sending.pending()) {
            // A link already answered does not accept another answer: what was
            // stored is the record of a consultation that happened.
            throw new BusinessRuleException("Este formulário já foi respondido.");
        }

        Map<Long, String> byQuestion = new LinkedHashMap<>();
        req.answers().forEach(r -> byQuestion.put(r.questionId(), r.value()));

        List<String> missing = new ArrayList<>();
        sending.getItems().clear();
        Integer score = sending.getQuestionnaire().isScorable() ? 0 : null;

        for (Question question : sending.getQuestionnaire().getQuestions()) {
            if (!question.getType().answerable()) {
                continue;
            }
            String value = byQuestion.get(question.getId());
            if (!StringUtils.hasText(value)) {
                if (question.isRequired()) {
                    missing.add(question.getStatement());
                }
                continue;
            }
            var item = new AnswerItem(sending, question, trim(value, 2000));
            if (question.getType().hasOptions()) {
                Integer points = question.getType() == QuestionType.CHOICE_SINGLE
                        ? Option.pointsDe(question.getOptions(), value)
                        : Option.pointsOfAll(question.getOptions(), value);
                item.setPoints(points);
                if (score != null && points != null) {
                    score += points;
                }
            }
            sending.getItems().add(item);
        }

        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "Falta responder: " + String.join("; ", missing));
        }

        sending.setAnsweredAt(Instant.now());
        sending.setScore(score);
        sending.setClassification(CutoffRange.classify(sending.getCutoffRange(), score));
        log.info("Questionário respondido: envio={} escore={}", sending.getId(), score);
    }

    // ------------------------------------------------------------------ apoio

    private QuestionnaireAnswer requireSending(String identifier) {
        return answerRepository.findByPublicIdentifier(identifier)
                .orElseThrow(() -> new NotFoundException(
                        "Formulário não encontrado. Confira o link recebido."));
    }

    private Questionnaire visibleRequire(Long id) {
        return questionnaireRepository.visibleFind(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Questionário", id));
    }

    private Questionnaire accountRequire(Long id) {
        Questionnaire questionnaire = visibleRequire(id);
        if (questionnaire.isSystemTemplate()) {
            throw new BusinessRuleException(
                    "Modelos do sistema não podem ser alterados. Duplique para criar a sua versão.");
        }
        return questionnaire;
    }

    private String trim(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
