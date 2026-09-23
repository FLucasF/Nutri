import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Calculator, ChevronLeft, ClipboardList, Flame, Plus } from "lucide-react";

import { api } from "../api/client";
import { explainError } from "../api/errors";
import { formatBr, todayIso } from "../api/dates";
import { useFeedback } from "../components/Feedback";
import { NotesField } from "../components/RichText/NotesField";
import type {
  ActivityLevel,
  EnergyEquationName,
  EnergyOptions,
  EnergyPlan,
  EnergyPlanSummary,
  Patient,
} from "../api/types";
import { count } from "../text";

const kcal = (value: number) => Math.round(value).toLocaleString("pt-BR");

/**
 * O cálculo energético do paciente.
 *
 * Deixou de ser um punhado de campos dentro da antropometria porque o cliente
 * pediu área própria, com histórico, fator de injúria, calorias por MET e
 * programação de peso. E a faixa de peso saudável aparece aqui, que é onde ele
 * conta que precisa dela — hoje ele fecha o cardápio para ir consultar.
 */
export default function EnergyPlans() {
  const { id } = useParams();
  const patientId = Number(id);

  const [patient, setPatient] = useState<Patient | null>(null);
  const [plans, setPlans] = useState<EnergyPlanSummary[]>([]);
  const [options, setOptions] = useState<EnergyOptions | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<number | "novo" | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, list, opts] = await Promise.all([
        api.patients.find(patientId),
        api.energyPlans.ofPatient(patientId),
        api.energyPlans.options(),
      ]);
      setPatient(p);
      setPlans(list);
      setOptions(opts);
    } catch (e) {
      setError(explainError(e, "abrir os cálculos energéticos"));
    } finally {
      setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(plan: EnergyPlanSummary) {
    if (!confirm(`Excluir "${plan.name}"?`)) return;
    try {
      await api.energyPlans.remove(plan.id);
      await load();
    } catch (e) {
      setError(explainError(e, "excluir o cálculo"));
    }
  }

  if (editing !== null && options) {
    return (
      <Editor
        patientId={patientId}
        patientName={patient?.name ?? ""}
        planId={editing === "novo" ? null : editing}
        options={options}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await load();
        }}
      />
    );
  }

  return (
    <>
      <div className="header-page energia-header">
        <div>
          <Link to={`/patients/${patientId}`} className="migalha">
            <ChevronLeft aria-hidden="true" />
            {patient?.name ?? "Paciente"}
          </Link>
          <h1>Cálculo energético</h1>
          <p>{loading ? "Carregando…" : count(plans.length, "cálculo", "cálculos")}</p>
        </div>
        <div className="header-page-actions">
          <button className="button" onClick={() => setEditing("novo")} disabled={!options}>
            <Plus aria-hidden="true" />
            Novo cálculo
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {!loading && plans.length === 0 ? (
        <div className="card empty">
          <span className="empty-icon">
            <Flame aria-hidden="true" />
          </span>
          <span className="empty-title">Nenhum cálculo ainda.</span>
          <span className="empty-hint">O primeiro define a meta energética do cardápio.</span>
        </div>
      ) : (
        <div className="card-list">
          {plans.map((plan) => (
            <PlanCard
              key={plan.id}
              plan={plan}
              onOpen={() => setEditing(plan.id)}
              onRemove={() => void remove(plan)}
            />
          ))}
        </div>
      )}
    </>
  );
}

// ---------------------------------------------------------------- o cartão

