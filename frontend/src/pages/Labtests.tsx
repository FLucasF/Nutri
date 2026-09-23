import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import {
  BookmarkPlus,
  ChevronLeft,
  CircleCheck,
  ClipboardList,
  FileText,
  FlaskConical,
  Paperclip,
  Plus,
  Trash2,
  X,
} from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../components/Feedback";
import { formatBr, todayIso } from "../api/dates";
import type {
  LabtestResult,
  LabtestPanel,
  LabtestParameter,
  Patient,
  LabtestSeries,
  LabtestOrder,
  LabtestClassification,
} from "../api/types";
import { count } from "../text";
import { Own } from "../components/Own";
import { useIsNarrow } from "../hooks/useMediaQuery";

/**
 * Compara ignorando acento e caixa.
 *
 * "Hemoglobina glicada" tem de ser achado por "glicada" e por "HEMOGLOBINA".
 * Quem digita numa consulta não vai acertar o acento, e não deveria precisar.
 */
function matches(text: string | undefined, term: string): boolean {
  if (!term.trim()) return true;
  if (!text) return false;
  const normalize = (value: string) =>
    value
      .normalize("NFD")
      .replace(/\p{Diacritic}/gu, "")
      .toLowerCase();
  return normalize(text).includes(normalize(term));
}

/** Tag color according to the position against the reference. */
const CLASSE_BY_CLASSIFICATION: Record<string, string> = {
  BELOW: "ambar",
  NORMAL: "verde",
  ABOVE: "vermelha",
};

/** The short word for the series table, where the column already says "against the reference". */
const SHORT_BY_CLASSIFICATION: Record<LabtestClassification, string> = {
  BELOW: "Abaixo",
  NORMAL: "Normal",
  ABOVE: "Acima",
};

function formatValue(value: number | undefined, unit: string): ReactNode {
  if (value === undefined) return <span className="minusculo">não determinado</span>;
  return (
    <span className="labtest-value">
      {value.toLocaleString("pt-BR")} <span className="labtest-unit">{unit}</span>
    </span>
  );
}

/**
 * A numeric table that scrolls sideways with its first column pinned
 * (.table-scroll). The right-edge fade goes away once the table is scrolled
 * to its end — or when there was never anything to scroll to.
 */
function ScrollTable({ className, children }: { className?: string; children: ReactNode }) {
  const box = useRef<HTMLDivElement>(null);
  const [atEnd, setAtEnd] = useState(true);

  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const check = () => setAtEnd(el.scrollLeft + el.clientWidth >= el.scrollWidth - 1);
    check();
    el.addEventListener("scroll", check, { passive: true });
    const observer = new ResizeObserver(check);
    observer.observe(el);
    return () => {
      el.removeEventListener("scroll", check);
      observer.disconnect();
    };
  }, []);

  // Rows come and go with the filter; the width they need changes with them.
  useEffect(() => {
    const el = box.current;
    if (el) setAtEnd(el.scrollLeft + el.clientWidth >= el.scrollWidth - 1);
  });

  const classes = ["table-scroll", className].filter(Boolean).join(" ");
  return (
    <div ref={box} className={classes} data-scroll-end={atEnd ? "true" : "false"}>
      {children}
    </div>
  );
}

/**
 * The patient's lab tests.
 *
 * The screen shows the reference range **used at entry** next to each result,
 * and not the one registered today. It is what makes it possible to read a test
 * from two years ago knowing what it was classified against — a reference range
 * depends on the laboratory's method and changes over time.
 */
