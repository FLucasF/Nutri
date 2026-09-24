import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { formatBr } from "../api/dates";
import type { HealthyWeight, Progress, ProgressPoint } from "../api/types";

/**
 * Os gráficos da evolução, na tela da consulta.
 *
 * "Gráficos na consulta": o relatório em PDF já trazia as linhas, mas a tela
 * só tinha tabelas. Aqui cada grandeza ganha o seu gráfico — peso, IMC,
 * gordura, massa magra, cintura —, um ao lado do outro e cada um com o seu
 * eixo: grandezas de escalas diferentes no mesmo eixo mentiriam sobre qual
 * mudou mais.
 *
 * Desenhados em SVG, na largura real do cartão: um viewBox esticado
 * aumentaria o texto junto com o desenho. O valor sai escrito só no último
 * ponto; os outros aparecem ao passar o ponteiro ou ao navegar pelo teclado,
 * e a tabela logo abaixo traz todos.
 */

type Metric = {
  key: "weightKg" | "bmi" | "percentageFat" | "massLeanKg" | "waistCm";
  title: string;
  unit: string;
  /** Unidade da diferença: pontos percentuais para a gordura. */
  deltaUnit: string;
};

const METRICS: Metric[] = [
  { key: "weightKg", title: "Peso", unit: "kg", deltaUnit: "kg" },
  { key: "bmi", title: "IMC", unit: "kg/m²", deltaUnit: "kg/m²" },
  { key: "percentageFat", title: "Gordura corporal", unit: "%", deltaUnit: "p.p." },
  { key: "massLeanKg", title: "Massa magra", unit: "kg", deltaUnit: "kg" },
  { key: "waistCm", title: "Cintura", unit: "cm", deltaUnit: "cm" },
];

export function EvolutionCharts({
  progress,
  healthyWeight,
}: {
  progress: Progress;
  /** A faixa pela última altura: entra como fundo no gráfico do peso. */
  healthyWeight?: HealthyWeight;
}) {
  const charts = METRICS.map((metric) => ({
    metric,
    points: progress.points.filter((p) => valueOf(p, metric) !== undefined),
  })).filter((c) => c.points.length >= 2);

  if (charts.length === 0) return null;

  return (
    <section className="card mb-3 evolution-charts">
      <div className="card-head">
        <div>
          <h2 className="card-title">Gráficos da evolução</h2>
          <p className="card-sub">
            Cada medida no seu eixo. Passe o ponteiro, ou use as setas, para ler cada avaliação; a
            tabela abaixo traz todos os números.
          </p>
        </div>
      </div>
      <div className="evolution-grid">
        {charts.map(({ metric, points }) => (
          <LineChart
            key={metric.key}
            metric={metric}
            points={points}
            band={metric.key === "weightKg" ? healthyWeight : undefined}
          />
        ))}
      </div>
    </section>
  );
}

function valueOf(point: ProgressPoint, metric: Metric): number | undefined {
  const value = point[metric.key];
  return value === undefined || value === null ? undefined : Number(value);
}

function format(value: number, unit?: string): string {
  const text = value.toLocaleString("pt-BR", { maximumFractionDigits: 1, minimumFractionDigits: 0 });
  return unit ? (unit === "%" ? `${text}%` : `${text} ${unit}`) : text;
}

function signed(value: number, unit: string): string {
  const rounded = Math.round(value * 10) / 10;
  if (rounded === 0) return "sem variação";
  const sign = rounded > 0 ? "+" : "−";
  return `${sign}${format(Math.abs(rounded), unit)}`;
}

/** Degraus limpos para o eixo: 1, 2, 2,5 ou 5 vezes uma potência de dez. */
function niceStep(range: number, ticks: number): number {
  const raw = range / ticks;
  const power = Math.pow(10, Math.floor(Math.log10(raw)));
  const fraction = raw / power;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
  return nice * power;
}

const HEIGHT = 188;
const MARGIN = { top: 26, right: 18, bottom: 28, left: 44 };

