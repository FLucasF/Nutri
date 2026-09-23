import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import {
  CalendarDays,
  ChevronLeft,
  ClipboardList,
  FileText,
  Pencil,
  Plus,
  Ruler,
  Trash2,
  X,
} from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { formatBr, todayIso } from "../api/dates";
import type {
  CircumferenceSite,
  CircumferenceValue,
  Side,
  Assessment,
  Pregnancy,
  Derived,
  Progress,
  ProgressPoint,
  CompositionProtocol,
  ProtocolInfo,
  Change,
} from "../api/types";
import { count } from "../text";
import { AREAS_HIDDEN } from "../features";
import { NotesField, NotesView } from "../components/RichText/NotesField";

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

/**
 * Os treze locais que o cliente lista, na ordem anatômica do enum do domínio.
 *
 * Os marcados como bilaterais ganham dois campos, direito e esquerdo. Era essa
 * a razão de as circunferências saírem das colunas planas do banco.
 */
const CIRCUMFERENCES: { site: CircumferenceSite; label: string; bilateral: boolean }[] = [
  { site: "NECK", label: "Pescoço", bilateral: false },
  { site: "SHOULDER", label: "Ombro", bilateral: false },
  { site: "CHEST", label: "Tórax", bilateral: false },
  { site: "WAIST", label: "Cintura", bilateral: false },
  { site: "ABDOMEN", label: "Abdômen", bilateral: false },
  { site: "HIP", label: "Quadril", bilateral: false },
  { site: "ARM_RELAXED", label: "Braço relaxado", bilateral: true },
  { site: "ARM_CONTRACTED", label: "Braço contraído", bilateral: true },
  { site: "FOREARM", label: "Antebraço", bilateral: true },
  { site: "THIGH_PROXIMAL", label: "Coxa proximal", bilateral: true },
  { site: "THIGH_MEDIAL", label: "Coxa medial", bilateral: true },
  { site: "THIGH_DISTAL", label: "Coxa distal", bilateral: true },
  { site: "CALF", label: "Panturrilha", bilateral: true },
];

/** A chave do campo no formulário: local mais lado. */
function keyOf(site: CircumferenceSite, side: Side): string {
  return `${site}|${side}`;
}

const EXTRAS_MEASURES: { key: string; label: string }[] = [
  { key: "heightSittingCm", label: "Altura sentado" },
  { key: "heightKneeCm", label: "Altura do joelho" },
  { key: "diameterHumerus", label: "Diâmetro do úmero" },
  { key: "diameterWrist", label: "Diâmetro do punho" },
  { key: "diameterFemur", label: "Diâmetro do fêmur" },
];

