import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

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
          <Link to={`/patients/${patientId}`} className="minusculo">
            ← {patient?.name ?? "Paciente"}
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>Anamnese geral</h1>
          <p>
            {loading ? "Carregando…" : count(anamneses.length, "anamnese", "anamneses")}
          </p>
        </div>
        <div className="row">
          <button className="button secundario" onClick={() => setConfiguring(true)}>
            Campos de destaque
          </button>
          <button className="button" onClick={() => setEditing("nova")}>
            Nova anamnese
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
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
        <p className="empty">
          Nenhuma anamnese ainda. A primeira é o registro da consulta inicial.
        </p>
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
    <article className="card-anamnese">
      <div className="card-anamnese-top">
        <div>
          <h2>{anamnesis.name}</h2>
          <p className="minusculo">
            {formatBr(anamnesis.date)}
          </p>
        </div>
        <div className="row">
          <button className="button secundario pequeno" onClick={onPdf}>
            PDF
          </button>
          <button className="button secundario pequeno" onClick={onOpen}>
            Editar
          </button>
          <button className="button secundario pequeno" onClick={onDuplicate}>
            Duplicar
          </button>
          <button className="button perigo pequeno" onClick={() => setRemoving(true)}>
            Excluir
          </button>
        </div>
      </div>

      {anamnesis.highlights.length > 0 && (
        <dl className="destaques">
          {anamnesis.highlights.map((highlight) => (
            <div key={`${highlight.fieldId}-${highlight.order}`}>
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
    <div className="warning error" style={{ marginTop: "0.8rem" }}>
      <p style={{ marginTop: 0 }}>
        Excluir <strong>{name}</strong> apaga o registro da consulta, e isso não tem volta.
        Digite <strong>DELETAR</strong> para confirmar.
      </p>
      <div className="row">
        <input
          type="text"
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
          aria-label="Digite DELETAR para confirmar"
          placeholder="DELETAR"
          style={{ maxWidth: "12rem" }}
        />
        <button className="button perigo" disabled={!allowed} onClick={() => void onConfirm()}>
          Excluir
        </button>
        <button className="button secundario" onClick={onCancel}>
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
          <button className="link-voltar minusculo" onClick={onClose}>
            ← Anamneses de {patientName}
          </button>
          <h1 style={{ marginTop: "0.2rem" }}>
            {anamnesisId === null ? "Nova anamnese" : "Editar anamnese"}
          </h1>
        </div>
        <div className="row">
          <button className="button secundario" onClick={onClose} disabled={saving}>
            Cancelar
          </button>
          <button className="button" onClick={() => void save()} disabled={saving || loading}>
            {saving ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      {loading ? (
        <p className="empty">Carregando…</p>
      ) : (
        <>
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
            <div className="grid two" style={{ marginTop: "0.4rem" }}>
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
                    <span className="minusculo">Aparece na listagem.</span>
                  )}
                </div>
              ))}
            </div>
          )}

          <h2 style={{ margin: "1.4rem 0 0.5rem" }}>Registro da consulta</h2>
          {ready && (
            <RichTextEditor
              value={body}
              onChange={setBody}
              label="Registro da consulta"
              minHeight="22rem"
            />
          )}
        </>
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
    <section className="painel" style={{ marginBottom: "1.2rem" }}>
      <h2 style={{ marginTop: 0 }}>Campos de destaque</h2>
      <p className="discreto">
        Os pontos que você preenche em toda anamnese. Os marcados aparecem na listagem, para
        você reconhecer a consulta sem abrir.
      </p>

      {error && (
        <div className="warning error" role="alert">
          {error}
        </div>
      )}

      {draft.map((field, index) => (
        <div className="row campo-destaque" key={index}>
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
          <label className="row" style={{ gap: "0.35rem", whiteSpace: "nowrap" }}>
            <input
              type="checkbox"
              checked={field.showInListing}
              style={{ width: "auto" }}
              onChange={(e) =>
                setDraft((old) =>
                  old.map((f, i) =>
                    i === index ? { ...f, showInListing: e.target.checked } : f,
                  ),
                )
              }
            />
            <span className="minusculo">Na listagem</span>
          </label>
          <button
            className="button secundario pequeno"
            aria-label="Subir"
            disabled={index === 0}
            onClick={() => move(index, -1)}
          >
            ↑
          </button>
          <button
            className="button secundario pequeno"
            aria-label="Descer"
            disabled={index === draft.length - 1}
            onClick={() => move(index, 1)}
          >
            ↓
          </button>
          <button
            className="button perigo pequeno"
            onClick={() => setDraft((old) => old.filter((_, i) => i !== index))}
          >
            Remover
          </button>
        </div>
      ))}

      <div className="row" style={{ marginTop: "0.8rem" }}>
        <button
          className="button secundario"
          onClick={() => setDraft((old) => [...old, { label: "", showInListing: true }])}
        >
          Adicionar campo
        </button>
        <button className="button" onClick={() => void save()} disabled={saving}>
          {saving ? "Salvando…" : "Salvar campos"}
        </button>
        <button className="button secundario" onClick={onClose} disabled={saving}>
          Fechar
        </button>
      </div>
    </section>
  );
}
