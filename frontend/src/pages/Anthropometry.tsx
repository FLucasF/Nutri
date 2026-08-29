import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { formatBr, todayIso } from "../api/dates";
import type {
  Assessment,
  Pregnancy,
  Derived,
  Progress,
  CompositionProtocol,
  ProtocolInfo,
  Change,
} from "../api/types";
import { count } from "../text";

const SKINFOLDS: { key: string; label: string }[] = [
  { key: "TRICEPS", label: "Tricipital" },
  { key: "BICEPS", label: "Bicipital" },
  { key: "SUBSCAPULAR", label: "Subescapular" },
  { key: "SUPRAILIAC", label: "Supra-ilíaca" },
  { key: "ABDOMINAL", label: "Abdominal" },
  { key: "CHEST", label: "Peitoral" },
  { key: "THIGH", label: "Coxa" },
  { key: "CALF", label: "Panturrilha" },
  { key: "MEAN_AXILLARY", label: "Axilar média" },
];

const CIRCUMFERENCES: { key: string; label: string }[] = [
  { key: "waist", label: "Cintura" },
  { key: "hip", label: "Quadril" },
  { key: "abdomen", label: "Abdômen" },
  { key: "arm", label: "Braço" },
  { key: "forearm", label: "Antebraço" },
  { key: "thigh", label: "Coxa" },
  { key: "calf", label: "Panturrilha" },
  { key: "chest", label: "Tórax" },
];

const CLASSIFICATIONS: Record<string, string> = {
  LOW_WEIGHT: "Baixo peso",
  NORMAL: "Eutrofia",
  OVERWEIGHT: "Sobrepeso",
  OBESITY_I: "Obesidade grau I",
  OBESITY_II: "Obesidade grau II",
  OBESITY_III: "Obesidade grau III",
};

const RISKS: Record<string, string> = {
  LOW: "Risco baixo",
  MODERATE: "Risco moderado",
  HIGH: "Risco alto",
};