/** Um cálculo salvo: nome, data, equações usadas e o número que importa. */
function PlanCard({
  plan,
  onOpen,
  onRemove,
}: {
  plan: EnergyPlanSummary;
  onOpen: () => void;
  onRemove: () => void;
}) {
  return (
    <article className="card plano-energia">
      <div className="plano-energia-body">
        <h2 className="card-title">{plan.name}</h2>
        <div className="plano-energia-meta">
          <time dateTime={plan.date}>{formatBr(plan.date)}</time>
          {plan.equations.map((equation) => (
            <span className="equacao-chip" key={equation}>
              {equation}
            </span>
          ))}
        </div>
      </div>
      <div className="stat plano-energia-kcal">
        <span className="stat-label">Prescrito</span>
        <span className="readout strong kcal-destaque">
          {kcal(plan.prescribedKcal)}
          <span> kcal</span>
        </span>
      </div>
      <div className="plano-energia-actions">
        <button className="button secundario pequeno" onClick={onOpen}>
          Abrir
        </button>
        <button className="button perigo pequeno" onClick={onRemove}>
          Excluir
        </button>
      </div>
    </article>
  );
}

// --------------------------------------------------------------------- editor

function Editor({
  patientId,
  patientName,
  planId,
  options,
  onClose,
  onSaved,
}: {
  patientId: number;
  patientName: string;
  planId: number | null;
  options: EnergyOptions;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const feedback = useFeedback();
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [date, setDate] = useState(todayIso());
  const [weight, setWeight] = useState("");
  const [height, setHeight] = useState("");
  const [activity, setActivity] = useState<ActivityLevel>("INACTIVE");
  const [injury, setInjury] = useState("1");
  const [met, setMet] = useState("");
  const [chosen, setChosen] = useState<EnergyEquationName[]>(["EER_2023"]);
  const [targetWeight, setTargetWeight] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [notes, setNotes] = useState("");

  const [result, setResult] = useState<EnergyPlan | null>(null);
  const [loading, setLoading] = useState(planId !== null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (planId === null) return;
    let live = true;
    void (async () => {
      try {
        const plan = await api.energyPlans.detail(planId);
        if (!live) return;
        setName(plan.name);
        setDate(plan.date);
        setWeight(String(plan.weightKg));
        setHeight(String(plan.heightCm));
        setActivity(plan.activityLevel);
        setInjury(String(plan.injuryFactor));
        setMet(plan.metKcal ? String(plan.metKcal) : "");
        setChosen(plan.equations.map((e) => e.equation));
        setTargetWeight(plan.targetWeightKg ? String(plan.targetWeightKg) : "");
        setTargetDate(plan.targetDate ?? "");
        setNotes(plan.notes ?? "");
        setResult(plan);
      } catch (e) {
        if (live) setError(explainError(e, "abrir o cálculo"));
      } finally {
        if (live) setLoading(false);
      }
    })();
    return () => {
      live = false;
    };
  }, [planId]);

  const number = (value: string) =>
    value.trim() ? Number(value.replace(",", ".")) : undefined;

  async function save() {
    if (chosen.length === 0) {
      setError("Escolha pelo menos uma equação. É ela que responde o gasto.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload = {
        patientId,
        name: name.trim() || undefined,
        date,
        weightKg: number(weight),
        heightCm: number(height),
        activityLevel: activity,
        injuryFactor: number(injury),
        metKcal: number(met),
        equations: chosen,
        targetWeightKg: number(targetWeight),
        targetDate: targetDate || undefined,
        notes: notes.trim() || undefined,
      };
      const saved =
        planId === null
          ? await api.energyPlans.create(payload)
          : await api.energyPlans.update(planId, payload);
      setResult(saved);
      feedback.confirm("Cálculo salvo.");
      if (planId === null) await onSaved();
    } catch (e) {
      setError(explainError(e, "salvar o cálculo"));
    } finally {
      setSaving(false);
    }
  }

  /** Alguma equação escolhida usa o fator de atividade por multiplicação? */
  const hasBasal = useMemo(
    () =>
      options.equations.some(
        (option) => chosen.includes(option.equation) && !option.total,
      ),
    [chosen, options.equations],
  );

  return (
    <>
      <div className="header-page energia-header">
        <div>
          <button type="button" className="migalha link-voltar" onClick={onClose}>
            <ChevronLeft aria-hidden="true" />
            Cálculos de {patientName}
          </button>
          <h1>{planId === null ? "Novo cálculo" : name || "Cálculo energético"}</h1>
        </div>
        <div className="header-page-actions">
          <button className="button secundario" onClick={onClose} disabled={saving}>
            Voltar
          </button>
          <button className="button" onClick={() => void save()} disabled={saving || loading}>
            <Calculator aria-hidden="true" />
            {saving ? "Calculando…" : "Calcular e salvar"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p className="empty">Carregando…</p>
      ) : (
        <div className="energia-layout">
          <section className="card energia-form">
            <div className="energia-secao">
              <div className="energia-campos">
                <div className="field">
                  <label htmlFor="en-nome">Nome</label>
                  <input
                    id="en-nome"
                    type="text"
                    value={name}
                    placeholder={`Cálculo de ${patientName}`}
                    onChange={(e) => setName(e.target.value)}
                    maxLength={150}
                  />
                </div>
                <div className="field">
                  <label htmlFor="en-data">Data</label>
                  <input
                    id="en-data"
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor="en-peso">Peso (kg)</label>
                  <input
                    id="en-peso"
                    inputMode="decimal"
                    value={weight}
                    placeholder="80"
                    onChange={(e) => setWeight(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor="en-altura">Altura (cm)</label>
                  <input
                    id="en-altura"
                    inputMode="decimal"
                    value={height}
                    placeholder="180"
                    onChange={(e) => setHeight(e.target.value)}
                  />
                </div>
              </div>
            </div>

            <div className="energia-secao">
              <div className="card-head">
                <div>
                  <h2 className="card-title">Equações</h2>
                  <p className="card-sub">Escolhendo mais de uma, o resultado é a média entre elas.</p>
                </div>
              </div>
              <div className="equacoes">
                {options.equations.map((option) => (
                  <label className="equacao" key={option.equation}>
                    <input
                      type="checkbox"
                      aria-label={option.description}
                      checked={chosen.includes(option.equation)}
                      onChange={(e) =>
                        setChosen((old) =>
                          e.target.checked
                            ? [...old, option.equation]
                            : old.filter((name) => name !== option.equation),
                        )
                      }
                    />
                    <span className="equacao-texto">
                      <strong>{option.description}</strong>
                      <span className="equacao-nota">
                        {option.total
                          ? "Já inclui o nível de atividade"
                          : "Gasto basal — recebe o fator de atividade"}
                      </span>
                    </span>
                  </label>
                ))}
              </div>

              <div className="energia-campos mt-3">
                <div className="field largo">
                  <label htmlFor="en-atividade">Nível de atividade física</label>
                  <select
                    id="en-atividade"
                    value={activity}
                    onChange={(e) => setActivity(e.target.value as ActivityLevel)}
                  >
                    {options.activityLevels.map((level) => (
                      <option key={level.level} value={level.level}>
                        {level.description}
                      </option>
                    ))}
                  </select>
                  {!hasBasal && (
                    <span className="field-hint">
                      As equações escolhidas já contam a atividade nos próprios coeficientes.
                    </span>
                  )}
                </div>
                <div className="field fator">
                  <label htmlFor="en-injuria">Fator de injúria</label>
                  <input
                    id="en-injuria"
                    inputMode="decimal"
                    value={injury}
                    onChange={(e) => setInjury(e.target.value)}
                  />
                  <span className="field-hint">1,00 fora de estresse metabólico.</span>
                </div>
                <div className="field fator">
                  <label htmlFor="en-met">Calorias por MET (kcal/dia)</label>
                  <input
                    id="en-met"
                    inputMode="decimal"
                    value={met}
                    placeholder="0"
                    onChange={(e) => setMet(e.target.value)}
                  />
                </div>
              </div>
            </div>

            <div className="energia-secao">
              <div className="card-head">
                <div>
                  <h2 className="card-title">Programar o peso</h2>
                  <p className="card-sub">
                    Pelo Valor Energético do Tecido Adiposo. Deixe em branco para não programar.
                  </p>
                </div>
              </div>
              <div className="energia-campos">
                <div className="field">
                  <label htmlFor="en-peso-alvo">Peso desejado (kg)</label>
                  <input
                    id="en-peso-alvo"
                    inputMode="decimal"
                    value={targetWeight}
                    onChange={(e) => setTargetWeight(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor="en-data-alvo">Até a data</label>
                  <input
                    id="en-data-alvo"
                    type="date"
                    value={targetDate}
                    onChange={(e) => setTargetDate(e.target.value)}
                  />
                </div>
              </div>
            </div>

            <div className="energia-secao">
              <NotesField
                label="Observações"
                value={notes}
                onChange={setNotes}
                minHeight="7rem"
                onDemand
              />
            </div>
          </section>

          <Result
            plan={result}
            onToPlan={(target) =>
              navigate(`/prescriptions/new?patientId=${patientId}&target=${Math.round(target)}`)
            }
          />
        </div>
      )}
    </>
  );
}

// -------------------------------------------------------------- o resultado

/**
 * O painel de resultado, fixo ao lado do formulário.
 *
 * Fica à direita e acompanha a rolagem pelo mesmo motivo que o cliente pede no
 * cardápio: ele quer ver o que a alteração causou sem descer a página. No
 * telefone, onde não há lado, o resultado calculado vem antes do formulário e
 * o convite para calcular fica depois dele.
 */
function Result({
  plan,
  onToPlan,
}: {
  plan: EnergyPlan | null;
  onToPlan: (kcal: number) => void;
}) {
  if (!plan) {
    return (
      <aside className="card energia-resultado vazio">
        <div className="empty">
          <span className="empty-icon">
            <Flame aria-hidden="true" />
          </span>
          <span className="empty-hint">Preencha e calcule para ver o resultado aqui.</span>
        </div>
      </aside>
    );
  }

  return (
    <aside className="card energia-resultado">
      <div className="stat energia-prescrito">
        <span className="stat-label">Prescrito</span>
        <p className="energia-kcal readout strong">
          {kcal(plan.prescribedKcal)}
          <span> kcal/dia</span>
        </p>
      </div>

      <dl className="energia-linhas">
        {plan.equations.map((result) => (
          <div key={result.equation}>
            <dt>{result.description}</dt>
            <dd className="readout strong">
              {kcal(result.totalKcal)} kcal
              {result.basalKcal !== undefined && (
                <span className="minusculo"> (basal {kcal(result.basalKcal)})</span>
              )}
            </dd>
          </div>
        ))}
        {plan.equations.length > 1 && (
          <div className="energia-media">
            <dt>Média</dt>
            <dd className="readout strong">{kcal(plan.averageKcal)} kcal</dd>
          </div>
        )}
        {plan.adjustmentKcal !== undefined && plan.adjustmentKcal !== null && (
          <div>
            <dt>Programação de peso</dt>
            <dd className={plan.adjustmentKcal < 0 ? "readout strong negativo" : "readout strong"}>
              {plan.adjustmentKcal > 0 ? "+" : ""}
              {kcal(plan.adjustmentKcal)} kcal
            </dd>
          </div>
        )}
      </dl>

      {plan.healthyWeight && (
        <div className="stat energia-faixa">
          <span className="stat-label">Faixa de peso saudável</span>
          <p className="readout strong">
            {plan.healthyWeight.minimumKg.toLocaleString("pt-BR")} a{" "}
            {plan.healthyWeight.maximumKg.toLocaleString("pt-BR")} kg
          </p>
          <span className="minusculo">
            IMC de {plan.healthyWeight.bmiMinimum.toLocaleString("pt-BR")} a{" "}
            {plan.healthyWeight.bmiMaximum.toLocaleString("pt-BR")} kg/m², para{" "}
            {plan.heightCm.toLocaleString("pt-BR")} cm.
          </span>
        </div>
      )}

      <button className="button w-full energia-montar" onClick={() => onToPlan(plan.prescribedKcal)}>
        <ClipboardList aria-hidden="true" />
        Montar cardápio com esta meta
      </button>
    </aside>
  );
}
