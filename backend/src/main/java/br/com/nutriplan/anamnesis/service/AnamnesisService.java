package br.com.nutriplan.anamnesis.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.nutriplan.anamnesis.domain.Anamnesis;
import br.com.nutriplan.anamnesis.domain.AnamnesisAnswer;
import br.com.nutriplan.anamnesis.domain.AnamnesisField;
import br.com.nutriplan.anamnesis.domain.AnamnesisValue;
import br.com.nutriplan.anamnesis.dto.AnamnesisDtos;
import br.com.nutriplan.anamnesis.repository.AnamnesisFieldRepository;
import br.com.nutriplan.anamnesis.repository.AnamnesisRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.questionnaire.domain.AnswerItem;
import br.com.nutriplan.questionnaire.domain.Question;
import br.com.nutriplan.questionnaire.domain.QuestionType;
import br.com.nutriplan.questionnaire.domain.Questionnaire;
import br.com.nutriplan.questionnaire.domain.QuestionnaireAnswer;
import br.com.nutriplan.questionnaire.repository.QuestionnaireAnswerRepository;
import br.com.nutriplan.questionnaire.repository.QuestionnaireRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The anamnesis, the fields the practice declared for it, and the
 * questionnaire it can be built from.
 *
 * Three rules here are worth stating, because all cost something and all buy
 * the same thing — a record that still says in five years what it said today:
 *
 *   - removing a field deactivates it instead of deleting it, so the values
 *     already written keep the field they point at;
 *   - a value copies the field's label when it is written, so renaming the
 *     field later does not rewrite past records;
 *   - an answer copies the question's statement and type, so a questionnaire
 *     edited later does not rewrite what was asked on the day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnamnesisService {

    private static final int HIGHLIGHT_ORDER_OFFSET = 1000;

    private final AnamnesisRepository anamnesisRepository;
    private final AnamnesisFieldRepository fieldRepository;
    private final PatientRepository patientRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireAnswerRepository sendingRepository;
    private final CurrentContext currentContext;
    private final ObjectMapper mapper;

    // ------------------------------------------------------------- os campos

    @Transactional(readOnly = true)
    public List<AnamnesisDtos.FieldResponse> fields() {
        return fieldRepository
                .findByAccountIdAndActiveTrueOrderByOrderAsc(currentContext.accountId())
                .stream()
                .map(AnamnesisDtos.FieldResponse::from)
                .toList();
    }

    /**
     * Replaces the practice's field list.
     *
     * The whole set arrives at once because the order is a property of the set.
     * What disappears from the request is deactivated, never deleted: anamneses
     * already written point at it.
     */
    @Transactional
    public List<AnamnesisDtos.FieldResponse> saveFields(AnamnesisDtos.FieldsRequest request) {
        Long accountId = currentContext.accountId();
        List<AnamnesisField> existing = fieldRepository.findByAccountIdOrderByOrderAsc(accountId);
        Map<String, AnamnesisField> byLabel = new HashMap<>();
        for (AnamnesisField field : existing) {
            byLabel.putIfAbsent(field.getLabel().toLowerCase(), field);
        }

        List<AnamnesisField> kept = new ArrayList<>();
        int order = 1;
        for (AnamnesisDtos.FieldRequest wanted : request.fields()) {
            String label = wanted.label().trim();
            // Reusing the row of a field with the same label keeps the link of
            // every value already written under it.
            AnamnesisField field = byLabel.remove(label.toLowerCase());
            if (field == null) {
                field = new AnamnesisField(accountId, label, order, wanted.showInListing());
            } else {
                field.setLabel(label);
                field.setOrder(order);
                field.setShowInListing(wanted.showInListing());
                field.setActive(true);
            }
            kept.add(fieldRepository.save(field));
            order++;
        }

        for (AnamnesisField gone : byLabel.values()) {
            gone.setActive(false);
            fieldRepository.save(gone);
        }

        log.info("Campos de anamnese atualizados: conta={} ativos={}", accountId, kept.size());
        return kept.stream().map(AnamnesisDtos.FieldResponse::from).toList();
    }

    // ----------------------------------------------------------- a anamnese

    @Transactional(readOnly = true)
    public List<AnamnesisDtos.AnamnesisSummary> ofPatient(Long patientId) {
        Long accountId = currentContext.accountId();
        requirePatient(patientId, accountId);

        List<Long> shown = fieldRepository
                .findByAccountIdAndActiveTrueOrderByOrderAsc(accountId)
                .stream()
                .filter(AnamnesisField::isShowInListing)
                .map(AnamnesisField::getId)
                .toList();

        return anamnesisRepository.ofPatient(accountId, patientId).stream()
                .map(anamnesis -> new AnamnesisDtos.AnamnesisSummary(
                        anamnesis.getId(),
                        anamnesis.getName(),
                        anamnesis.getDate(),
                        highlights(anamnesis, shown),
                        anamnesis.getQuestionnaireName(),
                        anamnesis.getSendingId()))
                .toList();
    }

    /**
     * What the listing shows without opening: the declared fields marked for
     * it, then the questionnaire answers whose question is highlighted.
     */
    private static List<AnamnesisDtos.ValueResponse> highlights(Anamnesis anamnesis, List<Long> shown) {
        List<AnamnesisDtos.ValueResponse> out = new ArrayList<>();
        anamnesis.getValues().stream()
                .filter(AnamnesisValue::hasValue)
                .filter(value -> value.getFieldId() != null && shown.contains(value.getFieldId()))
                .map(AnamnesisDtos.ValueResponse::from)
                .forEach(out::add);
        anamnesis.getAnswers().stream()
                .filter(AnamnesisAnswer::isHighlight)
                .filter(AnamnesisAnswer::hasValue)
                .map(answer -> new AnamnesisDtos.ValueResponse(null, answer.getStatement(),
                        answer.getValue(), HIGHLIGHT_ORDER_OFFSET + answer.getOrder()))
                .forEach(out::add);
        return out;
    }

    @Transactional(readOnly = true)
    public AnamnesisDtos.AnamnesisResponse detail(Long id) {
        Anamnesis anamnesis = require(id);
        return AnamnesisDtos.AnamnesisResponse.from(anamnesis,
                patientName(anamnesis.getPatientId(), anamnesis.getAccountId()));
    }

    @Transactional
    public AnamnesisDtos.AnamnesisResponse create(AnamnesisDtos.AnamnesisRequest request) {
        Long accountId = currentContext.accountId();
        requirePatient(request.patientId(), accountId);

        var anamnesis = new Anamnesis(accountId, request.patientId(),
                request.name().trim(), request.date());
        apply(anamnesis, request, accountId);
        anamnesisRepository.save(anamnesis);
        log.info("Anamnese criada: id={} paciente={} questionário={}",
                anamnesis.getId(), anamnesis.getPatientId(), anamnesis.getQuestionnaireId());
        return AnamnesisDtos.AnamnesisResponse.from(anamnesis,
                patientName(anamnesis.getPatientId(), accountId));
    }

    @Transactional
    public AnamnesisDtos.AnamnesisResponse update(Long id, AnamnesisDtos.AnamnesisRequest request) {
        Anamnesis anamnesis = require(id);
        anamnesis.setName(request.name().trim());
        anamnesis.setDate(request.date());
        apply(anamnesis, request, anamnesis.getAccountId());
        return AnamnesisDtos.AnamnesisResponse.from(anamnesisRepository.save(anamnesis),
                patientName(anamnesis.getPatientId(), anamnesis.getAccountId()));
    }

    /**
     * Copies an anamnesis into a new one, dated today.
     *
     * It is how a returning patient's record starts from the previous visit
     * instead of from a blank page.
     */
    @Transactional
    public AnamnesisDtos.AnamnesisResponse duplicate(Long id) {
        Anamnesis source = require(id);
        var copy = new Anamnesis(source.getAccountId(), source.getPatientId(),
                shorten(source.getName() + " (cópia)"), LocalDate.now());
        copy.setBody(source.getBody());
        copy.setQuestionnaireId(source.getQuestionnaireId());
        copy.setQuestionnaireName(source.getQuestionnaireName());
        copy.setTemplateVersion(source.getTemplateVersion());
        for (AnamnesisValue value : source.getValues()) {
            copy.add(new AnamnesisValue(value.getFieldId(), value.getLabel(),
                    value.getValue(), value.getOrder()));
        }
        for (AnamnesisAnswer answer : source.getAnswers()) {
            copy.add(new AnamnesisAnswer(answer.getQuestionId(), answer.getStatement(),
                    answer.getType(), answer.getValue(), answer.isHighlight(), answer.getOrder()));
        }
        anamnesisRepository.save(copy);
        log.info("Anamnese duplicada: origem={} copia={}", id, copy.getId());
        return AnamnesisDtos.AnamnesisResponse.from(copy,
                patientName(copy.getPatientId(), copy.getAccountId()));
    }

    /**
     * Turns what the patient answered before the consultation into an
     * anamnesis, so the visit starts from the answers instead of from a blank
     * page.
     *
     * Once only: the sending is the record of what the patient said, and the
     * anamnesis made from it is the record the professional goes on to edit.
     * Importing twice would be two records claiming to be the same visit.
     */
    @Transactional
    public AnamnesisDtos.AnamnesisResponse fromSending(Long sendingId) {
        Long accountId = currentContext.accountId();
        QuestionnaireAnswer sending = sendingRepository.accountFind(sendingId, accountId)
                .orElseThrow(() -> new NotFoundException("Envio", sendingId));
        if (sending.pending()) {
            throw new BusinessRuleException(
                    "O paciente ainda não respondeu este questionário. A anamnese sai das respostas dele.");
        }
        if (anamnesisRepository.existsByAccountIdAndSendingId(accountId, sendingId)) {
            throw new BusinessRuleException(
                    "Estas respostas já viraram uma anamnese. Abra-a na lista para continuar dali.");
        }

        Questionnaire questionnaire = sending.getQuestionnaire();
        var anamnesis = new Anamnesis(accountId, sending.getPatientId(),
                shorten(questionnaire.getName() + " (pré-consulta)"), LocalDate.now());
        anamnesis.setQuestionnaireId(questionnaire.getId());
        anamnesis.setQuestionnaireName(questionnaire.getName());
        anamnesis.setTemplateVersion(sending.getTemplateVersion());
        anamnesis.setSendingId(sending.getId());

        Map<Long, Question> live = new HashMap<>();
        for (Question question : questionnaire.getQuestions()) {
            live.put(question.getId(), question);
        }
        int fallbackOrder = 1;
        for (AnswerItem item : sending.getItems()) {
            Question question = item.getQuestionId() == null ? null : live.get(item.getQuestionId());
            anamnesis.add(new AnamnesisAnswer(
                    item.getQuestionId(),
                    item.getQuestionText(),
                    question != null ? question.getType() : QuestionType.TEXT,
                    item.getValue(),
                    question != null && question.isHighlight(),
                    question != null ? question.getOrder() : fallbackOrder));
            fallbackOrder++;
        }
        anamnesisRepository.save(anamnesis);
        log.info("Anamnese importada da pré-consulta: envio={} anamnese={}", sendingId, anamnesis.getId());
        return AnamnesisDtos.AnamnesisResponse.from(anamnesis,
                patientName(anamnesis.getPatientId(), accountId));
    }

    @Transactional
    public void remove(Long id) {
        Anamnesis anamnesis = require(id);
        anamnesisRepository.delete(anamnesis);
        log.info("Anamnese removida: id={} paciente={}", id, anamnesis.getPatientId());
    }

    // -------------------------------------------------------------- internos

    private void apply(Anamnesis anamnesis, AnamnesisDtos.AnamnesisRequest request, Long accountId) {
        // The body is checked here and not at the entity: this is the edge
        // where the browser's JSON stops being a string and becomes a document.
        anamnesis.setBody(RichTextDocument.of(request.body(), mapper).json());

        applyValues(anamnesis, request, accountId);
        applyAnswers(anamnesis, request, accountId);
    }

    private void applyValues(Anamnesis anamnesis, AnamnesisDtos.AnamnesisRequest request, Long accountId) {
        anamnesis.clearValues();
        if (request.values() == null || request.values().isEmpty()) {
            return;
        }
        Map<Long, AnamnesisField> byId = new HashMap<>();
        for (AnamnesisField field : fieldRepository.findByAccountIdOrderByOrderAsc(accountId)) {
            byId.put(field.getId(), field);
        }
        int order = 1;
        for (AnamnesisDtos.ValueRequest wanted : request.values()) {
            AnamnesisField field = wanted.fieldId() == null ? null : byId.get(wanted.fieldId());
            if (field == null) {
                // A field from another practice, or one that no longer exists.
                // Dropping it is better than writing a value under a label we
                // cannot name.
                continue;
            }
            if (!StringUtils.hasText(wanted.value())) {
                continue;
            }
            anamnesis.add(new AnamnesisValue(field.getId(), field.getLabel(),
                    wanted.value().trim(), order));
            order++;
        }
    }

    /**
     * The questionnaire answers.
     *
     * A question still in the questionnaire lends its current statement,
     * type and highlight. A question that has left it keeps what this
     * anamnesis already froze for it — the visit asked that question, whatever
     * the model says today. An answer to a question this record has never
     * seen is dropped: there is no wording to write it under.
     */
    private void applyAnswers(Anamnesis anamnesis, AnamnesisDtos.AnamnesisRequest request, Long accountId) {
        if (request.questionnaireId() != null) {
            Questionnaire questionnaire = questionnaireRepository
                    .visibleFind(request.questionnaireId(), accountId)
                    .orElseThrow(() -> new NotFoundException("Questionário", request.questionnaireId()));
            anamnesis.setQuestionnaireId(questionnaire.getId());
            anamnesis.setQuestionnaireName(questionnaire.getName());
            anamnesis.setTemplateVersion(questionnaire.getTemplateVersion());
        }
        if (!anamnesis.fromQuestionnaire()) {
            anamnesis.clearAnswers();
            return;
        }

        Map<Long, AnamnesisAnswer> frozen = new HashMap<>();
        for (AnamnesisAnswer answer : anamnesis.getAnswers()) {
            if (answer.getQuestionId() != null) {
                frozen.put(answer.getQuestionId(), answer);
            }
        }
        Map<Long, Question> live = new HashMap<>();
        questionnaireRepository.visibleFind(anamnesis.getQuestionnaireId(), accountId)
                .ifPresent(q -> q.getQuestions().forEach(question -> live.put(question.getId(), question)));

        List<AnamnesisAnswer> rebuilt = new ArrayList<>();
        if (request.answers() != null) {
            for (AnamnesisDtos.AnswerRequest wanted : request.answers()) {
                if (!StringUtils.hasText(wanted.value())) {
                    continue;
                }
                String value = wanted.value().trim();
                Question question = live.get(wanted.questionId());
                if (question != null) {
                    if (!question.getType().answerable()) {
                        continue;
                    }
                    rebuilt.add(new AnamnesisAnswer(question.getId(), question.getStatement(),
                            question.getType(), value, question.isHighlight(), question.getOrder()));
                    continue;
                }
                AnamnesisAnswer old = frozen.get(wanted.questionId());
                if (old != null) {
                    rebuilt.add(new AnamnesisAnswer(old.getQuestionId(), old.getStatement(),
                            old.getType(), value, old.isHighlight(), old.getOrder()));
                }
            }
        }
        rebuilt.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));

        anamnesis.clearAnswers();
        rebuilt.forEach(anamnesis::add);
    }

    private Anamnesis require(Long id) {
        return anamnesisRepository.find(id, currentContext.accountId())
                .orElseThrow(() -> new NotFoundException("Anamnese", id));
    }

    /**
     * O nome do paciente, para o cabecalho do PDF.
     *
     * Lido na hora em vez de copiado para a anamnese: o nome pode ser
     * corrigido depois — um erro de digitacao, um sobrenome de casamento —
     * e a folha reimpressa deve sair com o nome de agora. E o contrario do
     * rotulo do campo, que fica congelado porque muda de significado.
     */
    private String patientName(Long patientId, Long accountId) {
        return patientRepository.findByIdAndAccountId(patientId, accountId)
                .map(br.com.nutriplan.patient.domain.Patient::getName)
                .orElse(null);
    }

    private void requirePatient(Long patientId, Long accountId) {
        patientRepository.findByIdAndAccountId(patientId, accountId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }

    private static String shorten(String name) {
        return name.length() <= 150 ? name : name.substring(0, 150);
    }
}
