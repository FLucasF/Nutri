import { useCallback, useEffect, useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronUp,
  Copy,
  Heading,
  Info,
  ListChecks,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { ErrorApi, api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../components/Feedback";
import type { QuestionType, Questionnaire, QuestionnaireQuestion, QuestionnaireRequest } from "../api/types";
import { count } from "../text";

/**
 * The library of questionnaires, and the builder that makes them.
 *
 * The client chose the questionnaire builder as the way to make the anamnesis
 * "programável": a form with short answers, paragraphs, dates, checkboxes and
 * sections, answered by the patient before the visit or by the professional
 * during it. What is built here is what the anamnesis page offers as a model.
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
  const [editing, setEditing] = useState<Questionnaire | "new" | null>(null);

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

  if (editing !== null) {
    return (
      <Builder
        questionnaire={editing === "new" ? null : editing}
        onClose={() => setEditing(null)}
        onSaved={async (saved) => {
          setEditing(null);
          await load();
          setOpen(saved.id);
        }}
      />
    );
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
        <div className="header-page-actions">
          <button type="button" className="button" onClick={() => setEditing("new")}>
            <Plus aria-hidden="true" />
            Novo questionário
          </button>
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
            const answerable = q.questions.filter((p) => p.type !== "SECTION").length;
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
                        {count(answerable, "pergunta", "perguntas")}
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
                    {q.questions.map((p) =>
                      p.type === "SECTION" ? (
                        <li key={p.id} className="question-section-row">
                          <span className="question-statement question-section-title">{p.statement}</span>
                        </li>
                      ) : (
                        <li key={p.id}>
                          <span className="question-statement">
                            {p.statement}
                            <span className="minusculo"> · {p.typeDescription.toLowerCase()}</span>
                            {p.required && <span className="minusculo"> (obrigatória)</span>}
                            {p.highlight && <span className="minusculo"> (destaque)</span>}
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
                      ),
                    )}
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
                    {q.editable && (
                      <button
                        type="button"
                        className="button secundario pequeno"
                        onClick={() => setEditing(q)}
                      >
                        <Pencil aria-hidden="true" />
                        Editar
                      </button>
                    )}
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

/* ---------------------------------------------------------------- builder */

const TYPE_LABELS: { value: QuestionType; label: string }[] = [
  { value: "TEXT", label: "Resposta curta" },
  { value: "PARAGRAPH", label: "Parágrafo" },
  { value: "NUMBER", label: "Número" },
  { value: "DATE", label: "Data" },
  { value: "CHOICE_SINGLE", label: "Escolha única" },
  { value: "MULTIPLE", label: "Múltipla escolha (caixas)" },
  { value: "SECTION", label: "Seção (título)" },
];

type Draft = {
  key: number;
  statement: string;
  type: QuestionType;
  required: boolean;
  highlight: boolean;
  /** One alternative per line; "Rótulo=2" carries the score. */
  options: string;
  ajuda: string;
};

let nextKey = 1;

function draftOf(q?: QuestionnaireQuestion): Draft {
  return {
    key: nextKey++,
    statement: q?.statement ?? "",
    type: q?.type ?? "TEXT",
    required: q?.required ?? false,
    highlight: q?.highlight ?? false,
    options: (q?.options ?? [])
      .map((o) => (o.points !== undefined && o.points !== null ? `${o.label}=${o.points}` : o.label))
      .join("\n"),
    ajuda: q?.ajuda ?? "",
  };
}

function hasOptions(type: QuestionType) {
  return type === "CHOICE_SINGLE" || type === "MULTIPLE";
}

/**
 * The builder: name, description, the questions in order, and for each one
 * its type, whether it is required and whether its answer is highlighted in
 * the anamnesis listing.
 *
 * The alternatives are typed one per line, because that is how a person
 * lists things; the server keeps them separated by a bar.
 */
function Builder({
  questionnaire,
  onClose,
  onSaved,
}: {
  questionnaire: Questionnaire | null;
  onClose: () => void;
  onSaved: (saved: Questionnaire) => Promise<void>;
}) {
  const feedback = useFeedback();
  const [name, setName] = useState(questionnaire?.name ?? "");
  const [description, setDescription] = useState(questionnaire?.description ?? "");
  const [scorable, setScorable] = useState(questionnaire?.scorable ?? false);
  const [cutoffRange, setCutoffRange] = useState(questionnaire?.cutoffRange ?? "");
  const [questions, setQuestions] = useState<Draft[]>(
    questionnaire ? questionnaire.questions.map((q) => draftOf(q)) : [draftOf()],
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function change(index: number, patch: Partial<Draft>) {
    setQuestions((old) => old.map((q, i) => (i === index ? { ...q, ...patch } : q)));
  }

  function move(index: number, by: number) {
    const target = index + by;
    setQuestions((old) => {
      const here = old[index];
      const there = old[target];
      if (!here || !there) return old;
      const next = [...old];
      next[index] = there;
      next[target] = here;
      return next;
    });
  }

  function add(type: QuestionType) {
    setQuestions((old) => [...old, { ...draftOf(), type }]);
  }

  async function save() {
    const cleaned = questions.map((q) => ({ ...q, statement: q.statement.trim() }));
    if (!name.trim()) {
      setError("O questionário precisa de um nome.");
      return;
    }
    if (cleaned.length === 0 || cleaned.every((q) => q.type === "SECTION")) {
      setError("Um questionário precisa de ao menos uma pergunta que se responda.");
      return;
    }
    const blank = cleaned.findIndex((q) => !q.statement);
    if (blank >= 0) {
      setError(`A pergunta ${blank + 1} está sem enunciado.`);
      return;
    }
    const noOptions = cleaned.findIndex((q) => hasOptions(q.type) && !q.options.trim());
    if (noOptions >= 0) {
      setError(`A pergunta ${noOptions + 1} é de escolha e precisa das alternativas, uma por linha.`);
      return;
    }
    const data: QuestionnaireRequest = {
      name: name.trim(),
      description: description.trim() || undefined,
      scorable,
      cutoffRange: scorable && cutoffRange.trim() ? cutoffRange.trim() : undefined,
      questions: cleaned.map((q) => ({
        statement: q.statement,
        type: q.type,
        required: q.type !== "SECTION" && q.required,
        highlight: q.type !== "SECTION" && q.highlight,
        options: hasOptions(q.type)
          ? q.options
              .split("\n")
              .map((line) => line.trim())
              .filter(Boolean)
              .join("|")
          : undefined,
        ajuda: q.ajuda.trim() || undefined,
      })),
    };
    setSaving(true);
    setError(null);
    try {
      const saved = questionnaire
        ? await api.questionnaires.update(questionnaire.id, data)
        : await api.questionnaires.create(data);
      feedback.confirm(
        questionnaire
          ? `"${saved.name}" atualizado. As respostas já recebidas guardam a versão de antes.`
          : `"${saved.name}" pronto. Já aparece como modelo de anamnese e para enviar ao paciente.`,
      );
      await onSaved(saved);
    } catch (e) {
      setError(explainError(e, "salvar o questionário"));
      setSaving(false);
    }
  }

  return (
    <>
      <div className="header-page">
        <div>
          <button type="button" className="link-voltar migalha" onClick={onClose}>
            <ChevronLeft aria-hidden="true" />
            Questionários
          </button>
          <h1>{questionnaire ? "Editar questionário" : "Novo questionário"}</h1>
          <p>Resposta curta, parágrafo, número, data, escolha ou caixas — e seções para agrupar.</p>
        </div>
        <div className="header-page-actions acoes-par">
          <button type="button" className="button secundario" onClick={onClose} disabled={saving}>
            Cancelar
          </button>
          <button type="button" className="button" onClick={() => void save()} disabled={saving}>
            <Check aria-hidden="true" />
            {saving ? "Salvando…" : "Salvar questionário"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      <section className="card mb-3 stack">
        <div className="grid two">
          <div className="field">
            <label htmlFor="qb-nome">Nome do questionário</label>
            <input
              id="qb-nome"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={150}
              placeholder="Anamnese padrão"
            />
          </div>
          <div className="field">
            <label htmlFor="qb-desc">Descrição</label>
            <input
              id="qb-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={1000}
              placeholder="O que o paciente lê antes de responder"
            />
          </div>
        </div>
        <div className="qb-flags">
          <label className="check-inline">
            <input type="checkbox" checked={scorable} onChange={(e) => setScorable(e.target.checked)} />
            <span>Pontuável: as alternativas valem pontos e a soma classifica</span>
          </label>
        </div>
        {scorable && (
          <div className="field">
            <label htmlFor="qb-faixas">Faixas de corte</label>
            <input
              id="qb-faixas"
              value={cutoffRange}
              onChange={(e) => setCutoffRange(e.target.value)}
              placeholder="0-5=Baixo|6-10=Moderado|11-99=Alto"
              maxLength={500}
            />
            <span className="field-hint">
              Escreva as alternativas como Rótulo=pontos; a soma cai numa destas faixas.
            </span>
          </div>
        )}
      </section>

      <div className="qb-list">
        {questions.map((q, index) => {
          const section = q.type === "SECTION";
          return (
            <div className={`qb-item${section ? " section" : ""}`} key={q.key}>
              <span className="qb-index readout">{index + 1}</span>
              <div className="qb-body">
                <div className="qb-row">
                  <div className="field">
                    <label htmlFor={`qb-p-${q.key}`}>{section ? `Título da seção ${index + 1}` : `Pergunta ${index + 1}`}</label>
                    <input
                      id={`qb-p-${q.key}`}
                      value={q.statement}
                      onChange={(e) => change(index, { statement: e.target.value })}
                      maxLength={500}
                      placeholder={section ? "Hábitos alimentares" : "Qual é a sua queixa principal?"}
                    />
                  </div>
                  <div className="field">
                    <label htmlFor={`qb-t-${q.key}`}>{`Tipo da pergunta ${index + 1}`}</label>
                    <select
                      id={`qb-t-${q.key}`}
                      value={q.type}
                      onChange={(e) => change(index, { type: e.target.value as QuestionType })}
                    >
                      {TYPE_LABELS.map((t) => (
                        <option key={t.value} value={t.value}>
                          {t.label}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {hasOptions(q.type) && (
                  <div className="field qb-options">
                    <label htmlFor={`qb-o-${q.key}`}>{`Alternativas da pergunta ${index + 1}`}</label>
                    <textarea
                      id={`qb-o-${q.key}`}
                      rows={3}
                      value={q.options}
                      onChange={(e) => change(index, { options: e.target.value })}
                      placeholder={"Uma por linha\nNunca\nÀs vezes\nSempre"}
                    />
                    <div className="row qb-presets">
                      <span className="field-hint">
                        Uma por linha{scorable ? "; Rótulo=pontos para pontuar" : ""}.
                      </span>
                      <button
                        type="button"
                        className="button ghost pequeno"
                        onClick={() => change(index, { options: scorable ? "Sim=1\nNão=0" : "Sim\nNão" })}
                      >
                        Sim / Não
                      </button>
                    </div>
                  </div>
                )}

                <div className="field">
                  <label htmlFor={`qb-a-${q.key}`}>{section ? `Descrição da seção ${index + 1}` : `Ajuda da pergunta ${index + 1}`}</label>
                  <input
                    id={`qb-a-${q.key}`}
                    value={q.ajuda}
                    onChange={(e) => change(index, { ajuda: e.target.value })}
                    maxLength={500}
                    placeholder={section ? "O que este bloco cobre" : "Uma dica para quem responde"}
                  />
                </div>

                <div className="qb-item-foot">
                  {!section && (
                    <div className="qb-flags">
                      <label className="check-inline">
                        <input
                          type="checkbox"
                          checked={q.required}
                          onChange={(e) => change(index, { required: e.target.checked })}
                        />
                        <span>Obrigatória</span>
                      </label>
                      <label className="check-inline">
                        <input
                          type="checkbox"
                          checked={q.highlight}
                          onChange={(e) => change(index, { highlight: e.target.checked })}
                        />
                        <span>Destacar na listagem de anamneses</span>
                      </label>
                    </div>
                  )}
                  <div className="qb-actions">
                    <button
                      type="button"
                      className="button secundario pequeno icon"
                      aria-label={`Subir a pergunta ${index + 1}`}
                      title="Subir"
                      disabled={index === 0}
                      onClick={() => move(index, -1)}
                    >
                      <ArrowUp aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      className="button secundario pequeno icon"
                      aria-label={`Descer a pergunta ${index + 1}`}
                      title="Descer"
                      disabled={index === questions.length - 1}
                      onClick={() => move(index, 1)}
                    >
                      <ArrowDown aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      className="button perigo pequeno"
                      aria-label={`Remover a pergunta ${index + 1}`}
                      onClick={() => setQuestions((old) => old.filter((_, i) => i !== index))}
                    >
                      <Trash2 aria-hidden="true" />
                      Remover
                    </button>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="qb-foot">
        <button type="button" className="button secundario" onClick={() => add("TEXT")}>
          <Plus aria-hidden="true" />
          Adicionar pergunta
        </button>
        <button type="button" className="button secundario" onClick={() => add("SECTION")}>
          <Heading aria-hidden="true" />
          Adicionar seção
        </button>
      </div>
    </>
  );
}
