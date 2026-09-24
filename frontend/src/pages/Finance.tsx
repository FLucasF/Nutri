import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { Plus, Wallet, X } from "lucide-react";
import { allPages, api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { ReceiptPanel } from "../components/ReceiptPanel";
import { Pagination } from "../components/Pagination";
import { useIsNarrow } from "../hooks/useMediaQuery";
import { formatBr, todayIso, firstMonthIsoDay, lastMonthIsoDay } from "../api/dates";
import type {
  Summary,
  Transaction,
  PatientSummary,
  Receipt,
  ServicePackage,
  TransactionStatus,
  TransactionType,
} from "../api/types";
import { count, currency } from "../text";

const FINANCE_PAGE_SIZE = 50;

const CATEGORIES_INCOME = ["Consulta", "Retorno", "Avaliação", "Pacote", "Outros"];
const CATEGORIES_EXPENSE = ["Aluguel", "Material", "Software", "Impostos", "Marketing", "Outros"];

const STATUS_OPTIONS: { value: TransactionStatus | ""; label: string }[] = [
  { value: "", label: "Todas" },
  { value: "PENDING", label: "Pendente" },
  { value: "PAID", label: "Pago" },
  { value: "CANCELED", label: "Cancelado" },
];

export default function Finance() {
  const [params] = useSearchParams();
  // Coming from the patient's chart: the form opens with them chosen.
  const patientFromLink = params.get("patientId") ?? "";

  const [from, setFrom] = useState(firstMonthIsoDay());
  const [to, setTo] = useState(lastMonthIsoDay());

  const [summary, setSummary] = useState<Summary | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalTransactions, setTotalTransactions] = useState(0);
  const [overdue, setOverdue] = useState<Transaction[]>([]);
  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [packages, setPackages] = useState<ServicePackage[]>([]);
  const [filterStatus, setFilterStatus] = useState<TransactionStatus | "">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(Boolean(patientFromLink));
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const narrow = useIsNarrow();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [result, listed, overdue] = await Promise.all([
        api.finance.settle(from, to),
        api.finance.list({
          from,
          to,
          status: filterStatus || undefined,
          page,
          size: FINANCE_PAGE_SIZE,
        }),
        api.finance.overdue(),
      ]);
      setSummary(result);
      setTransactions(listed.content);
      setTotalPages(listed.totalPages);
      setTotalTransactions(listed.totalElements);
      setOverdue(overdue);
    } catch (e) {
      setError(explainError(e, "abrir o financeiro"));
    } finally {
      setLoading(false);
    }
  }, [from, to, filterStatus, page]);

  // Another period or status starts from the first page.
  useEffect(() => {
    setPage(0);
  }, [from, to, filterStatus]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    allPages((page, size) => api.patients.list({ active: true, page, size }))
      .then(setPatients)
      .catch(() => setPatients([]));
    api.packages.list().then(setPackages).catch(() => setPackages([]));
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

  const rowProps = { onAction: action, onReceipt: openReceipt };

  return (
    <div className="finance-page">
      <div className="header-page">
        <div>
          <h1>Financeiro</h1>
          <p>Lançamentos, inadimplência e apuração do período.</p>
        </div>
        <div className="header-page-actions">
          <button
            type="button"
            className={creating ? "button secundario" : "button"}
            onClick={() => setCreating((v) => !v)}
          >
            {creating ? <X aria-hidden="true" /> : <Plus aria-hidden="true" />}
            {creating ? "Cancelar" : "Novo lançamento"}
          </button>
        </div>
      </div>

      <div className="finance-body">
        {error && (
          <div className="warning error" role="alert">
            {error}
          </div>
        )}

        {receipt && <ReceiptPanel receipt={receipt} onClose={() => setReceipt(null)} />}

        {creating && (
          <FormTransaction
            patients={patients}
            packages={packages}
            patientSuggested={patientFromLink}
            onSave={async () => {
              setCreating(false);
              await load();
            }}
          />
        )}

        <div className="finance-toolbar">
          <div className="finance-period">
            <div className="field">
              <label htmlFor="fin-de">Competência de</label>
              <input id="fin-de" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="fin-ate">até</label>
              <input id="fin-ate" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
          </div>
          <div className="finance-status">
            <span className="finance-status-label" id="fin-sit-label">
              Situação
            </span>
            <div className="segmented" role="group" aria-labelledby="fin-sit-label" id="fin-sit">
              {STATUS_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={filterStatus === option.value}
                  onClick={() => setFilterStatus(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {summary && <PanelSummary summary={summary} />}

        {overdue.length > 0 && (
          <div className="warning attention">
            <strong>
              {count(overdue.length, "lançamento vencido", "lançamentos vencidos")}, somando {currency(
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
          <p className="loading">Carregando…</p>
        ) : transactions.length === 0 ? (
          <div className="card empty">
            <span className="empty-icon">
              <Wallet aria-hidden="true" />
            </span>
            <span className="empty-title">Nenhum lançamento nesta competência.</span>
            <span className="empty-hint">Registre uma cobrança para acompanhar o caixa do mês.</span>
          </div>
        ) : narrow ? (
          <ul className="list-rows finance-list">
            {transactions.map((l) => (
              <li className="finance-item" key={l.id}>
                <div className="finance-item-main">
                  <div className="list-row-body">
                    <span className="list-row-title">
                      {l.category}
                      <InstallmentBadge transaction={l} />
                    </span>
                    <span className="list-row-meta">
                      {formatBr(l.accrual)}
                      {l.patientName ? ` · ${l.patientName}` : ""}
                    </span>
                    <Details transaction={l} className="list-row-meta" />
                  </div>
                  <div className="finance-item-trail">
                    <Amount transaction={l} className="readout strong" />
                    <StatusTag transaction={l} />
                    {l.datePayment && <span className="finance-paid">pago em {formatBr(l.datePayment)}</span>}
                  </div>
                </div>
                <RowActions transaction={l} {...rowProps} />
              </li>
            ))}
          </ul>
        ) : (
          <div className="rolagem finance-table">
            <table>
              <thead>
                <tr>
                  <th>Competência</th>
                  <th>Descrição</th>
                  <th>Paciente</th>
                  <th className="num">Valor</th>
                  <th>Situação</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((l) => (
                  <tr key={l.id}>
                    <td className="finance-date">{formatBr(l.accrual)}</td>
                    <td>
                      <strong>{l.category}</strong>
                      <InstallmentBadge transaction={l} />
                      <Details transaction={l} className="finance-desc" />
                    </td>
                    <td className="discreto">{l.patientName ?? "—"}</td>
                    <td className="num">
                      <Amount transaction={l} />
                    </td>
                    <td>
                      <StatusTag transaction={l} />
                      {l.datePayment && <span className="finance-paid">pago em {formatBr(l.datePayment)}</span>}
                    </td>
                    <td className="finance-actions-cell">
                      <RowActions transaction={l} {...rowProps} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!loading && (
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalTransactions}
            size={FINANCE_PAGE_SIZE}
            noun={["lançamento", "lançamentos"]}
            onChange={setPage}
          />
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ pieces */

/** "+ R$ 120,00" for income, "− R$ 120,00" for an expense; the class carries the colour. */
export function Amount({ transaction, className }: { transaction: Transaction; className?: string }) {
  const income = transaction.type === "INCOME";
  const classes = ["finance-amount", income ? "entrada" : "saida", className].filter(Boolean).join(" ");
  return (
    <span className={classes}>
      {income ? "+" : "−"} {currency(transaction.value)}
    </span>
  );
}

export function StatusTag({ transaction: l }: { transaction: Transaction }) {
  return (
    <span className={`tag ${l.status === "PAID" ? "verde" : l.overdue ? "vermelha" : "ambar"}`}>
      {l.overdue ? "overdue" : l.statusDescription.toLowerCase()}
    </span>
  );
}

/** "Parcela 2/6", when the transaction is one. */
export function InstallmentBadge({ transaction: l }: { transaction: Transaction }) {
  if (!l.installmentIndex || !l.installmentCount) return null;
  return (
    <span className="tag azul finance-installment">
      parcela {l.installmentIndex}/{l.installmentCount}
    </span>
  );
}

/** Description, package and document number: what tells one charge from another. */
function Details({ transaction: l, className }: { transaction: Transaction; className: string }) {
  const parts = [
    l.description,
    l.packageName ? `pacote ${l.packageName}` : null,
    l.documentNumber ? `doc. ${l.documentNumber}` : null,
  ].filter(Boolean);
  if (parts.length === 0) return null;
  return <span className={className}>{parts.join(" · ")}</span>;
}

export function RowActions({
  transaction: l,
  onAction,
  onReceipt,
}: {
  transaction: Transaction;
  onAction: (run: () => Promise<unknown>) => Promise<void>;
  onReceipt: (id: number) => Promise<void>;
}) {
  if (l.status === "PENDING") {
    return (
      <div className="finance-actions">
        <button type="button" className="button pequeno" onClick={() => onAction(() => api.finance.pay(l.id))}>
          Dar baixa
        </button>
        <button
          type="button"
          className="button perigo pequeno"
          onClick={() => onAction(() => api.finance.cancel(l.id))}
        >
          Cancelar
        </button>
      </div>
    );
  }
  if (l.status === "PAID") {
    return (
      <div className="finance-actions">
        {l.type === "INCOME" && (
          <button type="button" className="button secundario pequeno" onClick={() => onReceipt(l.id)}>
            Recibo
          </button>
        )}
        <button
          type="button"
          className="button secundario pequeno"
          onClick={() => onAction(() => api.finance.refund(l.id))}
        >
          Estornar
        </button>
      </div>
    );
  }
  return null;
}

/**
 * Settled and expected appear side by side, never added together.
 * A single number would mix money that came in with money that might.
 */
function PanelSummary({ summary }: { summary: Summary }) {
  return (
    <div className="card">
      <div className="card-head">
        <div>
          <h2 className="card-title">Apuração do período</h2>
          <p className="card-sub">
            {formatBr(summary.from)} a {formatBr(summary.to)} ·{" "}
            {count(summary.transactions, "lançamento", "lançamentos")}
          </p>
        </div>
      </div>

      <div className="finance-stats">
        <div className="finance-stat-group">
          <span className="eyebrow">Entradas</span>
          <Value label="Recebido" value={summary.totalReceived} />
          <Value label="A receber" value={summary.totalReceive} />
        </div>
        <div className="finance-stat-group">
          <span className="eyebrow">Saídas</span>
          <Value label="Despesas pagas" value={summary.expensesPaid} />
          <Value label="Despesas a pagar" value={summary.expensesPay} />
        </div>
        <div className="finance-stat-group">
          <span className="eyebrow">Resultado</span>
          <Value label="Resultado efetivado" value={summary.resultEfetivado} signed />
          <Value label="Resultado previsto" value={summary.resultExpected} signed />
        </div>
      </div>

      <p className="finance-note">
        O resultado efetivado conta apenas o que já entrou e saiu. O previsto inclui pendências —
        são números diferentes de propósito.
      </p>
    </div>
  );
}

/** A stat tile. `signed` colours the figure by its sign: a result, not a total. */
function Value({ label, value, signed }: { label: string; value: number; signed?: boolean }) {
  const tone = !signed || value === 0 ? "" : value > 0 ? " positivo" : " negativo";
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <span className={`stat-value${tone}`}>{currency(value)}</span>
    </div>
  );
}

function FormTransaction({
  patients,
  packages,
  patientSuggested,
  onSave,
}: {
  patients: PatientSummary[];
  packages: ServicePackage[];
  patientSuggested: string;
  onSave: () => Promise<void>;
}) {
  const today = todayIso();

  const [type, setType] = useState<TransactionType>("INCOME");
  const [value, setValue] = useState("");
  const [accrual, setAccrual] = useState(today);
  const [due, setDue] = useState(today);
  const [category, setCategory] = useState("Consulta");
  const [patientId, setPatientId] = useState(patientSuggested);
  const [packageId, setPackageId] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [description, setDescription] = useState("");
  const [documentNumber, setDocumentNumber] = useState("");
  const [installments, setInstallments] = useState("1");
  const [alreadyPaid, setAlreadyPaid] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  const categories = type === "INCOME" ? CATEGORIES_INCOME : CATEGORIES_EXPENSE;
  const parcels = Math.max(1, Number(installments) || 1);
  const total = Number(value.replace(",", "."));

  function changeType(novo: TransactionType) {
    setType(novo);
    setCategory(novo === "INCOME" ? CATEGORIES_INCOME[0]! : CATEGORIES_EXPENSE[0]!);
    if (novo === "EXPENSE") {
      setPatientId("");
      setPackageId("");
    }
  }

  /** Choosing a package fills in what the package already says: value and category. */
  function choosePackage(id: string) {
    setPackageId(id);
    const pack = packages.find((p) => String(p.id) === id);
    if (pack) {
      setValue(String(pack.amount).replace(".", ","));
      setCategory("Pacote");
      if (!description.trim()) setDescription(pack.name);
    }
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      const created = await api.finance.create({
        type,
        // The amount is always positive: what defines income or expense is the type.
        value: total,
        accrual,
        due: due || undefined,
        category,
        paymentMethod: paymentMethod.trim() || undefined,
        description: description.trim() || undefined,
        patientId: patientId ? Number(patientId) : undefined,
        packageId: packageId ? Number(packageId) : undefined,
        documentNumber: documentNumber.trim() || undefined,
        installments: parcels > 1 ? parcels : undefined,
      });
      if (alreadyPaid) {
        await api.finance.pay(created.id, today);
      }
      const what = type === "INCOME" ? "Receita" : "Despesa";
      const how =
        parcels > 1
          ? ` em ${count(parcels, "parcela", "parcelas")}${alreadyPaid ? ", a primeira já paga" : ""}`
          : alreadyPaid
            ? " e marcada como paga"
            : "";
      feedback.confirm(`${what} de ${currency(total)} registrada${how}.`);
      await onSave();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "registrar o lançamento"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card finance-form" onSubmit={send}>
      <div className="card-head">
        <h2 className="card-title">Novo lançamento</h2>
        <div className="segmented" role="group" aria-label="Tipo do lançamento">
          <button type="button" aria-pressed={type === "INCOME"} onClick={() => changeType("INCOME")}>
            Receita
          </button>
          <button type="button" aria-pressed={type === "EXPENSE"} onClick={() => changeType("EXPENSE")}>
            Despesa
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      <div className="finance-form-grid">
        <div className="field">
          <label htmlFor="fl-valor">Valor (R$)</label>
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
          <label htmlFor="fl-cat">Categoria</label>
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
          <label htmlFor="fl-parcelas">Parcelas</label>
          <input
            id="fl-parcelas"
            name="installments"
            type="number"
            min={1}
            max={48}
            value={installments}
            onChange={(e) => setInstallments(e.target.value)}
            {...fields.props("installments")}
          />
          <FieldError field="installments" errors={fields.errors} />
          {parcels > 1 && total > 0 && (
            <span className="field-hint">
              {parcels}× de {currency(Math.floor((total / parcels) * 100) / 100)}, uma por mês a partir
              da competência.
            </span>
          )}
        </div>
        <div className="field largo">
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
          <label htmlFor="fl-doc">Nº do documento</label>
          <input
            id="fl-doc"
            name="documentNumber"
            value={documentNumber}
            onChange={(e) => setDocumentNumber(e.target.value)}
            placeholder="recibo ou nota, se houver"
            maxLength={60}
            {...fields.props("documentNumber")}
          />
          <FieldError field="documentNumber" errors={fields.errors} />
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
          <label htmlFor="fl-venc">Vencimento</label>
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
          <>
            <div className="field">
              <label htmlFor="fl-paciente">Paciente</label>
              <select id="fl-paciente" value={patientId} onChange={(e) => setPatientId(e.target.value)}>
                <option value="">Nenhum</option>
                {patients.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
            {packages.length > 0 && (
              <div className="field largo">
                <label htmlFor="fl-pacote">Pacote</label>
                <select id="fl-pacote" value={packageId} onChange={(e) => choosePackage(e.target.value)}>
                  <option value="">Nenhum</option>
                  {packages.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} · {currency(p.amount)}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </>
        )}
      </div>

      <div className="field finance-form-full">
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

      <div className="finance-form-foot">
        <label className="finance-check">
          <input type="checkbox" checked={alreadyPaid} onChange={(e) => setAlreadyPaid(e.target.checked)} />
          <span>{parcels > 1 ? "A primeira parcela já foi paga hoje" : "Já foi pago hoje"}</span>
        </label>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Registrar"}
        </button>
      </div>
    </form>
  );
}