function LineChart({
  metric,
  points,
  band,
}: {
  metric: Metric;
  points: ProgressPoint[];
  band?: HealthyWeight;
}) {
  const frame = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(320);
  const [active, setActive] = useState<number | null>(null);

  useLayoutEffect(() => {
    const element = frame.current;
    if (!element) return;
    setWidth(Math.max(240, Math.round(element.getBoundingClientRect().width)));
  }, []);

  useEffect(() => {
    const element = frame.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver((entries) => {
      const next = Math.round(entries[0]?.contentRect.width ?? 0);
      if (next > 0) setWidth(Math.max(240, next));
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const values = points.map((p) => valueOf(p, metric) as number);
  let low = Math.min(...values);
  let high = Math.max(...values);
  if (band) {
    low = Math.min(low, band.minimumKg);
    high = Math.max(high, band.maximumKg);
  }
  // Uma faixa mínima evita que uma variação de 200 g ocupe o gráfico inteiro.
  const minimumRange = Math.max(Math.abs(high) * 0.04, 1);
  if (high - low < minimumRange) {
    const middle = (high + low) / 2;
    low = middle - minimumRange / 2;
    high = middle + minimumRange / 2;
  }
  const step = niceStep(high - low, 3);
  const floor = Math.floor(low / step) * step;
  const ceiling = Math.ceil(high / step) * step;
  const ticks: number[] = [];
  for (let t = floor; t <= ceiling + step / 2; t += step) ticks.push(Math.round(t * 1000) / 1000);

  const plotLeft = MARGIN.left;
  const plotRight = width - MARGIN.right;
  const plotTop = MARGIN.top;
  const plotBottom = HEIGHT - MARGIN.bottom;
  const x = (i: number) =>
    points.length === 1 ? plotLeft : plotLeft + ((plotRight - plotLeft) * i) / (points.length - 1);
  const y = (v: number) => plotBottom - ((v - floor) / (ceiling - floor)) * (plotBottom - plotTop);

  const path = values.map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");

  const first = values[0] as number;
  const last = values[values.length - 1] as number;
  const lastIndex = values.length - 1;

  // As datas do eixo: todas quando cabem, senão a primeira e a última.
  // Cada rótulo ocupa uns 52 px; o primeiro sai alinhado à esquerda e o do
  // meio centrado, então a distância entre dois pontos tem de passar de ~90.
  const everyDate = (plotRight - plotLeft) / Math.max(1, points.length - 1) >= 92;

  function nearest(clientX: number, target: Element) {
    const box = target.getBoundingClientRect();
    const px = clientX - box.left;
    let best = 0;
    let distance = Infinity;
    for (let i = 0; i < points.length; i++) {
      const d = Math.abs(x(i) - px);
      if (d < distance) {
        distance = d;
        best = i;
      }
    }
    return best;
  }

  function onPointerMove(event: PointerEvent<SVGSVGElement>) {
    setActive(nearest(event.clientX, event.currentTarget));
  }

  function onKeyDown(event: KeyboardEvent<SVGSVGElement>) {
    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      event.preventDefault();
      const current = active ?? lastIndex;
      const next = event.key === "ArrowRight" ? Math.min(lastIndex, current + 1) : Math.max(0, current - 1);
      setActive(next);
    } else if (event.key === "Home") {
      event.preventDefault();
      setActive(0);
    } else if (event.key === "End") {
      event.preventDefault();
      setActive(lastIndex);
    } else if (event.key === "Escape") {
      setActive(null);
    }
  }

  const shown = active ?? null;
  const shownPoint = shown === null ? null : points[shown];
  const shownValue = shown === null ? null : values[shown];
  const previousValue = shown !== null && shown > 0 ? values[shown - 1] : undefined;
  const tooltipLeft = shown === null ? 0 : Math.min(Math.max(x(shown) - 80, 4), width - 164);

  const summary = `${metric.title}: de ${format(first, metric.unit)} em ${formatBr(points[0]?.date)} a ${format(
    last,
    metric.unit,
  )} em ${formatBr(points[lastIndex]?.date)}, ${points.length} avaliações.`;

  return (
    <figure className="evolution-chart">
      <figcaption className="evolution-chart-head">
        <span className="evolution-chart-title">{metric.title}</span>
        <span className="evolution-chart-delta">
          {signed(last - first, metric.deltaUnit)} desde {formatBr(points[0]?.date)}
        </span>
      </figcaption>
      <div className="evolution-chart-frame" ref={frame}>
        <svg
          width={width}
          height={HEIGHT}
          viewBox={`0 0 ${width} ${HEIGHT}`}
          role="img"
          aria-label={summary}
          tabIndex={0}
          onPointerMove={onPointerMove}
          onPointerLeave={() => setActive(null)}
          onFocus={() => setActive(lastIndex)}
          onBlur={() => setActive(null)}
          onKeyDown={onKeyDown}
        >
          {band && (
            <g className="chart-band">
              <rect
                x={plotLeft}
                y={y(band.maximumKg)}
                width={plotRight - plotLeft}
                height={Math.max(0, y(band.minimumKg) - y(band.maximumKg))}
              />
              <text x={plotLeft + 6} y={y(band.maximumKg) + 12}>
                faixa de peso saudável
              </text>
            </g>
          )}

          <g className="chart-grid">
            {ticks.map((t) => (
              <g key={t}>
                <line x1={plotLeft} x2={plotRight} y1={y(t)} y2={y(t)} />
                <text x={plotLeft - 8} y={y(t) + 4} textAnchor="end">
                  {format(t)}
                </text>
              </g>
            ))}
          </g>

          <g className="chart-axis">
            {points.map((p, i) =>
              everyDate || i === 0 || i === lastIndex ? (
                <text
                  key={p.assessmentId}
                  x={x(i)}
                  y={HEIGHT - 8}
                  textAnchor={i === 0 ? "start" : i === lastIndex ? "end" : "middle"}
                >
                  {formatBr(p.date).slice(0, 5) + "/" + p.date.slice(2, 4)}
                </text>
              ) : null,
            )}
          </g>

          {shown !== null && (
            <line className="chart-crosshair" x1={x(shown)} x2={x(shown)} y1={plotTop - 6} y2={plotBottom} />
          )}

          <path className="chart-line" d={path} />

          {values.map((v, i) => (
            <circle
              key={points[i]?.assessmentId}
              className={i === shown ? "chart-dot active" : "chart-dot"}
              cx={x(i)}
              cy={y(v)}
              r={i === shown ? 5.5 : 4}
            />
          ))}

          <text
            className="chart-end-label"
            x={x(lastIndex)}
            y={Math.max(12, y(last) - 12)}
            textAnchor="end"
          >
            {format(last, metric.unit)}
          </text>

          {/* O alvo do ponteiro é a área inteira: ninguém mira numa linha de 2px. */}
          <rect
            className="chart-hit"
            x={plotLeft - 12}
            y={0}
            width={plotRight - plotLeft + 24}
            height={HEIGHT}
          />
        </svg>

        {shownPoint && shownValue !== null && shownValue !== undefined && (
          <div className="chart-tooltip" style={{ left: tooltipLeft }} role="status">
            <strong>{format(shownValue, metric.unit)}</strong>
            <span>{formatBr(shownPoint.date)}</span>
            {previousValue !== undefined && (
              <span className="chart-tooltip-delta">
                {signed(shownValue - previousValue, metric.deltaUnit)} desde a anterior
              </span>
            )}
          </div>
        )}
      </div>
    </figure>
  );
}
