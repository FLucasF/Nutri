import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../componentes/Feedback";
import type { Handout } from "../api/types";
import { count } from "../text";

/**
 * The practice's library of handouts.
 *
 * It exists so that the nutritionist does not rewrite "how to build your plate"
 * for every patient. The system ships a few templates as a starting point; they
 * are not editable, for the same reason the food reference tables are not.
 * Whoever wants to change one, duplicates it.
 */
export default function Handouts() {
  const feedback = useFeedback();
  const [handouts, setHandouts] = useState<Handout[]>([]);
  const [term, setTerm] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [inEdit, setAtEdit] = useState<Handout | "nova" | null>(null);

  const find = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await api.handouts.list({ term: term.trim() || undefined, size: 50 });
      setHandouts(page.content);
    } catch (e) {
      setError(explainError(e, "abrir as orientações"));
    } finally {
      setLoading(false);
    }
  }, [term]);

  useEffect(() => {
    const clock = setTimeout(find, term ? 300 : 0);
    return () => clearTimeout(clock);
  }, [find, term]);

  const minhas = handouts.filter((o) => !o.systemTemplate);
  const templates = handouts.filter((o) => o.systemTemplate);

  async function duplicate(o: Handout) {
    try {
      const copies = await api.handouts.duplicate(o.id);
      feedback.confirm("Cópia criada na sua biblioteca, pronta para editar.");
      await find();
      setAtEdit(copies);
    } catch (e) {
      setError(explainError(e, "duplicate"));
    }
  }

  async function remove(o: Handout) {
    if (!confirm(`Remover "${o.title}" da biblioteca?`)) return;
    try {
      await api.handouts.remove(o.id);
      feedback.confirm(`"${o.title}" saiu da biblioteca. Os planos já entregues seguem com o texto.`);
      await find();
    } catch (e) {
      setError(explainError(e, "remove"));
    }
  }

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Orientações</h1>
          <p>
            {count(minhas.length, "texto seu", "textos seus")} ·{" "}
            {count(templates.length, "modelo do sistema", "modelos do sistema")}
          </p>
        </div>
        <button className="button" onClick={() => setAtEdit("nova")}>
          Nova orientação
        </button>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      {inEdit && (
        <Editor
          handout={inEdit === "nova" ? null : inEdit}
          onClose={() => setAtEdit(null)}
          onSave={async () => {
            setAtEdit(null);
            await find();
          }}
        />
      )}

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="field">
          <label htmlFor="busca-orientacao">Find</label>
          <input
            id="busca-orientacao"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            placeholder="Hidratação, rótulo, compras…"
          />
        </div>
      </div>

      {loading ? (
        <p className="loading">Loading…</p>
      ) : handouts.length === 0 ? (
        <div className="card empty">
          Nenhuma orientação com esse termo. Use <strong>Nova orientação</strong> para escrever a
          primeira.
        </div>
      ) : (
        <div className="list-handouts">
          {minhas.map((o) => (
            <Card
              key={o.id}
              handout={o}
              onEdit={() => setAtEdit(o)}
              onDuplicate={() => duplicate(o)}
              onRemove={() => remove(o)}
            />
          ))}
          {templates.length > 0 && (
            <p className="minusculo" style={{ margin: "0.6rem 0 0" }}>
              Modelos do sistema — duplique para criar a sua versão.
            </p>
          )}
          {templates.map((o) => (
            <Card key={o.id} handout={o} onDuplicate={() => duplicate(o)} />
          ))}
        </div>
      )}
    </>
  );
}

