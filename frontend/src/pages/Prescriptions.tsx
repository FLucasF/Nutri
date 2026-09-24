import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ErrorApi, api } from "../api/client";
import type { PlanSummary } from "../api/types";
import { TagStatus } from "./PatientDetail";
import { Pagination } from "../components/Pagination";

const PAGE_SIZE = 30;

type Tab = "plans" | "templates";

export default function Prescriptions() {
  const navigate = useNavigate();

  const [tab, setTab] = useState<Tab>("plans");
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [term, setTerm] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.prescriptions.list({
        template: tab === "templates",
        term: term.trim() || undefined,
        page,
        size: PAGE_SIZE,
      });
      setPlans(result.content);
      setTotalPages(result.totalPages);
      setTotal(result.totalElements);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : "Falha ao carregar as prescrições.");
    } finally {
      setLoading(false);
    }
  }, [tab, term, page]);

  // Another tab or a new search starts from the first page.
  useEffect(() => {
    setPage(0);
  }, [tab, term]);

  useEffect(() => {
    const clock = setTimeout(load, term ? 300 : 0);
    return () => clearTimeout(clock);
  }, [load, term]);

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Prescrições</h1>
          <p>Planos alimentares e modelos reaproveitáveis.</p>
        </div>
        <button className="button" onClick={() => navigate("/prescriptions/new")}>
          Novo plano
        </button>
      </div>

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="row">
          <div className="row" style={{ gap: "0.3rem" }}>
            <button
              className={`button ${tab === "plans" ? "" : "secundario"} pequeno`}
              onClick={() => setTab("plans")}
            >
              Planos
            </button>
            <button
              className={`button ${tab === "templates" ? "" : "secundario"} pequeno`}
              onClick={() => setTab("templates")}
            >
              Modelos
            </button>
          </div>
          <div className="field" style={{ flex: 1, minWidth: 200 }}>
            <input
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Buscar pelo título do plano"
              aria-label="Buscar plano"
            />
          </div>
        </div>
      </div>

      {error && <div className="warning error" style={{ marginBottom: "0.9rem" }}>{error}</div>}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : plans.length === 0 ? (
        <div className="card empty">
          {tab === "templates"
            ? "Nenhum modelo salvo. Marque um plano como modelo para reaproveitá-lo em outro paciente."
            : "Nenhuma prescrição ainda. Use Nova prescrição para montar o primeiro plano."}
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Plano</th>
                {tab === "plans" && <th>Paciente</th>}
                <th>Situação</th>
                <th className="num">Itens</th>
                <th className="num">kcal/dia</th>
              </tr>
            </thead>
            <tbody>
              {plans.map((plan) => (
                <tr
                  key={plan.id}
                  className="clicavel"
                  onClick={() => navigate(`/prescriptions/${plan.id}`)}
                >
                  <td>
                    <strong>{plan.title}</strong>
                    <div className="minusculo">
                      {plan.meals} {plan.meals === 1 ? "refeição" : "refeições"}
                    </div>
                  </td>
                  {tab === "plans" && <td className="discreto">{plan.patientName ?? "—"}</td>}
                  <td>
                    {plan.template ? (
                      <span className="tag">modelo</span>
                    ) : (
                      <TagStatus status={plan.status} />
                    )}
                  </td>
                  <td className="num">{plan.items}</td>
                  <td className="num">{plan.energyKcal ? Math.round(plan.energyKcal) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!loading && (
        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={total}
          size={PAGE_SIZE}
          noun={tab === "templates" ? ["modelo", "modelos"] : ["plano", "planos"]}
          onChange={setPage}
        />
      )}
    </>
  );
}
