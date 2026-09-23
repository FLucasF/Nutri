import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowDown,
  ArrowUp,
  CalendarDays,
  Check,
  ChevronLeft,
  ClipboardList,
  Copy,
  FileText,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
} from "lucide-react";

import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr, todayIso } from "../api/dates";
import { useFeedback } from "../components/Feedback";
import { RichTextEditor } from "../components/RichText/LazyEditor";
import {
  emptyDoc,
  isEmptyDoc,
  parseRichDoc,
  type RichDoc,
} from "../components/RichText/document";
import type { AnamnesisField, AnamnesisSummary, Patient } from "../api/types";
import { count } from "../text";

/**
 * A anamnese geral do paciente.
 *
 * É o que o cliente chama de coração do atendimento, e o formato veio da
 * resposta dele: um corpo de texto livre mais pontos rotulados que ele mesmo
 * declara. Os pontos marcados aparecem na listagem, e é isso que permite achar
 * a consulta certa sem abrir uma por uma.
 */
export default function Anamneses() {
  const { id } = useParams();
  const patientId = Number(id);
  const feedback = useFeedback();

  const [patient, setPatient] = useState<Patient | null>(null);
  const [anamneses, setAnamneses] = useState<AnamnesisSummary[]>([]);
  const [fields, setFields] = useState<AnamnesisField[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<number | "nova" | null>(null);
  const [configuring, setConfiguring] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, list, campos] = await Promise.all([
        api.patients.find(patientId),
        api.anamneses.ofPatient(patientId),
        api.anamneses.fields(),
      ]);
      setPatient(p);
      setAnamneses(list);
      setFields(campos);
    } catch (e) {
      setError(explainError(e, "abrir as anamneses"));
    } finally {
      setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function openPdf(anamnesisId: number) {
    try {
      const { url } = await api.anamneses.pdf(anamnesisId);
      window.open(url, "_blank", "noopener");
    } catch (e) {
      setError(explainError(e, "gerar o PDF da anamnese"));
    }
  }

  async function duplicate(anamnesis: AnamnesisSummary) {
    try {
      const copy = await api.anamneses.duplicate(anamnesis.id);
      feedback.confirm(`"${copy.name}" criada com a data de hoje, pronta para editar.`);
      await load();
      setEditing(copy.id);
    } catch (e) {
      setError(explainError(e, "duplicar a anamnese"));
    }
  }

  if (editing !== null) {
    return (
      <Editor
        patientId={patientId}
        patientName={patient?.name ?? ""}
        anamnesisId={editing === "nova" ? null : editing}
        fields={fields}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await load();
        }}
      />
    );
  }

  return (
    <>
      <div className="header-page">
        <div>
          <Link to={`/patients/${patientId}`} className="migalha">
            <ChevronLeft aria-hidden="true" />
            {patient?.name ?? "Paciente"}
          </Link>
          <h1>Anamnese geral</h1>
          <p>{loading ? "Carregando…" : count(anamneses.length, "anamnese", "anamneses")}</p>
        </div>
        <div className="header-page-actions acoes-anamneses">
          <button type="button" className="button secundario" onClick={() => setConfiguring(true)}>
            <SlidersHorizontal aria-hidden="true" />
            Campos de destaque
          </button>
          <button type="button" className="button" onClick={() => setEditing("nova")}>
            <Plus aria-hidden="true" />
            Nova anamnese
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {configuring && (
        <FieldSettings
          fields={fields}
          onClose={() => setConfiguring(false)}
          onSaved={async () => {
            setConfiguring(false);
            await load();
          }}
        />
      )}

      {!loading && anamneses.length === 0 ? (
        <div className="card empty">
          <span className="empty-icon">
            <ClipboardList aria-hidden="true" />
          </span>
          <p className="empty-hint">
            Nenhuma anamnese ainda. A primeira é o registro da consulta inicial.
          </p>
        </div>
      ) : (
        <div className="card-list">
          {anamneses.map((anamnesis) => (
            <Row
              key={anamnesis.id}
              anamnesis={anamnesis}
              onOpen={() => setEditing(anamnesis.id)}
              onPdf={() => void openPdf(anamnesis.id)}
              onDuplicate={() => void duplicate(anamnesis)}
              onRemoved={load}
              onError={setError}
            />
          ))}
        </div>
      )}
    </>
  );
}

// ------------------------------------------------------------------- listagem