export default function Labtests() {
  const feedback = useFeedback();
  const { id } = useParams();
  const patientId = Number(id);
  const narrow = useIsNarrow();

  const [patient, setPatient] = useState<Patient | null>(null);
  const [labtests, setLabtests] = useState<LabtestResult[]>([]);
  const [parameters, setParameters] = useState<LabtestParameter[]>([]);
  const [requests, setRequests] = useState<LabtestOrder[]>([]);
  const [series, setSeries] = useState<LabtestSeries | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [panel, setPanel] = useState<"none" | "entry" | "request">("none");
  /** Filtro do histórico: nome do exame, grupo ou observação. */
  const [term, setTerm] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, e, s] = await Promise.all([
        api.patients.find(patientId),
        api.labtests.forPatient(patientId),
        api.labtests.requests(patientId),
      ]);
      setPatient(p);
      setLabtests(e);
      setRequests(s);
    } catch (e) {
      setError(explainError(e, "abrir os exames"));
    } finally {
      setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    void load();
    api.labtests.parameters().then(setParameters).catch(() => setParameters([]));
  }, [load]);

  async function openSeries(parameterId: number) {
    try {
      setSeries(await api.labtests.series(patientId, parameterId));
    } catch {
      setSeries(null);
    }
  }

  async function attachReport(labtestId: number, file: File) {
    try {
      await api.labtests.attachReport(labtestId, file);
      feedback.confirm("Laudo anexado ao resultado.");
      await load();
    } catch (e) {
      setError(explainError(e, "anexar o laudo"));
    }
  }

  async function openReport(labtestId: number) {
    try {
      const { url } = await api.labtests.report(labtestId);
      window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      setError("Não foi possível abrir o laudo.");
    }
  }

  async function remove(labtest: LabtestResult) {
    if (!confirm(`Remover ${labtest.parameter} de ${formatBr(labtest.dateCollection)}?`)) return;
    try {
      await api.labtests.remove(labtest.id);
      feedback.confirm(`${labtest.parameter} de ${formatBr(labtest.dateCollection)} removido.`);
      await load();
    } catch (e) {
      setError(explainError(e, "remover o exame"));
    }
  }

  const changed = labtests.filter((e) => e.classification && e.classification !== "NORMAL");
  /** O histórico já filtrado pelo que foi digitado na busca. */
  const shown = labtests.filter(
    (e) => matches(e.parameter, term) || matches(e.group, term) || matches(e.notes, term),
  );

  /** The name opens the series; the two buttons act on the row. Same in the table and in the cards. */
  const nameOf = (e: LabtestResult) => (
    <button
      type="button"
      className="link labtest-name"
      title="Ver a evolução deste exame"
      onClick={() => openSeries(e.parameterId)}
    >
      <strong>{e.parameter}</strong>
    </button>
  );
  const actionsOf = (e: LabtestResult) => (
    <div className="labtest-actions">
      <ButtonReport labtest={e} onAttach={(file) => attachReport(e.id, file)} onOpen={() => openReport(e.id)} />
      <button type="button" className="button perigo pequeno" onClick={() => remove(e)}>
        <Trash2 aria-hidden="true" />
        Remover
      </button>
    </div>
  );
  const tagOf = (e: LabtestResult) =>
    e.classification ? (
      <span className={`tag ${CLASSE_BY_CLASSIFICATION[e.classification] ?? ""}`}>
        {e.classificationDescription}
      </span>
    ) : null;

  return (
    <>
      <div className="header-page">
        <div>
          <Link to={`/patients/${patientId}`} className="migalha">
            <ChevronLeft aria-hidden="true" />
            {patient?.name ?? "Paciente"}
          </Link>
          <h1>Exames</h1>
          <p>
            {count(labtests.length, "resultado", "resultados")}
            {changed.length > 0 && (
              <>
                {" · "}
                <span className="labtest-off">{changed.length} fora da referência</span>
              </>
            )}
          </p>
        </div>
        <div className="header-page-actions">
          <button
            type="button"
            className="button secundario"
            aria-expanded={panel === "request"}
            onClick={() => setPanel(panel === "request" ? "none" : "request")}
          >
            <ClipboardList aria-hidden="true" />
            Solicitar exames
          </button>
          <button
            type="button"
            className="button"
            aria-expanded={panel === "entry"}
            onClick={() => setPanel(panel === "entry" ? "none" : "entry")}
          >
            <Plus aria-hidden="true" />
            Registrar resultado
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {panel === "entry" && (
        <FormResult
          patientId={patientId}
          parameters={parameters}
          onClose={() => setPanel("none")}
          onSave={async () => {
            setPanel("none");
            await load();
          }}
        />
      )}

      {panel === "request" && (
        <FormOrder
          patientId={patientId}
          parameters={parameters}
          onClose={() => setPanel("none")}
          onSave={async () => {
            setPanel("none");
            await load();
          }}
        />
      )}

      {series && <PanelSeries series={series} onClose={() => setSeries(null)} />}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : labtests.length === 0 ? (
        <div className="card empty">
          <span className="empty-icon">
            <FlaskConical aria-hidden="true" />
          </span>
          <span className="empty-title">Nenhum exame registrado.</span>
          <span className="empty-hint">
            Use <strong>Registrar resultado</strong> quando o laudo chegar.
          </span>
        </div>
      ) : (
        <>
          <div className="field labtest-search mb-3">
            <label htmlFor="ex-busca">Buscar exame</label>
            <div className="input-search">
              <input
                id="ex-busca"
                type="search"
                value={term}
                onChange={(e) => setTerm(e.target.value)}
                placeholder="Glicose, TGP, vitamina D…"
              />
            </div>
          </div>

          {shown.length === 0 ? (
            <div className="card empty">Nenhum exame com “{term}”.</div>
          ) : narrow ? (
            <ul className="labtest-cards">
              {shown.map((e) => (
                <li className="card labtest-card" key={e.id}>
                  <div className="labtest-card-top">
                    <div className="labtest-card-title">
                      {nameOf(e)}
                      {e.group && <div className="minusculo">{e.group}</div>}
                    </div>
                    <span className="mono minusculo nowrap">{formatBr(e.dateCollection)}</span>
                  </div>
                  <div className="labtest-card-result">
                    {formatValue(e.value, e.unit)}
                    {tagOf(e)}
                  </div>
                  <div className="labtest-card-ref">
                    <span className="minusculo">Referência usada</span>
                    <span className="discreto">{e.reference ?? "—"}</span>
                  </div>
                  {e.notes && <div className="minusculo">{e.notes}</div>}
                  {actionsOf(e)}
                </li>
              ))}
            </ul>
          ) : (
            <ScrollTable className="rolagem">
              <table className="labtest-table">
                <thead>
                  <tr>
                    <th>Exame</th>
                    <th>Coleta</th>
                    <th className="num">Resultado</th>
                    <th>Referência usada</th>
                    <th className="text-right">
                      <span className="visually-hidden">Ações</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {shown.map((e) => (
                    <tr key={e.id}>
                      <td>
                        {nameOf(e)}
                        {e.group && <div className="minusculo">{e.group}</div>}
                        {e.notes && <div className="minusculo">{e.notes}</div>}
                      </td>
                      <td className="mono nowrap">{formatBr(e.dateCollection)}</td>
                      <td className="num">
                        <span className="labtest-result">
                          {formatValue(e.value, e.unit)}
                          {e.value !== undefined && tagOf(e)}
                        </span>
                      </td>
                      <td className="discreto">{e.reference ?? "—"}</td>
                      <td className="labtest-actions-cell">{actionsOf(e)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </ScrollTable>
          )}
        </>
      )}

      {requests.length > 0 && (
        <section className="card mt-3" aria-labelledby="labtest-orders-title">
          <div className="card-head">
            <div>
              <h2 className="card-title" id="labtest-orders-title">
                Solicitações
              </h2>
              <p className="card-sub">{count(requests.length, "pedido entregue", "pedidos entregues")}</p>
            </div>
          </div>
          <ul className="labtest-orders">
            {requests.map((s) => (
              <li className="labtest-order" key={s.id}>
                <span className="mono labtest-order-date">{formatBr(s.date)}</span>
                <div className="labtest-order-body">
                  <span className="discreto">{s.labtests.join(", ")}</span>
                  {s.notes && <div className="minusculo">{s.notes}</div>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

function ButtonReport({
  labtest,
  onAttach,
  onOpen,
}: {
  labtest: LabtestResult;
  onAttach: (file: File) => void;
  onOpen: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);

  return (
    <>
      {labtest.hasReport ? (
        <button type="button" className="button secundario pequeno" title={labtest.reportName} onClick={onOpen}>
          <FileText aria-hidden="true" />
          Abrir
        </button>
      ) : (
        <button type="button" className="button secundario pequeno" onClick={() => input.current?.click()}>
          <Paperclip aria-hidden="true" />
          Anexar
        </button>
      )}
      {/* Out of sight and out of the tab order: the button above is the control. */}
      <input
        ref={input}
        type="file"
        accept=".pdf,image/*"
        className="visually-hidden"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onAttach(file);
          e.target.value = "";
        }}
      />
    </>
  );
}

function PanelSeries({ series, onClose }: { series: LabtestSeries; onClose: () => void }) {
  return (
    <section className="card mb-3" aria-labelledby="labtest-series-title">
      <div className="card-head">
        <div>
          <h2 className="card-title" id="labtest-series-title">
            {series.parameter} ao longo do tempo
          </h2>
          <p className="card-sub">
            {count(series.points.length, "coleta", "coletas")}
            {series.unit ? ` · ${series.unit}` : ""}
          </p>
        </div>
        <button type="button" className="button secundario pequeno" onClick={onClose}>
          <X aria-hidden="true" />
          Fechar
        </button>
      </div>

      {series.unitsMixed && (
        <div className="warning attention mb-3">
          Esta série tem coletas em unidades diferentes. Os valores não são comparáveis entre si, e
          a variação não é calculada entre eles.
        </div>
      )}

      <ScrollTable className="labtest-series">
        <table>
          <thead>
            <tr>
              <th>Coleta</th>
              <th className="num">Resultado</th>
              <th>Referência</th>
              <th className="num" title="Variação desde a coleta anterior">
                Desde a anterior
              </th>
            </tr>
          </thead>
          <tbody>
            {series.points.map((p, i) => (
              <tr key={i}>
                <td className="mono nowrap">{formatBr(p.dateCollection)}</td>
                <td className="num">{formatValue(p.value, p.unit)}</td>
                <td>
                  {p.classification && (
                    <span className={`tag ${CLASSE_BY_CLASSIFICATION[p.classification] ?? ""}`}>
                      {SHORT_BY_CLASSIFICATION[p.classification]}
                    </span>
                  )}
                </td>
                <td className="num labtest-change">
                  {p.change === undefined ? (
                    <span className="minusculo">—</span>
                  ) : (
                    <>
                      {p.change > 0 ? "+" : ""}
                      {p.change.toLocaleString("pt-BR")}
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </ScrollTable>
    </section>
  );
}

function FormResult({
  patientId,
  parameters,
  onClose,
  onSave,
}: {
  patientId: number;
  parameters: LabtestParameter[];
  onClose: () => void;
  onSave: () => Promise<void>;
}) {
  const [parameterId, setParameterId] = useState("");
  /** Filtra a lista de exames; não escolhe nenhum por si. */
  const [filter, setFilter] = useState("");
  const [dateCollection, setDateCollection] = useState(todayIso());
  const [value, setValue] = useState("");
  const [unit, setUnit] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const chosen = parameters.find((p) => String(p.id) === parameterId);

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSaving(true);
    try {
      await api.labtests.entry(patientId, {
        parameterId: Number(parameterId),
        dateCollection,
        value: value ? Number(value.replace(",", ".")) : undefined,
        unit: unit.trim() || undefined,
        notes: notes.trim() || undefined,
      });
      await onSave();
    } catch (e) {
      setError(explainError(e, "registrar o resultado"));
      setSaving(false);
    }
  }

  return (
    <form className="card labtest-form mb-3" onSubmit={send} aria-labelledby="labtest-entry-title">
      <div className="card-head">
        <h2 className="card-title" id="labtest-entry-title">
          Registrar resultado
        </h2>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      <div className="labtest-form-grid">
        <div className="field lt-span-3">
          <label htmlFor="ex-filtro">Buscar exame</label>
          {/*
            O filtro fica antes da lista em vez de substituí-la: a lista
            continua sendo um select nativo, que no celular abre a roda do
            sistema e responde ao teclado sem que a gente reimplemente nada.
          */}
          <div className="input-search">
            <input
              id="ex-filtro"
              type="search"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Glicose, TGP, vitamina D…"
            />
          </div>
        </div>
        <div className="field lt-span-3">
          <label htmlFor="ex-parametro">Exame</label>
          <select
            id="ex-parametro"
            value={parameterId}
            onChange={(e) => {
              setParameterId(e.target.value);
              setUnit("");
            }}
            required
          >
            <option value="">Selecione…</option>
            {parameters
              .filter((p) => matches(p.name, filter) || matches(p.group, filter))
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.group ? `${p.group} · ` : ""}
                  {p.name}
                </option>
              ))}
          </select>
          {chosen && chosen.ranges.length > 0 && (
            <span className="field-hint">
              Referência: {chosen.ranges.map((f) => f.text).join(" · ")} {chosen.unitStandard}
            </span>
          )}
        </div>
        <div className="field lt-span-2">
          <label htmlFor="ex-data">Data da coleta</label>
          <input
            id="ex-data"
            type="date"
            value={dateCollection}
            max={todayIso()}
            onChange={(e) => setDateCollection(e.target.value)}
            required
          />
        </div>
        <div className="field lt-span-2">
          <label htmlFor="ex-valor">Resultado</label>
          <input
            id="ex-valor"
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={chosen?.unitStandard}
          />
          <span className="field-hint">Deixe vazio se o exame foi pedido e ainda não saiu.</span>
        </div>
        <div className="field lt-span-2">
          <label htmlFor="ex-unidade">Unidade</label>
          <input
            id="ex-unidade"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
            placeholder={chosen?.unitStandard ?? ""}
          />
          <span className="field-hint">Só se o laudo usar outra. Nesse caso o valor não é classificado.</span>
        </div>
        <div className="field lt-span-6">
          <label htmlFor="ex-obs">Observação</label>
          <input id="ex-obs" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
      </div>

      <div className="labtest-form-actions">
        <button type="button" className="button secundario" onClick={onClose}>
          Cancelar
        </button>
        <button className="button" type="submit" disabled={saving}>
          {saving ? "Salvando…" : "Registrar"}
        </button>
      </div>
    </form>
  );
}

function FormOrder({
  patientId,
  parameters,
  onClose,
  onSave,
}: {
  patientId: number;
  parameters: LabtestParameter[];
  onClose: () => void;
  onSave: () => Promise<void>;
}) {
  const [chosen, setChosen] = useState<number[]>([]);
  const [panels, setPanels] = useState<LabtestPanel[]>([]);
  const [savingPanel, setSavingPanel] = useState(false);
  /** Filtra a lista de exames do pedido, sem mexer no que está marcado. */
  const [filter, setFilter] = useState("");
  const [date, setDate] = useState(todayIso());
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.labtests
      .panels()
      .then(setPanels)
      .catch(() => setPanels([]));
  }, []);

  /**
   * Um clique no painel acrescenta os parâmetros dele ao pedido.
   *
   * Acrescenta, nunca substitui: ele descreve juntar painéis — "caso ele
   * queira selecionar alguns como eu fiz nos de cima e formular um seu". E o
   * que já estava marcado é escolha dele, não rascunho.
   */
  function applyPanel(panel: LabtestPanel) {
    setChosen((current) => {
      const next = [...current];
      panel.parameters.forEach((p) => {
        if (!next.includes(p.id)) next.push(p.id);
      });
      return next;
    });
  }

  /** Guarda o que está marcado como um painel do consultório. */
  async function savePanel() {
    if (chosen.length === 0) {
      setError("Marque os exames antes de salvar como painel.");
      return;
    }
    const name = window.prompt("Nome do painel:", "Primeira consulta");
    if (!name?.trim()) return;
    setSavingPanel(true);
    try {
      await api.labtests.createPanel({ name: name.trim(), parameterIds: chosen });
      setPanels(await api.labtests.panels());
    } catch (e) {
      setError(explainError(e, "salvar o painel"));
    } finally {
      setSavingPanel(false);
    }
  }

  function toggle(id: number) {
    setChosen((current) =>
      current.includes(id) ? current.filter((x) => x !== id) : [...current, id],
    );
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    if (chosen.length === 0) {
      setError("Escolha ao menos um exame.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await api.labtests.request(patientId, {
        date,
        parameterIds: chosen,
        notes: notes.trim() || undefined,
      });
      await onSave();
    } catch (e) {
      setError(explainError(e, "registrar a solicitação"));
      setSaving(false);
    }
  }

  const byGroup = new Map<string, LabtestParameter[]>();
  parameters
    .filter((p) => matches(p.name, filter) || matches(p.group, filter))
    .forEach((p) => {
      const key = p.group ?? "Outros";
      byGroup.set(key, [...(byGroup.get(key) ?? []), p]);
    });

  return (
    <form className="card labtest-form mb-3" onSubmit={send} aria-labelledby="labtest-order-title">
      <div className="card-head">
        <div>
          <h2 className="card-title" id="labtest-order-title">
            Solicitar exames
          </h2>
          <p className="card-sub">
            Registra o pedido entregue ao paciente. Os resultados são lançados quando o laudo chegar.
          </p>
        </div>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      {panels.length > 0 && (
        <div className="paineis">
          <div className="paineis-head">
            <span className="paineis-hint">Painéis — um clique acrescenta os exames dele ao pedido</span>
            <button
              type="button"
              className="button secundario pequeno"
              onClick={() => void savePanel()}
              disabled={savingPanel || chosen.length === 0}
              title="Guarda o que está marcado como um painel seu"
            >
              <BookmarkPlus aria-hidden="true" />
              Salvar como painel
            </button>
          </div>
          <div className="paineis-botoes">
            {panels.map((panel) => (
              <button
                type="button"
                key={panel.id}
                className={`button secundario pequeno${panel.own ? " meu-painel" : ""}`}
                onClick={() => applyPanel(panel)}
                title={panel.parameters.map((p) => p.name).join(", ")}
              >
                {panel.name}
                {panel.own && <Own />}
                <span className="painel-n">{panel.parameters.length}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="labtest-order-search">
        <div className="field labtest-search">
          <label htmlFor="sol-busca">Buscar exame</label>
          {/*
            A busca some com os grupos que não têm nada a ver e deixa à vista só
            o que foi procurado. O que já estava marcado continua marcado: filtrar
            é olhar de outro jeito, não desmarcar.
          */}
          <div className="input-search">
            <input
              id="sol-busca"
              type="search"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Glicose, TGP, vitamina D…"
            />
          </div>
        </div>
        {chosen.length > 0 && (
          <span className="labtest-marked" role="status">
            <CircleCheck aria-hidden="true" />
            {chosen.length} marcado(s) no pedido
          </span>
        )}
      </div>

      <div className="labtest-groups">
        {[...byGroup.entries()].map(([group, list]) => (
          <fieldset className="labtest-group" key={group}>
            <legend className="labtest-group-name">{group}</legend>
            <div className="labtest-checks">
              {list.map((p) => (
                <label className="labtest-check" key={p.id}>
                  <input type="checkbox" checked={chosen.includes(p.id)} onChange={() => toggle(p.id)} />
                  <span>{p.name}</span>
                </label>
              ))}
            </div>
          </fieldset>
        ))}
        {byGroup.size === 0 && <div className="empty">Nenhum exame com “{filter}”.</div>}
      </div>

      <div className="labtest-order-meta">
        <div className="field">
          <label htmlFor="sol-data">Data</label>
          <input
            id="sol-data"
            type="date"
            value={date}
            max={todayIso()}
            onChange={(e) => setDate(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="sol-obs">Observação</label>
          <input
            id="sol-obs"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Jejum de 8 horas."
          />
        </div>
      </div>

      <div className="labtest-form-actions">
        <button type="button" className="button secundario" onClick={onClose}>
          Cancelar
        </button>
        <button className="button" type="submit" disabled={saving}>
          {saving ? "Salvando…" : `Solicitar ${count(chosen.length, "exame", "exames")}`}
        </button>
      </div>
    </form>
  );
}
