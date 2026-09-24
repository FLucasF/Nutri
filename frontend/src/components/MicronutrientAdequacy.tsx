import { useEffect, useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { api } from "../api/client";
import type { AdequacyResponse, AdequacyRow, AdequacyStatus } from "../api/types";

/**
 * Os micronutrientes do dia contra as DRI do paciente, no trilho do editor.
 *
 * "Micronutrientes × DRI": a barra mostra quanto da referência o cardápio
 * entrega, com a faixa de 80 a 120% marcada no trilho. A cor diz o estado,
 * e o estado vem sempre escrito ao lado — cor sozinha não carrega
 * informação. O sódio se lê ao contrário: a referência é um limite.
 *
 * Recarrega quando o total salvo muda: é o mesmo número que os totais do dia
 * mostram, e não uma conta à parte com o que ainda não foi salvo.
 */

const STATUS_TEXT: Record<AdequacyStatus, string> = {
  BELOW: "abaixo",
  ADEQUATE: "adequado",
  ABOVE: "acima",
  WITHIN_LIMIT: "dentro do limite",
  ABOVE_LIMIT: "acima do limite",
  NO_DATA: "sem dado",
};

/** Até onde a barra vai: 150% da referência ocupa o trilho inteiro. */
const SCALE_MAX = 150;

export function MicronutrientAdequacy({ planId, version }: { planId: number; version: unknown }) {
  const [data, setData] = useState<AdequacyResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    api.prescriptions
      .adequacy(planId)
      .then((d) => {
        if (alive) {
          setData(d);
          setFailed(false);
        }
      })
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [planId, version]);

  const below = data?.rows.filter((r) => r.status === "BELOW" || r.status === "ABOVE_LIMIT").length ?? 0;

  return (
    <div className="card adequacy-card">
      <button
        type="button"
        className="adequacy-toggle"
        aria-expanded={open}
        aria-controls={`adequacy-${planId}`}
        onClick={() => setOpen((v) => !v)}
      >
        <span>
          <span className="card-title">Micronutrientes × DRI</span>
          <span className="adequacy-summary">
            {failed
              ? "Não foi possível calcular agora."
              : !data
                ? "Calculando…"
                : !data.available
                  ? "Sem comparação para este plano."
                  : below === 0
                    ? `${data.reference}: nada abaixo da referência.`
                    : `${data.reference}: ${below} ${below === 1 ? "pede" : "pedem"} atenção.`}
          </span>
        </span>
        {open ? <ChevronUp aria-hidden="true" /> : <ChevronDown aria-hidden="true" />}
      </button>

      {open && data && (
        <div className="adequacy-body" id={`adequacy-${planId}`}>
          {!data.available ? (
            <p className="discreto adequacy-unavailable">{data.unavailableBecause}</p>
          ) : (
            <>
              <ul className="adequacy-list">
                {data.rows.map((row) => (
                  <Row key={row.nutrient} row={row} />
                ))}
              </ul>
              <p className="minusculo adequacy-note">
                RDA ou AI das DRI (IOM/NASEM) para {data.reference?.toLowerCase()}; sódio pelo limite
                de 2019. A faixa marcada vai de 80 a 120% da referência; acima dela não é excesso,
                que as DRI medem pelo limite superior (UL). Um asterisco indica total parcial: nem
                todos os alimentos informaram o nutriente.
              </p>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function Row({ row }: { row: AdequacyRow }) {
  const percent = row.percent ?? 0;
  const width = Math.min(percent, SCALE_MAX) / SCALE_MAX * 100;
  const format = (value: number) =>
    value.toLocaleString("pt-BR", { maximumFractionDigits: value < 10 ? 2 : 0 });
  const tone =
    row.status === "ADEQUATE" || row.status === "WITHIN_LIMIT"
      ? "ok"
      : row.status === "ABOVE_LIMIT"
        ? "danger"
        : row.status === "NO_DATA"
          ? "none"
          : row.status === "ABOVE"
            ? "high"
            : "warn";

  return (
    <li className={`adequacy-row ${tone}`}>
      <div className="adequacy-row-head">
        <span className="adequacy-label">
          {row.label}
          {row.incomplete && (
            <span className="adequacy-partial" title="Total parcial: nem todos os alimentos informaram">
              *
            </span>
          )}
        </span>
        <span className="adequacy-value">
          {row.percent === undefined || row.percent === null ? "—" : `${row.percent}%`}
          <span className="adequacy-state"> · {STATUS_TEXT[row.status]}</span>
        </span>
      </div>
      <div
        className={`adequacy-track${row.kind === "LIMIT" ? " limit" : ""}`}
        role="img"
        aria-label={`${row.label}: ${row.percent ?? 0}% da referência, ${STATUS_TEXT[row.status]}`}
      >
        {row.kind !== "LIMIT" && <span className="adequacy-band" aria-hidden="true" />}
        <span className="adequacy-fill" style={{ width: `${width}%` }} aria-hidden="true" />
      </div>
      <span className="adequacy-readout">
        {row.intake === undefined || row.intake === null ? "sem dado" : `${format(row.intake)} ${row.unit}`} de{" "}
        {format(row.reference)} {row.unit} ({row.kind === "LIMIT" ? "limite" : row.kindDescription})
      </span>
    </li>
  );
}
