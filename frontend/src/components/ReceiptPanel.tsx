import { Printer, X } from "lucide-react";
import { formatBr } from "../api/dates";
import { currency } from "../text";
import type { Receipt } from "../api/types";

/**
 * O recibo de um lançamento pago, pronto para imprimir.
 *
 * Um componente só porque o recibo sai de três lugares — do financeiro, da
 * agenda e da ficha do paciente — e é o mesmo papel nos três: quem recebeu,
 * de quem, quanto e por quê.
 */
export function ReceiptPanel({ receipt, onClose }: { receipt: Receipt; onClose: () => void }) {
  return (
    <div className="card finance-receipt">
      <div className="card-head">
        <div>
          <h2 className="card-title">Recibo</h2>
          <p className="card-sub">
            Emitido em {formatBr(receipt.emitidoAt)}
            {receipt.documentNumber ? ` · documento nº ${receipt.documentNumber}` : ""}
          </p>
        </div>
        <button type="button" className="button ghost pequeno" onClick={onClose}>
          <X aria-hidden="true" />
          Fechar
        </button>
      </div>

      <p className="finance-receipt-text">
        Recebi de <strong>{receipt.payerName ?? "—"}</strong> a importância de{" "}
        <strong>{currency(receipt.value)}</strong> ({receipt.valueByWords}), referente a{" "}
        <strong>{receipt.related}</strong>.
      </p>
      <p className="finance-receipt-issuer">
        {receipt.practiceName} · {receipt.profissionalName}
        {receipt.profissionalCrn ? (
          <>
            {" · "}
            <span className="readout">{receipt.profissionalCrn}</span>
          </>
        ) : null}
        <br />
        Pagamento em {formatBr(receipt.datePayment)} · Emitido em {formatBr(receipt.emitidoAt)}
        {receipt.installment ? ` · Parcela ${receipt.installment}` : ""}
      </p>

      <div className="finance-receipt-foot">
        <button type="button" className="button secundario" onClick={() => window.print()}>
          <Printer aria-hidden="true" />
          Imprimir
        </button>
      </div>
    </div>
  );
}
