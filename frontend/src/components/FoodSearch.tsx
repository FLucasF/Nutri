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
 *
 * The suggestions are <button>s inside .results-search (the tests find them
 * that way), and they stay buttons: onMouseDown preventDefault is what lets
 * the click land before the input blurs and closes the list.
 */
export default function FoodSearch({
  freeValue,
  onFreeType,
  onChoose,
  disabled,
  /**
   * Como o campo se chama para quem lê a tela com leitor ou testa por papel.
   *
   * O substituto usa a mesma busca do item — é o mesmo catálogo, e um segundo
   * componente divergiria — mas os dois campos coexistem na mesma refeição, e
   * "Alimento" nos dois deixaria ambíguo qual é qual.
   */
  label = "Alimento",
}: {
  freeValue: string;
  onFreeType: (text: string) => void;
  onChoose: (summary: FoodSummary) => void;
  disabled: boolean;
  label?: string;
}) {
  const [results, setResults] = useState<FoodSummary[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    if (freeValue.trim().length < 2) {
      setResults([]);
      setSearching(false);
      return;
    }
    let canceled = false;
    setSearching(true);
    // 180 ms: a consulta responde em algumas dezenas de milissegundos, e a
    // espera existe só para não disparar uma por tecla. Era 280, que somado ao
    // percurso fazia a lista parecer travada entre uma letra e outra.
    const clock = setTimeout(() => {
      api.foods
        .find({ term: freeValue.trim(), size: 8 })
        .then((p) => {
          if (!canceled) {
            setResults(p.content);
            setOpen(true);
          }
        })
        .catch(() => setResults([]))
        .finally(() => {
          if (!canceled) setSearching(false);
        });
    }, 180);
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
        aria-label={label}
        autoComplete="off"
      />
      {/*
        Dizer que está procurando, em vez de não dizer nada. Sem isso, o tempo
        entre a tecla e a lista passa por travamento — e quem está usando
        clica de novo, o que só recomeça a busca.
      */}
      {open && searching && results.length === 0 && freeValue.trim().length >= 2 && (
        <div className="results-search">
          <span className="results-status">Procurando…</span>
        </div>
      )}
      {open && results.length > 0 && (
        <div className="results-search">
          {results.map((r) => {
            const meta = r.group ?? r.brand;
            return (
              <button
                key={r.id}
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  onChoose(r);
                  setOpen(false);
                }}
              >
                <span className="results-name">{r.description}</span>
                {meta && <small className="results-meta">{meta}</small>}
                {r.energyKcal !== undefined && (
                  <span className="readout">{Math.round(r.energyKcal)} kcal/100 g</span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
