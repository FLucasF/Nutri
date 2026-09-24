import { ChevronLeft, ChevronRight } from "lucide-react";

/**
 * A navegação entre as páginas de uma lista que o servidor pagina.
 *
 * As listas vinham só com a primeira página: pacientes paravam no
 * quinquagésimo, alimentos nos primeiros 40 de um acervo de 24 mil, e o
 * resto não aparecia em lugar nenhum. Aqui a lista diz quantos há, quais está
 * mostrando, e anda para a frente e para trás. Com uma página só, some.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  noun,
  onChange,
}: {
  /** A página atual, a partir de zero, como o servidor conta. */
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  /** Singular e plural do que se lista: ["paciente", "pacientes"]. */
  noun: [string, string];
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  const first = page * size + 1;
  const last = Math.min(totalElements, (page + 1) * size);
  const format = (n: number) => n.toLocaleString("pt-BR");

  function go(target: number) {
    onChange(target);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  return (
    <nav className="pagination" aria-label={`Páginas de ${noun[1]}`}>
      <span className="pagination-status" aria-live="polite">
        {format(first)}–{format(last)} de {format(totalElements)}{" "}
        {totalElements === 1 ? noun[0] : noun[1]}
        <span className="pagination-page"> · página {format(page + 1)} de {format(totalPages)}</span>
      </span>
      <div className="pagination-buttons">
        <button
          type="button"
          className="button secundario pequeno"
          onClick={() => go(page - 1)}
          disabled={page <= 0}
        >
          <ChevronLeft aria-hidden="true" />
          Anterior
        </button>
        <button
          type="button"
          className="button secundario pequeno"
          onClick={() => go(page + 1)}
          disabled={page >= totalPages - 1}
        >
          Próxima
          <ChevronRight aria-hidden="true" />
        </button>
      </div>
    </nav>
  );
}