function Card({
  handout,
  onEdit,
  onDuplicate,
  onRemove,
}: {
  handout: Handout;
  onEdit?: () => void;
  onDuplicate: () => void;
  onRemove?: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <article className={`card handout ${handout.systemTemplate ? "template" : ""}`}>
      <header>
        <button type="button" className="title-handout" onClick={() => setOpen(!open)}>
          <h2>{handout.title}</h2>
        </button>
        {handout.systemTemplate && <span className="tag">do sistema</span>}
      </header>

      <p className={`body-handout ${open ? "open" : ""}`}>{handout.body}</p>

      {handout.hasImage && (
        <Figure
          id={handout.id}
          alt={`Figura da orientação ${handout.title}`}
          className={open ? "" : "recolhida"}
        />
      )}

      <div className="row" style={{ marginTop: "0.6rem" }}>
        <button type="button" className="button secundario pequeno" onClick={() => setOpen(!open)}>
          {open ? "Recolher" : "Ler tudo"}
        </button>
        <button type="button" className="button secundario pequeno" onClick={onDuplicate}>
          Duplicate
        </button>
        {onEdit && (
          <button type="button" className="button secundario pequeno" onClick={onEdit}>
            Edit
          </button>
        )}
        {onRemove && (
          <button type="button" className="button perigo pequeno" onClick={onRemove}>
            Remove
          </button>
        )}
      </div>
    </article>
  );
}

function Editor({
  handout,
  onClose,
  onSave,
}: {
  handout: Handout | null;
  onClose: () => void;
  onSave: () => Promise<void>;
}) {
  const [title, setTitle] = useState(handout?.title ?? "");
  const [body, setBody] = useState(handout?.body ?? "");
  const [figure, setFigure] = useState<File | null>(null);
  const feedback = useFeedback();
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const data = { title: title.trim(), body: body.trim() };
      // The figure goes after the text because it needs the identifier — in a
      // new handout it only exists once the handout is saved.
      const saved = handout
        ? await api.handouts.update(handout.id, data)
        : await api.handouts.create(data);
      if (figure) {
        await api.handouts.sendImage(saved.id, figure);
      }
      feedback.confirm(`"${saved.title}" salva${figure ? ", com a figura anexada" : ""}.`);
      await onSave();
    } catch (e) {
      setError(explainError(e, "save"));
      setSaving(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>{handout ? "Editar orientação" : "Nova orientação"}</h2>

      {error && (
        <div className="warning error" style={{ margin: "0.8rem 0" }}>
          {error}
        </div>
      )}

      <div className="field" style={{ marginTop: "0.8rem" }}>
        <label htmlFor="or-titulo">Título</label>
        <input
          id="or-titulo"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          maxLength={150}
          placeholder="Como montar o prato"
        />
      </div>

      <div className="field" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="or-corpo">Text</label>
        <textarea
          id="or-corpo"
          rows={10}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          required
          maxLength={8000}
          placeholder="Escreva como falaria com o paciente."
        />
        <span className="minusculo">
          Este texto vai para o paciente como você escrever — no link do plano e no PDF.
        </span>
      </div>

      <div className="field" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="or-figura">Figure</label>
        <input
          id="or-figura"
          type="file"
          accept="image/*"
          onChange={(e) => setFigure(e.target.files?.[0] ?? null)}
        />
        <span className="minusculo">
          {handout?.hasImage
            ? "Esta orientação já tem uma figura. Escolher outra substitui a atual."
            : "Opcional, até 2 MB. Um desenho do prato dividido diz mais que o parágrafo que o descreve."}
        </span>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="button secundario" onClick={onClose}>
          Cancel
        </button>
        <button className="button" type="submit" disabled={saving}>
          {saving ? "Salvando…" : "Salvar"}
        </button>
      </div>
    </form>
  );
}

/**
 * The figure of a handout.
 *
 * The route requires a credential and the `img` tag sends no header: the file
 * comes by `fetch` and becomes a temporary URL, released on unmount. Without
 * releasing it, every figure opened stays held in memory for as long as the tab
 * lives.
 */
function Figure({ id, alt, className = "" }: { id: number; alt: string; className?: string }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    let created: string | null = null;

    api.handouts
      .image(id)
      .then((file) => {
        created = file.url;
        if (alive) setUrl(file.url);
        else URL.revokeObjectURL(file.url);
      })
      .catch(() => {
        // A figure that does not open does not become an error message: the
        // handout's text is already on screen and it is what the patient
        // needs.
        if (alive) setUrl(null);
      });

    return () => {
      alive = false;
      if (created) URL.revokeObjectURL(created);
    };
  }, [id]);

  if (!url) return null;
  return <img className={`figure-handout ${className}`} src={url} alt={alt} />;
}
