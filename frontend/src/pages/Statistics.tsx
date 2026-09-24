import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { BarChart3 } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr } from "../api/dates";
import { useIsNarrow } from "../hooks/useMediaQuery";
import { count, currency } from "../text";
import type { StatisticsResponse } from "../api/types";

const SPANS = [
  { months: 3, label: "3 meses" },
  { months: 6, label: "6 meses" },
  { months: 12, label: "12 meses" },
];

/**
 * Os números do consultório.
 *
 * "Consultas por mês, retornos, pacientes sem consulta há 60 dias": o que o
 * cliente quer olhar para saber se o consultório anda. Uma tabela por mês,
 * com a barra das consultas para o olho comparar, e a lista de quem sumiu —
 * que é a lista de quem ligar.
 */
export default function Statistics() {
  const [months, setMonths] = useState(6);
  const [data, setData] = useState<StatisticsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const narrow = useIsNarrow();

  useEffect(() => {
    let alive = true;
    setError(null);
    api.statistics
      .overview(months)
      .then((d) => {
        if (alive) setData(d);
      })
      .catch((e) => {
        if (alive) setError(explainError(e, "montar as estatísticas"));
      });
    return () => {
      alive = false;
    };
  }, [months]);

  const most = data ? Math.max(1, ...data.months.map((m) => m.appointments)) : 1;

  return (
    <div className="statistics-page">
      <div className="header-page">
        <div>
          <h1>Estatísticas</h1>
          <p>
            {data
              ? `${formatBr(data.from)} a ${formatBr(data.to)} · ${count(data.activePatients, "paciente ativo", "pacientes ativos")}`
              : "Os números do consultório, mês a mês."}
          </p>
        </div>
        <div className="header-page-actions">
          <div className="segmented" role="group" aria-label="Período">
            {SPANS.map((s) => (
              <button
                key={s.months}
                type="button"
                aria-pressed={months === s.months}
                onClick={() => setMonths(s.months)}
              >
                {s.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {!data ? (
        <p className="loading">Carregando…</p>
      ) : (
        <>
          <div className="card mb-3">
            <div className="finance-stats statistics-tiles">
              <div className="finance-stat-group">
                <span className="eyebrow">Pacientes</span>
                <Tile label="Ativos" value={String(data.activePatients)} />
                <Tile label="Novos no período" value={String(data.newPatients)} />
              </div>
              <div className="finance-stat-group">
                <span className="eyebrow">Consultas</span>
                <Tile label="Marcadas" value={String(data.appointments)} />
                <Tile label="Realizadas" value={String(data.completed)} />
                <Tile label="Faltas" value={String(data.noshows)} />
              </div>
              <div className="finance-stat-group">
                <span className="eyebrow">Receita</span>
                <Tile label="Paga no período" value={currency(data.revenuePaid)} />
              </div>
            </div>
          </div>

          <div className="card mb-3">
            <div className="card-head">
              <div>
                <h2 className="card-title">Mês a mês</h2>
                <p className="card-sub">
                  Consultas pela data marcada; pacientes, avaliações e cardápios pela data de
                  cadastro; receita pela data do pagamento.
                </p>
              </div>
            </div>
            {data.months.every((m) => m.appointments === 0 && m.newPatients === 0) ? (
              <div className="empty">
                <span className="empty-icon">
                  <BarChart3 aria-hidden="true" />
                </span>
                <span className="empty-title">Nada registrado neste período.</span>
              </div>
            ) : narrow ? (
              <ul className="list-rows statistics-months">
                {data.months.map((m) => (
                  <li key={m.month} className="list-row">
                    <span className="list-row-body">
                      <span className="list-row-title">{m.label}</span>
                      <span className="stat-bar" aria-hidden="true">
                        <span style={{ width: `${(m.appointments / most) * 100}%` }} />
                      </span>
                      <span className="list-row-meta">
                        {count(m.appointments, "consulta", "consultas")} · {m.completed} realizadas ·{" "}
                        {m.noshows} faltas · {m.followups} retornos
                      </span>
                      <span className="list-row-meta">
                        {count(m.newPatients, "paciente novo", "pacientes novos")} · {m.assessments}{" "}
                        avaliações · {m.plans} cardápios
                      </span>
                    </span>
                    <span className="list-row-trail readout strong">{currency(m.revenuePaid)}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="rolagem">
                <table className="statistics-table">
                  <thead>
                    <tr>
                      <th>Mês</th>
                      <th>Consultas</th>
                      <th className="num">Realizadas</th>
                      <th className="num">Faltas</th>
                      <th className="num">Primeiras</th>
                      <th className="num">Retornos</th>
                      <th className="num">Pacientes novos</th>
                      <th className="num">Avaliações</th>
                      <th className="num">Cardápios</th>
                      <th className="num">Receita paga</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.months.map((m) => (
                      <tr key={m.month}>
                        <td>
                          <strong>{m.label}</strong>
                        </td>
                        <td>
                          <span className="stat-bar-cell">
                            <span className="stat-bar" aria-hidden="true">
                              <span style={{ width: `${(m.appointments / most) * 100}%` }} />
                            </span>
                            <span className="readout">{m.appointments}</span>
                          </span>
                        </td>
                        <td className="num">{m.completed}</td>
                        <td className="num">{m.noshows}</td>
                        <td className="num">{m.firstConsultations}</td>
                        <td className="num">{m.followups}</td>
                        <td className="num">{m.newPatients}</td>
                        <td className="num">{m.assessments}</td>
                        <td className="num">{m.plans}</td>
                        <td className="num">{currency(m.revenuePaid)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-head">
              <div>
                <h2 className="card-title">Sem consulta há mais de {data.inactivityDays} dias</h2>
                <p className="card-sub">
                  Pacientes ativos cuja última consulta realizada ficou para trás — ou que foram
                  cadastrados e nunca vieram.
                </p>
              </div>
            </div>
            {data.withoutVisit.length === 0 ? (
              <p className="empty-hint">Ninguém: todos os pacientes ativos passaram por aqui nos últimos {data.inactivityDays} dias.</p>
            ) : (
              <ul className="list-rows">
                {data.withoutVisit.map((p) => (
                  <li key={p.id} className="list-row">
                    <Link to={`/patients/${p.id}`} className="list-row-body">
                      <span className="list-row-title">{p.name}</span>
                      <span className="list-row-meta">
                        {p.lastVisit
                          ? `última consulta em ${formatBr(p.lastVisit)}`
                          : "nunca teve consulta realizada"}
                      </span>
                    </Link>
                    <span className="list-row-trail readout">{count(p.daysSince, "dia", "dias")}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
    </div>
  );
}
