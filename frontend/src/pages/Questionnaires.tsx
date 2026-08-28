import { useCallback, useEffect, useState } from "react";
import { ErrorApi, api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../components/Feedback";
import type { Questionnaire } from "../api/types";
import { count } from "../text";

/**
 * The library of questionnaires.
 *
 * The system ships a generic pre-appointment template, of its own authorship.
 * Published instruments — metabolic screening, FINDRISC, sleep scales — have
 * licenses of their own, and it is up to each practice to register the ones it
 * has the right to use. It is the same reason TBCA is not in the food catalog.
 */
export default function Questionnaires() {
  const feedback = useFeedback();
  const [questionnaires, setQuestionnaires] = useState<Questionnaire[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setQuestionnaires(await api.questionnaires.list());
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : "Falha ao carregar os questionários.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function duplicate(q: Questionnaire) {
    try {
      const copies = await api.questionnaires.duplicate(q.id);
      feedback.confirm("Cópia criada na sua biblioteca, pronta para editar.");
      await load();
      setOpen(copies.id);
    } catch (e) {
      setError(explainError(e, "duplicate"));
    }
  }

  async function remove(q: Questionnaire) {
    if (!confirm(`Remover "${q.name}" da biblioteca?`)) return;
    try {
      await api.questionnaires.remove(q.id);
      feedback.confirm(`"${q.name}" saiu da biblioteca. As respostas já recebidas continuam.`);
      await load();
    } catch (e) {
      setError(explainError(e, "remover o questionário"));
    }
  }

  const meus = questionnaires.filter((q) => !q.systemTemplate);
  const templates = questionnaires.filter((q) => q.systemTemplate);

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Questionários</h1>
          <p>
            {count(meus.length, "formulário seu", "formulários seus")} ·{" "}
            {count(templates.length, "modelo do sistema", "modelos do sistema")}
          </p>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      {loading ? (
        <p className="loading">Loading…</p>
      ) : (
        <div className="list-handouts">
          {[...meus, ...templates].map((q) => (
            <article
              className={`card handout ${q.systemTemplate ? "template" : ""}`}
              key={q.id}
            >
              <header>
                <button
                  type="button"
                  className="title-handout"
                  onClick={() => setOpen(open === q.id ? null : q.id)}
                >
                  <h2>{q.name}</h2>
                </button>
                <div className="row" style={{ gap: "0.3rem" }}>
                  {q.scorable && <span className="tag ambar">pontuável</span>}
                  {q.systemTemplate && <span className="tag">do sistema</span>}
                </div>
              </header>

              <p className="body-handout open">
                {q.description ?? `${count(q.questions.length, "question", "questions")}.`}
              </p>

              {open === q.id && (
                <ol style={{ margin: "0.7rem 0 0", paddingLeft: "1.2rem" }}>
                  {q.questions.map((p) => (
                    <li key={p.id} style={{ marginBottom: "0.4rem", fontSize: "0.9rem" }}>
                      {p.statement}
                      {p.required && <span className="minusculo"> (obrigatória)</span>}
                      {p.options.length > 0 && (
                        <div className="minusculo">
                          {p.options
                            .map((o) => (o.points !== undefined ? `${o.label} = ${o.points}` : o.label))
                            .join(" · ")}
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}

              <div className="row" style={{ marginTop: "0.6rem" }}>
                <button
                  type="button"
                  className="button secundario pequeno"
                  onClick={() => setOpen(open === q.id ? null : q.id)}
                >
                  {open === q.id ? "Recolher" : "Ver perguntas"}
                </button>
                <button
                  type="button"
                  className="button secundario pequeno"
                  onClick={() => duplicate(q)}
                >
                  Duplicate
                </button>
                {q.editable && (
                  <button
                    type="button"
                    className="button perigo pequeno"
                    onClick={() => remove(q)}
                  >
                    Remove
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <div className="card" style={{ marginTop: "0.9rem" }}>
        <h2>Sobre instrumentos publicados</h2>
        <p className="discreto" style={{ marginTop: "0.4rem", marginBottom: 0 }}>
          O sistema traz um modelo genérico de pré-consulta, de autoria própria. Instrumentos
          publicados — rastreamento metabólico, FINDRISC, escalas de sono — têm licença própria,
          e cabe a você cadastrar os que tem direito de usar. É a mesma razão pela qual a TBCA
          não está no acervo de alimentos.
        </p>
      </div>
    </>
  );
}
