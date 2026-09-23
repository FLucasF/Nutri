import { useCallback, useEffect, useState } from "react";
import { ChevronDown, ChevronUp, Copy, Info, ListChecks, Trash2 } from "lucide-react";
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
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : (
        <div className="list-handouts">
          {[...meus, ...templates].map((q) => {
            const aberto = open === q.id;
            return (
              <article
                className={`card handout ${q.systemTemplate ? "template" : ""}`}
                key={q.id}
              >
                <header className="handout-head">
                  <button
                    type="button"
                    className="title-handout"
                    aria-expanded={aberto}
                    onClick={() => setOpen(aberto ? null : q.id)}
                  >
                    <span className="handout-icon">
                      <ListChecks aria-hidden="true" />
                    </span>
                    <span className="handout-title">
                      <h2>{q.name}</h2>
                      <span className="handout-meta">
                        {count(q.questions.length, "pergunta", "perguntas")}
                      </span>
                    </span>
                  </button>
                  {(q.scorable || q.systemTemplate) && (
                    <div className="tags">
                      {q.scorable && <span className="tag ambar">pontuável</span>}
                      {q.systemTemplate && <span className="tag">do sistema</span>}
                    </div>
                  )}
                </header>

                {q.description && <p className="handout-desc">{q.description}</p>}

                {aberto && (
                  <ol className="questions">
                    {q.questions.map((p) => (
                      <li key={p.id}>
                        <span className="question-statement">
                          {p.statement}
                          {p.required && <span className="minusculo"> (obrigatória)</span>}
                        </span>
                        {p.options.length > 0 && (
                          <ul className="options">
                            {p.options.map((o, i) => (
                              <li key={i}>
                                {o.points !== undefined ? `${o.label} = ${o.points}` : o.label}
                              </li>
                            ))}
                          </ul>
                        )}
                      </li>
                    ))}
                  </ol>
                )}

                <div className="handout-actions">
                  <button
                    type="button"
                    className="button ghost pequeno"
                    onClick={() => setOpen(aberto ? null : q.id)}
                  >
                    {aberto ? <ChevronUp aria-hidden="true" /> : <ChevronDown aria-hidden="true" />}
                    {aberto ? "Recolher" : "Ver perguntas"}
                  </button>
                  <div className="handout-actions-right">
                    <button
                      type="button"
                      className="button secundario pequeno"
                      onClick={() => duplicate(q)}
                    >
                      <Copy aria-hidden="true" />
                      Duplicar
                    </button>
                    {q.editable && (
                      <button
                        type="button"
                        className="button perigo pequeno"
                        onClick={() => remove(q)}
                      >
                        <Trash2 aria-hidden="true" />
                        Remover
                      </button>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      <aside className="library-note">
        <Info aria-hidden="true" />
        <div>
          <h2>Sobre instrumentos publicados</h2>
          <p>
            O sistema traz um modelo genérico de pré-consulta, de autoria própria. Instrumentos
            publicados — rastreamento metabólico, FINDRISC, escalas de sono — têm licença própria,
            e cabe a você cadastrar os que tem direito de usar. É a mesma razão pela qual a TBCA
            não está no acervo de alimentos.
          </p>
        </div>
      </aside>
    </>
  );
}
