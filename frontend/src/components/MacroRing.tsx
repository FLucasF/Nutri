import type { ReactNode } from "react";

/**
 * The macro ring: three arcs — protein, carbohydrate, fat — on a quiet track.
 *
 * It is the brand mark at 28px, the totals donut at 96px (with the kcal
 * readout at the centre) and the phone readout bar at 20px. There is no 16px
 * per-meal ring on purpose: at that size three arcs are unreadable, and the
 * meal shows the mono readout instead.
 *
 * The values are proportions, not grams: pass whatever units you have and
 * only the shares matter. With no values the ring shows the brand 25/50/25.
 * Colours come from the stylesheet (--macro-prot / --macro-carb / --macro-fat),
 * so the ring follows the theme without knowing it exists.
 */
export type MacroRingProps = {
  /** Outer size in px. */
  size?: number;
  /** Stroke width in px; by default about a ninth of the size, never under 3. */
  stroke?: number;
  protein?: number;
  carbohydrate?: number;
  fat?: number;
  /**
   * Accessible name ("Distribuição: 33% proteína, 50% carboidrato, 17% gordura").
   * Without one the ring is decorative and hidden from assistive technology.
   */
  label?: string;
  /** Centred content — the kcal readout on the 96px donut. */
  children?: ReactNode;
  className?: string;
};

/** Visual gap between two arcs, in px along the circumference. */
const GAP = 2;

type Arc = { key: "prot" | "carb" | "fat"; dash: number; offset: number };

/**
 * Turns the three shares into dash arrays.
 *
 * A round cap extends the visible stroke by half its width at each end, so
 * the dash itself is shortened by the stroke width plus the gap, and pushed
 * forward by half of that to stay centred in its slot. With a single non-zero
 * value there is nothing to separate and the arc closes the circle.
 */
function arcsOf(
  circumference: number,
  stroke: number,
  values: { key: Arc["key"]; value: number }[],
): Arc[] {
  const total = values.reduce((sum, v) => sum + Math.max(0, v.value), 0);
  if (total <= 0) return [];
  const shown = values.filter((v) => v.value > 0);
  const pad = shown.length > 1 ? GAP + stroke : 0;

  let start = 0;
  const arcs: Arc[] = [];
  for (const { key, value } of values) {
    const share = (Math.max(0, value) / total) * circumference;
    const dash = Math.max(0, share - pad);
    if (value > 0 && dash > 0) {
      arcs.push({ key, dash, offset: start + pad / 2 });
    }
    start += share;
  }
  return arcs;
}

export function MacroRing({
  size = 28,
  stroke,
  protein = 25,
  carbohydrate = 50,
  fat = 25,
  label,
  children,
  className,
}: MacroRingProps) {
  const width = stroke ?? Math.max(3, Math.round(size / 9));
  const centre = size / 2;
  const radius = (size - width) / 2;
  const circumference = 2 * Math.PI * radius;

  const arcs = arcsOf(circumference, width, [
    { key: "prot", value: protein },
    { key: "carb", value: carbohydrate },
    { key: "fat", value: fat },
  ]);

  return (
    <span className={className ? `macro-ring ${className}` : "macro-ring"}>
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        role={label ? "img" : undefined}
        aria-label={label}
        aria-hidden={label ? undefined : true}
        focusable="false"
      >
        <circle className="macro-ring-track" cx={centre} cy={centre} r={radius} fill="none" strokeWidth={width} />
        {arcs.map((arc) => (
          <circle
            key={arc.key}
            className={`macro-ring-${arc.key}`}
            cx={centre}
            cy={centre}
            r={radius}
            fill="none"
            strokeWidth={width}
            strokeLinecap="round"
            strokeDasharray={`${arc.dash} ${circumference}`}
            strokeDashoffset={-arc.offset}
            transform={`rotate(-90 ${centre} ${centre})`}
          />
        ))}
      </svg>
      {children !== undefined && children !== null && (
        <span className="macro-ring-center">{children}</span>
      )}
    </span>
  );
}
