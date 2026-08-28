import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import FoodSearch from "../componentes/FoodSearch";
import { ErrorApi, api } from "../api/client";
import { explainError, whereLook } from "../api/errors";
import { useFeedback } from "../componentes/Feedback";
import { COLORS_MACRO, MACROS_PRINCIPAIS, NUTRIENTS, formatNutrient, labelDe } from "../api/nutrients";
import type {
  FoodDetail,
  FoodSummary,
  PrescriptionMethod,
  Handout,
  PlanHandout,
  PatientSummary,
  PlanRequest,
  PlanResponse,
  Total,
} from "../api/types";

/** Local editing state — it mirrors the body the API expects. */
interface ItemEdit {
  key: string;
  foodId?: number;
  measureId?: number;
  description: string;
  quantity: string;
  notes: string;
  /** Portions of the food, loaded when it is chosen. */
  measures: { id: number; description: string; grams: number; standard: boolean }[];
  substitutions: { key: string; description: string; quantity: string; foodId?: number; measureId?: number }[];
}

interface MealEdit {
  key: string;
  name: string;
  time: string;
  notes: string;
  items: ItemEdit[];
}

let counter = 0;
const novaKey = () => `k${++counter}`;

const MEALS_SUGGESTED = [
  { name: "Café da manhã", time: "07:30" },
  { name: "Lanche da manhã", time: "10:00" },
  { name: "Almoço", time: "12:30" },
  { name: "Lanche da tarde", time: "16:00" },
  { name: "Jantar", time: "19:30" },
  { name: "Ceia", time: "21:30" },
];

