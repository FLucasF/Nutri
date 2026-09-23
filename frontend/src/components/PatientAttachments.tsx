import { useCallback, useEffect, useState } from "react";

import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr } from "../api/dates";
import type { PatientAttachment } from "../api/types";

/**
 * Os arquivos e links guardados junto do prontuário.
 *
 * Estava na lista de áreas dele e não existia. É também a resposta à dúvida
 * sobre os pacientes ativos: o cardápio do sistema antigo não precisa ser
 * recriado — ele é anexado, o histórico fica junto do paciente, e o próximo
 * planejamento de cada um nasce aqui, na consulta seguinte.
 */
export function PatientAttachments({ patientId }: { patientId: number }) {
  const [items, setItems] = useState<PatientAttachment[]>([]);
  const [adding, setAdding] = useState<"file" | "link" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setItems(await api.patients.attachments(patientId));
    } catch (e) {
      setError(explainError(e, "abrir os anexos"));
    }
  }, [patientId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function open(attachment: PatientAttachment) {
    if (attachment.kind === "LINK") {
      window.open(attachment.url, "_blank", "noopener");
      return;
    }
    try {
      const { url } = await api.patients.attachmentFile(patientId, attachment.id);
      window.open(url, "_blank", "noopener");
    } catch (e) {
      setError(explainError(e, "abrir o arquivo"));
    }
  }

  return (
    <section className="card" style={{ marginBottom: "1.1rem" }}>
      <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h2 style={{ margin: 0 }}>Arquivos anexos</h2>
          <p className="minusculo" style={{ margin: "0.15rem 0 0" }}>
            Laudo, cardápio antigo, foto de exame — o que já existe fora do sistema.
          </p>
        </div>
        <div className="row">
          <button
            className="button secundario pequeno"
            onClick={() => setAdding(adding === "file" ? null : "file")}
          >
            Anexar arquivo
          </button>
          <button
            className="button secundario pequeno"
            onClick={() => setAdding(adding === "link" ? null : "link")}
          >
            Guardar link
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginTop: "0.7rem" }}>
          {error}
        </div>
      )}

      {adding && (
        <FormAttachment
          patientId={patientId}
          kind={adding}
          onClose={() => setAdding(null)}
          onSaved={async () => {
            setAdding(null);
            await load();
          }}
          onError={setError}
        />
      )}

      {items.length === 0 ? (
        <p className="empty" style={{ marginBottom: 0 }}>
          Nenhum anexo. É aqui que o cardápio do sistema antigo pode ficar, sem precisar
          ser refeito.
        </p>
      ) : (
        <div className="card-list" style={{ marginTop: "0.9rem" }}>
          {items.map((item) => (
            <div className="row anexo" key={item.id}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <strong>{item.title}</strong>
                <div className="minusculo">
                  {item.kindDescription}
                  {item.referenceDate ? ` · ${formatBr(item.referenceDate)}` : ""}
                  {item.fileSize ? ` · ${Math.round(item.fileSize / 1024)} KB` : ""}
                </div>
                {item.notes && <p className="minusculo">{item.notes}</p>}
              </div>
              <div className="row">
                <button className="button secundario pequeno" onClick={() => void open(item)}>
                  Abrir
                </button>
                <button
                  className="button perigo pequeno"
                  onClick={async () => {
                    if (!confirm(`Remover "${item.title}"?`)) return;
                    await api.patients.removeAttachment(patientId, item.id);
                    await load();
                  }}
                >
                  Remover
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function FormAttachment({
  patientId,
  kind,
  onClose,
  onSaved,
  onError,
}: {
  patientId: number;
  kind: "file" | "link";
  onClose: () => void;
  onSaved: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [title, setTitle] = useState("");
  const [url, setUrl] = useState("");
  const [notes, setNotes] = useState("");
  const [referenceDate, setReferenceDate] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!title.trim()) {
      onError("Dê um título ao anexo. O nome do arquivo raramente diz o que ele é.");
      return;
    }
    setSaving(true);
    try {
      if (kind === "file") {
        if (!file) {
          onError("Escolha um arquivo.");
          return;
        }
        await api.patients.attachFile(patientId, file, {
          title: title.trim(),
          notes: notes.trim() || undefined,
          referenceDate: referenceDate || undefined,
        });
      } else {
        await api.patients.attachLink(patientId, {
          title: title.trim(),
          url: url.trim(),
          notes: notes.trim() || undefined,
          referenceDate: referenceDate || undefined,
        });
      }
      await onSaved();
    } catch (e) {
      onError(explainError(e, "guardar o anexo"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="painel" style={{ marginTop: "0.9rem" }}>
      <div className="grid two">
        <div className="field">
          <label htmlFor="an-titulo">Título</label>
          <input
            id="an-titulo"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Cardápio anterior (Webdiet)"
            maxLength={150}
            autoFocus
          />
        </div>
        <div className="field">
          <label htmlFor="an-data">Data do documento</label>
          <input
            id="an-data"
            type="date"
            value={referenceDate}
            onChange={(e) => setReferenceDate(e.target.value)}
          />
          <span className="minusculo">Raramente é a data de hoje.</span>
        </div>

        {kind === "file" ? (
          <div className="field">
            <label htmlFor="an-arquivo">Arquivo</label>
            <input
              id="an-arquivo"
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <span className="minusculo">Até 15 MB.</span>
          </div>
        ) : (
          <div className="field">
            <label htmlFor="an-url">Endereço</label>
            <input
              id="an-url"
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://…"
              maxLength={2000}
            />
          </div>
        )}

        <div className="field">
          <label htmlFor="an-obs">Observação</label>
          <input
            id="an-obs"
            type="text"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={1000}
          />
        </div>
      </div>

      <div className="row" style={{ marginTop: "0.7rem" }}>
        <button className="button" onClick={() => void save()} disabled={saving}>
          {saving ? "Guardando…" : "Guardar"}
        </button>
        <button className="button secundario" onClick={onClose} disabled={saving}>
          Cancelar
        </button>
      </div>
    </div>
  );
}
