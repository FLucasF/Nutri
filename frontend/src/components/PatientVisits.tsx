import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CalendarDays, Plus, Wallet } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr } from "../api/dates";
import { useAuth } from "../auth/AuthContext";
import { count, currency } from "../text";
import type { Appointment, AppointmentStatus, Receipt, Transaction } from "../api/types";
import { AppointmentMoney } from "./AppointmentMoney";
import { ReceiptPanel } from "./ReceiptPanel";
import { Amount, InstallmentBadge, RowActions, StatusTag } from "../pages/Finance";

const CLASSE_BY_STATUS: Record<AppointmentStatus, string> = {
  SCHEDULED: "neutra",
  CONFIRMED: "azul",
  COMPLETED: "verde",
  NOSHOW: "vermelha",
  CANCELED: "neutra",
};

/**
 * As consultas e o dinheiro do paciente, na ficha dele.
 *
 * "Gostaria que existisse uma forma de fazer isso dentro do próprio cadastro
 * do paciente, sem precisar acessar a página de financeiro": daqui se
 * registra o pagamento de uma consulta, sai o recibo e o atestado, e se vê o
 * que ele ainda deve. A secretária vê as consultas e tira o atestado; os
 * lançamentos são do dono.
 */
export function PatientVisits({ patientId }: { patientId: number }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canCharge = user?.role !== "ASSISTANT";

  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [visits, money] = await Promise.all([
        api.schedule.forPatient(patientId),
        canCharge ? api.finance.list({ patientId, size: 50 }) : Promise.resolve(null),
      ]);
      setAppointments(visits);
      setTransactions(money?.content ?? []);
    } catch (e) {
      setError(explainError(e, "abrir as consultas do paciente"));
    } finally {
      setLoading(false);
    }
  }, [patientId, canCharge]);

  useEffect(() => {
    void load();
  }, [load]);

  async function action(run: () => Promise<unknown>) {
    setError(null);
    try {
      await run();
      await load();
    } catch (e) {
      setError(explainError(e, "concluir a operação"));
    }
  }

  async function openReceipt(id: number) {
    setError(null);
    try {
      setReceipt(await api.finance.receipt(id));
    } catch (e) {
      setError(explainError(e, "emitir o recibo"));
    }
  }

  const pending = transactions.filter((t) => t.status === "PENDING");
  const owed = pending.reduce((sum, t) => sum + (t.type === "INCOME" ? t.value : 0), 0);
  const visible = showAll ? appointments : appointments.slice(0, 5);

  return (
    <section className="card patient-visits">
      <div className="card-head">
        <div>
          <h2 className="card-title">Consultas e pagamentos</h2>
          <p className="card-sub">
            {count(appointments.length, "consulta", "consultas")}
            {canCharge && owed > 0 ? ` · ${currency(owed)} a receber` : ""}
          </p>
        </div>
        {canCharge && (
          <button
            type="button"
            className="button secundario pequeno"
            onClick={() => navigate(`/finance?patientId=${patientId}`)}
          >
            <Plus aria-hidden="true" />
            Novo lançamento
          </button>
        )}
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {receipt && (
        <div className="mb-3">
          <ReceiptPanel receipt={receipt} onClose={() => setReceipt(null)} />
        </div>
      )}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : appointments.length === 0 ? (
        <p className="empty-hint patient-visits-empty">
          <CalendarDays aria-hidden="true" />
          Nenhuma consulta registrada. Use <strong>Consulta</strong>, acima, para marcar ou registrar
          a primeira.
        </p>
      ) : (
        <ul className="patient-visit-list">
          {visible.map((a) => (
            <li key={a.id} className={`patient-visit${a.status === "CANCELED" ? " cancelado" : ""}`}>
              <div className="patient-visit-when">
                <span className="readout strong">{formatBr(a.start.slice(0, 10))}</span>
                <span className="readout">{a.start.slice(11, 16)}</span>
              </div>
              <div className="patient-visit-body">
                <div className="patient-visit-top">
                  <strong>{a.typeDescription}</strong>
                  <span className={`tag ${CLASSE_BY_STATUS[a.status]}`}>{a.statusDescription}</span>
                </div>
                {a.notes && <p className="appointment-note">{a.notes}</p>}
                <AppointmentMoney
                  appointment={a}
                  canCharge={canCharge}
                  onChanged={load}
                  onReceipt={openReceipt}
                  onError={setError}
                />
              </div>
            </li>
          ))}
        </ul>
      )}

      {appointments.length > visible.length && (
        <button type="button" className="button ghost pequeno" onClick={() => setShowAll(true)}>
          Ver todas as {appointments.length} consultas
        </button>
      )}

      {canCharge && transactions.length > 0 && (
        <div className="patient-money">
          <h3 className="patient-money-title">
            <Wallet aria-hidden="true" />
            Lançamentos
          </h3>
          <ul className="patient-money-list">
            {transactions.map((t) => (
              <li key={t.id} className="patient-money-row">
                <div className="patient-money-main">
                  <span className="readout">{formatBr(t.accrual)}</span>
                  <span className="patient-money-what">
                    {t.category}
                    <InstallmentBadge transaction={t} />
                    {t.description ? <span className="finance-desc">{t.description}</span> : null}
                  </span>
                  <Amount transaction={t} />
                  <StatusTag transaction={t} />
                </div>
                <RowActions transaction={t} onAction={action} onReceipt={openReceipt} />
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
