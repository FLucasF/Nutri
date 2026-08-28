import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { formatBr, todayIso, firstMonthIsoDay, lastMonthIsoDay } from "../api/dates";
import type {
  Summary,
  Transaction,
  PatientSummary,
  Receipt,
  TransactionStatus,
  TransactionType,
} from "../api/types";
import { count } from "../text";

const CATEGORIES_INCOME = ["Consulta", "Retorno", "Avaliação", "Pacote", "Outros"];
const CATEGORIES_EXPENSE = ["Aluguel", "Material", "Software", "Impostos", "Marketing", "Outros"];

export default function Finance() {
  const [from, setFrom] = useState(firstMonthIsoDay());
  const [to, setTo] = useState(lastMonthIsoDay());

  const [summary, setSummary] = useState<Summary | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [overdue, setOverdue] = useState<Transaction[]>([]);
  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [filterStatus, setFilterStatus] = useState<TransactionStatus | "">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [result, page, overdue] = await Promise.all([
        api.finance.settle(from, to),
        api.finance.list({
          from,
          to,
          status: filterStatus || undefined,
          size: 100,
        }),
        api.finance.overdue(),
      ]);
      setSummary(result);
      setTransactions(page.content);
      setOverdue(overdue);
    } catch (e) {
      setError(explainError(e, "abrir o financeiro"));
    } finally {
      setLoading(false);
    }
  }, [from, to, filterStatus]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    api.patients.list({ active: true, size: 200 })
      .then((p) => setPatients(p.content))
      .catch(() => setPatients([]));
  }, []);

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

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Finance</h1>
          <p>Lançamentos, inadimplência e apuração do período.</p>
        </div>
        <button className="button" onClick={() => setCreating((v) => !v)}>
          {creating ? "Cancelar" : "Novo lançamento"}
        </button>
      </div>

      {error && <div className="warning error" style={{ marginBottom: "0.9rem" }}>{error}</div>}

      {receipt && <PanelReceipt receipt={receipt} onClose={() => setReceipt(null)} />}

      {creating && (
        <FormTransaction
          patients={patients}
          onSave={async () => {
            setCreating(false);
            await load();
          }}
        />
      )}

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="row">
          <div className="field" style={{ width: 165 }}>
            <label htmlFor="fin-de">Competência de</label>
            <input id="fin-de" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field" style={{ width: 165 }}>
            <label htmlFor="fin-ate">até</label>
            <input id="fin-ate" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="field" style={{ width: 175 }}>
            <label htmlFor="fin-sit">Situação</label>
            <select
              id="fin-sit"
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as TransactionStatus | "")}
            >
              <option value="">All</option>
              <option value="PENDING">Pending</option>
              <option value="PAID">Paid</option>
              <option value="CANCELED">Canceled</option>
            </select>
          </div>
        </div>
      </div>

      {summary && <PanelSummary summary={summary} />}

      {overdue.length > 0 && (
        <div className="warning attention" style={{ marginBottom: "0.9rem" }}>
          <strong>
            {count(overdue.length, "lançamento vencido", "lançamentos vencidos")}, summing {currency(
              overdue.reduce((sums, l) => sums + l.value, 0),
            )}
            .
          </strong>{" "}
          {overdue
            .slice(0, 3)
            .map((l) => `${l.patientName ?? l.category} (${formatBr(l.due)})`)
            .join(", ")}
          {overdue.length > 3 ? " e outros." : "."}
        </div>
      )}

      {loading ? (
        <p className="loading">Loading…</p>
      ) : transactions.length === 0 ? (
        <div className="card empty">
          Nenhum lançamento nesta competência. Registre uma cobrança para acompanhar o caixa do mês.
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Competência</th>
                <th>Descrição</th>
                <th>Patient</th>
                <th className="num">Value</th>
                <th>Situação</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((l) => (
                <tr key={l.id}>
                  <td className="mono">{formatBr(l.accrual)}</td>
                  <td>
                    <strong>{l.category}</strong>
                    {l.description && <div className="minusculo">{l.description}</div>}
                  </td>
                  <td className="discreto">{l.patientName ?? "—"}</td>
                  <td
                    className="num"
                    style={{ color: l.type === "INCOME" ? "var(--accent)" : "var(--error)" }}
                  >
                    {l.type === "INCOME" ? "+" : "−"} {currency(l.value)}
                  </td>
                  <td>
                    <span
                      className={`tag ${
                        l.status === "PAID" ? "verde" : l.overdue ? "vermelha" : "ambar"
                      }`}
                    >
                      {l.overdue ? "overdue" : l.statusDescription.toLowerCase()}
                    </span>
                    {l.datePayment && (
                      <div className="minusculo">pago em {formatBr(l.datePayment)}</div>
                    )}
                  </td>
                  <td>
                    <div className="row" style={{ gap: "0.3rem" }}>
                      {l.status === "PENDING" && (
                        <>
                          <button
                            className="button pequeno"
                            onClick={() => action(() => api.finance.pay(l.id))}
                          >
                            Dar baixa
                          </button>
                          <button
                            className="button secundario pequeno"
                            onClick={() => action(() => api.finance.cancel(l.id))}
                          >
                            Cancel
                          </button>
                        </>
                      )}
                      {l.status === "PAID" && (
                        <>
                          {l.type === "INCOME" && (
                            <button
                              className="button secundario pequeno"
                              onClick={() => openReceipt(l.id)}
                            >
                              Receipt
                            </button>
                          )}
                          <button
                            className="button secundario pequeno"
                            onClick={() => action(() => api.finance.refund(l.id))}
                          >
                            Refund
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

/**
 * Settled and expected appear side by side, never added together.
 * A single number would mix money that came in with money that might.
 */
function PanelSummary({ summary }: { summary: Summary }) {
  return (
    <div className="card" style={{ marginBottom: "0.9rem" }}>
      <h2>Apuração do período</h2>

      <div className="grid three" style={{ marginTop: "0.8rem" }}>
        <Value label="Recebido" value={summary.totalReceived} color="var(--accent)" />
        <Value label="A receber" value={summary.totalReceive} color="var(--alerta)" />
        <Value label="Despesas pagas" value={summary.expensesPaid} color="var(--error)" />
        <Value label="Despesas a pagar" value={summary.expensesPay} color="var(--ink-soft)" />
        <Value
          label="Resultado efetivado"
          value={summary.resultEfetivado}
          color={summary.resultEfetivado >= 0 ? "var(--accent)" : "var(--error)"}
          highlight
        />
        <Value
          label="Resultado previsto"
          value={summary.resultExpected}
          color="var(--ink-soft)"
        />
      </div>

      <p className="minusculo" style={{ marginTop: "0.8rem", marginBottom: 0 }}>
        O resultado efetivado conta apenas o que já entrou e saiu. O previsto inclui pendências —
        são números diferentes de propósito.
      </p>
    </div>
  );
}

function Value({
  label,
  value,
  color,
  highlight,
}: {
  label: string;
  value: number;
  color: string;
  highlight?: boolean;
}) {
  return (
    <div>
      <span className="minusculo">{label}</span>
      <div
        className="mono"
        style={{ fontSize: highlight ? "1.3rem" : "1.1rem", fontWeight: 700, color: color }}
      >
        {currency(value)}
      </div>
    </div>
  );
}

function PanelReceipt({ receipt, onClose }: { receipt: Receipt; onClose: () => void }) {
  return (
    <div className="card" style={{ marginBottom: "0.9rem", borderColor: "var(--accent)" }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Receipt</h2>
        <button className="button secundario pequeno" onClick={onClose}>
          Close
        </button>
      </div>

      <div style={{ marginTop: "0.9rem", lineHeight: 1.8 }}>
        <p style={{ margin: 0 }}>
          Recebi de <strong>{receipt.payerName ?? "—"}</strong> a importância de{" "}
          <strong>{currency(receipt.value)}</strong> ({receipt.valueByWords}), related a{" "}
          <strong>{receipt.related}</strong>.
        </p>
        <p style={{ margin: "0.8rem 0 0" }} className="discreto">
          {receipt.practiceName} · {receipt.profissionalName}
          {receipt.profissionalCrn ? ` · ${receipt.profissionalCrn}` : ""}
          <br />
          Pagamento em {formatBr(receipt.datePayment)} · Emitido em {formatBr(receipt.emitidoAt)}
        </p>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button className="button secundario pequeno" onClick={() => window.print()}>
          Print
        </button>
      </div>
    </div>
  );
}

function FormTransaction({
  patients,
  onSave,
}: {
  patients: PatientSummary[];
  onSave: () => Promise<void>;
}) {
  const today = todayIso();

  const [type, setType] = useState<TransactionType>("INCOME");
  const [value, setValue] = useState("");
  const [accrual, setAccrual] = useState(today);
  const [due, setDue] = useState(today);
  const [category, setCategory] = useState("Consulta");
  const [patientId, setPatientId] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [description, setDescription] = useState("");
  const [alreadyPaid, setAlreadyPaid] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  const categories = type === "INCOME" ? CATEGORIES_INCOME : CATEGORIES_EXPENSE;

  function changeType(novo: TransactionType) {
    setType(novo);
    setCategory(novo === "INCOME" ? CATEGORIES_INCOME[0]! : CATEGORIES_EXPENSE[0]!);
    if (novo === "EXPENSE") setPatientId("");
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      const created = await api.finance.create({
        type,
        // The amount is always positive: what defines income or expense is the type.
        value: Number(value.replace(",", ".")),
        accrual,
        due: due || undefined,
        category,
        paymentMethod: paymentMethod.trim() || undefined,
        description: description.trim() || undefined,
        patientId: patientId ? Number(patientId) : undefined,
      });
      if (alreadyPaid) {
        await api.finance.pay(created.id, today);
      }
      feedback.confirm(
        `${type === "INCOME" ? "Receita" : "Despesa"} de R$ ${value} registrada${alreadyPaid ? " e marcada como paga" : ""}.`,
      );
      await onSave();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "registrar o lançamento"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Novo lançamento</h2>

      {error && <div className="warning error" style={{ margin: "0.8rem 0" }}>{error}</div>}

      <div className="row" style={{ marginTop: "0.8rem", gap: "0.3rem" }}>
        <button
          type="button"
          className={`button ${type === "INCOME" ? "" : "secundario"} pequeno`}
          onClick={() => changeType("INCOME")}
        >
          Recipe
        </button>
        <button
          type="button"
          className={`button ${type === "EXPENSE" ? "" : "secundario"} pequeno`}
          onClick={() => changeType("EXPENSE")}
        >
          Expense
        </button>
      </div>

      <div className="grid three" style={{ marginTop: "0.8rem" }}>
        <div className="field">
          <label htmlFor="fl-valor">Value (R$)</label>
          <input
            id="fl-valor"
            name="value"
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            required
            {...fields.props("value")}
          />
          <FieldError field="value" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="fl-cat">Category</label>
          <select
            id="fl-cat"
            name="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            {...fields.props("category")}
          >
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <FieldError field="category" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="fl-forma">Forma de pagamento</label>
          <input
            id="fl-forma"
            name="paymentMethod"
            value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value)}
            placeholder="Pix, cartão, dinheiro…"
            {...fields.props("paymentMethod")}
          />
          <FieldError field="paymentMethod" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="fl-comp">Competência</label>
          <input
            id="fl-comp"
            name="accrual"
            type="date"
            value={accrual}
            onChange={(e) => setAccrual(e.target.value)}
            required
            {...fields.props("accrual")}
          />
          <FieldError field="accrual" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="fl-venc">Due</label>
          <input
            id="fl-venc"
            name="due"
            type="date"
            value={due}
            onChange={(e) => setDue(e.target.value)}
            {...fields.props("due")}
          />
          <FieldError field="due" errors={fields.errors} />
        </div>
        {type === "INCOME" && (
          <div className="field">
            <label htmlFor="fl-paciente">Patient</label>
            <select
              id="fl-paciente"
              value={patientId}
              onChange={(e) => setPatientId(e.target.value)}
            >
              <option value="">None</option>
              {patients.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      <div className="field" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="fl-desc">Descrição</label>
        <input
          id="fl-desc"
          name="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          {...fields.props("description")}
        />
        <FieldError field="description" errors={fields.errors} />
      </div>

      <label className="row" style={{ gap: "0.4rem", marginTop: "0.8rem" }}>
        <input
          type="checkbox"
          checked={alreadyPaid}
          onChange={(e) => setAlreadyPaid(e.target.checked)}
          style={{ width: "auto" }}
        />
        <span className="discreto">Já foi pago hoje</span>
      </label>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Registrar"}
        </button>
      </div>
    </form>
  );
}

// -------------------------------------------------------------------- helpers

function currency(value: number) {
  return value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}


