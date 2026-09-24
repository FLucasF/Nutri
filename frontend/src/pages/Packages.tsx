import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Package, Plus, X } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { useIsNarrow } from "../hooks/useMediaQuery";
import { count, currency } from "../text";
import type { PackageRequest, ServicePackage } from "../api/types";

/**
 * Pacotes de trabalho: "um nome atrelado a um valor".
 *
 * Com número de encontros e intervalo, a agenda marca as próximas consultas
 * de uma vez. O pacote fica ligado à consulta e ao lançamento, e é assim que
 * o financeiro sabe quanto cada um rendeu.
 */
export default function Packages() {
  const [packages, setPackages] = useState<ServicePackage[]>([]);
  const [includeInactive, setIncludeInactive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<ServicePackage | "new" | null>(null);
  const narrow = useIsNarrow();
  const feedback = useFeedback();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPackages(await api.packages.list(includeInactive));
    } catch (e) {
      setError(explainError(e, "abrir os pacotes"));
    } finally {
      setLoading(false);
    }
  }, [includeInactive]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggle(pack: ServicePackage) {
    setError(null);
    try {
      if (pack.active) {
        await api.packages.deactivate(pack.id);
        feedback.confirm(`${pack.name} desativado. Deixa de aparecer para agendar; o que já foi marcado continua.`);
      } else {
        await api.packages.reactivate(pack.id);
        feedback.confirm(`${pack.name} reativado.`);
      }
      await load();
    } catch (e) {
      setError(explainError(e, "alterar o pacote"));
    }
  }

  return (
    <div className="packages-page">
      <div className="header-page">
        <div>
          <h1>Pacotes</h1>
          <p>Um nome, um valor e, se for por encontros, quantos e de quanto em quanto.</p>
        </div>
        <div className="header-page-actions">
          <button
            type="button"
            className={editing === "new" ? "button secundario" : "button"}
            onClick={() => setEditing(editing === "new" ? null : "new")}
          >
            {editing === "new" ? <X aria-hidden="true" /> : <Plus aria-hidden="true" />}
            {editing === "new" ? "Cancelar" : "Novo pacote"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {editing && (
        <FormPackage
          pack={editing === "new" ? null : editing}
          onDone={async () => {
            setEditing(null);
            await load();
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      <div className="card">
        <div className="card-head">
          <div>
            <h2 className="card-title">Cadastro</h2>
            <p className="card-sub">{count(packages.length, "pacote", "pacotes")}</p>
          </div>
          <label className="check-inline">
            <input
              type="checkbox"
              checked={includeInactive}
              onChange={(e) => setIncludeInactive(e.target.checked)}
            />
            <span>Mostrar desativados</span>
          </label>
        </div>

        {loading ? (
          <p className="loading">Carregando…</p>
        ) : packages.length === 0 ? (
          <div className="empty">
            <span className="empty-icon">
              <Package aria-hidden="true" />
            </span>
            <span className="empty-title">Nenhum pacote cadastrado.</span>
            <span className="empty-hint">
              Cadastre os seus pacotes de trabalho para escolhê-los ao agendar e ao lançar a receita.
            </span>
          </div>
        ) : narrow ? (
          <ul className="list-rows">
            {packages.map((p) => (
              <li key={p.id} className="list-row partner-row">
                <span className="list-row-body">
                  <span className="list-row-title">
                    {p.name}
                    {!p.active && <span className="tag neutra">desativado</span>}
                  </span>
                  <span className="list-row-meta">
                    {currency(p.amount)}
                    {p.sessions ? ` · ${sessionsText(p)}` : ""}
                  </span>
                </span>
                <span className="partner-actions">
                  <button type="button" className="button secundario pequeno" onClick={() => setEditing(p)}>
                    Editar
                  </button>
                  <button type="button" className="button ghost pequeno" onClick={() => toggle(p)}>
                    {p.active ? "Desativar" : "Reativar"}
                  </button>
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <div className="rolagem">
            <table className="packages-table">
              <thead>
                <tr>
                  <th>Pacote</th>
                  <th className="num">Valor</th>
                  <th>Encontros</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {packages.map((p) => (
                  <tr key={p.id} className={p.active ? undefined : "inativo"}>
                    <td>
                      <strong>{p.name}</strong>
                      {!p.active && <span className="tag neutra">desativado</span>}
                      {p.notes && <span className="finance-desc">{p.notes}</span>}
                    </td>
                    <td className="num">{currency(p.amount)}</td>
                    <td className="discreto">{p.sessions ? sessionsText(p) : "—"}</td>
                    <td>
                      <span className="partner-actions">
                        <button
                          type="button"
                          className="button secundario pequeno"
                          onClick={() => setEditing(p)}
                        >
                          Editar
                        </button>
                        <button type="button" className="button ghost pequeno" onClick={() => toggle(p)}>
                          {p.active ? "Desativar" : "Reativar"}
                        </button>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function sessionsText(p: ServicePackage): string {
  const sessions = count(p.sessions ?? 0, "encontro", "encontros");
  return p.intervalDays ? `${sessions} a cada ${count(p.intervalDays, "dia", "dias")}` : sessions;
}

function FormPackage({
  pack,
  onDone,
  onCancel,
}: {
  pack: ServicePackage | null;
  onDone: () => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(pack?.name ?? "");
  const [amount, setAmount] = useState(pack ? String(pack.amount).replace(".", ",") : "");
  const [sessions, setSessions] = useState(pack?.sessions ? String(pack.sessions) : "");
  const [intervalDays, setIntervalDays] = useState(pack?.intervalDays ? String(pack.intervalDays) : "");
  const [notes, setNotes] = useState(pack?.notes ?? "");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    fields.clear();
    setSending(true);
    const data: PackageRequest = {
      name: name.trim(),
      amount: Number(amount.replace(",", ".")),
      sessions: sessions ? Number(sessions) : undefined,
      intervalDays: intervalDays ? Number(intervalDays) : undefined,
      notes: notes.trim() || undefined,
    };
    try {
      if (pack) {
        await api.packages.update(pack.id, data);
        feedback.confirm(`${data.name} atualizado.`);
      } else {
        await api.packages.create(data);
        feedback.confirm(`${data.name} cadastrado. Já aparece ao agendar e ao lançar receita.`);
      }
      await onDone();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "salvar o pacote"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card mb-3 package-form" onSubmit={send}>
      <div className="card-head">
        <h2 className="card-title">{pack ? `Editar ${pack.name}` : "Novo pacote"}</h2>
      </div>
      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}
      <div className="stack">
        <div className="grid two">
          <div className="field">
            <label htmlFor="pk-nome">Nome</label>
            <input
              id="pk-nome"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={120}
              placeholder="Acompanhamento trimestral"
              {...fields.props("name")}
            />
            <FieldError field="name" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="pk-valor">Valor (R$)</label>
            <input
              id="pk-valor"
              name="amount"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
              {...fields.props("amount")}
            />
            <FieldError field="amount" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="pk-encontros">Encontros presenciais</label>
            <input
              id="pk-encontros"
              name="sessions"
              type="number"
              min={1}
              max={200}
              value={sessions}
              onChange={(e) => setSessions(e.target.value)}
              placeholder="vazio se não for por encontros"
              {...fields.props("sessions")}
            />
            <FieldError field="sessions" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="pk-intervalo">Intervalo entre encontros (dias)</label>
            <input
              id="pk-intervalo"
              name="intervalDays"
              type="number"
              min={1}
              max={365}
              value={intervalDays}
              onChange={(e) => setIntervalDays(e.target.value)}
              placeholder="7"
              {...fields.props("intervalDays")}
            />
            <FieldError field="intervalDays" errors={fields.errors} />
            <span className="field-hint">
              Ao agendar com o pacote, a agenda marca os próximos encontros com esse intervalo.
            </span>
          </div>
        </div>
        <div className="field">
          <label htmlFor="pk-obs">Observações</label>
          <input
            id="pk-obs"
            name="notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={500}
            {...fields.props("notes")}
          />
          <FieldError field="notes" errors={fields.errors} />
        </div>
        <div className="row end">
          <button type="button" className="button ghost" onClick={onCancel} disabled={sending}>
            Desistir
          </button>
          <button className="button" type="submit" disabled={sending}>
            {sending ? "Salvando…" : pack ? "Salvar alterações" : "Cadastrar pacote"}
          </button>
        </div>
      </div>
    </form>
  );
}