function Row({
  anamnesis,
  onOpen,
  onPdf,
  onDuplicate,
  onRemoved,
  onError,
}: {
  anamnesis: AnamnesisSummary;
  onOpen: () => void;
  onPdf: () => void;
  onDuplicate: () => void;
  onRemoved: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [removing, setRemoving] = useState(false);

  return (
    <article className="card card-anamnese">
      <div className="card-anamnese-top">
        <div className="card-anamnese-titulo">
          <span className="eyebrow card-anamnese-data">
            <CalendarDays aria-hidden="true" />
            {formatBr(anamnesis.date)}
          </span>
          <h2 className="card-title">{anamnesis.name}</h2>
        </div>
        <div className="card-anamnese-acoes">
          <button type="button" className="button secundario pequeno" onClick={onPdf}>
            <FileText aria-hidden="true" />
            PDF
          </button>
          <button type="button" className="button secundario pequeno" onClick={onOpen}>
            <Pencil aria-hidden="true" />
            Editar
          </button>
          <button type="button" className="button secundario pequeno" onClick={onDuplicate}>
            <Copy aria-hidden="true" />
            Duplicar
          </button>
          <button type="button" className="button perigo pequeno" onClick={() => setRemoving(true)}>
            <Trash2 aria-hidden="true" />
            Excluir
          </button>
        </div>
      </div>

      {anamnesis.highlights.length > 0 && (
        <dl className="destaques">
          {anamnesis.highlights.map((highlight) => (
            <div className="destaque" key={`${highlight.fieldId}-${highlight.order}`}>
              <dt>{highlight.label}</dt>
              <dd>{highlight.value}</dd>
            </div>
          ))}
        </dl>
      )}

      {removing && (
        <ConfirmRemoval
          name={anamnesis.name}
          onCancel={() => setRemoving(false)}
          onConfirm={async () => {
            try {
              await api.anamneses.remove(anamnesis.id);
              await onRemoved();
            } catch (e) {
              onError(explainError(e, "excluir a anamnese"));
              setRemoving(false);
            }
          }}
        />
      )}
    </article>
  );
}

/**
 * A confirmação por digitação, como o cliente descreveu.
 *
 * Um "tem certeza?" é respondido no reflexo. Digitar DELETAR obriga a ler o
 * que está sendo apagado — e o que está sendo apagado é registro clínico, que
 * não tem lixeira.
 */
function ConfirmRemoval({
  name,
  onCancel,
  onConfirm,
}: {
  name: string;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  const [typed, setTyped] = useState("");
  const allowed = typed.trim().toUpperCase() === "DELETAR";

  return (
    <div className="warning error confirmacao-exclusao">
      <p className="confirmacao-texto">
        Excluir <strong>{name}</strong> apaga o registro da consulta, e isso não tem volta.
        Digite <strong>DELETAR</strong> para confirmar.
      </p>
      <div className="confirmacao-acoes">
        <input
          type="text"
          className="confirmacao-input"
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
          aria-label="Digite DELETAR para confirmar"
          placeholder="DELETAR"
          autoComplete="off"
        />
        <button
          type="button"
          className="button perigo"
          disabled={!allowed}
          onClick={() => void onConfirm()}
        >
          <Trash2 aria-hidden="true" />
          Excluir
        </button>
        <button type="button" className="button secundario" onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

// --------------------------------------------------------------------- editor

function Editor({
  patientId,
  patientName,
  anamnesisId,
  fields,
  onClose,
  onSaved,
}: {
  patientId: number;
  patientName: string;
  anamnesisId: number | null;
  fields: AnamnesisField[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const feedback = useFeedback();
  const [name, setName] = useState("");
  const [date, setDate] = useState(todayIso());
  const [body, setBody] = useState<RichDoc>(emptyDoc());
  const [values, setValues] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(anamnesisId !== null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState(anamnesisId === null);

  useEffect(() => {
    if (anamnesisId === null) {
      setName(`Consulta de ${formatBr(todayIso())}`);
      return;
    }
    let live = true;
    void (async () => {
      try {
        const anamnesis = await api.anamneses.detail(anamnesisId);
        if (!live) return;
        setName(anamnesis.name);
        setDate(anamnesis.date);
        setBody(parseRichDoc(anamnesis.body) ?? emptyDoc());
        const filled: Record<number, string> = {};
        for (const value of anamnesis.values) {
          if (value.fieldId !== null && value.value) filled[value.fieldId] = value.value;
        }
        setValues(filled);
        setReady(true);
      } catch (e) {
        if (live) setError(explainError(e, "abrir a anamnese"));
      } finally {
        if (live) setLoading(false);
      }
    })();
    return () => {
      live = false;
    };
  }, [anamnesisId]);

  async function save() {
    if (!name.trim()) {
      setError("A anamnese precisa de um nome para você achá-la na listagem.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload = {
        patientId,
        name: name.trim(),
        date,
        body: isEmptyDoc(body) ? undefined : JSON.stringify(body),
        values: fields
          .map((field) => ({ fieldId: field.id, value: (values[field.id] ?? "").trim() }))
          .filter((filled) => filled.value !== ""),
      };
      if (anamnesisId === null) await api.anamneses.create(payload);
      else await api.anamneses.update(anamnesisId, payload);
      feedback.confirm("Anamnese salva.");
      await onSaved();
    } catch (e) {
      setError(explainError(e, "salvar a anamnese"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <div className="header-page">
        <div>
          <button type="button" className="link-voltar migalha" onClick={onClose}>
            <ChevronLeft aria-hidden="true" />
            Anamneses de {patientName}
          </button>
          <h1>{anamnesisId === null ? "Nova anamnese" : "Editar anamnese"}</h1>
        </div>
        <div className="header-page-actions acoes-par">
          <button type="button" className="button secundario" onClick={onClose} disabled={saving}>
            Cancelar
          </button>
          <button
            type="button"
            className="button"
            onClick={() => void save()}
            disabled={saving || loading}
          >
            <Check aria-hidden="true" />
            {saving ? "Salvando…" : "Salvar"}
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
        <div className="editor-anamnese">
          <section className="card stack">
            <div className="grid two">
              <div className="field">
                <label htmlFor="an-nome">Nome</label>
                <input
                  id="an-nome"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={150}
                />
              </div>
              <div className="field">
                <label htmlFor="an-data">Data</label>
                <input
                  id="an-data"
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                />
              </div>
            </div>

            {fields.length > 0 && (
              <div className="grid two campos-anamnese">
                {fields.map((field) => (
                  <div className="field" key={field.id}>
                    <label htmlFor={`an-campo-${field.id}`}>{field.label}</label>
                    <input
                      id={`an-campo-${field.id}`}
                      type="text"
                      value={values[field.id] ?? ""}
                      onChange={(e) =>
                        setValues((old) => ({ ...old, [field.id]: e.target.value }))
                      }
                      maxLength={500}
                    />
                    {field.showInListing && (
                      <span className="field-hint">Aparece na listagem.</span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="registro-anamnese">
            <h2 className="card-title">Registro da consulta</h2>
            {ready && (
              <RichTextEditor
                value={body}
                onChange={setBody}
                label="Registro da consulta"
                minHeight="22rem"
              />
            )}
          </section>
        </div>
      )}
    </>
  );
}

// ---------------------------------------------------------- campos do consultório

/**
 * Os campos que o consultório quer em toda anamnese.
 *
 * A lista inteira é salva de uma vez porque a ordem é propriedade do conjunto.
 * Tirar um campo daqui não apaga o que já foi escrito sob ele: o servidor
 * desativa, e as anamneses antigas continuam dizendo o que diziam.
 */
function FieldSettings({
  fields,
  onClose,
  onSaved,
}: {
  fields: AnamnesisField[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [draft, setDraft] = useState(
    fields.map((field) => ({ label: field.label, showInListing: field.showInListing })),
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function move(index: number, by: number) {
    const target = index + by;
    const here = draft[index];
    const there = draft[target];
    if (!here || !there) return;
    const next = [...draft];
    next[index] = there;
    next[target] = here;
    setDraft(next);
  }

  async function save() {
    const clean = draft
      .map((field) => ({ ...field, label: field.label.trim() }))
      .filter((field) => field.label);
    setSaving(true);
    setError(null);
    try {
      await api.anamneses.saveFields(clean);
      await onSaved();
    } catch (e) {
      setError(explainError(e, "salvar os campos"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card campos-destaque">
      <div className="card-head">
        <div>
          <h2 className="card-title">Campos de destaque</h2>
          <p className="card-sub">
            Os pontos que você preenche em toda anamnese. Os marcados aparecem na listagem,
            para você reconhecer a consulta sem abrir.
          </p>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {draft.length > 0 && (
        <div className="painel campos-destaque-lista">
          {draft.map((field, index) => (
            <div className="campo-destaque" key={index}>
              <input
                type="text"
                value={field.label}
                placeholder="Propósito da consulta"
                aria-label={`Rótulo do campo ${index + 1}`}
                onChange={(e) =>
                  setDraft((old) =>
                    old.map((f, i) => (i === index ? { ...f, label: e.target.value } : f)),
                  )
                }
                maxLength={120}
              />
              <label className="campo-destaque-listagem">
                <input
                  type="checkbox"
                  checked={field.showInListing}
                  onChange={(e) =>
                    setDraft((old) =>
                      old.map((f, i) =>
                        i === index ? { ...f, showInListing: e.target.checked } : f,
                      ),
                    )
                  }
                />
                <span>Na listagem</span>
              </label>
              <div className="campo-destaque-ordem">
                <button
                  type="button"
                  className="button secundario pequeno icon"
                  aria-label="Subir"
                  title="Subir"
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  <ArrowUp aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className="button secundario pequeno icon"
                  aria-label="Descer"
                  title="Descer"
                  disabled={index === draft.length - 1}
                  onClick={() => move(index, 1)}
                >
                  <ArrowDown aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className="button perigo pequeno"
                  onClick={() => setDraft((old) => old.filter((_, i) => i !== index))}
                >
                  Remover
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="campos-destaque-rodape">
        <button
          type="button"
          className="button secundario"
          onClick={() => setDraft((old) => [...old, { label: "", showInListing: true }])}
        >
          <Plus aria-hidden="true" />
          Adicionar campo
        </button>
        <div className="campos-destaque-fim acoes-par">
          <button type="button" className="button secundario" onClick={onClose} disabled={saving}>
            Fechar
          </button>
          <button type="button" className="button" onClick={() => void save()} disabled={saving}>
            <Check aria-hidden="true" />
            {saving ? "Salvando…" : "Salvar campos"}
          </button>
        </div>
      </div>
    </section>
  );
}
