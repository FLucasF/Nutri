import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../componentes/Feedback";
import { NUTRIENTS, formatNutrient } from "../api/nutrients";
import type { FoodDetail as Food, CalculatedServing } from "../api/types";

const GROUPS_SHOWN = [
  { group: "energy", title: "Energia" },
  { group: "macro", title: "Macronutrientes" },
  { group: "lipidio", title: "Lipídios" },
  { group: "mineral", title: "Minerais" },
  { group: "vitamin", title: "Vitaminas" },
  { group: "other", title: "Outros" },
] as const;

export default function FoodDetail() {
  const { id } = useParams();
  const foodId = Number(id);

  const [food, setFood] = useState<Food | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [quantity, setQuantity] = useState("1");
  const [measureId, setMeasureId] = useState<string>("");
  const [serving, setServing] = useState<CalculatedServing | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api.foods.detail(foodId);
      setFood(data);
      const standard = data.measures.find((m) => m.standard) ?? data.measures[0];
      setMeasureId(standard ? String(standard.id) : "");
    } catch (e) {
      setError(explainError(e, "abrir o alimento"));
    } finally {
      setLoading(false);
    }
  }, [foodId]);

  useEffect(() => {
    void load();
  }, [load]);

  // It recalculates the portion whenever the quantity or the measure changes.
  useEffect(() => {
    const value = Number(quantity.replace(",", "."));
    if (!food || !Number.isFinite(value) || value <= 0) {
      setServing(null);
      return;
    }
    let canceled = false;
    api.foods
      .serving(food.id, value, measureId ? Number(measureId) : undefined)
      .then((result) => {
        if (!canceled) setServing(result);
      })
      .catch(() => {
        if (!canceled) setServing(null);
      });
    return () => {
      canceled = true;
    };
  }, [food, quantity, measureId]);

  if (loading) return <p className="loading">Loading…</p>;
  if (error && !food) return <div className="warning error">{error}</div>;
  if (!food) return null;

  const shown = serving?.composition ?? food.composition;
  const base = serving ? serving.measureUsed : "100 g";

  return (
    <>
      <div className="header-page">
        <div>
          <Link to="/foods" className="minusculo">
            ← Foods
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>{food.description}</h1>
          <p>
            {food.group ?? "Sem grupo"}
            {food.brand ? ` · ${food.brand}` : ""} · Source: {food.sourceDescription}
          </p>
        </div>
        {food.publicBase && <span className="tag">tabela de referência</span>}
      </div>

      {error && <div className="warning error" style={{ marginBottom: "0.9rem" }}>{error}</div>}

      <div className="card" style={{ marginBottom: "1.1rem" }}>
        <h2>Calcular porção</h2>
        <div className="row" style={{ marginTop: "0.7rem" }}>
          <div className="field" style={{ width: 120 }}>
            <label htmlFor="qtd">Quantity</label>
            <input id="qtd" inputMode="decimal" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </div>
          <div className="field" style={{ flex: 1, minWidth: 200 }}>
            <label htmlFor="med">Measure</label>
            <select id="med" value={measureId} onChange={(e) => setMeasureId(e.target.value)}>
              <option value="">grams</option>
              {food.measures.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.description} ({formatWeight(m.grams)})
                  {m.forCatalogBase ? "" : " · sua"}
                </option>
              ))}
            </select>
          </div>
          {serving && (
            <div style={{ paddingTop: "1.1rem" }}>
              <span className="tag verde">{formatWeight(serving.grams)}</span>
            </div>
          )}
        </div>
      </div>

      <div className="card" style={{ marginBottom: "1.1rem" }}>
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h2>Composição</h2>
          <span className="minusculo">by {base}</span>
        </div>

        <div className="grid two" style={{ marginTop: "0.8rem" }}>
          {GROUPS_SHOWN.map(({ group, title }) => {
            const forGroup = NUTRIENTS.filter((n) => n.group === group);
            if (forGroup.length === 0) return null;
            return (
              <div key={group}>
                <h3 style={{ marginBottom: "0.3rem" }}>{title}</h3>
                {forGroup.map((n) => {
                  const value = shown[n.key];
                  const missing = value === undefined || value === null;
                  return (
                    <div key={n.key} className={`nutrient-row ${missing ? "missing" : ""}`}>
                      <span>{n.label}</span>
                      <span>{formatNutrient(value, n.unit)}</span>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      <Measures food={food} onChange={load} />
    </>
  );
}

function formatWeight(grams: number) {
  const text = grams % 1 === 0 ? String(grams) : grams.toFixed(grams < 1 ? 2 : 1);
  return `${text.replace(".", ",")} g`;
}

function Measures({ food, onChange }: { food: Food; onChange: () => void }) {
  const [description, setDescription] = useState("");
  const [grams, setGrams] = useState("");
  const [standard, setStandard] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function add(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      await api.foods.addMeasure(food.id, {
        description: description.trim(),
        grams: Number(grams.replace(",", ".")),
        standard,
      });
      setDescription("");
      setGrams("");
      setStandard(false);
      onChange();
    } catch (e) {
      setError(explainError(e, "cadastrar a porção"));
    } finally {
      setSending(false);
    }
  }

  const feedback = useFeedback();

  async function remove(measureId: number) {
    setError(null);
    try {
      await api.foods.removeMeasure(food.id, measureId);
      feedback.confirm("Porção removida.");
      onChange();
    } catch (e) {
      setError(explainError(e, "remover a porção"));
    }
  }

  return (
    <div className="card">
      <h2>Porções usuais</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.8rem" }}>
        É o que torna o plano legível para o paciente: ninguém serve 5 g de sal, serve uma pitada.
        Você pode cadastrar sua própria versão de qualquer porção — a sua aparece primeiro, e as que
        acompanham o sistema continuam disponíveis para os demais.
      </p>

      {error && <div className="warning error" style={{ marginBottom: "0.85rem" }}>{error}</div>}

      {food.measures.length === 0 ? (
        <div className="empty" style={{ padding: "1.2rem" }}>
          Este alimento ainda não tem porção usual. Cadastre uma para prescrevê-lo em colheres,
          fatias ou unidades — e não só em gramas.
        </div>
      ) : (
        <div className="rolagem" style={{ marginBottom: "0.9rem" }}>
          <table>
            <thead>
              <tr>
                <th>Porção</th>
                <th className="num">Weight</th>
                <th>Origin</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {food.measures.map((m) => (
                <tr key={m.id}>
                  <td>
                    {m.description}
                    {m.standard && <span className="tag verde" style={{ marginLeft: "0.4rem" }}>padrão</span>}
                  </td>
                  <td className="num">{formatWeight(m.grams)}</td>
                  <td>
                    <span className="tag">{m.forCatalogBase ? "do sistema" : "sua"}</span>
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {m.editable && (
                      <button className="button perigo pequeno" onClick={() => remove(m.id)}>
                        Remove
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form onSubmit={add}>
        <div className="row">
          <div className="field" style={{ flex: 2, minWidth: 190 }}>
            <label htmlFor="nova-med">Nova porção</label>
            <input
              id="nova-med"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="colher de servir da clínica"
              required
            />
          </div>
          <div className="field" style={{ width: 110 }}>
            <label htmlFor="nova-med-g">Weight (g)</label>
            <input
              id="nova-med-g"
              inputMode="decimal"
              value={grams}
              onChange={(e) => setGrams(e.target.value)}
              required
            />
          </div>
          <label className="row" style={{ gap: "0.35rem", paddingTop: "1.1rem" }}>
            <input
              type="checkbox"
              checked={standard}
              onChange={(e) => setStandard(e.target.checked)}
              style={{ width: "auto" }}
            />
            <span className="discreto">Padrão</span>
          </label>
          <button className="button" type="submit" disabled={sending} style={{ marginTop: "1.1rem" }}>
            Add
          </button>
        </div>
      </form>
    </div>
  );
}
