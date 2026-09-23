import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
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
} from "../api/types";
import { count } from "../text";
import { Own } from "../components/Own";

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

  return (
    <>
      <div className="header-page">
        <div>
          <Link to={`/patients/${patientId}`} className="minusculo">
            ← {patient?.name ?? "Paciente"}
          </Link>
          <h1>Exames</h1>
          <p>
            {count(labtests.length, "resultado", "resultados")}
            {changed.length > 0 ? ` · ${changed.length} fora da referência` : ""}
          </p>
        </div>
        <div className="row">
          <button
            className="button secundario"
            onClick={() => setPanel(panel === "request" ? "none" : "request")}
          >
            Solicitar exames
          </button>
          <button
            className="button"
            onClick={() => setPanel(panel === "entry" ? "none" : "entry")}
          >
            Registrar resultado
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
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
          Nenhum exame registrado. Use <strong>Registrar resultado</strong> quando o laudo chegar.
        </div>
      ) : (
        <>
        <div className="field" style={{ marginBottom: "0.7rem", maxWidth: 360 }}>
          <label htmlFor="ex-busca">Buscar exame</label>
          <input
            id="ex-busca"
            type="search"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            placeholder="Glicose, TGP, vitamina D…"
          />
        </div>
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Exame</th>
                <th>Coleta</th>
                <th className="num">Resultado</th>
                <th>Referência usada</th>
                <th>Laudo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {shown.map((e) => (
                <tr key={e.id}>
                  <td>
                    <button
                      type="button"
                      className="link"
                      onClick={() => openSeries(e.parameterId)}
                    >
                      <strong>{e.parameter}</strong>
                    </button>
                    {e.group && <div className="minusculo">{e.group}</div>}
                    {e.notes && <div className="minusculo">{e.notes}</div>}
                  </td>
                  <td className="mono">{formatBr(e.dateCollection)}</td>
                  <td className="num">
                    {e.value === undefined ? (
                      <span className="minusculo">não determinado</span>
                    ) : (
                      <>
                        {e.value.toLocaleString("pt-BR")} {e.unit}
                        {e.classification && (
                          <div>
                            <span
                              className={`tag ${CLASSE_BY_CLASSIFICATION[e.classification] ?? ""}`}
                            >
                              {e.classificationDescription}
                            </span>
                          </div>
                        )}
                      </>
                    )}
                  </td>
                  <td className="discreto">{e.reference ?? "—"}</td>
                  <td>
                    <ButtonReport
                      labtest={e}
                      onAttach={(file) => attachReport(e.id, file)}
                      onOpen={() => openReport(e.id)}
                    />
                  </td>
                  <td>
                    <button className="button perigo pequeno" onClick={() => remove(e)}>
                      Remover
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {shown.length === 0 && (
          <p className="empty">Nenhum exame com “{term}”.</p>
        )}
        </>
      )}

      {requests.length > 0 && (
        <div className="card" style={{ marginTop: "0.9rem" }}>
          <h2>Solicitações</h2>
          {requests.map((s) => (
            <div key={s.id} style={{ marginTop: "0.6rem" }}>
              <span className="mono">{formatBr(s.date)}</span>{" "}
              <span className="discreto">{s.labtests.join(", ")}</span>
              {s.notes && <div className="minusculo">{s.notes}</div>}
            </div>
          ))}
        </div>
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
        <button className="button secundario pequeno" onClick={onOpen}>
          Abrir
        </button>
      ) : (
        <button className="button secundario pequeno" onClick={() => input.current?.click()}>
          Anexar
        </button>
      )}
      <input
        ref={input}
        type="file"
        accept=".pdf,image/*"
        style={{ display: "none" }}
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
    <div className="card" style={{ marginBottom: "0.9rem" }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>{series.parameter} ao longo do tempo</h2>
        <button className="button secundario pequeno" onClick={onClose}>
          Fechar
        </button>
      </div>

      {series.unitsMixed && (
        <div className="warning attention" style={{ marginTop: "0.7rem" }}>
          Esta série tem coletas em unidades diferentes. Os valores não são comparáveis entre si, e
          a variação não é calculada entre eles.
        </div>
      )}

      <div className="ruler-day" style={{ marginTop: "0.8rem" }}>
        {series.points.map((p, i) => (
          <div className="ruler-item" key={i}>
            <span className="ruler-hour without-hour">{formatBr(p.dateCollection).slice(0, 5)}</span>
            <div className="ruler-body">
              <strong style={{ fontSize: "1rem" }}>
                {p.value === undefined ? "não determinado" : `${p.value.toLocaleString("pt-BR")} ${p.unit}`}
              </strong>
              {p.classification && (
                <span
                  className={`tag ${CLASSE_BY_CLASSIFICATION[p.classification] ?? ""}`}
                  style={{ marginLeft: "0.5rem" }}
                >
                  {p.classification.toLowerCase()}
                </span>
              )}
              {p.change !== undefined && (
                <p className="appointment-target">
                  {p.change > 0 ? "+" : ""}
                  {p.change.toLocaleString("pt-BR")} desde a coleta anterior
                </p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
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
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Registrar resultado</h2>

      {error && (
        <div className="warning error" style={{ margin: "0.8rem 0" }}>
          {error}
        </div>
      )}

      <div className="grid three" style={{ marginTop: "0.8rem" }}>
        <div className="field" style={{ gridColumn: "span 2" }}>
          <label htmlFor="ex-filtro">Buscar exame</label>
          {/*
            O filtro fica antes da lista em vez de substituí-la: a lista
            continua sendo um select nativo, que no celular abre a roda do
            sistema e responde ao teclado sem que a gente reimplemente nada.
          */}
          <input
            id="ex-filtro"
            type="search"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Glicose, TGP, vitamina D…"
          />
        </div>
        <div className="field" style={{ gridColumn: "span 2" }}>
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
            <span className="minusculo">
              Referência: {chosen.ranges.map((f) => f.text).join(" · ")} {chosen.unitStandard}
            </span>
          )}
        </div>
        <div className="field">
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
        <div className="field">
          <label htmlFor="ex-valor">Resultado</label>
          <input
            id="ex-valor"
            inputMode="decimal"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={chosen?.unitStandard}
          />
          <span className="minusculo">
            Deixe vazio se o exame foi pedido e ainda não saiu.
          </span>
        </div>
        <div className="field">
          <label htmlFor="ex-unidade">Unidade</label>
          <input
            id="ex-unidade"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
            placeholder={chosen?.unitStandard ?? ""}
          />
          <span className="minusculo">
            Só se o laudo usar outra. Nesse caso o valor não é classificado.
          </span>
        </div>
        <div className="field">
          <label htmlFor="ex-obs">Observação</label>
          <input id="ex-obs" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
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
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Solicitar exames</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.8rem" }}>
        Registra o pedido entregue ao paciente. Os resultados são lançados quando o laudo chegar.
      </p>

      {error && (
        <div className="warning error" style={{ marginBottom: "0.8rem" }}>
          {error}
        </div>
      )}

      {panels.length > 0 && (
        <div className="paineis">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <span className="minusculo">
              Painéis — um clique acrescenta os exames dele ao pedido
            </span>
            <button
              type="button"
              className="button secundario pequeno"
              onClick={() => void savePanel()}
              disabled={savingPanel || chosen.length === 0}
              title="Guarda o que está marcado como um painel seu"
            >
              Salvar como painel
            </button>
          </div>
          <div className="row paineis-botoes">
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
                <span className="minusculo"> {panel.parameters.length}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="field" style={{ maxWidth: 360, marginBottom: "0.6rem" }}>
        <label htmlFor="sol-busca">Buscar exame</label>
        {/*
          A busca some com os grupos que não têm nada a ver e deixa à vista só
          o que foi procurado. O que já estava marcado continua marcado: filtrar
          é olhar de outro jeito, não desmarcar.
        */}
        <input
          id="sol-busca"
          type="search"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Glicose, TGP, vitamina D…"
        />
        {chosen.length > 0 && (
          <span className="minusculo">{chosen.length} marcado(s) no pedido</span>
        )}
      </div>

      <div className="grid two">
        {[...byGroup.entries()].map(([group, list]) => (
          <div key={group}>
            <span className="minusculo">{group}</span>
            {list.map((p) => (
              <label className="row" key={p.id} style={{ gap: "0.4rem", marginTop: "0.2rem" }}>
                <input
                  type="checkbox"
                  checked={chosen.includes(p.id)}
                  onChange={() => toggle(p.id)}
                  style={{ width: "auto" }}
                />
                <span style={{ fontSize: "0.88rem" }}>{p.name}</span>
              </label>
            ))}
          </div>
        ))}
      </div>

      <div className="row" style={{ marginTop: "0.9rem" }}>
        <div className="field" style={{ width: 170 }}>
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
        <div className="field" style={{ flex: 1, minWidth: 220 }}>
          <label htmlFor="sol-obs">Observação</label>
          <input
            id="sol-obs"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Jejum de 8 horas."
          />
        </div>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
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
