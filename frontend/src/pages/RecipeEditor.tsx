import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { formatNutrient, MACROS_PRINCIPAIS, labelDe } from "../api/nutrients";
import type { FoodSummary, RecipeIngredient, Measure, Recipe } from "../api/types";
import FoodSearch from "../components/FoodSearch";
import { count } from "../text";

/** An ingredient while it is being assembled on screen, before going to the server. */
type Draft = {
  foodId: number;
  description: string;
  measureId?: number;
  quantity: string;
  measures: Measure[];
};

/**
 * Recipe editor.
 *
 * The screen exists because the base has 23,945 foods and no preparations: you
 * can prescribe rice and broccoli separately, not "rice with broccoli". The
 * recipe solves that by becoming a food like any other — once saved, it appears
 * in the search and enters a plan with no special treatment.
 */
export default function RecipeEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const editing = id !== undefined;

  const [name, setName] = useState("");
  const [group, setGroup] = useState("");
  const [yieldGrams, setYield] = useState("");
  const [servings, setServings] = useState("");
  const [modeInstructions, setModeInstructions] = useState("");
  const [ingredients, setIngredients] = useState<Draft[]>([]);
  const [searchIngredient, setSearchIngredient] = useState("");

  const [calculated, setCalculated] = useState<Recipe | null>(null);
  const [loading, setLoading] = useState(editing);
  const [saving, setSaving] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!editing) return;
    setLoading(true);
    try {
      const r = await api.recipes.detail(Number(id));
      setCalculated(r);
      setName(r.name);
      setGroup(r.group ?? "");
      setYield(r.estimatedYield ? "" : String(r.yieldGrams));
      setServings(r.servings ? String(r.servings) : "");
      setModeInstructions(r.modeInstructions ?? "");
      setIngredients(await Promise.all(r.ingredients.map(toDraft)));
    } catch (e) {
      setError(explainError(e, "abrir a receita"));
    } finally {
      setLoading(false);
    }
  }, [id, editing]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toDraft(i: RecipeIngredient): Promise<Draft> {
    const detail = await api.foods.detail(i.foodId).catch(() => null);
    // The quantity arrives formatted ("2 colheres de sopa"); to edit it you
    // need the number, which is the first term.
    const number = i.quantity.split(" ")[0]?.replace(",", ".") ?? String(i.grams);
    return {
      foodId: i.foodId,
      description: i.description,
      measureId: i.measureId,
      quantity: number,
      measures: detail?.measures ?? [],
    };
  }

  async function add(summary: FoodSummary) {
    const detail = await api.foods.detail(summary.id).catch(() => null);
    const standard = detail?.measures.find((m) => m.standard) ?? detail?.measures[0];
    setIngredients((current) => [
      ...current,
      {
        foodId: summary.id,
        description: summary.description,
        measureId: standard?.id,
        quantity: standard ? "1" : "100",
        measures: detail?.measures ?? [],
      },
    ]);
  }

  function change(index: number, change: Partial<Draft>) {
    setIngredients((current) =>
      current.map((i, n) => (n === index ? { ...i, ...change } : i)),
    );
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const body = {
        name: name.trim(),
        group: group.trim() || undefined,
        yieldGrams: yieldGrams ? Number(yieldGrams.replace(",", ".")) : undefined,
        servings: servings ? Number(servings) : undefined,
        modeInstructions: modeInstructions.trim() || undefined,
        ingredients: ingredients.map((i) => ({
          foodId: i.foodId,
          measureId: i.measureId,
          quantity: Number(i.quantity.replace(",", ".")) || 0,
        })),
      };
      const saved = editing
        ? await api.recipes.update(Number(id), body)
        : await api.recipes.create(body);
      setCalculated(saved);
      feedback.confirm(`"${saved.name}" salva.`);
      if (!editing) {
        navigate(`/recipes/${saved.id}`, { replace: true });
      }
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "salvar a receita"));
      }
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <p className="loading">Loading…</p>;
  }

  const ingredientsWeight = ingredients.reduce((sums, i) => sums + estimateGrams(i), 0);

  return (
    <form onSubmit={save}>
      <div className="header-page">
        <div>
          <h1>{editing ? name || "Receita" : "Nova receita"}</h1>
          <p>
            {count(ingredients.length, "ingredient", "ingredients")}
            {calculated ? ` · rende ${formatGrams(calculated.yieldGrams)}` : ""}
          </p>
        </div>
        <div className="row">
          <Link className="button secundario" to="/foods?source=RECEITA">
            Back
          </Link>
          <button className="button" type="submit" disabled={saving}>
            {saving ? "Salvando…" : "Salvar receita"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      <div className="editor-plan">
        <div>
          <div className="card" style={{ marginBottom: "0.9rem" }}>
            <div className="grid two">
              <div className="field">
                <label htmlFor="rc-nome">Nome da receita</label>
                <input
                  id="rc-nome"
                  name="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  placeholder="Panqueca de aveia"
                  {...fields.props("name")}
                />
                <FieldError field="name" errors={fields.errors} />
              </div>
              <div className="field">
                <label htmlFor="rc-grupo">Group</label>
                <input
                  id="rc-grupo"
                  name="group"
                  value={group}
                  onChange={(e) => setGroup(e.target.value)}
                  placeholder="Lanches"
                  {...fields.props("group")}
                />
                <FieldError field="group" errors={fields.errors} />
              </div>
              <div className="field">
                <label htmlFor="rc-rendimento">Weight ready (g)</label>
                <input
                  id="rc-rendimento"
                  name="yieldGrams"
                  inputMode="decimal"
                  value={yieldGrams}
                  onChange={(e) => setYield(e.target.value)}
                  placeholder={ingredientsWeight ? String(Math.round(ingredientsWeight)) : ""}
                  {...fields.props("yieldGrams")}
                />
                <FieldError field="yieldGrams" errors={fields.errors} />
                <span className="minusculo">
                  Pese a preparação pronta. Cozinhar perde ou ganha água, e sem este número a
                  composição por 100 g é estimativa.
                </span>
              </div>
              <div className="field">
                <label htmlFor="rc-porcoes">Rende quantas porções</label>
                <input
                  id="rc-porcoes"
                  name="servings"
                  inputMode="numeric"
                  value={servings}
                  onChange={(e) => setServings(e.target.value)}
                  placeholder="8"
                  {...fields.props("servings")}
                />
                <FieldError field="servings" errors={fields.errors} />
                <span className="minusculo">
                  Gera a medida “1 porção”, que é como a receita chega ao paciente.
                </span>
              </div>
            </div>
          </div>

          <div className="meal">
            <div className="meal-top">
              <strong style={{ flex: 1 }}>Ingredients</strong>
              <span className="tag">
                raw: {formatGrams(ingredientsWeight)}
              </span>
            </div>

            {ingredients.length === 0 ? (
              <p className="empty" style={{ padding: "1rem" }}>
                Busque um alimento abaixo para montar a receita.
              </p>
            ) : (
              ingredients.map((i, n) => (
                <div className="item-row" key={`${i.foodId}-${n}`}>
                  <div className="description-item">
                    {i.description}
                    <small>{formatGrams(estimateGrams(i))}</small>
                  </div>
                  <input
                    inputMode="decimal"
                    value={i.quantity}
                    onChange={(e) => change(n, { quantity: e.target.value })}
                    aria-label={`Quantidade de ${i.description}`}
                  />
                  <select
                    value={i.measureId ?? ""}
                    onChange={(e) =>
                      change(n, { measureId: e.target.value ? Number(e.target.value) : undefined })
                    }
                    aria-label={`Medida de ${i.description}`}
                  >
                    <option value="">grams</option>
                    {i.measures.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.description}
                      </option>
                    ))}
                  </select>
                  <span />
                  <button
                    type="button"
                    className="button perigo pequeno"
                    onClick={() => setIngredients((a) => a.filter((_, x) => x !== n))}
                  >
                    Remove
                  </button>
                </div>
              ))
            )}

            <div style={{ padding: "0.7rem 0.9rem" }}>
              <FoodSearch
                freeValue={searchIngredient}
                onFreeType={setSearchIngredient}
                onChoose={(summary) => {
                  void add(summary);
                  // Clears it for the next one: building a recipe is adding several
                  // in a row, and reusing the previous term would get in the
                  // way.
                  setSearchIngredient("");
                }}
                disabled={false}
              />
            </div>
          </div>

          <div className="card" style={{ marginTop: "0.9rem" }}>
            <div className="field">
              <label htmlFor="rc-preparo">Modo de preparo</label>
              <textarea
                id="rc-preparo"
                name="modeInstructions"
                rows={6}
                value={modeInstructions}
                onChange={(e) => setModeInstructions(e.target.value)}
                placeholder="Bata os ingredientes, despeje na frigideira quente…"
                {...fields.props("modeInstructions")}
              />
              <FieldError field="modeInstructions" errors={fields.errors} />
            </div>
          </div>
        </div>

        <aside className="panel-totals">
          {calculated ? (
            <RecipeSummary recipe={calculated} />
          ) : (
            <div className="card">
              <h2>Composição</h2>
              <p className="discreto" style={{ marginTop: "0.4rem", marginBottom: 0 }}>
                Salve a receita para ver a composição calculada.
              </p>
            </div>
          )}
        </aside>
      </div>
    </form>
  );
}

