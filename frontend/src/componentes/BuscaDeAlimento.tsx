import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { AlimentoResumo } from "../api/types";

/**
 * Busca de alimento com sugestão enquanto se digita.
 *
 * Vive fora das páginas porque duas telas a usam — o editor de plano e o de
 * receita — e uma cópia divergiria: a busca já carrega o ranqueamento por
 * relevância e o atraso que evita uma consulta por tecla, e nenhuma das duas
 * telas deveria ter de saber disso.
 */
export default function BuscaDeAlimento({
  valorLivre,
  aoDigitarLivre,
  aoEscolher,
  desabilitado,
}: {
  valorLivre: string;
  aoDigitarLivre: (texto: string) => void;
  aoEscolher: (resumo: AlimentoResumo) => void;
  desabilitado: boolean;
}) {
  const [resultados, setResultados] = useState<AlimentoResumo[]>([]);
  const [aberto, setAberto] = useState(false);

  useEffect(() => {
    if (valorLivre.trim().length < 2) {
      setResultados([]);
      return;
    }
    let cancelado = false;
    const relogio = setTimeout(() => {
      api.alimentos
        .buscar({ termo: valorLivre.trim(), size: 8 })
        .then((p) => {
          if (!cancelado) {
            setResultados(p.content);
            setAberto(true);
          }
        })
        .catch(() => setResultados([]));
    }, 280);
    return () => {
      cancelado = true;
      clearTimeout(relogio);
    };
  }, [valorLivre]);

  return (
    <div className="seletor-alimento">
      <input
        value={valorLivre}
        onChange={(e) => aoDigitarLivre(e.target.value)}
        onFocus={() => setAberto(true)}
        onBlur={() => setTimeout(() => setAberto(false), 150)}
        placeholder="Buscar alimento ou escrever livremente"
        disabled={desabilitado}
        aria-label="Alimento"
      />
      {aberto && resultados.length > 0 && (
        <div className="resultados-busca">
          {resultados.map((r) => (
            <button
              key={r.id}
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                aoEscolher(r);
                setAberto(false);
              }}
            >
              {r.descricao}
              <small>
                {r.grupo ?? r.marca ?? "—"}
                {r.energiaKcal !== undefined ? ` · ${Math.round(r.energiaKcal)} kcal/100 g` : ""}
              </small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
