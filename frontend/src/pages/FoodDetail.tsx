import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ChevronLeft, Plus, Ruler } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../components/Feedback";
import { NUTRIENTS, formatNutrient } from "../api/nutrients";
import type { FoodDetail as Food, CalculatedServing } from "../api/types";
import { Own, Reference } from "../components/Own";
import { useIsNarrow } from "../hooks/useMediaQuery";

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

  if (loading) return <p className="loading">Carregando…</p>;
  if (error && !food) return <div className="warning error">{error}</div>;
  if (!food) return null;

  const shown = serving?.composition ?? food.composition;
  const base = serving ? serving.measureUsed : "100 g";

  return (
    <div className="page-food">
      <div className="header-page">
        <div>
          <Link to="/foods" className="migalha">
            <ChevronLeft aria-hidden="true" />
            Alimentos
          </Link>
          <div className="food-title">
            <h1>{food.description}</h1>
            {food.publicBase ? <Reference>tabela de referência</Reference> : <Own>alimento seu</Own>}
          </div>
          <p>
            {food.group ?? "Sem grupo"}
            {food.brand ? ` · ${food.brand}` : ""} · Fonte: {food.sourceDescription}
          </p>
        </div>
      </div>

      {error && <div className="warning error">{error}</div>}

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Calcular porção</h2>
        </div>
        <div className="calc-row">
          <div className="field">
            <label htmlFor="qtd">Quantidade</label>
            <input id="qtd" inputMode="decimal" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="med">Medida</label>
            <select id="med" value={measureId} onChange={(e) => setMeasureId(e.target.value)}>
              <option value="">gramas</option>
              {food.measures.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.description} ({formatWeight(m.grams)})
                  {m.forCatalogBase ? "" : " · sua"}
                </option>
              ))}
            </select>
          </div>
          <div className="stat calc-result" aria-live="polite">
            <span className="stat-label">Equivale a</span>
            <span className="stat-value">{serving ? formatWeight(serving.grams) : "—"}</span>
          </div>
        </div>
      </section>

      <section className="card">
        <div className="card-head">
          <h2 className="card-title">Composição</h2>
          <span className="readout strong">por {base}</span>
        </div>

        <div className="food-composition">
          {GROUPS_SHOWN.map(({ group, title }) => {
            const forGroup = NUTRIENTS.filter((n) => n.group === group);
            if (forGroup.length === 0) return null;
            return (
              <div key={group} className="nutrient-group">
                <h3>{title}</h3>
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
      </section>

      <Measures food={food} onChange={load} />
    </div>
  );
}

function formatWeight(grams: number) {
  const text = grams % 1 === 0 ? String(grams) : grams.toFixed(grams < 1 ? 2 : 1);
  return `${text.replace(".", ",")} g`;
}

function Measures({ food, onChange }: { food: Food; onChange: () => void }) {
  const narrow = useIsNarrow();
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
    <section className="card">
      <div className="card-head">
        <div>
          <h2 className="card-title">Porções usuais</h2>
          <p className="card-sub">
            É o que torna o plano legível para o paciente: ninguém serve 5 g de sal, serve uma
            pitada. Você pode cadastrar sua própria versão de qualquer porção — a sua aparece
            primeiro, e as que acompanham o sistema continuam disponíveis para os demais.
          </p>
        </div>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      {food.measures.length === 0 ? (
        <div className="empty measure-empty">
          <span className="empty-icon">
            <Ruler aria-hidden="true" />
          </span>
          <span className="empty-hint">
            Este alimento ainda não tem porção usual. Cadastre uma para prescrevê-lo em colheres,
            fatias ou unidades — e não só em gramas.
          </span>
        </div>
      ) : narrow ? (
        <div className="measure-list">
          {food.measures.map((m) => (
            <div key={m.id} className="measure-item">
              <div className="measure-item-body">
                <span className="measure-name">{m.description}</span>
                <span className="measure-item-tags">
                  {m.standard && <span className="tag verde">padrão</span>}
                  {m.forCatalogBase ? <Reference>do sistema</Reference> : <Own />}
                </span>
              </div>
              <span className="readout strong">{formatWeight(m.grams)}</span>
              {m.editable && (
                <button type="button" className="button perigo pequeno" onClick={() => remove(m.id)}>
                  Remover
                </button>
              )}
            </div>
          ))}
        </div>
      ) : (
        <table className="measure-table">
          <thead>
            <tr>
              <th>Porção</th>
              <th className="num">Peso</th>
              <th>Origem</th>
              <th>
                <span className="visually-hidden">Ações</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {food.measures.map((m) => (
              <tr key={m.id}>
                <td>
                  <span className="measure-name">
                    {m.description}
                    {m.standard && <span className="tag verde">padrão</span>}
                  </span>
                </td>
                <td className="num">{formatWeight(m.grams)}</td>
                <td>{m.forCatalogBase ? <Reference>do sistema</Reference> : <Own />}</td>
                <td>
                  {m.editable && (
                    <button type="button" className="button perigo pequeno" onClick={() => remove(m.id)}>
                      Remover
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form onSubmit={add} className="measure-form">
        <div className="field">
          <label htmlFor="nova-med">Nova porção</label>
          <input
            id="nova-med"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="colher de servir da clínica"
            required
          />
        </div>
        <div className="field">
          <label htmlFor="nova-med-g">Peso (g)</label>
          <input
            id="nova-med-g"
            inputMode="decimal"
            value={grams}
            onChange={(e) => setGrams(e.target.value)}
            required
          />
        </div>
        <label className="measure-standard">
          <input type="checkbox" checked={standard} onChange={(e) => setStandard(e.target.checked)} />
          <span className="discreto">Padrão</span>
        </label>
        <button className="button" type="submit" disabled={sending}>
          <Plus aria-hidden="true" />
          Adicionar
        </button>
      </form>
    </section>
  );
}