function RecipeSummary({ recipe }: { recipe: Recipe }) {
  const incomplete = new Set(recipe.nutrientsIncomplete);
  // The note only makes sense if one of the nutrients shown is in italics.
  // The recipe may have dozens of incomplete ones that do not appear in this
  // list, and warning about italics where there are none sends the reader
  // looking for nothing.
  const someShownIncomplete = MACROS_PRINCIPAIS.some((c) => incomplete.has(c));

  return (
    <>
      <div className="card">
        <h2>Yield</h2>
        <div className="nutrient-row">
          <span>Ingredients</span>
          <span>{formatGrams(recipe.ingredientsWeight)}</span>
        </div>
        <div className="nutrient-row">
          <span>Preparação pronta</span>
          <span>{formatGrams(recipe.yieldGrams)}</span>
        </div>
        {recipe.gramsByServing !== undefined && (
          <div className="nutrient-row">
            <span>Cada porção</span>
            <span>{formatGrams(recipe.gramsByServing)}</span>
          </div>
        )}
        {recipe.estimatedYield && (
          <div className="warning attention" style={{ marginTop: "0.7rem" }}>
            O peso da preparação pronta não foi informado, então a soma dos ingredientes foi
            usada. Cozinhar muda o peso: pese e informe para que a composição seja exata.
          </div>
        )}
      </div>

      <div className="card">
        <h2>By 100 g</h2>
        {MACROS_PRINCIPAIS.map((key) => (
          <div
            className={`nutrient-row ${incomplete.has(key) ? "missing" : ""}`}
            key={key}
          >
            <span>{labelDe(key)}</span>
            <span>
              {formatNutrient(
                recipe.compositionPor100g[key as keyof typeof recipe.compositionPor100g] as
                  | number
                  | undefined,
                unitDe(key),
              )}
            </span>
          </div>
        ))}
        {someShownIncomplete && (
          <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
            Em itálico: somado a partir de apenas parte dos ingredientes. O valor é um mínimo,
            não um total — algum ingrediente não tem esse nutriente determinado na fonte.
          </p>
        )}
      </div>

      {!someShownIncomplete && incomplete.size > 0 && (
        <div className="card">
          <p className="minusculo" style={{ margin: 0 }}>
            {count(incomplete.size, "nutriente foi somado", "nutrientes foram somados")} a partir
            de apenas parte dos ingredientes, e são mínimos e não totais. Nenhum deles está entre
            os exibidos acima.
          </p>
        </div>
      )}

      {recipe.servingComposition && (
        <div className="card">
          <h2>Por porção</h2>
          {MACROS_PRINCIPAIS.map((key) => (
            <div className="nutrient-row" key={key}>
              <span>{labelDe(key)}</span>
              <span>
                {formatNutrient(
                  recipe.servingComposition![
                    key as keyof typeof recipe.servingComposition
                  ] as number | undefined,
                  unitDe(key),
                )}
              </span>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

/** Approximate weight of the ingredient while the recipe has not been saved. */
function estimateGrams(i: Draft): number {
  const quantity = Number(i.quantity.replace(",", ".")) || 0;
  const measure = i.measures.find((m) => m.id === i.measureId);
  return measure ? quantity * measure.grams : quantity;
}

function formatGrams(value?: number): string {
  if (value === undefined || value === null) return "—";
  return `${Number(value.toFixed(1)).toLocaleString("pt-BR")} g`;
}

const UNITS: Record<string, string> = {
  energyKcal: "kcal",
  proteinG: "g",
  carbohydrateG: "g",
  fatG: "g",
  fiberG: "g",
  sodiumMg: "mg",
};

function unitDe(key: string): string {
  return UNITS[key] ?? "";
}
