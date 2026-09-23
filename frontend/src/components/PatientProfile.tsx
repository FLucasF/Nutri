import { useCallback, useEffect, useState } from "react";

import { api } from "../api/client";
import { explainError } from "../api/errors";
import { NotesField, NotesView } from "./RichText/NotesField";
import type { PatientNote, PatientTag } from "../api/types";

/**
 * As TAGs do paciente.
 *
 * "A TAG do paciente deverá funcionar como um aglutinador dos pacientes." É o
 * que faz a listagem ser varrida sem ler nome — e é por isso que o que se
 * marca aqui aparece lá.
 *
 * O conjunto inteiro é enviado de uma vez porque marcar e desmarcar é uma
 * operação só para quem está usando: ele abre, escolhe, e fecha.
 */
export function PatientTags({ patientId }: { patientId: number }) {
  const [available, setAvailable] = useState<PatientTag[]>([]);
  const [chosen, setChosen] = useState<number[]>([]);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [all, mine] = await Promise.all([
        api.patients.tags(),
        api.patients.tagsOf(patientId),
      ]);
      setAvailable(all);
      setChosen(mine.map((t) => t.id));
    } catch (e) {
      setError(explainError(e, "abrir as TAGs"));
    }
  }, [patientId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function save(next: number[]) {
    setChosen(next);
    setSaving(true);
    try {
      await api.patients.setTags(patientId, next);
    } catch (e) {
      setError(explainError(e, "salvar as TAGs"));
      await load();
    } finally {
      setSaving(false);
    }
  }

  async function createTag() {
    const name = window.prompt("Nome da nova TAG:");
    if (!name?.trim()) return;
    try {
      const tag = await api.patients.createTag(name.trim());
      setAvailable(await api.patients.tags());
      await save([...chosen, tag.id]);
    } catch (e) {
      setError(explainError(e, "criar a TAG"));
    }
  }

  const marked = available.filter((tag) => chosen.includes(tag.id));

  return (
    <div className="bloco-tags">
      <div className="row" style={{ alignItems: "center", gap: "0.4rem" }}>
        <span className="minusculo">TAGs</span>
        {marked.length === 0 && !open && (
          <span className="minusculo discreto">nenhuma</span>
        )}
        {marked.map((tag) => (
          <span className="tag-paciente" key={tag.id}>
            {tag.name}
          </span>
        ))}
        <button className="link-voltar minusculo" onClick={() => setOpen((v) => !v)}>
          {open ? "fechar" : "alterar"}
        </button>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginTop: "0.5rem" }}>
          {error}
        </div>
      )}

      {open && (
        <div className="escolha-tags">
          {available.map((tag) => {
            const on = chosen.includes(tag.id);
            return (
              <button
                key={tag.id}
                className={`tag-escolha${on ? " ativa" : ""}`}
                disabled={saving}
                aria-pressed={on}
                onClick={() =>
                  void save(on ? chosen.filter((x) => x !== tag.id) : [...chosen, tag.id])
                }
              >
                {tag.name}
                {/*
                  A TAG criada aqui no consultório se distingue das oito que o
                  sistema traz. Sem isso não dá para saber qual delas é sua
                  antes de tentar renomeá-la e descobrir que não pode.
                */}
                {tag.own && <span className="sinal-sua" title="TAG do seu consultório" />}
              </button>
            );
          })}
          <button className="tag-escolha nova" onClick={() => void createTag()}>
            + nova TAG
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * As anotações do nutricionista sobre o paciente.
 *
 * "como se fosse um tweet sabe?" — curtas, datadas e sucessivas. Não
 * substituem as observações do cadastro: aquele campo descreve o paciente,
 * este registra o que aconteceu.
 *
 * Ficam visíveis só para o consultório. É a leitura que o profissional faz do
 * paciente, e ela nunca sai no plano nem no link que o paciente abre.
 */
export function PatientNotes({ patientId }: { patientId: number }) {
  const [notes, setNotes] = useState<PatientNote[]>([]);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setNotes(await api.patients.notes(patientId));
    } catch (e) {
      setError(explainError(e, "abrir as anotações"));
    }
  }, [patientId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function add() {
    if (!draft.trim()) return;
    setSaving(true);
    try {
      await api.patients.addNote(patientId, draft.trim());
      setDraft("");
      await load();
    } catch (e) {
      setError(explainError(e, "salvar a anotação"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card" style={{ marginBottom: "1.1rem" }}>
      <h2 style={{ marginTop: 0 }}>Anotações</h2>
      <p className="minusculo" style={{ marginTop: 0 }}>
        Só você vê. Não sai no plano nem no link do paciente.
      </p>

      {error && (
        <div className="warning error" role="alert">
          {error}
        </div>
      )}

      <div>
        <NotesField
          label="Nova anotação"
          value={draft}
          onChange={setDraft}
          minHeight="6rem"
          onDemand
        />
        <div className="row end">
          <button className="button" onClick={() => void add()} disabled={saving || !draft.trim()}>
            Anotar
          </button>
        </div>
      </div>

      {notes.length === 0 ? (
        <p className="empty" style={{ marginBottom: 0 }}>
          Nenhuma anotação ainda.
        </p>
      ) : (
        <ol className="feed-anotacoes">
          {notes.map((note) => (
            <li key={note.id}>
              {/* A anotação virou documento: formatada quando foi escrita
                  assim, e a frase antiga continua legível do mesmo jeito. */}
              <NotesView value={note.body} />
              <div className="row" style={{ justifyContent: "space-between" }}>
                <span className="minusculo">
                  {new Date(note.createdAt).toLocaleString("pt-BR", {
                    dateStyle: "short",
                    timeStyle: "short",
                  })}
                </span>
                <button
                  className="link-voltar minusculo"
                  onClick={async () => {
                    if (!confirm("Remover esta anotação?")) return;
                    await api.patients.removeNote(patientId, note.id);
                    await load();
                  }}
                >
                  remover
                </button>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
