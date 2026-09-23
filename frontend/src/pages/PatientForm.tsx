import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { CircleCheck, FileQuestion } from "lucide-react";
import { ErrorApi, api } from "../api/client";
import { explainError } from "../api/errors";
import type { PublicForm, QuestionnaireQuestion } from "../api/types";

/**
 * The questionnaire as the patient answers it.
 *
 * Without a login: whoever has the link, answers — the same mechanism by which
 * they read the plan. The page is deliberately simpler than the rest of the
 * system because whoever opens it is not a user of the product: they open it
 * once, answer and leave. One question per card, so that a phone shows one
 * thing to answer at a time, and a progress line so that a long questionnaire
 * says how much is left.
 */
export default function PatientForm() {
  const { identifier } = useParams();

  const [form, setForm] = useState<PublicForm | null>(null);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!identifier) return;
    api.questionnaires
      .form(identifier)
      .then((f) => {
        setForm(f);
        setReady(f.alreadyAnswered);
      })
      .catch((e) =>
        setError(
          e instanceof ErrorApi
            ? e.message
            : "Não foi possível abrir o formulário. Verifique sua conexão.",
        ),
      )
      .finally(() => setLoading(false));
  }, [identifier]);

  useEffect(() => {
    if (form?.title) {
      document.title = `${form.title} — NutriPlan`;
    }
  }, [form]);

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!identifier) return;
    setError(null);
    setSending(true);
    try {
      await api.questionnaires.answer(
        identifier,
        Object.entries(answers)
          .filter(([, value]) => value.trim())
          .map(([questionId, value]) => ({ questionId: Number(questionId), value })),
      );
      setReady(true);
    } catch (e) {
      setError(explainError(e, "enviar as respostas"));
    } finally {
      setSending(false);
    }
  }

  if (loading) {
    return (
      <div className="page-patient">
        <p className="loading">Carregando…</p>
      </div>
    );
  }

  if (error && !form) {
    return (
      <div className="page-patient">
        <div className="plan-patient plan-patient-empty">
          <div className="card card-centered">
            <span className="empty-icon">
              <FileQuestion aria-hidden="true" />
            </span>
            <h1>Formulário não encontrado</h1>
            <p className="discreto">{error}</p>
          </div>
        </div>
      </div>
    );
  }

  if (!form) return null;

  const total = form.questions.length;
  const answered = form.questions.filter((p) => (answers[p.id] ?? "").trim()).length;
  const hasRequired = form.questions.some((p) => p.required);

  return (
    <div className="page-patient">
      <div className="plan-patient">
        <header className="cover-plan">
          {form.practice && (
            <span className="cover-brand">
              <span className="cover-dot" aria-hidden="true" />
              <span className="eyebrow">{form.practice}</span>
            </span>
          )}
          <h1>{form.title}</h1>
          {form.description && <p className="cover-greeting">{form.description}</p>}
        </header>

        {ready ? (
          <div className="card card-centered">
            <span className="empty-icon ok">
              <CircleCheck aria-hidden="true" />
            </span>
            <h2>Respostas enviadas</h2>
            <p className="discreto">
              Obrigado. Seu nutricionista já recebeu. Pode fechar esta página.
            </p>
          </div>
        ) : (
          <form className="form-patient" onSubmit={send}>
            {error && (
              <div className="warning error form-alert" role="alert">
                {error}
              </div>
            )}

            {total > 0 && (
              <div className="form-progress">
                <div className="form-progress-line">
                  <span className="form-progress-count">
                    <span className="readout strong">
                      {answered} de {total}
                    </span>{" "}
                    respondidas
                  </span>
                  {hasRequired && (
                    <span className="form-progress-legend">
                      <span className="required" aria-hidden="true">
                        *
                      </span>{" "}
                      obrigatória
                    </span>
                  )}
                </div>
                <div className="form-progress-track" aria-hidden="true">
                  <div
                    className="form-progress-fill"
                    style={{ width: `${total ? (answered / total) * 100 : 0}%` }}
                  />
                </div>
              </div>
            )}

            <ol className="question-list">
              {form.questions.map((p, index) => (
                <Question
                  key={p.id}
                  question={p}
                  index={index + 1}
                  total={total}
                  value={answers[p.id] ?? ""}
                  onChange={(value) => setAnswers((r) => ({ ...r, [p.id]: value }))}
                />
              ))}
            </ol>

            <div className="form-patient-actions">
              <button className="button grande w-full" type="submit" disabled={sending}>
                {sending ? "Enviando…" : "Enviar respostas"}
              </button>
              <p className="minusculo form-footnote">
                As respostas vão direto para o seu nutricionista. O formulário aceita um envio só.
              </p>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

type QuestionProps = {
  question: QuestionnaireQuestion;
  index: number;
  total: number;
  value: string;
  onChange: (value: string) => void;
};

function kindOf(type: QuestionnaireQuestion["type"]): "choice" | "number" | "text" {
  if (type === "CHOICE_SINGLE" || type === "MULTIPLE") return "choice";
  if (type === "NUMBER") return "number";
  return "text";
}

/**
 * One card per question. The label keeps the `p-{id}` pairing — it is the only
 * accessible name the control has — and the help text, when there is one, is
 * tied to the control through aria-describedby so that it is read with it.
 */
function Question({ question: p, index, total, value, onChange }: QuestionProps) {
  const id = `p-${p.id}`;
  const hintId = p.ajuda ? `${id}-hint` : undefined;
  const kind = kindOf(p.type);

  return (
    <li className="card question-card field" data-kind={kind}>
      <span className="question-index readout">
        {index}/{total}
      </span>
      <label htmlFor={id}>
        {p.statement}
        {p.required && (
          <span className="required" aria-hidden>
            {" "}
            *
          </span>
        )}
      </label>
      {p.ajuda && (
        <p className="field-hint" id={hintId}>
          {p.ajuda}
        </p>
      )}

      {kind === "choice" ? (
        <select
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={p.required}
          aria-describedby={hintId}
        >
          <option value="">Selecione…</option>
          {p.options.map((o) => (
            <option key={o.label} value={o.label}>
              {o.label}
            </option>
          ))}
        </select>
      ) : kind === "number" ? (
        // Text with a decimal keyboard, not type=number: "1,5" is how the
        // patient writes it, and a number input would refuse the comma.
        <input
          id={id}
          inputMode="decimal"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={p.required}
          aria-describedby={hintId}
        />
      ) : (
        <textarea
          id={id}
          rows={3}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={p.required}
          aria-describedby={hintId}
        />
      )}
    </li>
  );
}