const BIA_FIELDS: { key: string; label: string }[] = [
  { key: "biaFatPercentage", label: "% de gordura" },
  { key: "biaFatMassKg", label: "Massa gorda (kg)" },
  { key: "biaMusclePercentage", label: "% de massa muscular" },
  { key: "biaMuscleMassKg", label: "Massa muscular (kg)" },
  { key: "biaLeanMassKg", label: "Massa livre de gordura (kg)" },
  { key: "biaBoneMassKg", label: "Peso ósseo (kg)" },
  { key: "biaVisceralFat", label: "Gordura visceral" },
  { key: "biaBodyWaterPercentage", label: "% de água corporal" },
  { key: "biaMetabolicAge", label: "Idade metabólica" },
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
  /** A avaliação que está sendo corrigida, quando há uma. */
  const [editing, setEditing] = useState<Assessment | null>(null);
  const [generatingReport, setGeneratingReport] = useState(false);

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

  /** Abre o relatório de evolução em outra guia. */
  async function openReport() {
    setGeneratingReport(true);
    try {
      const { url } = await api.anthropometry.report(patientId);
      window.open(url, "_blank", "noopener");
      // Solta o endereço depois: revogar antes de a guia nova ler abriria em
      // branco.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      setError(explainError(e, "gerar o relatório de evolução"));
    } finally {
      setGeneratingReport(false);
    }
  }

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
          <Link to={`/patients/${patientId}`} className="migalha">
            <ChevronLeft aria-hidden="true" />
            {progress?.patientName ?? "Paciente"}
          </Link>
          <h1>Antropometria</h1>
          <p>
            {assessments.length === 0
              ? "Nenhuma avaliação registrada."
              : `${assessments.length} ${assessments.length === 1 ? "avaliação" : "avaliações"}`}
          </p>
        </div>
        <div className="header-page-actions">
          {/*
            O relatório só aparece com duas avaliações: uma evolução de um
            ponto só não é uma evolução, e o botão prometeria uma folha que o
            servidor recusaria.
          */}
          {assessments.length > 1 && (
            <button
              className="button secundario"
              onClick={() => void openReport()}
              disabled={generatingReport}
              title="Abre em outra guia"
            >
              <FileText aria-hidden="true" />
              {generatingReport ? "Gerando…" : "Relatório de evolução"}
            </button>
          )}
          <button
            className={creating ? "button secundario" : "button"}
            onClick={() => {
              setEditing(null);
              setCreating((v) => !v);
            }}
          >
            {creating ? <X aria-hidden="true" /> : <Plus aria-hidden="true" />}
            {creating ? "Cancelar" : "Nova avaliação"}
          </button>
        </div>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      {(creating || editing) && (
        <FormAssessment
          // A chave troca junto com a avaliação editada: sem isso o formulário
          // seria reaproveitado com os valores da anterior ainda dentro.
          key={editing ? `editar-${editing.id}` : "nova"}
          patientId={patientId}
          protocols={protocols}
          last={moreRecent}
          editing={editing ?? undefined}
          onCancel={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSave={async () => {
            setCreating(false);
            setEditing(null);
            await load();
          }}
        />
      )}

      {assessments.length === 0 && !creating && (
        <section className="card">
          <div className="empty">
            <span className="empty-icon">
              <Ruler aria-hidden="true" />
            </span>
            <span className="empty-title">Sem avaliações por enquanto</span>
            <span className="empty-hint">
              Registre a primeira: a evolução aparece a partir da segunda.
            </span>
          </div>
        </section>
      )}

      {moreRecent && <AssessmentSummary assessment={moreRecent} patientId={patientId} />}

      {progress && progress.points.length > 1 && <ProgressTable progress={progress} />}

      {assessments.length > 0 && (
        <section className="card">
          <div className="card-head">
            <div>
              <h2 className="card-title">Histórico</h2>
              <p className="card-sub">Da mais recente para a primeira.</p>
            </div>
          </div>
          <TableScroll>
            <table className="history-table">
              <thead>
                <tr>
                  <th>Data</th>
                  <th className="num">Peso</th>
                  <th className="num">IMC</th>
                  <th>Classificação</th>
                  <th className="num">% gordura</th>
                  <th className="num col-secundaria">Massa magra</th>
                  <th className="col-secundaria">Protocolo</th>
                  <th className="acoes">
                    <span className="visually-hidden">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {[...assessments].reverse().map((a) => (
                  <tr key={a.id}>
                    <td className="mono">{formatBr(a.date)}</td>
                    <td className="num">{a.weightKg ? `${num(a.weightKg)} kg` : "—"}</td>
                    <td className="num">{a.bmi ? num(a.bmi) : "—"}</td>
                    <td className="col-class">
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
                    <td className="num col-secundaria">
                      {a.composition?.massLeanKg
                        ? `${num(a.composition.massLeanKg)} kg`
                        : a.biaMuscleMassKg
                          ? `${num(a.biaMuscleMassKg)} kg`
                          : "—"}
                    </td>
                    <td className="discreto col-secundaria col-protocolo">
                      {a.composition?.protocolDescription ?? "—"}
                    </td>
                    <td className="acoes">
                      <div className="history-actions">
                        <button
                          className="button secundario pequeno"
                          onClick={() => {
                            setCreating(false);
                            setEditing(a);
                            window.scrollTo({ top: 0, behavior: "smooth" });
                          }}
                        >
                          <Pencil aria-hidden="true" />
                          Editar
                        </button>
                        <button className="button perigo pequeno" onClick={() => remove(a.id)}>
                          <Trash2 aria-hidden="true" />
                          Remover
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableScroll>
        </section>
      )}
    </>
  );
}

// ------------------------------------------------------------ table scroll

/**
 * The numeric-table wrapper: scrolls sideways, keeps the first column, fades
 * the right edge until the end is reached (data-scroll-end, components.css).
 */
function TableScroll({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [atEnd, setAtEnd] = useState(true);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const update = () => setAtEnd(el.scrollLeft + el.clientWidth >= el.scrollWidth - 1);
    update();
    el.addEventListener("scroll", update, { passive: true });
    const observer = new ResizeObserver(update);
    observer.observe(el);
    if (el.firstElementChild) observer.observe(el.firstElementChild);
    return () => {
      el.removeEventListener("scroll", update);
      observer.disconnect();
    };
  }, []);

  return (
    <div className="table-frame">
      <div ref={ref} className="table-scroll" data-scroll-end={atEnd ? "true" : "false"}>
        {children}
      </div>
    </div>
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
    <section className="card mb-3">
      <div className="card-head">
        <h2 className="card-title">Última avaliação</h2>
        <span className="summary-date">
          <CalendarDays aria-hidden="true" />
          {formatBr(assessment.date)}
        </span>
      </div>

      <div className="stats">
        <Stat label="Peso" value={assessment.weightKg} unit="kg" />
        <Stat label="Altura" value={assessment.heightCm} unit="cm" />
        <Stat label="IMC" value={assessment.bmi} />
        <DerivedStat
          label="Classificação"
          derived={assessment.classificationBmi}
          map={CLASSIFICATIONS}
        />
        <Stat label="Cintura/quadril" value={assessment.ratioWaistHip} />
        <DerivedStat
          label="Risco cardiometabólico"
          derived={assessment.riskCardiometabolico}
          map={RISKS}
        />
      </div>

      {assessment.composition && (
        <section className="summary-group">
          <div className="summary-group-head">
            <h3>Composição corporal</h3>
            <span className="tag">{assessment.composition.protocolDescription}</span>
          </div>
          <div className="stats">
            <Stat label="Gordura corporal" value={assessment.composition.percentageFat} unit="%" />
            <Stat label="Massa gorda" value={assessment.composition.massFatKg} unit="kg" />
            <Stat label="Massa magra" value={assessment.composition.massLeanKg} unit="kg" />
          </div>
        </section>
      )}

      {assessment.childGrowth?.value && (
        <section className="summary-group">
          <div className="summary-group-head">
            <h3>Crescimento</h3>
            <span className="tag">{assessment.childGrowth.value.ageAtMonths} meses</span>
          </div>
          <div className="growth-list">
            {assessment.childGrowth.value.indicators.map((i) => (
              <div className="growth-row" key={i.indicator}>
                <span>
                  {i.indicatorDescription}
                  <span className="growth-row-ref">{i.reference}</span>
                </span>
                <span className="growth-row-score">
                  <span>escore-z {i.scoreZ?.toFixed(2).replace(".", ",")}</span>
                  <span className={`tag ${i.requiresAttention ? "ambar" : "verde"}`}>
                    {i.classificationDescription}
                  </span>
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {AREAS_HIDDEN.gestation && assessment.pregnancy?.value && (
        <BlockPregnancy pregnancy={assessment.pregnancy.value} />
      )}

      {AREAS_HIDDEN.gestation && assessment.pregnancy && !assessment.pregnancy.value && (
        <div className="warning attention mt-3">{assessment.pregnancy.unavailableBecause}</div>
      )}

      {assessment.expenditureEnergy && (
        <section className="summary-group">
          <div className="summary-group-head">
            <h3>Gasto energético</h3>
            <span className="tag">{assessment.expenditureEnergy.equationDescription}</span>
          </div>
          <div className="stats">
            <Stat label="Basal" value={assessment.expenditureEnergy.basalKcal} unit="kcal" />
            <Stat label="Fator de atividade" value={assessment.expenditureEnergy.factorActivity} />
            <Stat label="Total" value={assessment.expenditureEnergy.totalKcal} unit="kcal" />
          </div>
          {assessment.expenditureEnergy.totalKcal !== undefined && (
            <div className="summary-action">
              <Link
                className="button secundario pequeno"
                to={`/prescriptions/new?patientId=${patientId}&target=${assessment.expenditureEnergy.totalKcal}`}
              >
                <ClipboardList aria-hidden="true" />
                Usar como meta do plano
              </Link>
              <span className="minusculo">
                Abre um plano novo com a meta energética já preenchida.
              </span>
            </div>
          )}
        </section>
      )}

      {assessment.circumferences.length > 0 && (
        <section className="summary-group">
          <div className="summary-group-head">
            <h3>Circunferências (cm)</h3>
          </div>
          <div className="stats dense">
            {assessment.circumferences.map((c) => (
              <Stat
                key={keyOf(c.site, c.side)}
                label={
                  c.side === "SINGLE"
                    ? (c.description ?? c.site)
                    : `${c.description ?? c.site} (${c.side === "RIGHT" ? "D" : "E"})`
                }
                value={c.valueCm}
                unit="cm"
              />
            ))}
          </div>
        </section>
      )}

      {assessment.notes && (
        <div className="summary-notes">
          <NotesView value={assessment.notes} />
        </div>
      )}
    </section>
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
    <section className="summary-group">
      <div className="summary-group-head">
        <h3>Gestação</h3>
        <span className="tag">{pregnancy.gestationalWeek}ª semana</span>
      </div>

      <div className="stats">
        <Stat label="IMC pré-gestacional" value={pregnancy.bmiGestationalPre} />
        <Stat label="Ganho até agora" value={pregnancy.gainAteNow} unit="kg" />
        <div className="stat">
          <span className="stat-label">Situação</span>
          <span className="stat-tag">
            <span className={`tag ${classe}`}>{pregnancy.statusDescription}</span>
          </span>
        </div>
      </div>

      <p className="summary-hint">
        Faixa {pregnancy.rangeDescription.toLowerCase()}: esperado{" "}
        {pregnancy.expectedMin.toLocaleString("pt-BR")} a{" "}
        {pregnancy.expectedMax.toLocaleString("pt-BR")} kg até a {pregnancy.gestationalWeek}ª
        semana, e {pregnancy.totalGainRecommendedMin.toLocaleString("pt-BR")} a{" "}
        {pregnancy.totalGainRecommendedMax.toLocaleString("pt-BR")} kg em toda a gestação
        (IOM, 2009).
      </p>
    </section>
  );
}

/** A measured value as a stat tile. `null` reads as not measured, like undefined. */
function Stat({
  label,
  value,
  unit,
}: {
  label: string;
  value?: number | null;
  unit?: string;
}) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      {value === undefined || value === null ? (
        <span className="stat-empty">não medido</span>
      ) : (
        <span className="stat-value">
          {num(value)}
          {unit && <span className="stat-unit">{unit}</span>}
        </span>
      )}
    </div>
  );
}

/**
 * A derived value that may not exist by rule, and not for lack of a
 * measurement. Showing the reason is what tells the professional what to do
 * about it.
 */
function DerivedStat<T extends string>({
  label,
  derived,
  map,
}: {
  label: string;
  derived: Derived<T>;
  map: Record<string, string>;
}) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      {derived.value ? (
        <span className="stat-value texto">{map[derived.value]}</span>
      ) : (
        <span className="stat-empty">{derived.unavailableBecause ?? "não disponível"}</span>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- progress

function ProgressTable({ progress }: { progress: Progress }) {
  const measures = ["weightKg", "bmi", "circumferenceWaist", "percentageFat"];
  // The first point has no comparison of its own; its figures come out of
  // the second point's comparison against it.
  const second = progress.points[1];

  return (
    <section className="card mb-3">
      <div className="card-head">
        <div>
          <h2 className="card-title">Evolução</h2>
          <p className="card-sub">
            A variação só aparece quando as duas avaliações são comparáveis: medida presente nas
            duas, e composição estimada pelo mesmo protocolo.
          </p>
        </div>
      </div>

      <TableScroll>
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
                <td className="mono">
                  {formatBr(point.date)}
                  {index === 0 && <span className="tag neutra baseline-tag">primeira</span>}
                </td>
                {measures.map((measure) => {
                  const v = point.changesPreviousFront.find((x) => x.measure === measure);
                  return (
                    <td key={measure} className="num">
                      <ValueComChange
                        change={v}
                        first={index === 0}
                        baseline={index === 0 ? baselineOf(point, second, measure) : undefined}
                      />
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </TableScroll>
    </section>
  );
}

/** What the first assessment measured, read back from the second's comparison. */
function baselineOf(first: ProgressPoint, second: ProgressPoint | undefined, measure: string) {
  const previous = second?.changesPreviousFront.find((x) => x.measure === measure)?.previous;
  if (previous !== undefined && previous !== null) return previous;
  if (measure === "weightKg") return first.weightKg;
  if (measure === "bmi") return first.bmi;
  if (measure === "percentageFat") return first.percentageFat;
  return undefined;
}

function ValueComChange({
  change,
  first,
  baseline,
}: {
  change?: Change;
  first: boolean;
  baseline?: number;
}) {
  if (first) {
    return baseline === undefined ? (
      <span className="minusculo">—</span>
    ) : (
      <span>{num(baseline)}</span>
    );
  }

  if (!change) return <span className="minusculo">—</span>;

  const current = change.current;
  if (current === undefined || current === null) {
    return <span className="minusculo">—</span>;
  }

  if (!change.comparable) {
    return (
      <span className="value-change" title={change.notes}>
        {num(current)}
        <span className="minusculo sem-comparacao">(sem comparação)</span>
      </span>
    );
  }

  return (
    <span className="value-change">
      {num(current)}
      <Delta value={change.difference ?? 0} />
    </span>
  );
}

/** The change as a badge: direction by colour, sign spelled out. */
function Delta({ value }: { value: number }) {
  const kind = value > 0 ? "up" : value < 0 ? "down" : "flat";
  const sign = value > 0 ? "+" : value < 0 ? "−" : "";
  return (
    <span className={`delta ${kind}`}>
      {sign}
      {num(Math.abs(value))}
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
  editing,
  onSave,
  onCancel,
}: {
  patientId: number;
  protocols: ProtocolInfo[];
  last?: Assessment;
  /**
   * A avaliação sendo corrigida.
   *
   * Ausente quer dizer avaliação nova. É o mesmo formulário nos dois casos
   * porque são os mesmos campos e as mesmas regras — duas telas divergiriam no
   * primeiro campo que só uma delas ganhasse.
   */
  editing?: Assessment;
  onSave: () => Promise<void>;
  onCancel: () => void;
}) {
  const today = todayIso();

  const [date, setDate] = useState(editing?.date ?? today);
  // Height rarely changes between appointments: repeating the last one saves typing.
  const [weight, setWeight] = useState(editing?.weightKg ? String(editing.weightKg) : "");
  const [height, setHeight] = useState(
    editing?.heightCm ? String(editing.heightCm) : last?.heightCm ? String(last.heightCm) : "",
  );
  /**
   * Altura travada para adulto que já foi medido.
   *
   * O cliente pede a altura "imutável para adultos" e puxada da avaliação
   * anterior. Criança continua editável — ela ainda cresce, e travar aí
   * congelaria justamente o dado que a curva de crescimento acompanha.
   */
  const heightLocked =
    !editing && last?.heightCm !== undefined && (last.ageYears ?? 0) >= 20;
  const [skinfolds, setSkinfolds] = useState<Record<string, string>>(
    () => textOf(editing?.skinfolds),
  );
  /**
   * Diâmetros, alturas de apoio e bioimpedância.
   *
   * Num único mapa de texto porque são todos "rótulo e número", sem lado e sem
   * regra própria — dar um estado a cada um seria quinze useState para dizer a
   * mesma coisa quinze vezes.
   */
  const [extras, setExtras] = useState<Record<string, string>>(() => extrasOf(editing));
  const [circumferences, setCircumferences] = useState<Record<string, string>>(
    () => circumferencesOf(editing),
  );
  const [protocol, setProtocol] = useState<string>(
    editing?.composition?.protocol ?? "",
  );
  const [equation, setEquation] = useState<string>(
    editing?.expenditureEnergy?.equation ?? "",
  );
  const [factor, setFactor] = useState(
    editing?.expenditureEnergy?.factorActivity
      ? String(editing.expenditureEnergy.factorActivity)
      : "1.55",
  );
  const [notes, setNotes] = useState(editing?.notes ?? "");
  const [pregnant, setPregnant] = useState(!!editing?.pregnancy?.value);
  const [week, setWeek] = useState(
    editing?.pregnancy?.value?.gestationalWeek
      ? String(editing.pregnancy.value.gestationalWeek)
      : "",
  );
  const [weightPre, setWeightPre] = useState(
    editing?.pregnancy?.value?.weightGestationalPreKg
      ? String(editing.pregnancy.value.weightGestationalPreKg)
      : "",
  );
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
      const body = {
        date,
        weightKg: numberOuUndefined(weight),
        heightCm: numberOuUndefined(height),
        skinfolds: numbersMap(skinfolds),
        circumferences: circumferencesToSend(circumferences),
        ...numbersMap(extras),
        protocolComposition: (protocol || undefined) as CompositionProtocol | undefined,
        equationExpenditure: (equation || undefined) as never,
        factorActivity: equation ? numberOuUndefined(factor) : undefined,
        notes: notes.trim() || undefined,
        gestationalWeek: pregnant && week ? Number(week) : undefined,
        weightGestationalPreKg:
          pregnant && weightPre ? Number(weightPre.replace(",", ".")) : undefined,
      };
      if (editing) {
        await api.anthropometry.update(editing.id, body);
      } else {
        await api.anthropometry.create(patientId, body);
      }
      feedback.confirm(
        editing
          ? `Avaliação de ${formatBr(date)} corrigida.`
          : `Avaliação de ${formatBr(date)} registrada.`,
      );
      await onSave();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "registrar a avaliação"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card form-assessment mb-3" onSubmit={send}>
      <div className="card-head">
        <div>
          <h2 className="card-title">
            {editing ? `Corrigir avaliação de ${formatBr(editing.date)}` : "Nova avaliação"}
          </h2>
          <p className="card-sub">Só a data é obrigatória; o resto entra conforme foi medido.</p>
        </div>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      <section className="card form-section">
        <div className="form-section-head">
          <h3>Medidas</h3>
        </div>
        <div className="form-grid three">
          <div className="field largo">
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
              readOnly={heightLocked}
              onChange={(e) => setHeight(e.target.value)}
              {...fields.props("heightCm")}
            />
            {heightLocked && (
              <span className="field-hint">
                Puxada da avaliação anterior. Adulto não muda de altura.
              </span>
            )}
            <FieldError field="heightCm" errors={fields.errors} />
          </div>
        </div>
      </section>

      <section className="card form-section">
        <div className="form-section-head">
          <h3>Circunferências (cm)</h3>
        </div>
        <div className="form-grid">
          {CIRCUMFERENCES.flatMap((c) =>
            (c.bilateral ? (["RIGHT", "LEFT"] as Side[]) : (["SINGLE"] as Side[])).map((side) => {
              const key = keyOf(c.site, side);
              const label =
                side === "SINGLE" ? c.label : `${c.label} (${side === "RIGHT" ? "D" : "E"})`;
              return (
                <div className="field" key={key}>
                  <label htmlFor={`circ-${key}`}>{label}</label>
                  <input
                    id={`circ-${key}`}
                    inputMode="decimal"
                    value={circumferences[key] ?? ""}
                    onChange={(e) =>
                      setCircumferences((v) => ({ ...v, [key]: e.target.value }))
                    }
                  />
                </div>
              );
            }),
          )}
        </div>
      </section>

      <section className="card form-section">
        <div className="form-section-head">
          <h3>Alturas de apoio e diâmetros ósseos (cm)</h3>
        </div>
        <div className="form-grid">
          {EXTRAS_MEASURES.map((field) => (
            <div className="field" key={field.key}>
              <label htmlFor={`ex-${field.key}`}>{field.label}</label>
              <input
                id={`ex-${field.key}`}
                inputMode="decimal"
                value={extras[field.key] ?? ""}
                onChange={(e) => setExtras((v) => ({ ...v, [field.key]: e.target.value }))}
              />
            </div>
          ))}
        </div>
      </section>

      <section className="card form-section">
        <div className="form-section-head">
          <h3>Bioimpedância</h3>
        </div>
        <p className="form-section-hint">
          Como o aparelho informou. Estes números são guardados, não recalculados.
        </p>
        <div className="form-grid">
          {BIA_FIELDS.map((field) => (
            <div className="field" key={field.key}>
              <label htmlFor={`bia-${field.key}`}>{field.label}</label>
              <input
                id={`bia-${field.key}`}
                inputMode="decimal"
                value={extras[field.key] ?? ""}
                onChange={(e) => setExtras((v) => ({ ...v, [field.key]: e.target.value }))}
              />
            </div>
          ))}
        </div>
      </section>

      <section className="card form-section">
        <div className="form-section-head">
          <h3>Dobras cutâneas (mm)</h3>
        </div>
        <div className="form-grid">
          {SKINFOLDS.map((d) => {
            const mandatory = required.has(d.key);
            return (
              <div className="field" key={d.key}>
                <label htmlFor={`dobra-${d.key}`}>
                  {d.label}
                  {mandatory && <span className="tag verde">exigida</span>}
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
      </section>

      <section className="card form-section">
        <div className="form-section-head">
          <h3>Estimativas</h3>
        </div>
        <div className="form-grid three">
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
              <span className="field-hint">
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

          {equation && (
            <div className="field">
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
        </div>

        {AREAS_HIDDEN.gestation && (
          <label className="form-check">
            <input
              type="checkbox"
              checked={pregnant}
              onChange={(e) => setPregnant(e.target.checked)}
            />
            <span>Gestante</span>
          </label>
        )}

        {AREAS_HIDDEN.gestation && pregnant && (
          <div className="form-grid three mt-3">
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
              <span className="field-hint">
                A faixa de ganho vem do IMC de antes. O IMC de hoje já embute o ganho que se quer
                avaliar.
              </span>
            </div>
          </div>
        )}
      </section>

      <div className="form-notes">
        <NotesField
          label="Observações"
          value={notes}
          onChange={setNotes}
          minHeight="7rem"
          onDemand
        />
        <FieldError field="notes" errors={fields.errors} />
      </div>

      <div className="form-actions">
        <button className="button" type="submit" disabled={sending}>
          {sending
            ? "Salvando…"
            : editing
              ? "Salvar correção"
              : "Registrar avaliação"}
        </button>
        <button
          className="button secundario"
          type="button"
          onClick={onCancel}
          disabled={sending}
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}

// -------------------------------------------------------------------- helpers

/** Os números de uma avaliação como o formulário os guarda: texto. */
function textOf(origin?: Record<string, number>): Record<string, string> {
  const output: Record<string, string> = {};
  Object.entries(origin ?? {}).forEach(([key, value]) => {
    output[key] = String(value);
  });
  return output;
}

/** Diâmetros, alturas de apoio e bioimpedância, no mapa único do formulário. */
function extrasOf(assessment?: Assessment): Record<string, string> {
  if (!assessment) return {};
  const output: Record<string, string> = {};
  [...EXTRAS_MEASURES, ...BIA_FIELDS].forEach(({ key }) => {
    const value = (assessment as unknown as Record<string, unknown>)[key];
    if (typeof value === "number") output[key] = String(value);
  });
  return output;
}

/** A lista tipada de circunferências de volta para "local|lado". */
function circumferencesOf(assessment?: Assessment): Record<string, string> {
  const output: Record<string, string> = {};
  (assessment?.circumferences ?? []).forEach((c) => {
    output[keyOf(c.site, c.side)] = String(c.valueCm);
  });
  return output;
}

function numberOuUndefined(text: string): number | undefined {
  const clean = text.replace(",", ".").trim();
  if (!clean) return undefined;
  const value = Number(clean);
  return Number.isFinite(value) ? value : undefined;
}

/** O formulário guarda texto por "local|lado"; a API quer a lista tipada. */
function circumferencesToSend(origin: Record<string, string>): CircumferenceValue[] {
  const output: CircumferenceValue[] = [];
  Object.entries(origin).forEach(([key, text]) => {
    const value = numberOuUndefined(text);
    if (value === undefined || value <= 0) return;
    const [site, side] = key.split("|");
    if (!site || !side) return;
    output.push({ site: site as CircumferenceSite, side: side as Side, valueCm: value });
  });
  return output;
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
