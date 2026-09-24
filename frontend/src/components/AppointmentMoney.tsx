import { useState, type FormEvent } from "react";
import { FileBadge, Receipt as ReceiptIcon, Wallet } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr, todayIso } from "../api/dates";
import { currency } from "../text";
import type { Appointment, AppointmentType } from "../api/types";
import { useFeedback } from "./Feedback";

const CATEGORIES = ["Consulta", "Retorno", "Avaliação", "Pacote", "Outros"];

/** Abre o atestado de comparecimento numa aba; só de consulta realizada. */
export async function openCertificate(appointmentId: number) {
  const { url } = await api.schedule.certificate(appointmentId);
  window.open(url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * O que a consulta tem de dinheiro e de papel: pacote, parceiro, pagamento,
 * recibo e atestado.
 *
 * Aparece na agenda e na ficha do paciente, porque o cliente quer o
 * financeiro "dentro do próprio cadastro do paciente, sem precisar acessar a
 * página de financeiro". A secretária vê pacote e parceiro e tira o atestado;
 * o dinheiro é só do dono, e para ela os botões de pagamento não existem.
 */
export function AppointmentMoney({
  appointment: a,
  canCharge,
  onChanged,
  onReceipt,
  onError,
}: {
  appointment: Appointment;
  /** Quem pode registrar pagamento e emitir recibo: o nutricionista. */
  canCharge: boolean;
  onChanged: () => Promise<void> | void;
  onReceipt: (transactionId: number) => void;
  onError: (message: string) => void;
}) {
  const [charging, setCharging] = useState(false);
  const paid = a.payment?.status === "PAID";
  const pending = a.payment?.status === "PENDING";
  const hasFacts = Boolean(a.packageName || a.partnerName || a.payment);
  const canCertify = a.status === "COMPLETED";
  const canPay = canCharge && !paid && a.status !== "CANCELED";

  if (!hasFacts && !canPay && !canCertify) {
    return null;
  }

  return (
    <>
      {hasFacts && (
        <p className="appointment-extra">
          {a.packageName && <span className="tag azul">{a.packageName}</span>}
          {a.partnerName && <span className="appointment-partner">via {a.partnerName}</span>}
          {paid && a.payment && (
            <span className="tag verde">
              pago {currency(a.payment.value)}
              {a.payment.datePayment ? ` em ${formatBr(a.payment.datePayment)}` : ""}
            </span>
          )}
          {pending && a.payment && (
            <span className="tag ambar">a receber {currency(a.payment.value)}</span>
          )}
        </p>
      )}

      {(canPay || (canCharge && paid) || canCertify) && (
        <div className="appointment-money">
          {canPay && (
            <button
              type="button"
              className="button secundario pequeno"
              aria-expanded={charging}
              onClick={() => setCharging((v) => !v)}
            >
              <Wallet aria-hidden="true" />
              Registrar pagamento
            </button>
          )}
          {canCharge && paid && a.payment && (
            <button
              type="button"
              className="button secundario pequeno"
              onClick={() => onReceipt(a.payment!.transactionId)}
            >
              <ReceiptIcon aria-hidden="true" />
              Recibo
            </button>
          )}
          {canCertify && (
            <button
              type="button"
              className="button secundario pequeno"
              onClick={() =>
                openCertificate(a.id).catch((e) => onError(explainError(e, "gerar o atestado")))
              }
            >
              <FileBadge aria-hidden="true" />
              Atestado
            </button>
          )}
        </div>
      )}

      {charging && canPay && (
        <PaymentForm
          appointment={a}
          onDone={async () => {
            setCharging(false);
            await onChanged();
          }}
          onCancel={() => setCharging(false)}
        />
      )}
    </>
  );
}

function defaultCategory(type: AppointmentType): string {
  switch (type) {
    case "FOLLOWUP":
      return "Retorno";
    case "ASSESSMENT":
      return "Avaliação";
    case "OTHER":
      return "Outros";
    default:
      return "Consulta";
  }
}

function PaymentForm({
  appointment: a,
  onDone,
  onCancel,
}: {
  appointment: Appointment;
  onDone: () => Promise<void>;
  onCancel: () => void;
}) {
  const suggested = a.payment?.value ?? a.packageAmount;
  const [value, setValue] = useState(suggested != null ? String(suggested).replace(".", ",") : "");
  const [method, setMethod] = useState("");
  const [date, setDate] = useState(todayIso());
  const [documentNumber, setDocumentNumber] = useState("");
  const [category, setCategory] = useState(a.packageName ? "Pacote" : defaultCategory(a.type));
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const feedback = useFeedback();
  const prefix = `pg-${a.id}`;

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      const transaction = await api.finance.payAppointment(a.id, {
        value: value.trim() ? Number(value.replace(",", ".")) : undefined,
        paymentMethod: method.trim() || undefined,
        datePayment: date,
        documentNumber: documentNumber.trim() || undefined,
        category,
      });
      feedback.confirm(
        `Pagamento de ${currency(transaction.value)} registrado. O recibo já pode ser emitido.`,
      );
      await onDone();
    } catch (e) {
      setError(explainError(e, "registrar o pagamento"));
      setSending(false);
    }
  }

  return (
    <form className="appointment-payment" onSubmit={send} aria-label="Pagamento da consulta">
      {error && (
        <div className="warning error" role="alert">
          {error}
        </div>
      )}
      <div className="appointment-payment-grid">
        <div className="field">
          <label htmlFor={`${prefix}-valor`}>Valor (R$)</label>
          <input
            id={`${prefix}-valor`}
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor={`${prefix}-cat`}>Categoria</label>
          <select id={`${prefix}-cat`} value={category} onChange={(e) => setCategory(e.target.value)}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${prefix}-forma`}>Forma</label>
          <input
            id={`${prefix}-forma`}
            value={method}
            onChange={(e) => setMethod(e.target.value)}
            placeholder="Pix, cartão, dinheiro…"
          />
        </div>
        <div className="field">
          <label htmlFor={`${prefix}-data`}>Data</label>
          <input
            id={`${prefix}-data`}
            type="date"
            value={date}
            max={todayIso()}
            onChange={(e) => setDate(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor={`${prefix}-doc`}>Nº do documento</label>
          <input
            id={`${prefix}-doc`}
            value={documentNumber}
            onChange={(e) => setDocumentNumber(e.target.value)}
            placeholder="recibo ou nota, se houver"
            maxLength={60}
          />
        </div>
      </div>
      <div className="row end">
        <button type="button" className="button ghost pequeno" onClick={onCancel} disabled={sending}>
          Não registrar
        </button>
        <button type="submit" className="button pequeno" disabled={sending}>
          {sending ? "Registrando…" : "Confirmar pagamento"}
        </button>
      </div>
    </form>
  );
}