export default function Anthropometry() {
  const { id } = useParams();
  const patientId = Number(id);

  const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [protocols, setProtocols] = useState<ProtocolInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [list, series] = await Promise.all([
        api.anthropometry.list(patientId),
        api.anthropometry.progress(patientId),
      ]);
      setAssessments(list);
      setProgress(series);
    } catch (e) {
      setError(explainError(e, "abrir as avaliações"));
    } finally {
      setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    void load();
    api.anthropometry.protocols().then(setProtocols).catch(() => setProtocols([]));
  }, [load]);

  const feedback = useFeedback();

  async function remove(assessmentId: number) {
    if (!confirm("Remover esta avaliação?")) return;
    try {
      await api.anthropometry.remove(assessmentId);
      feedback.confirm("Avaliação removida.");
      await load();
    } catch (e) {
      setError(explainError(e, "remover a avaliação"));
    }
  }

  if (loading) return <p className="loading">Carregando…</p>;

  const moreRecent = assessments[assessments.length - 1];

  return (
    <>
      <div className="header-page">
        <div>
          <Link to={`/patients/${patientId}`} className="minusculo">
            ← {progress?.patientName ?? "Paciente"}
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>Antropometria</h1>
          <p>
            {assessments.length === 0
              ? "Nenhuma avaliação registrada."
              : `${assessments.length} ${assessments.length === 1 ? "avaliação" : "avaliações"}`}
          </p>
        </div>
        <button className="button" onClick={() => setCreating((v) => !v)}>
          {creating ? "Cancelar" : "Nova avaliação"}
        </button>
      </div>

      {error && <div className="warning error" style={{ marginBottom: "0.9rem" }}>{error}</div>}

      {creating && (
        <FormAssessment
          patientId={patientId}
          protocols={protocols}
          last={moreRecent}
          onSave={async () => {
            setCreating(false);
            await load();
          }}
        />
      )}

      {moreRecent && <AssessmentSummary assessment={moreRecent} patientId={patientId} />}

      {progress && progress.points.length > 1 && <ProgressTable progress={progress} />}

      {assessments.length > 0 && (
        <>
          <h2 style={{ margin: "1.3rem 0 0.6rem" }}>Histórico</h2>
          <div className="rolagem">
            <table>
              <thead>
                <tr>
                  <th>Data</th>
                  <th className="num">Peso</th>
                  <th className="num">IMC</th>
                  <th>Classificação</th>
                  <th className="num">% gordura</th>
                  <th>Protocolo</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {[...assessments].reverse().map((a) => (
                  <tr key={a.id}>
                    <td className="mono">{formatBr(a.date)}</td>
                    <td className="num">{a.weightKg ? `${num(a.weightKg)} kg` : "—"}</td>
                    <td className="num">{a.bmi ? num(a.bmi) : "—"}</td>
                    <td>
                      {a.classificationBmi.value ? (
                        CLASSIFICATIONS[a.classificationBmi.value]
                      ) : (
                        <span className="minusculo">não classificado</span>
                      )}
                    </td>
                    <td className="num">
                      {a.composition?.percentageFat
                        ? `${num(a.composition.percentageFat)}%`
                        : "—"}
                    </td>
                    <td className="discreto">{a.composition?.protocolDescription ?? "—"}</td>
                    <td style={{ textAlign: "right" }}>
                      <button className="button perigo pequeno" onClick={() => remove(a.id)}>
                        Remover
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

// ----------------------------------------------------------------- summary

function AssessmentSummary({
  assessment,
  patientId,
}: {
  assessment: Assessment;
  patientId: number;
}) {
  return (
    <div className="card" style={{ marginBottom: "1.1rem" }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Última avaliação</h2>
        <span className="minusculo mono">{formatBr(assessment.date)}</span>
      </div>

      <div className="grid three" style={{ marginTop: "0.8rem" }}>
        <Indicator label="Peso" value={assessment.weightKg} unit="kg" />
        <Indicator label="Altura" value={assessment.heightCm} unit="cm" />
        <Indicator label="IMC" value={assessment.bmi} />
        <Derived label="Classificação" derived={assessment.classificationBmi} map={CLASSIFICATIONS} />
        <Indicator label="Cintura/quadril" value={assessment.ratioWaistHip} />
        <Derived
          label="Risco cardiometabólico"
          derived={assessment.riskCardiometabolico}
          map={RISKS}
        />
      </div>

      {assessment.composition && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>
            Composição corporal
            <span className="tag" style={{ marginLeft: "0.5rem" }}>
              {assessment.composition.protocolDescription}
            </span>
          </h3>
          <div className="grid three">
            <Indicator
              label="Gordura corporal"
              value={assessment.composition.percentageFat}
              unit="%"
            />
            <Indicator label="Massa gorda" value={assessment.composition.massFatKg} unit="kg" />
            <Indicator label="Massa magra" value={assessment.composition.massLeanKg} unit="kg" />
          </div>
        </>
      )}

      {assessment.childGrowth?.value && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>
            Crescimento
            <span className="tag" style={{ marginLeft: "0.5rem" }}>
              {assessment.childGrowth.value.ageAtMonths} meses
            </span>
          </h3>
          {assessment.childGrowth.value.indicators.map((i) => (
            <div className="nutrient-row" key={i.indicator}>
              <span>
                {i.indicatorDescription}
                <div className="minusculo">{i.reference}</div>
              </span>
              <span style={{ textAlign: "right" }}>
                escore-z {i.scoreZ?.toFixed(2).replace(".", ",")}
                <div>
                  <span className={`tag ${i.requiresAttention ? "ambar" : "verde"}`}>
                    {i.classificationDescription}
                  </span>
                </div>
              </span>
            </div>
          ))}
        </>
      )}

      {assessment.pregnancy?.value && <BlockPregnancy pregnancy={assessment.pregnancy.value} />}

      {assessment.pregnancy && !assessment.pregnancy.value && (
        <div className="warning attention" style={{ marginTop: "0.9rem" }}>
          {assessment.pregnancy.unavailableBecause}
        </div>
      )}

      {assessment.expenditureEnergy && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>
            Gasto energético
            <span className="tag" style={{ marginLeft: "0.5rem" }}>
              {assessment.expenditureEnergy.equationDescription}
            </span>
          </h3>
          <div className="grid three">
            <Indicator label="Basal" value={assessment.expenditureEnergy.basalKcal} unit="kcal" />
            <Indicator
              label="Fator de atividade"
              value={assessment.expenditureEnergy.factorActivity}
            />
            <Indicator label="Total" value={assessment.expenditureEnergy.totalKcal} unit="kcal" />
          </div>
          {assessment.expenditureEnergy.totalKcal !== undefined && (
            <div className="row" style={{ marginTop: "0.7rem" }}>
              <Link
                className="button secundario pequeno"
                to={`/prescriptions/new?patientId=${patientId}&target=${assessment.expenditureEnergy.totalKcal}`}
              >
                Usar como meta do plano
              </Link>
              <span className="minusculo">
                Abre um plano novo com a meta energética já preenchida.
              </span>
            </div>
          )}
        </>
      )}

      {Object.keys(assessment.circumferences).length > 0 && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>Circunferências (cm)</h3>
          <div className="grid three">
            {CIRCUMFERENCES.filter((c) => assessment.circumferences[c.key] !== undefined).map(
              (c) => (
                <Indicator
                  key={c.key}
                  label={c.label}
                  value={assessment.circumferences[c.key]}
                  unit="cm"
                />
              ),
            )}
          </div>
        </>
      )}

      {assessment.notes && (
        <p className="discreto" style={{ marginTop: "0.9rem", marginBottom: 0 }}>
          {assessment.notes}
        </p>
      )}
    </div>
  );
}

/**
 * Weight gain in pregnancy.
 *
 * The expected band comes from the BMI before pregnancy, and not from the
 * current one: today's BMI already embeds the gain being assessed.
 */
function BlockPregnancy({ pregnancy }: { pregnancy: Pregnancy }) {
  const classe =
    pregnancy.status === "ADEQUATE" ? "verde" : pregnancy.status === "ABOVE" ? "vermelha" : "ambar";

  return (
    <>
      <h3 style={{ margin: "1rem 0 0.4rem" }}>
        Gestação
        <span className="tag" style={{ marginLeft: "0.5rem" }}>
          {pregnancy.gestationalWeek}ª semana
        </span>
      </h3>

      <div className="grid three">
        <Indicator label="IMC pré-gestacional" value={pregnancy.bmiGestationalPre} />
        <Indicator label="Ganho até agora" value={pregnancy.gainAteNow} unit="kg" />
        <div>
          <span className="minusculo">Situação</span>
          <div style={{ marginTop: "0.2rem" }}>
            <span className={`tag ${classe}`}>{pregnancy.statusDescription}</span>
          </div>
        </div>
      </div>

      <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
        Faixa {pregnancy.rangeDescription.toLowerCase()}: esperado{" "}
        {pregnancy.expectedMin.toLocaleString("pt-BR")} a{" "}
        {pregnancy.expectedMax.toLocaleString("pt-BR")} kg até a {pregnancy.gestationalWeek}ª
        semana, e {pregnancy.totalGainRecommendedMin.toLocaleString("pt-BR")} a{" "}
        {pregnancy.totalGainRecommendedMax.toLocaleString("pt-BR")} kg na gestação whole
        (IOM, 2009).
      </p>
    </>
  );
}

function Indicator({
  label,
  value,
  unit,
}: {
  label: string;
  value?: number;
  unit?: string;
}) {
  return (
    <div>
      <span className="minusculo">{label}</span>
      <div className="mono" style={{ fontSize: "1.05rem", fontWeight: 600 }}>
        {value === undefined || value === null ? (
          <span style={{ color: "var(--ink-faint)", fontWeight: 400, fontSize: "0.9rem" }}>
            não medido
          </span>
        ) : (
          `${num(value)}${unit ? ` ${unit}` : ""}`
        )}
      </div>
    </div>
  );
}

/**
 * A derived value that may not exist by rule, and not for lack of a
 * measurement. Showing the reason is what tells the professional what to do
 * about it.
 */
function Derived<T extends string>({
  label,
  derived,
  map,
}: {
  label: string;
  derived: Derived<T>;
  map: Record<string, string>;
}) {
  return (
    <div>
      <span className="minusculo">{label}</span>
      {derived.value ? (
        <div style={{ fontSize: "1.05rem", fontWeight: 600 }}>{map[derived.value]}</div>
      ) : (
        <div className="minusculo" style={{ marginTop: "0.15rem", lineHeight: 1.35 }}>
          {derived.unavailableBecause ?? "não disponível"}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- progress

function ProgressTable({ progress }: { progress: Progress }) {
  const measures = ["weightKg", "bmi", "circumferenceWaist", "percentageFat"];

  return (
    <div className="card" style={{ marginBottom: "1.1rem" }}>
      <h2>Evolução</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.8rem" }}>
        A variação só aparece quando as duas avaliações são comparáveis: medida presente nas duas,
        e composição estimada pelo mesmo protocolo.
      </p>

      <div className="rolagem">
        <table>
          <thead>
            <tr>
              <th>Data</th>
              {measures.map((m) => (
                <th key={m} className="num">
                  {measureLabel(progress, m)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {progress.points.map((point, index) => (
              <tr key={point.assessmentId}>
                <td className="mono">{formatBr(point.date)}</td>
                {measures.map((measure) => {
                  const v = point.changesPreviousFront.find((x) => x.measure === measure);
                  return (
                    <td key={measure} className="num">
                      <ValueComChange change={v} first={index === 0} />
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ValueComChange({
  change,
  first,
}: {
  change?: Change;
  first: boolean;
}) {
  if (!change) return <span className="minusculo">—</span>;

  const current = change.current;
  if (current === undefined || current === null) {
    return <span className="minusculo">—</span>;
  }

  if (first) {
    return <span>{num(current)}</span>;
  }

  if (!change.comparable) {
    return (
      <span title={change.notes}>
        {num(current)}{" "}
        <span className="minusculo" style={{ cursor: "help" }}>
          (sem comparação)
        </span>
      </span>
    );
  }

  const d = change.difference ?? 0;
  const color = d < 0 ? "var(--accent)" : d > 0 ? "var(--alerta)" : "var(--ink-faint)";
  return (
    <span>
      {num(current)}{" "}
      <span style={{ color: color, fontSize: "0.8rem" }}>
        {d > 0 ? "+" : ""}
        {num(d)}
      </span>
    </span>
  );
}

function measureLabel(progress: Progress, measure: string) {
  for (const point of progress.points) {
    const v = point.changesPreviousFront.find((x) => x.measure === measure);
    if (v) return v.label;
  }
  return measure;
}

// ---------------------------------------------------------------------- form

function FormAssessment({
  patientId,
  protocols,
  last,
  onSave,
}: {
  patientId: number;
  protocols: ProtocolInfo[];
  last?: Assessment;
  onSave: () => Promise<void>;
}) {
  const today = todayIso();

  const [date, setDate] = useState(today);
  // Height rarely changes between appointments: repeating the last one saves typing.
  const [weight, setWeight] = useState("");
  const [height, setHeight] = useState(last?.heightCm ? String(last.heightCm) : "");
  const [skinfolds, setSkinfolds] = useState<Record<string, string>>({});
  const [circumferences, setCircumferences] = useState<Record<string, string>>({});
  const [protocol, setProtocol] = useState<string>("");
  const [equation, setEquation] = useState<string>("");
  const [factor, setFactor] = useState("1.55");
  const [notes, setNotes] = useState("");
  const [pregnant, setPregnant] = useState(false);
  const [week, setWeek] = useState("");
  const [weightPre, setWeightPre] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  const chosen = protocols.find((p) => p.protocol === protocol);
  // Not knowing the patient's sex here, it highlights the union of both lists.
  const required = new Set([
    ...(chosen?.skinfoldsFemale ?? []),
    ...(chosen?.skinfoldsMale ?? []),
  ]);

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      await api.anthropometry.create(patientId, {
        date,
        weightKg: numberOuUndefined(weight),
        heightCm: numberOuUndefined(height),
        skinfolds: numbersMap(skinfolds),
        circumferences: numbersMap(circumferences),
        protocolComposition: (protocol || undefined) as CompositionProtocol | undefined,
        equationExpenditure: (equation || undefined) as never,
        factorActivity: equation ? numberOuUndefined(factor) : undefined,
        notes: notes.trim() || undefined,
        gestationalWeek: pregnant && week ? Number(week) : undefined,
        weightGestationalPreKg:
          pregnant && weightPre ? Number(weightPre.replace(",", ".")) : undefined,
      });
      feedback.confirm(`Avaliação de ${formatBr(date)} registrada.`);
      await onSave();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "registrar a avaliação"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "1.1rem" }} onSubmit={send}>
      <h2>Nova avaliação</h2>

      {error && <div className="warning error" style={{ margin: "0.8rem 0" }}>{error}</div>}

      <div className="grid three" style={{ marginTop: "0.8rem" }}>
        <div className="field">
          <label htmlFor="av-data">Data</label>
          <input
            id="av-data"
            name="date"
            type="date"
            value={date}
            max={today}
            onChange={(e) => setDate(e.target.value)}
            required
            {...fields.props("date")}
          />
          <FieldError field="date" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="av-peso">Peso (kg)</label>
          <input
            id="av-peso"
            name="weightKg"
            inputMode="decimal"
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            {...fields.props("weightKg")}
          />
          <FieldError field="weightKg" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="av-altura">Altura (cm)</label>
          <input
            id="av-altura"
            name="heightCm"
            inputMode="decimal"
            value={height}
            onChange={(e) => setHeight(e.target.value)}
            {...fields.props("heightCm")}
          />
          <FieldError field="heightCm" errors={fields.errors} />
        </div>
      </div>

      <h3 style={{ margin: "1.1rem 0 0.4rem" }}>Circunferências (cm)</h3>
      <div className="grid three">
        {CIRCUMFERENCES.map((c) => (
          <div className="field" key={c.key}>
            <label htmlFor={`circ-${c.key}`}>{c.label}</label>
            <input
              id={`circ-${c.key}`}
              inputMode="decimal"
              value={circumferences[c.key] ?? ""}
              onChange={(e) =>
                setCircumferences((v) => ({ ...v, [c.key]: e.target.value }))
              }
            />
          </div>
        ))}
      </div>

      <h3 style={{ margin: "1.1rem 0 0.4rem" }}>Dobras cutâneas (mm)</h3>
      <div className="grid three">
        {SKINFOLDS.map((d) => {
          const mandatory = required.has(d.key);
          return (
            <div className="field" key={d.key}>
              <label htmlFor={`dobra-${d.key}`}>
                {d.label}
                {mandatory && (
                  <span className="tag verde" style={{ marginLeft: "0.35rem" }}>
                    exigida
                  </span>
                )}
              </label>
              <input
                id={`dobra-${d.key}`}
                inputMode="decimal"
                value={skinfolds[d.key] ?? ""}
                onChange={(e) => setSkinfolds((v) => ({ ...v, [d.key]: e.target.value }))}
              />
            </div>
          );
        })}
      </div>

      <div className="grid two" style={{ marginTop: "1.1rem" }}>
        <div className="field">
          <label htmlFor="av-protocolo">Estimar composição por</label>
          <select
            id="av-protocolo"
            value={protocol}
            onChange={(e) => setProtocol(e.target.value)}
          >
            <option value="">Não estimar</option>
            {protocols.map((p) => (
              <option key={p.protocol} value={p.protocol}>
                {p.description}
              </option>
            ))}
          </select>
          {chosen && (
            <span className="minusculo">
              Exige {count(required.size, "dobra", "dobras")}. Faltando alguma, o sistema recusa
              e diz qual.
            </span>
          )}
        </div>

        <div className="field">
          <label htmlFor="av-equacao">Estimar gasto energético por</label>
          <select id="av-equacao" value={equation} onChange={(e) => setEquation(e.target.value)}>
            <option value="">Não estimar</option>
            <option value="MIFFLIN_ST_JEOR">Mifflin-St Jeor</option>
            <option value="HARRIS_BENEDICT">Harris-Benedict revisada</option>
          </select>
        </div>
      </div>

      {equation && (
        <div className="field" style={{ marginTop: "0.6rem", maxWidth: 260 }}>
          <label htmlFor="av-fator">Fator de atividade</label>
          <select
            id="av-fator"
            name="factorActivity"
            value={factor}
            onChange={(e) => setFactor(e.target.value)}
            {...fields.props("factorActivity")}
          >
            <option value="1.2">1,20 — sedentário</option>
            <option value="1.375">1,375 — levemente ativo</option>
            <option value="1.55">1,55 — moderadamente ativo</option>
            <option value="1.725">1,725 — muito ativo</option>
            <option value="1.9">1,90 — extremamente ativo</option>
          </select>
          <FieldError field="factorActivity" errors={fields.errors} />
        </div>
      )}

      <label className="row" style={{ gap: "0.4rem", marginTop: "0.9rem" }}>
        <input
          type="checkbox"
          checked={pregnant}
          onChange={(e) => setPregnant(e.target.checked)}
          style={{ width: "auto" }}
        />
        <span className="discreto">Gestante</span>
      </label>

      {pregnant && (
        <div className="grid two" style={{ marginTop: "0.5rem" }}>
          <div className="field">
            <label htmlFor="av-semana">Semana gestacional</label>
            <input
              id="av-semana"
              name="gestationalWeek"
              inputMode="numeric"
              value={week}
              onChange={(e) => setWeek(e.target.value)}
              placeholder="20"
              {...fields.props("gestationalWeek")}
            />
            <FieldError field="gestationalWeek" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="av-peso-pre">Peso antes da gestação (kg)</label>
            <input
              id="av-peso-pre"
              name="weightGestationalPreKg"
              inputMode="decimal"
              value={weightPre}
              onChange={(e) => setWeightPre(e.target.value)}
              placeholder="61,2"
              {...fields.props("weightGestationalPreKg")}
            />
            <FieldError field="weightGestationalPreKg" errors={fields.errors} />
            <span className="minusculo">
              A faixa de ganho vem do IMC de antes. O IMC de hoje já embute o ganho que se quer
              avaliar.
            </span>
          </div>
        </div>
      )}

      <div className="field" style={{ marginTop: "0.8rem" }}>
        <label htmlFor="av-obs">Observações</label>
        <textarea
          id="av-obs"
          name="notes"
          rows={2}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          {...fields.props("notes")}
        />
        <FieldError field="notes" errors={fields.errors} />
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Registrar avaliação"}
        </button>
      </div>
    </form>
  );
}

// -------------------------------------------------------------------- helpers

function numberOuUndefined(text: string): number | undefined {
  const clean = text.replace(",", ".").trim();
  if (!clean) return undefined;
  const value = Number(clean);
  return Number.isFinite(value) ? value : undefined;
}

function numbersMap(origin: Record<string, string>): Record<string, number> {
  const output: Record<string, number> = {};
  Object.entries(origin).forEach(([key, text]) => {
    const value = numberOuUndefined(text);
    if (value !== undefined && value > 0) output[key] = value;
  });
  return output;
}

function num(value: number) {
  return value.toFixed(2).replace(".", ",").replace(/,00$/, "");
}


