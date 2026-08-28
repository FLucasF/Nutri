import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { FoodSummary } from "../api/types";

/**
 * Food search with suggestions as you type.
 *
 * It lives outside the pages because two screens use it — the plan editor and
 * the recipe editor — and a copy would diverge: the search already carries the
 * relevance ranking and the delay that avoids one query per keystroke, and
 * neither of the two screens should have to know about that.
 */
export default function FoodSearch({
  freeValue,
  onFreeType,
  onChoose,
  disabled,
}: {
  freeValue: string;
  onFreeType: (text: string) => void;
  onChoose: (summary: FoodSummary) => void;
  disabled: boolean;
}) {
  const [results, setResults] = useState<FoodSummary[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (freeValue.trim().length < 2) {
      setResults([]);
      return;
    }
    let canceled = false;
    const clock = setTimeout(() => {
      api.foods
        .find({ term: freeValue.trim(), size: 8 })
        .then((p) => {
          if (!canceled) {
            setResults(p.content);
            setOpen(true);
          }
        })
        .catch(() => setResults([]));
    }, 280);
    return () => {
      canceled = true;
      clearTimeout(clock);
    };
  }, [freeValue]);

  return (
    <div className="picker-food">
      <input
        value={freeValue}
        onChange={(e) => onFreeType(e.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder="Buscar alimento ou escrever livremente"
        disabled={disabled}
        aria-label="Alimento"
      />
      {open && results.length > 0 && (
        <div className="results-search">
          {results.map((r) => (
            <button
              key={r.id}
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                onChoose(r);
                setOpen(false);
              }}
            >
              {r.description}
              <small>
                {r.group ?? r.brand ?? "—"}
                {r.energyKcal !== undefined ? ` · ${Math.round(r.energyKcal)} kcal/100 g` : ""}
              </small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
