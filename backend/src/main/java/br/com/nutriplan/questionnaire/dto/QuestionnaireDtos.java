package br.com.nutriplan.questionnaire.dto;

import br.com.nutriplan.questionnaire.domain.AnswerItem;
import br.com.nutriplan.questionnaire.domain.Option;
import br.com.nutriplan.questionnaire.domain.Question;
import br.com.nutriplan.questionnaire.domain.Questionnaire;
import br.com.nutriplan.questionnaire.domain.QuestionnaireAnswer;
import br.com.nutriplan.questionnaire.domain.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class QuestionnaireDtos {

    private QuestionnaireDtos() {
    }

    // -------------------------------------------------------------------- input

    public record QuestionRequest(
            @NotBlank @Size(max = 500) String statement,
            @NotNull QuestionType type,
            boolean required,
            /** "Nunca=0|Às vezes=1|Sempre=2". The score is optional. */
            @Size(max = 2000) String options,
            @Size(max = 500) String ajuda
    ) {}

    public record QuestionnaireRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 120) String instrument,
            @Size(max = 30) String version,
            boolean scorable,
            /** "0-5=Baixo|6-10=Moderado|11-99=Alto". */
            @Size(max = 500) String cutoffRange,
            @NotEmpty(message = "Um questionário precisa de ao menos uma pergunta")
            @Valid List<QuestionRequest> questions
    ) {}

    public record SendingRequest(
            @NotNull Long questionnaireId,
            /** Ties it to the appointment, when the submission is for a consultation. */
            Long appointmentId
    ) {}

    /** One answer from the patient: the key is the question id. */
    public record ItemAnswered(@NotNull Long questionId, String value) {}

    public record FillingRequest(
            @NotNull @Valid List<ItemAnswered> answers
    ) {}

    // ------------------------------------------------------------------ output

    public record OptionResponse(String label, Integer points) {
        static OptionResponse from(Option o) {
            return new OptionResponse(o.label(), o.points());
        }
    }

    public record QuestionResponse(
            Long id,
            String statement,
            QuestionType type,
            String typeDescription,
            boolean required,
            Integer order,
            String ajuda,
            List<OptionResponse> options
    ) {
        public static QuestionResponse from(Question p) {
            return new QuestionResponse(p.getId(), p.getStatement(), p.getType(),
                    p.getType().getDescription(), p.isRequired(), p.getOrder(), p.getAjuda(),
                    p.optionsAnalisadas().stream().map(OptionResponse::from).toList());
        }
    }

    public record QuestionnaireResponse(
            Long id,
            String name,
            String description,
            String instrument,
            String version,
            boolean scorable,
            String cutoffRange,
            int templateVersion,
            boolean systemTemplate,
            boolean editable,
            List<QuestionResponse> questions
    ) {
        public static QuestionnaireResponse from(Questionnaire q) {
            return new QuestionnaireResponse(q.getId(), q.getName(), q.getDescription(),
                    q.getInstrument(), q.getVersion(), q.isScorable(), q.getCutoffRange(),
                    q.getTemplateVersion(), q.isSystemTemplate(), !q.isSystemTemplate(),
                    q.getQuestions().stream().map(QuestionResponse::from).toList());
        }
    }

    public record ItemResponse(String question, String value, Integer points) {
        static ItemResponse from(AnswerItem i) {
            return new ItemResponse(i.getQuestionText(), i.getValue(), i.getPoints());
        }
    }

    /**
     * One submission and what came back from it.
     *
     * `templateVersion` says which edition of the form the patient saw — the
     * template may have changed since, and the questions shown here come from
     * the answer, and not from today's questionnaire.
     */
    public record AnswerResponse(
            Long id,
            Long questionnaireId,
            String questionnaire,
            int templateVersion,
            String publicIdentifier,
            Instant sentAt,
            Instant answeredAt,
            boolean pending,
            Integer score,
            String classification,
            List<ItemResponse> items
    ) {
        public static AnswerResponse from(QuestionnaireAnswer r) {
            return new AnswerResponse(r.getId(), r.getQuestionnaire().getId(),
                    r.getQuestionnaire().getName(), r.getTemplateVersion(),
                    r.getPublicIdentifier(), r.getSentAt(), r.getAnsweredAt(),
                    r.pending(), r.getScore(), r.getClassification(),
                    r.getItems().stream().map(ItemResponse::from).toList());
        }
    }

    /**
     * The form as the patient sees it, without authentication.
     *
     * It carries no patient name and no account identifier: the protection is
     * in the shape of the contract, and not in remembering to filter on every
     * change — the same design as the public plan.
     */
    public record PublicFormResponse(
            String practice,
            String title,
            String description,
            boolean alreadyAnswered,
            List<QuestionResponse> questions
    ) {}
}
