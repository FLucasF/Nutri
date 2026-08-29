import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { ErrorApi, api } from "../api/client";
import { explainError } from "../api/errors";
import type { PublicForm } from "../api/types";

/**
 * The questionnaire as the patient answers it.
 *
 * Without a login: whoever has the link, answers — the same mechanism by which
 * they read the plan. The page is deliberately simpler than the rest of the
 * system because whoever opens it is not a user of the product: they open it
 * once, answer and leave.
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
        <div className="plan-patient" style={{ paddingTop: "3rem" }}>
          <div className="card" style={{ textAlign: "center", padding: "2.5rem 1.5rem" }}>
            <h1 style={{ fontSize: "1.5rem" }}>Formulário não encontrado</h1>
            <p className="discreto" style={{ marginTop: "0.6rem" }}>{error}</p>
          </div>
        </div>
      </div>
    );
  }

  if (!form) return null;

  return (
    <div className="page-patient">
      <div className="plan-patient">
        <header className="cover-plan">
          {form.practice && <span className="eyebrow">{form.practice}</span>}
          <h1>{form.title}</h1>
          {form.description && <p>{form.description}</p>}
        </header>

        {ready ? (
          <div className="card" style={{ textAlign: "center", padding: "2.5rem 1.5rem" }}>
            <h2>Respostas enviadas</h2>
            <p className="discreto" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
              Obrigado. Seu nutricionista já recebeu. Pode fechar esta página.
            </p>
          </div>
        ) : (
          <form className="card" onSubmit={send}>
            {error && (
              <div className="warning error" role="alert" style={{ marginBottom: "1rem" }}>
                {error}
              </div>
            )}

            {form.questions.map((p) => (
              <div className="field" key={p.id} style={{ marginBottom: "1.2rem" }}>
                <label htmlFor={`p-${p.id}`}>
                  {p.statement}
                  {p.required && <span aria-hidden> *</span>}
                </label>

                {p.type === "CHOICE_SINGLE" || p.type === "MULTIPLE" ? (
                  <select
                    id={`p-${p.id}`}
                    value={answers[p.id] ?? ""}
                    onChange={(e) =>
                      setAnswers((r) => ({ ...r, [p.id]: e.target.value }))
                    }
                    required={p.required}
                  >
                    <option value="">Selecione…</option>
                    {p.options.map((o) => (
                      <option key={o.label} value={o.label}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                ) : p.type === "NUMBER" ? (
                  <input
                    id={`p-${p.id}`}
                    inputMode="decimal"
                    value={answers[p.id] ?? ""}
                    onChange={(e) => setAnswers((r) => ({ ...r, [p.id]: e.target.value }))}
                    required={p.required}
                  />
                ) : (
                  <textarea
                    id={`p-${p.id}`}
                    rows={3}
                    value={answers[p.id] ?? ""}
                    onChange={(e) => setAnswers((r) => ({ ...r, [p.id]: e.target.value }))}
                    required={p.required}
                  />
                )}

                {p.ajuda && <span className="minusculo">{p.ajuda}</span>}
              </div>
            ))}

            <button className="button" type="submit" disabled={sending} style={{ width: "100%" }}>
              {sending ? "Enviando…" : "Enviar respostas"}
            </button>
            <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
              As respostas vão direto para o seu nutricionista. O formulário aceita um envio só.
            </p>
          </form>
        )}
      </div>
    </div>
  );
}