export default function PlanEditor() {
  const { id } = useParams();
  const [parameters] = useSearchParams();
  const navigate = useNavigate();
  const planId = id ? Number(id) : undefined;

  const [plan, setPlan] = useState<PlanResponse | null>(null);
  const [patients, setPatients] = useState<PatientSummary[]>([]);

  const [title, setTitle] = useState("");
  const [patientId, setPatientId] = useState(parameters.get("patientId") ?? "");
  const [method, setMethod] = useState<PrescriptionMethod>("FOODS");
  const [template, setTemplate] = useState(false);
  // The goal can arrive through the URL, coming from the energy expenditure
  // estimated in the anthropometry (RF68): without that the number is read on
  // one screen and retyped on another, which is where the typo gets in.
  const [targetEnergy, setTargetEnergy] = useState(parameters.get("target") ?? "");
  const [validityStart, setValidityStart] = useState("");
  const [validityEnd, setValidityEnd] = useState("");
  const [handouts, setHandouts] = useState("");
  const [internalNotes, setInternalNotes] = useState("");
  const [meals, setMeals] = useState<MealEdit[]>([]);

  const [loading, setLoading] = useState(!!planId);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const planWarnings = useFeedback();
  /** Validation errors of the plan, one per row and already located on screen. */
  const [problems, setProblems] = useState<string[]>([]);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    api.patients
      .list({ active: true, size: 200 })
      .then((p) => setPatients(p.content))
      .catch(() => setPatients([]));
  }, []);

  /**
   * Reloads the portions of the foods already used in the plan.
   *
   * The saved plan keeps only the id of the measure, not the food's list of
   * portions. Without fetching them back, the measure picker would open empty
   * and the weight shown on the row would fall to the raw quantity — "4 g"
   * instead of "4 spoons = 100 g". The server total stays right; what breaks is
   * the reading on screen.
   */
  const reloadMeasures = useCallback(async (data: PlanResponse) => {
    const ids = [
      ...new Set(
        data.meals.flatMap((r) =>
          r.items.map((i) => i.foodId).filter((x): x is number => x !== undefined),
        ),
      ),
    ];
    if (ids.length === 0) return;

    const details = await Promise.all(
      ids.map((idFood) =>
        api.foods.detail(idFood).catch(() => null),
      ),
    );
    const byFood = new Map<number, ItemEdit["measures"]>();
    details.forEach((detail) => {
      if (detail) {
        byFood.set(
          detail.id,
          detail.measures.map((m) => ({
            id: m.id,
            description: m.description,
            grams: m.grams,
            standard: m.standard,
          })),
        );
      }
    });

    setMeals((current) =>
      current.map((r) => ({
        ...r,
        items: r.items.map((i) =>
          i.foodId && byFood.has(i.foodId)
            ? { ...i, measures: byFood.get(i.foodId)! }
            : i,
        ),
      })),
    );
  }, []);

  const applyPlan = useCallback((data: PlanResponse) => {
    setPlan(data);
    setTitle(data.title);
    setPatientId(data.patientId ? String(data.patientId) : "");
    setMethod(data.method);
    setTemplate(data.template);
    setTargetEnergy(data.targetEnergyKcal ? String(data.targetEnergyKcal) : "");
    setValidityStart(data.validityStart ?? "");
    setValidityEnd(data.validityEnd ?? "");
    setHandouts(data.handouts ?? "");
    setInternalNotes(data.internalNotes ?? "");
    setMeals(
      data.meals.map((r) => ({
        key: novaKey(),
        name: r.name,
        time: r.time?.slice(0, 5) ?? "",
        notes: r.notes ?? "",
        items: r.items.map((i) => ({
          key: novaKey(),
          foodId: i.foodId,
          measureId: i.measureId,
          description: i.description,
          quantity: i.quantity !== undefined && i.quantity !== null ? String(i.quantity) : "",
          notes: i.notes ?? "",
          measures: [],
          substitutions: i.substitutions.map((e) => ({
            key: novaKey(),
            description: e.description,
            quantity: "",
            foodId: e.foodId,
          })),
        })),
      })),
    );
    setDirty(false);
    void reloadMeasures(data);
  }, [reloadMeasures]);

  useEffect(() => {
    if (!planId) return;
    setLoading(true);
    api.prescriptions
      .detail(planId)
      .then(applyPlan)
      .catch((e) => setError(explainError(e, "abrir o plano")))
      .finally(() => setLoading(false));
  }, [planId, applyPlan]);

  function buildBody(): PlanRequest {
    return {
      title: title.trim(),
      patientId: template ? undefined : patientId ? Number(patientId) : undefined,
      method,
      template,
      targetEnergyKcal: targetEnergy ? Number(targetEnergy.replace(",", ".")) : undefined,
      validityStart: validityStart || undefined,
      validityEnd: validityEnd || undefined,
      handouts: handouts.trim() || undefined,
      internalNotes: internalNotes.trim() || undefined,
      meals: meals.map((r) => ({
        name: r.name.trim() || "Refeição",
        time: r.time ? `${r.time}:00` : undefined,
        notes: r.notes.trim() || undefined,
        items: r.items
          .filter((i) => i.foodId || i.description.trim())
          .map((i) => ({
            foodId: i.foodId,
            measureId: i.measureId,
            description: i.description.trim() || undefined,
            quantity: i.quantity ? Number(i.quantity.replace(",", ".")) : undefined,
            notes: i.notes.trim() || undefined,
            substitutions:
              method === "SUBSTITUTIONS"
                ? i.substitutions
                    .filter((e) => e.description.trim())
                    .map((e) => ({
                      foodId: e.foodId,
                      measureId: e.measureId,
                      description: e.description.trim(),
                      quantity: e.quantity ? Number(e.quantity.replace(",", ".")) : undefined,
                    }))
                : undefined,
          })),
      })),
    };
  }

  async function save() {
    setError(null);
    setSaving(true);
    try {
      const body = buildBody();
      const saved = planId
        ? await api.prescriptions.update(planId, body)
        : await api.prescriptions.create(body);

      applyPlan(saved);
      setProblems([]);
      planWarnings.confirm("Plano salvo.");
      if (!planId) {
        navigate(`/prescriptions/${saved.id}`, { replace: true });
      }
    } catch (e) {
      // The plan has no one field per error: the validation points inside the
      // meals, and the technical path does not say where to look on screen.
      if (e instanceof ErrorApi && e.isValidation) {
        setProblems(
          e.fields!.map((c) => {
            const where = whereLook(c.field, (i) => meals[i]?.name);
            return where ? `${where}: ${c.message}` : c.message;
          }),
        );
      } else {
        setError(explainError(e, "salvar o plano"));
      }
    } finally {
      setSaving(false);
    }
  }

  async function action(run: () => Promise<PlanResponse>, message: string) {
    setError(null);
    try {
      applyPlan(await run());
      planWarnings.confirm(message);
    } catch (e) {
      setError(explainError(e, "concluir a ação"));
    }
  }

  // ---------------------------------------------------------------- handling

  function changeMeal(key: string, change: Partial<MealEdit>) {
    setDirty(true);
    setMeals((current) => current.map((r) => (r.key === key ? { ...r, ...change } : r)));
  }

  function changeItem(keyMeal: string, keyItem: string, change: Partial<ItemEdit>) {
    setDirty(true);
    setMeals((current) =>
      current.map((r) =>
        r.key !== keyMeal
          ? r
          : { ...r, items: r.items.map((i) => (i.key === keyItem ? { ...i, ...change } : i)) },
      ),
    );
  }

  function addMeal(name = "Nova refeição", time = "") {
    setDirty(true);
    setMeals((current) => [
      ...current,
      { key: novaKey(), name, time, notes: "", items: [] },
    ]);
  }

  function removeMeal(key: string) {
    setDirty(true);
    setMeals((current) => current.filter((r) => r.key !== key));
  }

  function addItem(keyMeal: string) {
    setDirty(true);
    setMeals((current) =>
      current.map((r) =>
        r.key !== keyMeal
          ? r
          : {
              ...r,
              items: [
                ...r.items,
                {
                  key: novaKey(),
                  description: "",
                  quantity: "",
                  notes: "",
                  measures: [],
                  substitutions: [],
                },
              ],
            },
      ),
    );
  }

  function removeItem(keyMeal: string, keyItem: string) {
    setDirty(true);
    setMeals((current) =>
      current.map((r) =>
        r.key !== keyMeal ? r : { ...r, items: r.items.filter((i) => i.key !== keyItem) },
      ),
    );
  }

  /** On choosing a food, it already brings the portions and preselects the standard one. */
  async function chooseFood(keyMeal: string, keyItem: string, summary: FoodSummary) {
    changeItem(keyMeal, keyItem, {
      foodId: summary.id,
      description: summary.description,
    });
    try {
      const detail: FoodDetail = await api.foods.detail(summary.id);
      const standard = detail.measures.find((m) => m.standard) ?? detail.measures[0];
      changeItem(keyMeal, keyItem, {
        measures: detail.measures.map((m) => ({
          id: m.id,
          description: m.description,
          grams: m.grams,
          standard: m.standard,
        })),
        measureId: standard?.id,
        quantity: standard ? "1" : "100",
      });
    } catch {
      changeItem(keyMeal, keyItem, { measures: [], quantity: "100" });
    }
  }

  const totalSaved = plan?.dayTotal;
  const totalsByMeal = useMemo(() => {
    const map = new Map<number, Total>();
    plan?.meals.forEach((r, index) => map.set(index, r.total));
    return map;
  }, [plan]);

  if (loading) return <p className="loading">Loading…</p>;

  const podeEdit = !plan || plan.status !== "CLOSED";

  return (
    <>
      <div className="header-page">
        <div>
          <Link to="/prescriptions" className="minusculo">
            ← Prescrições
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>{planId ? title || "Plano" : "Novo plano"}</h1>
          <p>
            {plan ? (
              <>
                {plan.statusDescription} · {plan.methodDescription}
                {plan.patientName ? ` · ${plan.patientName}` : ""}
              </>
            ) : (
              "Monte as refeições e salve para ver os totais calculados."
            )}
          </p>
        </div>
        <div className="row">
          {dirty && <span className="tag ambar">alterações não salvas</span>}
          <button className="button" onClick={save} disabled={saving || !podeEdit}>
            {saving ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </div>

      {error && <div className="warning error" style={{ marginBottom: "0.9rem" }}>{error}</div>}

      {problems.length > 0 && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
          <strong>O plano não foi salvo. Corrija {problems.length === 1 ? "isto" : "estes pontos"}:</strong>
          <ul className="problems-list">
            {problems.map((p, i) => (
              <li key={i}>{p}</li>
            ))}
          </ul>
        </div>
      )}
      {!podeEdit && (
        <div className="warning attention" style={{ marginBottom: "0.9rem" }}>
          Este plano está encerrado e não aceita alterações. Duplique-o para criar uma nova versão.
        </div>
      )}

      <div className="editor-plan">
        <div>
          {/* ------------------------------------------------------- plan header */}
          <div className="card" style={{ marginBottom: "0.9rem" }}>
            <div className="grid two">
              <div className="field" style={{ gridColumn: "span 2" }}>
                <label htmlFor="pl-titulo">Título do plano</label>
                <input
                  id="pl-titulo"
                  value={title}
                  onChange={(e) => {
                    setTitle(e.target.value);
                    setDirty(true);
                  }}
                  placeholder="Plano de emagrecimento — fase 1"
                  required
                />
              </div>

              <div className="field">
                <label htmlFor="pl-paciente">Patient</label>
                <select
                  id="pl-paciente"
                  value={patientId}
                  disabled={template}
                  onChange={(e) => {
                    setPatientId(e.target.value);
                    setDirty(true);
                  }}
                >
                  <option value="">Select…</option>
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
                {template && <span className="minusculo">Modelo não pertence a um paciente.</span>}
              </div>

              <div className="field">
                <label htmlFor="pl-metodo">Método</label>
                <select
                  id="pl-metodo"
                  value={method}
                  onChange={(e) => {
                    setMethod(e.target.value as PrescriptionMethod);
                    setDirty(true);
                  }}
                >
                  <option value="FOODS">Por alimentos</option>
                  <option value="SUBSTITUTIONS">By substitutions (com substituições)</option>
                  <option value="QUALITATIVE">Qualitative (without quantify)</option>
                </select>
              </div>

              <div className="field">
                <label htmlFor="pl-meta">Target energética (kcal/day)</label>
                <input
                  id="pl-meta"
                  inputMode="decimal"
                  value={targetEnergy}
                  onChange={(e) => {
                    setTargetEnergy(e.target.value);
                    setDirty(true);
                  }}
                  placeholder="2000"
                />
              </div>

              <div className="field">
                <label>Vigência</label>
                <div className="dates-par">
                  <input
                    type="date"
                    value={validityStart}
                    onChange={(e) => {
                      setValidityStart(e.target.value);
                      setDirty(true);
                    }}
                    aria-label="Início da vigência"
                  />
                  <input
                    type="date"
                    value={validityEnd}
                    onChange={(e) => {
                      setValidityEnd(e.target.value);
                      setDirty(true);
                    }}
                    aria-label="Fim da vigência"
                  />
                </div>
              </div>
            </div>

            <label className="row" style={{ gap: "0.4rem", marginTop: "0.8rem" }}>
              <input
                type="checkbox"
                checked={template}
                onChange={(e) => {
                  setTemplate(e.target.checked);
                  setDirty(true);
                }}
                style={{ width: "auto" }}
              />
              <span className="discreto">
                Save like template reaproveitável (without patient linked)
              </span>
            </label>

            <div className="field" style={{ marginTop: "0.8rem" }}>
              <label htmlFor="pl-orient">Orientações ao paciente</label>
              <textarea
                id="pl-orient"
                rows={2}
                value={handouts}
                onChange={(e) => {
                  setHandouts(e.target.value);
                  setDirty(true);
                }}
                placeholder="Aparece no plano que o paciente abre."
              />
            </div>

            <div className="field" style={{ marginTop: "0.6rem" }}>
              <label htmlFor="pl-interno">Anotações internas</label>
              <textarea
                id="pl-interno"
                rows={2}
                value={internalNotes}
                onChange={(e) => {
                  setInternalNotes(e.target.value);
                  setDirty(true);
                }}
                placeholder="Nunca sai no link do paciente."
              />
            </div>
          </div>

          {/* ----------------------------------------------------------- meals */}
          {meals.length === 0 && (
            <div className="card" style={{ marginBottom: "0.9rem" }}>
              <p className="discreto" style={{ marginTop: 0 }}>
                Comece adicionando as refeições do dia:
              </p>
              <div className="row">
                {MEALS_SUGGESTED.map((s) => (
                  <button
                    key={s.name}
                    className="button secundario pequeno"
                    onClick={() => addMeal(s.name, s.time)}
                  >
                    + {s.name}
                  </button>
                ))}
              </div>
              <div className="row" style={{ marginTop: "0.6rem" }}>
                <button
                  className="button secundario pequeno"
                  onClick={() => MEALS_SUGGESTED.forEach((s) => addMeal(s.name, s.time))}
                >
                  Adicionar as seis
                </button>
              </div>
            </div>
          )}

          {meals.map((meal, index) => (
            <BlockMeal
              key={meal.key}
              meal={meal}
              method={method}
              total={totalsByMeal.get(index)}
              onlyRead={!podeEdit}
              onChange={(change) => changeMeal(meal.key, change)}
              onRemove={() => removeMeal(meal.key)}
              onAddItem={() => addItem(meal.key)}
              onChangeItem={(keyItem, change) => changeItem(meal.key, keyItem, change)}
              onRemoveItem={(keyItem) => removeItem(meal.key, keyItem)}
              onChooseFood={(keyItem, summary) =>
                chooseFood(meal.key, keyItem, summary)
              }
            />
          ))}

          {meals.length > 0 && podeEdit && (
            <button className="button secundario" onClick={() => addMeal()}>
              + Add refeição
            </button>
          )}
        </div>

        {/* ------------------------------------------------------- totals panel */}
        <aside className="panel-totals">
          <PanelTotals total={totalSaved} dirty={dirty} target={targetEnergy} />
          {plan && <PanelHandouts planId={plan.id} />}
          {plan && !plan.template && <PanelPublication plan={plan} onRun={action} />}
          {plan && (
            <div className="card">
              <h3>Ações</h3>
              <div className="row" style={{ marginTop: "0.5rem" }}>
                <button
                  className="button secundario pequeno"
                  onClick={() =>
                    action(
                      () => api.prescriptions.duplicate(plan.id, plan.patientId ?? undefined),
                      "Cópia criada como rascunho.",
                    ).then(() => navigate("/prescriptions"))
                  }
                >
                  Duplicate
                </button>
                <button
                  className="button perigo pequeno"
                  onClick={async () => {
                    if (!confirm("Remover este plano definitivamente?")) return;
                    await api.prescriptions.remove(plan.id);
                    navigate("/prescriptions");
                  }}
                >
                  Remove
                </button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}

// --------------------------------------------------------------- subcomponentes

function BlockMeal({
  meal,
  method,
  total,
  onlyRead,
  onChange,
  onRemove,
  onAddItem,
  onChangeItem,
  onRemoveItem,
  onChooseFood,
}: {
  meal: MealEdit;
  method: PrescriptionMethod;
  total?: Total;
  onlyRead: boolean;
  onChange: (change: Partial<MealEdit>) => void;
  onRemove: () => void;
  onAddItem: () => void;
  onChangeItem: (keyItem: string, change: Partial<ItemEdit>) => void;
  onRemoveItem: (keyItem: string) => void;
  onChooseFood: (keyItem: string, summary: FoodSummary) => void;
}) {
  const quantifies = method !== "QUALITATIVE";

  return (
    <div className="meal">
      <div className="meal-top">
        <input
          type="text"
          value={meal.name}
          onChange={(e) => onChange({ name: e.target.value })}
          disabled={onlyRead}
          aria-label="Nome da refeição"
        />
        <input
          type="time"
          value={meal.time}
          onChange={(e) => onChange({ time: e.target.value })}
          disabled={onlyRead}
          aria-label="Horário"
        />
        {total && total.composition.energyKcal !== undefined && (
          <span className="tag verde">{Math.round(total.composition.energyKcal)} kcal</span>
        )}
        {!onlyRead && (
          <button className="button perigo pequeno" onClick={onRemove}>
            Remove
          </button>
        )}
      </div>

      {meal.items.length === 0 ? (
        <p className="empty" style={{ padding: "1rem" }}>
          Busque um alimento abaixo para montar esta refeição.
        </p>
      ) : (
        meal.items.map((item) => (
          <RowItem
            key={item.key}
            item={item}
            quantifies={quantifies}
            admitsSubstitutions={method === "SUBSTITUTIONS"}
            onlyRead={onlyRead}
            onChange={(change) => onChangeItem(item.key, change)}
            onRemove={() => onRemoveItem(item.key)}
            onChooseFood={(summary) => onChooseFood(item.key, summary)}
          />
        ))
      )}

      {!onlyRead && (
        <div style={{ padding: "0.6rem 0.9rem" }}>
          <button className="button secundario pequeno" onClick={onAddItem}>
            + Add item
          </button>
        </div>
      )}
    </div>
  );
}

function RowItem({
  item,
  quantifies,
  admitsSubstitutions,
  onlyRead,
  onChange,
  onRemove,
  onChooseFood,
}: {
  item: ItemEdit;
  quantifies: boolean;
  admitsSubstitutions: boolean;
  onlyRead: boolean;
  onChange: (change: Partial<ItemEdit>) => void;
  onRemove: () => void;
  onChooseFood: (summary: FoodSummary) => void;
}) {
  const measureEscolhida = item.measures.find((m) => m.id === item.measureId);
  const quantity = Number(item.quantity.replace(",", "."));
  const grams =
    measureEscolhida && Number.isFinite(quantity)
      ? measureEscolhida.grams * quantity
      : Number.isFinite(quantity)
        ? quantity
        : undefined;

  return (
    <div className="item-row">
      <div className="description-item">
        {item.foodId ? (
          <>
            <strong>{item.description}</strong>
            {!onlyRead && (
              <small>
                <button
                  className="button secundario pequeno"
                  style={{ marginTop: "0.2rem" }}
                  onClick={() => onChange({ foodId: undefined, measureId: undefined, measures: [] })}
                >
                  trocar alimento
                </button>
              </small>
            )}
          </>
        ) : (
          <FoodSearch
            freeValue={item.description}
            onFreeType={(text) => onChange({ description: text })}
            onChoose={onChooseFood}
            disabled={onlyRead}
          />
        )}
      </div>

      {quantifies ? (
        <>
          <input
            inputMode="decimal"
            value={item.quantity}
            onChange={(e) => onChange({ quantity: e.target.value })}
            placeholder="qtd"
            disabled={onlyRead}
            aria-label="Quantidade"
          />
          <select
            value={item.measureId ?? ""}
            onChange={(e) =>
              onChange({ measureId: e.target.value ? Number(e.target.value) : undefined })
            }
            disabled={onlyRead || item.measures.length === 0}
            aria-label="Medida"
          >
            <option value="">grams</option>
            {item.measures.map((m) => (
              <option key={m.id} value={m.id}>
                {m.description}
              </option>
            ))}
          </select>
          <span className="mono discreto" style={{ textAlign: "right" }}>
            {grams !== undefined && grams > 0 ? `${round(grams)} g` : "—"}
          </span>
        </>
      ) : (
        <>
          <span className="discreto" style={{ gridColumn: "span 3" }}>
            à vontade / without quantify
          </span>
        </>
      )}

      {!onlyRead ? (
        <button className="button perigo pequeno" onClick={onRemove} aria-label="Remover item">
          ×
        </button>
      ) : (
        <span />
      )}

      {admitsSubstitutions && item.foodId && (
        <div style={{ gridColumn: "1 / -1", paddingLeft: "0.4rem" }}>
          <Substitutions item={item} onlyRead={onlyRead} onChange={onChange} />
        </div>
      )}
    </div>
  );
}

function Substitutions({
  item,
  onlyRead,
  onChange,
}: {
  item: ItemEdit;
  onlyRead: boolean;
  onChange: (change: Partial<ItemEdit>) => void;
}) {
  return (
    <div style={{ borderLeft: "2px solid var(--line-strong)", paddingLeft: "0.6rem", marginTop: "0.3rem" }}>
      <span className="minusculo">Substituições</span>
      {item.substitutions.map((substitution) => (
        <div className="row" key={substitution.key} style={{ gap: "0.4rem", marginTop: "0.25rem" }}>
          <input
            style={{ flex: 2, minWidth: 140 }}
            value={substitution.description}
            placeholder="1 tapioca média"
            disabled={onlyRead}
            onChange={(e) =>
              onChange({
                substitutions: item.substitutions.map((x) =>
                  x.key === substitution.key ? { ...x, description: e.target.value } : x,
                ),
              })
            }
          />
          <input
            style={{ width: 90 }}
            inputMode="decimal"
            value={substitution.quantity}
            placeholder="g"
            disabled={onlyRead}
            onChange={(e) =>
              onChange({
                substitutions: item.substitutions.map((x) =>
                  x.key === substitution.key ? { ...x, quantity: e.target.value } : x,
                ),
              })
            }
          />
          {!onlyRead && (
            <button
              className="button perigo pequeno"
              onClick={() =>
                onChange({
                  substitutions: item.substitutions.filter((x) => x.key !== substitution.key),
                })
              }
            >
              ×
            </button>
          )}
        </div>
      ))}
      {!onlyRead && (
        <button
          className="button secundario pequeno"
          style={{ marginTop: "0.3rem" }}
          onClick={() =>
            onChange({
              substitutions: [
                ...item.substitutions,
                { key: novaKey(), description: "", quantity: "" },
              ],
            })
          }
        >
          + substituição
        </button>
      )}
    </div>
  );
}


function PanelTotals({ total, dirty, target }: { total?: Total; dirty: boolean; target: string }) {
  if (!total) {
    return (
      <div className="card">
        <h3>Totais do dia</h3>
        <p className="discreto" style={{ marginBottom: 0 }}>
          Salve o plano para calcular.
        </p>
      </div>
    );
  }

  const distribution = total.distribution;
  const targetNumber = target ? Number(target.replace(",", ".")) : undefined;

  return (
    <div className="card">
      <h3>Totais do dia</h3>

      {dirty && (
        <div className="warning attention" style={{ margin: "0.5rem 0", fontSize: "0.8rem" }}>
          Há alterações não salvas. Os números abaixo referem-se à última versão salva.
        </div>
      )}

      <div style={{ marginTop: "0.5rem" }}>
        {MACROS_PRINCIPAIS.map((key) => {
          const definition = NUTRIENTS.find((n) => n.key === key)!;
          const value = total.composition[key];
          const missing = value === undefined || value === null;
          return (
            <div key={key} className={`nutrient-row ${missing ? "missing" : ""}`}>
              <span>{definition.label}</span>
              <span>{formatNutrient(value, definition.unit)}</span>
            </div>
          );
        })}
      </div>

      {distribution && (
        <div style={{ marginTop: "0.9rem" }}>
          <span className="minusculo">Distribuição energética</span>
          <div className="bar-macro" style={{ marginTop: "0.3rem" }}>
            <i style={{ width: `${distribution.proteinPct}%`, background: COLORS_MACRO.protein }} />
            <i style={{ width: `${distribution.carbohydratePct}%`, background: COLORS_MACRO.carbohydrate }} />
            <i style={{ width: `${distribution.lipidPct}%`, background: COLORS_MACRO.lipid }} />
          </div>
          <div className="legenda-macro" style={{ marginTop: "0.35rem" }}>
            <span>
              <i style={{ background: COLORS_MACRO.protein }} />
              Prot. {distribution.proteinPct}%
            </span>
            <span>
              <i style={{ background: COLORS_MACRO.carbohydrate }} />
              Carb. {distribution.carbohydratePct}%
            </span>
            <span>
              <i style={{ background: COLORS_MACRO.lipid }} />
              Gord. {distribution.lipidPct}%
            </span>
          </div>
        </div>
      )}

      {total.adequacyEnergyPct !== undefined && targetNumber ? (
        <p className="discreto" style={{ marginTop: "0.8rem", marginBottom: 0 }}>
          <strong>{total.adequacyEnergyPct}%</strong> da meta de {targetNumber} kcal.
        </p>
      ) : null}

      {/*
        The caveat matters: when some of the items have no determined value for
        the nutrient in the source, the total is a floor, and showing it as an
        exact number would mislead the professional.
      */}
      {total.nutrientsIncomplete.length > 0 && (
        <div className="warning attention" style={{ marginTop: "0.8rem", fontSize: "0.8rem" }}>
          <strong>Total parcial.</strong> Nem todos os itens têm dado para{" "}
          {total.nutrientsIncomplete.slice(0, 4).map(labelDe).join(", ")}
          {total.nutrientsIncomplete.length > 4
            ? ` e mais ${total.nutrientsIncomplete.length - 4}`
            : ""}
          . Os valores são um piso, não um total exato.
        </div>
      )}

      {total.itemsOutsideCalculation > 0 && (
        <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
          {total.itemsOutsideCalculation} item(ns) without quantity não entraram no cálculo.
        </p>
      )}
    </div>
  );
}

/**
 * Handouts attached to this plan.
 *
 * The text is copied from the library at the moment of attaching, and not
 * referenced: correcting a template later must not change what the patient has
 * already received. The copy is also what allows adapting the text to this
 * patient without dirtying the template.
 */
function PanelHandouts({ planId }: { planId: number }) {
  const [attached, setAttached] = useState<PlanHandout[]>([]);
  const [library, setLibrary] = useState<Handout[]>([]);
  const [escolhida, setEscolhida] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setAttached(await api.handouts.forPlan(planId));
    } catch {
      setAttached([]);
    }
  }, [planId]);

  useEffect(() => {
    void load();
    api.handouts
      .list({ size: 100 })
      .then((p) => setLibrary(p.content))
      .catch(() => setLibrary([]));
  }, [load]);

  async function attach() {
    if (!escolhida) return;
    setError(null);
    try {
      await api.handouts.attach(planId, { handoutId: Number(escolhida) });
      setEscolhida("");
      await load();
    } catch (e) {
      setError(explainError(e, "anexar a orientação"));
    }
  }

  async function detach(attachmentId: number) {
    try {
      await api.handouts.detach(planId, attachmentId);
      await load();
    } catch (e) {
      setError(explainError(e, "remove"));
    }
  }

  const available = library.filter(
    (o) => !attached.some((a) => a.handoutId === o.id),
  );

  return (
    <div className="card">
      <h3>Orientações</h3>

      {error && (
        <div className="warning error" style={{ margin: "0.5rem 0" }}>
          {error}
        </div>
      )}

      {attached.length === 0 ? (
        <p className="minusculo" style={{ marginTop: "0.4rem" }}>
          Nenhuma anexada. O paciente recebe estes textos junto do plano, no link e no PDF.
        </p>
      ) : (
        attached.map((a) => (
          <div className="row" key={a.id} style={{ marginTop: "0.5rem", gap: "0.4rem" }}>
            <span style={{ flex: 1, fontSize: "0.88rem" }}>{a.title}</span>
            {/* The figure was copied along: whoever builds the plan needs to know
                that the patient will receive the drawing, not only the text. */}
            {a.hasImage && <span className="tag">com figura</span>}
            <button
              type="button"
              className="button perigo pequeno"
              onClick={() => detach(a.id)}
            >
              Tirar
            </button>
          </div>
        ))
      )}

      <div className="row" style={{ marginTop: "0.7rem", gap: "0.4rem" }}>
        <select
          value={escolhida}
          onChange={(e) => setEscolhida(e.target.value)}
          aria-label="Orientação da biblioteca"
          style={{
            flex: 1,
            fontSize: "0.84rem",
            padding: "0.3rem 0.45rem",
            border: "1px solid var(--outline-field)",
            borderRadius: "var(--radius)",
          }}
        >
          <option value="">Escolher da biblioteca…</option>
          {available.map((o) => (
            <option key={o.id} value={o.id}>
              {o.title}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="button secundario pequeno"
          onClick={attach}
          disabled={!escolhida}
        >
          Attach
        </button>
      </div>
    </div>
  );
}

function PanelPublication({
  plan,
  onRun,
}: {
  plan: PlanResponse;
  onRun: (run: () => Promise<PlanResponse>, message: string) => Promise<void>;
}) {
  const [copied, setCopied] = useState(false);
  const [generatingPdf, setGeneratingPdf] = useState(false);
  const warnings = useFeedback();
  const address = `${window.location.origin}/plano/${plan.publicIdentifier}`;

  async function openPdf() {
    setGeneratingPdf(true);
    try {
      const { url } = await api.prescriptions.pdf(plan.id);
      window.open(url, "_blank", "noopener");
      // Delayed release: revoking before the new tab reads the URL would open
      // blank. One minute covers the opening without holding the file in
      // memory.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      warnings.warn(e, "gerar o PDF do plano");
    } finally {
      setGeneratingPdf(false);
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(address);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="card">
      <h3>Entrega ao paciente</h3>

      {/* The printout comes before the link: the patient has no account in the
          system, and leaves the appointment with the sheet in hand. */}
      <button
        type="button"
        className="button secundario"
        style={{ width: "100%", justifyContent: "center", marginTop: "0.5rem" }}
        onClick={openPdf}
        disabled={generatingPdf}
      >
        {generatingPdf ? "Gerando…" : "Plano em PDF"}
      </button>

      {plan.status === "DRAFT" ? (
        <>
          <p className="discreto" style={{ marginTop: "0.7rem" }}>
            O plano ainda é um rascunho: quem abrir o link não encontra nada, e o PDF sai marcado
            como rascunho.
          </p>
          <button
            className="button"
            style={{ width: "100%", justifyContent: "center" }}
            onClick={() => onRun(() => api.prescriptions.publish(plan.id), "Plano publicado.")}
          >
            Publicar plano
          </button>
        </>
      ) : (
        <>
          <p className="minusculo" style={{ marginTop: "0.4rem", wordBreak: "break-all" }}>
            {address}
          </p>
          <div className="row" style={{ marginTop: "0.5rem" }}>
            <button className="button secundario pequeno" onClick={copy}>
              {copied ? "Copiado!" : "Copiar link"}
            </button>
            <a
              className="button secundario pequeno"
              href={address}
              target="_blank"
              rel="noreferrer"
            >
              Open
            </a>
          </div>

          <div className="row" style={{ marginTop: "0.6rem" }}>
            {plan.status === "ACTIVE" && (
              <button
                className="button secundario pequeno"
                onClick={() =>
                  onRun(() => api.prescriptions.close(plan.id), "Plano encerrado.")
                }
              >
                Close
              </button>
            )}
            <button
              className="button secundario pequeno"
              onClick={() =>
                onRun(
                  () => api.prescriptions.backToDraft(plan.id),
                  "Plano voltou a rascunho e saiu do ar.",
                )
              }
            >
              Voltar a rascunho
            </button>
          </div>

          <button
            className="button perigo pequeno"
            style={{ marginTop: "0.5rem", width: "100%", justifyContent: "center" }}
            onClick={() => {
              if (!confirm("Gerar um novo link invalida o que o paciente já recebeu. Continuar?")) return;
              void onRun(
                () => api.prescriptions.regenerateLink(plan.id),
                "Novo link gerado. O anterior deixou de funcionar.",
              );
            }}
          >
            Gerar novo link
          </button>
        </>
      )}
    </div>
  );
}

function round(value: number) {
  return (Math.round(value * 100) / 100).toString().replace(".", ",");
}
