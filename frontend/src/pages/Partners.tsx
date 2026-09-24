import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Handshake, Plus, X } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr, firstMonthIsoDay, lastMonthIsoDay } from "../api/dates";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { useIsNarrow } from "../hooks/useMediaQuery";
import { count, currency } from "../text";
import type { Partner, PartnerRequest, ReferralReport } from "../api/types";

const KINDS = ["Personal", "Médico", "Academia", "Paciente", "Clínica", "Outro"];

/**
 * Parceiros de indicação.
 *
 * "Se eu aumentar muito o volume de pacientes por indicação, eu quero vincular
 * a consulta/agendamento a um parceiro cadastrado, para eventuais benefícios
 * ao indicante." O cadastro é curto — quem é, o que é, como falar — e o que
 * importa está no relatório: quantos pacientes cada um trouxe e quanto isso
 * rendeu no período.
 */
export default function Partners() {
  const [partners, setPartners] = useState<Partner[]>([]);
  const [includeInactive, setIncludeInactive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Partner | "new" | null>(null);
  const narrow = useIsNarrow();
  const feedback = useFeedback();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPartners(await api.partners.list(includeInactive));
    } catch (e) {
      setError(explainError(e, "abrir os parceiros"));
    } finally {
      setLoading(false);
    }
  }, [includeInactive]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggle(partner: Partner) {
    setError(null);
    try {
      if (partner.active) {
        await api.partners.deactivate(partner.id);
        feedback.confirm(`${partner.name} desativado. Os pacientes indicados continuam apontando para ele.`);
      } else {
        await api.partners.reactivate(partner.id);
        feedback.confirm(`${partner.name} reativado.`);
      }
      await load();
    } catch (e) {
      setError(explainError(e, "alterar o parceiro"));
    }
  }

  return (
    <div className="partners-page">
      <div className="header-page">
        <div>
          <h1>Parceiros</h1>
          <p>Quem indica pacientes ao consultório, e o que cada indicação rendeu.</p>
        </div>
        <div className="header-page-actions">
          <button
            type="button"
            className={editing === "new" ? "button secundario" : "button"}
            onClick={() => setEditing(editing === "new" ? null : "new")}
          >
            {editing === "new" ? <X aria-hidden="true" /> : <Plus aria-hidden="true" />}
            {editing === "new" ? "Cancelar" : "Novo parceiro"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {editing && (
        <FormPartner
          partner={editing === "new" ? null : editing}
          onDone={async () => {
            setEditing(null);
            await load();
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      <div className="card mb-3">
        <div className="card-head">
          <div>
            <h2 className="card-title">Cadastro</h2>
            <p className="card-sub">{count(partners.length, "parceiro", "parceiros")}</p>
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
        ) : partners.length === 0 ? (
          <div className="empty">
            <span className="empty-icon">
              <Handshake aria-hidden="true" />
            </span>
            <span className="empty-title">Nenhum parceiro cadastrado.</span>
            <span className="empty-hint">
              Cadastre quem indica pacientes e marque a indicação na ficha de cada um.
            </span>
          </div>
        ) : narrow ? (
          <ul className="list-rows">
            {partners.map((p) => (
              <li key={p.id} className="list-row partner-row">
                <span className="list-row-body">
                  <span className="list-row-title">
                    {p.name}
                    {!p.active && <span className="tag neutra">desativado</span>}
                  </span>
                  <span className="list-row-meta">
                    {[p.kind, p.contact].filter(Boolean).join(" · ") || "—"}
                  </span>
                  <span className="list-row-meta">
                    {count(p.patientsReferred, "paciente indicado", "pacientes indicados")}
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
            <table className="partners-table">
              <thead>
                <tr>
                  <th>Parceiro</th>
                  <th>Tipo</th>
                  <th>Contato</th>
                  <th className="num">Pacientes indicados</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {partners.map((p) => (
                  <tr key={p.id} className={p.active ? undefined : "inativo"}>
                    <td>
                      <strong>{p.name}</strong>
                      {!p.active && <span className="tag neutra">desativado</span>}
                      {p.notes && <span className="finance-desc">{p.notes}</span>}
                    </td>
                    <td className="discreto">{p.kind ?? "—"}</td>
                    <td className="discreto">{p.contact ?? "—"}</td>
                    <td className="num">{p.patientsReferred}</td>
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

      <ReferralReportCard />
    </div>
  );
}

function FormPartner({
  partner,
  onDone,
  onCancel,
}: {
  partner: Partner | null;
  onDone: () => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(partner?.name ?? "");
  const [kind, setKind] = useState(partner?.kind ?? "");
  const [contact, setContact] = useState(partner?.contact ?? "");
  const [notes, setNotes] = useState(partner?.notes ?? "");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    fields.clear();
    setSending(true);
    const data: PartnerRequest = {
      name: name.trim(),
      kind: kind.trim() || undefined,
      contact: contact.trim() || undefined,
      notes: notes.trim() || undefined,
    };
    try {
      if (partner) {
        await api.partners.update(partner.id, data);
        feedback.confirm(`${data.name} atualizado.`);
      } else {
        await api.partners.create(data);
        feedback.confirm(`${data.name} cadastrado como parceiro.`);
      }
      await onDone();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "salvar o parceiro"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card mb-3 partner-form" onSubmit={send}>
      <div className="card-head">
        <h2 className="card-title">{partner ? `Editar ${partner.name}` : "Novo parceiro"}</h2>
      </div>
      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}
      <div className="stack">
        <div className="grid two">
          <div className="field">
            <label htmlFor="pa-nome">Nome</label>
            <input
              id="pa-nome"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={150}
              {...fields.props("name")}
            />
            <FieldError field="name" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="pa-tipo">Tipo</label>
            <input
              id="pa-tipo"
              name="kind"
              value={kind}
              onChange={(e) => setKind(e.target.value)}
              list="pa-tipos"
              placeholder="Personal, médico, academia…"
              maxLength={40}
              {...fields.props("kind")}
            />
            <datalist id="pa-tipos">
              {KINDS.map((k) => (
                <option key={k} value={k} />
              ))}
            </datalist>
            <FieldError field="kind" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="pa-contato">Contato</label>
            <input
              id="pa-contato"
              name="contact"
              value={contact}
              onChange={(e) => setContact(e.target.value)}
              placeholder="telefone, e-mail ou Instagram"
              maxLength={180}
              {...fields.props("contact")}
            />
            <FieldError field="contact" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="pa-obs">Observações</label>
            <input
              id="pa-obs"
              name="notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="o combinado com ele, por exemplo"
              maxLength={1000}
              {...fields.props("notes")}
            />
            <FieldError field="notes" errors={fields.errors} />
          </div>
        </div>
        <div className="row end">
          <button type="button" className="button ghost" onClick={onCancel} disabled={sending}>
            Desistir
          </button>
          <button className="button" type="submit" disabled={sending}>
            {sending ? "Salvando…" : partner ? "Salvar alterações" : "Cadastrar parceiro"}
          </button>
        </div>
      </div>
    </form>
  );
}

/**
 * O relatório de indicações do período: por parceiro, pacientes indicados,
 * consultas (com realizadas e faltas) e a receita paga atribuída a ele.
 */
function ReferralReportCard() {
  const [from, setFrom] = useState(firstMonthIsoDay());
  const [to, setTo] = useState(lastMonthIsoDay());
  const [report, setReport] = useState<ReferralReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const narrow = useIsNarrow();

  useEffect(() => {
    let alive = true;
    setError(null);
    api.partners
      .report(from, to)
      .then((r) => {
        if (alive) setReport(r);
      })
      .catch((e) => {
        if (alive) setError(explainError(e, "montar o relatório de indicações"));
      });
    return () => {
      alive = false;
    };
  }, [from, to]);

  return (
    <div className="card referral-report">
      <div className="card-head">
        <div>
          <h2 className="card-title">Relatório de indicações</h2>
          <p className="card-sub">
            A consulta conta para o parceiro marcado nela ou, sem marcação, para quem indicou o
            paciente. A receita é a paga no período.
          </p>
        </div>
      </div>

      <div className="finance-period mb-3">
        <div className="field">
          <label htmlFor="rel-de">De</label>
          <input id="rel-de" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rel-ate">até</label>
          <input id="rel-ate" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {!report ? (
        <p className="loading">Carregando…</p>
      ) : report.rows.length === 0 ? (
        <p className="empty-hint">Nenhum parceiro cadastrado ainda.</p>
      ) : narrow ? (
        <ul className="list-rows">
          {report.rows.map((r) => (
            <li key={r.partnerId} className="list-row">
              <span className="list-row-body">
                <span className="list-row-title">{r.partnerName}</span>
                <span className="list-row-meta">
                  {count(r.patientsReferred, "paciente", "pacientes")} ·{" "}
                  {count(r.appointments, "consulta", "consultas")}
                  {r.noshows > 0 ? ` · ${count(r.noshows, "falta", "faltas")}` : ""}
                </span>
              </span>
              <span className="list-row-trail readout strong">{currency(r.revenuePaid)}</span>
            </li>
          ))}
          <li className="list-row referral-total">
            <span className="list-row-body">
              <span className="list-row-title">Total</span>
              <span className="list-row-meta">
                {count(report.totalPatients, "paciente", "pacientes")} ·{" "}
                {count(report.totalAppointments, "consulta", "consultas")}
              </span>
            </span>
            <span className="list-row-trail readout strong">{currency(report.totalRevenue)}</span>
          </li>
        </ul>
      ) : (
        <div className="rolagem">
          <table className="referral-table">
            <thead>
              <tr>
                <th>Parceiro</th>
                <th className="num">Pacientes</th>
                <th className="num">Consultas</th>
                <th className="num">Realizadas</th>
                <th className="num">Faltas</th>
                <th className="num">Receita paga</th>
              </tr>
            </thead>
            <tbody>
              {report.rows.map((r) => (
                <tr key={r.partnerId}>
                  <td>
                    <strong>{r.partnerName}</strong>
                    {r.kind && <span className="finance-desc">{r.kind}</span>}
                  </td>
                  <td className="num">{r.patientsReferred}</td>
                  <td className="num">{r.appointments}</td>
                  <td className="num">{r.completed}</td>
                  <td className="num">{r.noshows}</td>
                  <td className="num">{currency(r.revenuePaid)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th>Total · {formatBr(report.from)} a {formatBr(report.to)}</th>
                <td className="num">{report.totalPatients}</td>
                <td className="num">{report.totalAppointments}</td>
                <td className="num">{report.rows.reduce((s, r) => s + r.completed, 0)}</td>
                <td className="num">{report.rows.reduce((s, r) => s + r.noshows, 0)}</td>
                <td className="num">{currency(report.totalRevenue)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </div>
  );
}
