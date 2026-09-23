import { useEffect, useState } from "react";

import { api } from "../api/client";
import { formatBr } from "../api/dates";
import type { Progress } from "../api/types";

/**
 * O gráfico horizontal de peso do paciente.
 *
 * "poderíamos ter diversas informações resumidas. P. ex., Um gráfico
 * horizontal das alterações de peso dele." É a primeira coisa da lista dele
 * para a consulta de retorno.
 *
 * Barras horizontais e não uma curva: com quatro ou cinco consultas, uma linha
 * sugere uma tendência que os pontos não sustentam. A barra mostra o que foi
 * medido, e o rótulo mostra a diferença — que é o que ele olha.
 *
 * Desenhado com divs e não com biblioteca de gráfico. São cinco barras; trazer
 * uma dependência de cem kilobytes para isso custaria mais do que a tela
 * inteira.
 */
export function WeightChart({ patientId }: { patientId: number }) {
  const [progress, setProgress] = useState<Progress | null>(null);

  useEffect(() => {
    api.anthropometry
      .progress(patientId)
      .then(setProgress)
      .catch(() => setProgress(null));
  }, [patientId]);

  const points = (progress?.points ?? []).filter((p) => p.weightKg !== undefined);
  if (points.length < 2) return null;

  const weights = points.map((p) => p.weightKg as number);
  const lowest = Math.min(...weights);
  const highest = Math.max(...weights);
  // Uma faixa mínima evita que uma variação de 200 g vire uma barra cheia.
  const range = Math.max(highest - lowest, 2);
  const first = weights[0] as number;

  return (
    <section className="card weight-chart mb-3">
      <div className="card-head">
        <div>
          <h2 className="card-title">Peso</h2>
          <p className="card-sub">
            {points.length} medições · da primeira até hoje,{" "}
            {formatDifference((weights[weights.length - 1] as number) - first)}
          </p>
        </div>
      </div>

      <div className="grafico-peso" role="list">
        {points.map((point) => {
          const weight = point.weightKg as number;
          const width = 12 + ((weight - lowest) / range) * 88;
          const diff = weight - first;
          const weightText = `${weight.toLocaleString("pt-BR")} kg`;
          return (
            <div
              className="linha-peso"
              key={point.assessmentId}
              role="listitem"
              aria-label={`${formatBr(point.date)}: ${weightText}${diff !== 0 ? `, ${formatDifference(diff)}` : ""}`}
            >
              <span className="data-peso">{formatBr(point.date)}</span>
              <div className="barra-peso" aria-hidden="true">
                {/* data-driven width: the only inline style on the page */}
                <div style={{ width: `${width}%` }} />
              </div>
              <span className="valor-peso">
                {weightText}
                {diff !== 0 && (
                  <span className={diff > 0 ? "delta up" : "delta down"}>
                    {formatDifference(diff)}
                  </span>
                )}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

/** A diferença com sinal, que é o que ele lê — não o valor absoluto. */
function formatDifference(value: number): string {
  if (value === 0) return "sem variação";
  const rounded = Math.round(value * 10) / 10;
  return `${rounded > 0 ? "+" : "−"}${Math.abs(rounded).toLocaleString("pt-BR")} kg`;
}
