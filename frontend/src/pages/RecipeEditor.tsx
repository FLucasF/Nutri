import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ChevronLeft, Salad, Trash2 } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { formatNutrient, MACROS_PRINCIPAIS, labelDe } from "../api/nutrients";
import type { Composition, FoodSummary, RecipeIngredient, Measure, Recipe } from "../api/types";
import FoodSearch from "../components/FoodSearch";
import { MacroRing } from "../components/MacroRing";
import { useIsCompact } from "../hooks/useMediaQuery";
import { useScrolled } from "../hooks/useScrolled";
import { count } from "../text";
import { NotesField } from "../components/RichText/NotesField";

/** An ingredient while it is being assembled on screen, before going to the server. */
type Draft = {
  foodId: number;
  description: string;
  measureId?: number;
  quantity: string;
  measures: Measure[];
};

/** Where "Voltar" leads: the food list already filtered to recipes. */
const BACK_TO = "/foods?source=RECEITA";

/**
 * Recipe editor.
 *
 * The screen exists because the base has 23,945 foods and no preparations: you
 * can prescribe rice and broccoli separately, not "rice with broccoli". The
 * recipe solves that by becoming a food like any other — once saved, it appears
 * in the search and enters a plan with no special treatment.
 *
 * It is a focused route: on the phone and the tablet the shell renders no app
 * bar and no tab bar, and the page header here is the sticky top with the back
 * chevron and the save button.
 */
export default function RecipeEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const editing = id !== undefined;
  const compact = useIsCompact();
  const scrolled = useScrolled();

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
    return <p className="loading">Carregando…</p>;
  }

  const ingredientsWeight = ingredients.reduce((sums, i) => sums + estimateGrams(i), 0);
  const title = editing ? name || "Receita" : "Nova receita";
  const subtitle =
    count(ingredients.length, "ingrediente", "ingredientes") +
    (calculated ? ` · rende ${formatGrams(calculated.yieldGrams)}` : "");

  return (
    <form className="page-recipe" onSubmit={save}>
      {/*
        One header, two shapes. Up to 900px the shell draws no bar, so this is
        the top of the screen: it sticks, carries the back chevron and lifts
        with a shadow once the form has moved under it. Wider than that it is
        an ordinary page header with a breadcrumb.
      */}
      <header
        className={compact ? "header-page sticky" : "header-page"}
        data-scrolled={scrolled ? "true" : "false"}
      >
        {compact && (
          <Link
            className="button icon ghost header-page-back"
            to={BACK_TO}
            aria-label="Voltar"
            title="Voltar"
          >
            <ChevronLeft aria-hidden="true" />
          </Link>
        )}
        <div className="header-page-title">
          {!compact && (
            <Link className="migalha" to={BACK_TO}>
              <ChevronLeft aria-hidden="true" />
              Alimentos
            </Link>
          )}
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        <div className="header-page-actions">
          <button className="button" type="submit" disabled={saving}>
            {saving ? "Salvando…" : "Salvar receita"}
          </button>
        </div>
      </header>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {calculated && <CompositionStrip recipe={calculated} />}

      <div className="recipe-editor">
        <div className="recipe-main">
          <div className="card recipe-fields">
            <div className="field recipe-field-name">
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
            <div className="field recipe-field-group">
              <label htmlFor="rc-grupo">Grupo</label>
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
            <div className="field recipe-field-yield">
              <label htmlFor="rc-rendimento">Peso pronto (g)</label>
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
              <span className="field-hint">
                Pese a preparação pronta. Cozinhar perde ou ganha água, e sem este número a
                composição por 100 g é estimativa.
              </span>
            </div>
            <div className="field recipe-field-servings">
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
              <span className="field-hint">
                Gera a medida “1 porção”, que é como a receita chega ao paciente.
              </span>
            </div>
          </div>

          <section className="card recipe-ingredients" aria-labelledby="rc-ingredientes">
            <div className="card-head">
              <h2 className="card-title" id="rc-ingredientes">
                Ingredientes
              </h2>
              <span className="recipe-raw">
                cru: <span className="readout strong">{formatGrams(ingredientsWeight)}</span>
              </span>
            </div>

            {ingredients.length === 0 ? (
              <div className="empty recipe-empty">
                <span className="empty-icon">
                  <Salad aria-hidden="true" />
                </span>
                <span className="empty-hint">Busque um alimento abaixo para montar a receita.</span>
              </div>
            ) : (
              <div className="ingredient-rows">
                <div className="ingredient-head" aria-hidden="true">
                  <span>Alimento</span>
                  <span>Quantidade</span>
                  <span>Medida</span>
                  <span className="text-right">Peso</span>
                  <span />
                </div>
                {ingredients.map((i, n) => (
                  <div className="ingredient-row" key={`${i.foodId}-${n}`}>
                    <div className="ingredient-name">{i.description}</div>
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
                      <option value="">gramas</option>
                      {i.measures.map((m) => (
                        <option key={m.id} value={m.id}>
                          {m.description}
                        </option>
                      ))}
                    </select>
                    <span className="readout ingredient-grams">{formatGrams(estimateGrams(i))}</span>
                    <button
                      type="button"
                      className="button icon ghost ingredient-remove"
                      aria-label="Remover"
                      title="Remover"
                      onClick={() => setIngredients((a) => a.filter((_, x) => x !== n))}
                    >
                      <Trash2 aria-hidden="true" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="recipe-add">
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
          </section>

          <div className="card recipe-preparation">
            {/*
              O preparo agora viaja para o cardápio: quando esta receita entra
              num plano, o texto daqui sai no PDF que o paciente recebe, numa
              seção "Modo de preparo" depois das refeições. Por isso ele ganhou
              o editor com tabela — a lista de ingredientes em coluna é o
              formato em que uma receita de fato se escreve.
            */}
            <NotesField
              label="Modo de preparo"
              value={modeInstructions}
              onChange={setModeInstructions}
              minHeight="14rem"
              help="Sai no PDF do cardápio sempre que esta receita for usada."
            />
            <FieldError field="modeInstructions" errors={fields.errors} />
          </div>
        </div>

        <aside className="recipe-aside">
          {calculated ? (
            <RecipeSummary recipe={calculated} />
          ) : (
            <div className="card">
              <div className="card-head">
                <h2 className="card-title">Composição</h2>
              </div>
              <p className="discreto recipe-note">
                Salve a receita para ver a composição calculada.
              </p>
            </div>
          )}
        </aside>
      </div>
    </form>
  );
}

/** Energy share of the three macros, for the ring. Undefined when nothing is known. */
function macroShares(composition: Composition) {
  const protein = composition.proteinG;
  const carbohydrate = composition.carbohydrateG;
  const fat = composition.fatG;
  if (protein === undefined && carbohydrate === undefined && fat === undefined) return null;
  const p = (protein ?? 0) * 4;
  const c = (carbohydrate ?? 0) * 4;
  const f = (fat ?? 0) * 9;
  const total = p + c + f;
  if (total <= 0) return null;
  const pct = (v: number) => Math.round((v / total) * 100);
  return {
    protein: p,
    carbohydrate: c,
    fat: f,
    label: `Distribuição energética: ${pct(p)}% proteína, ${pct(c)}% carboidrato, ${pct(f)}% gordura`,
  };
}

function kcalOf(composition: Composition): string {
  const kcal = composition.energyKcal;
  return kcal === undefined ? "—" : String(Math.round(kcal));
}

/**
 * The composition at a glance, right under the header — only where the aside
 * has fallen below the whole form (≤ 900), so the numbers are not a long
 * scroll away from the fields that change them.
 */
function CompositionStrip({ recipe }: { recipe: Recipe }) {
  const composition = recipe.compositionPor100g;
  const shares = macroShares(composition);
  const g = (key: string) => {
    const value = composition[key];
    return value === undefined ? "—" : value.toFixed(1).replace(".", ",");
  };
  return (
    <div className="recipe-strip" aria-label="Composição por 100 g">
      <MacroRing
        size={20}
        protein={shares?.protein ?? 0}
        carbohydrate={shares?.carbohydrate ?? 0}
        fat={shares?.fat ?? 0}
        label={shares?.label}
      />
      <span className="readout strong">{kcalOf(composition)} kcal</span>
      <span className="readout">
        P {g("proteinG")} · C {g("carbohydrateG")} · G {g("fatG")}
      </span>
      <span className="recipe-strip-per">por 100 g</span>
    </div>
  );
}

function RecipeSummary({ recipe }: { recipe: Recipe }) {
  const incomplete = new Set(recipe.nutrientsIncomplete);
  // The note only makes sense if one of the nutrients shown is in italics.
  // The recipe may have dozens of incomplete ones that do not appear in this
  // list, and warning about italics where there are none sends the reader
  // looking for nothing.
  const someShownIncomplete = MACROS_PRINCIPAIS.some((c) => incomplete.has(c));
  const shares = macroShares(recipe.compositionPor100g);

  return (
    <>
      <div className="card">
        <div className="card-head">
          <h2 className="card-title">Rendimento</h2>
        </div>
        <div className="recipe-rows">
          <div className="recipe-row">
            <span>Ingredientes</span>
            <span className="readout strong">{formatGrams(recipe.ingredientsWeight)}</span>
          </div>
          <div className="recipe-row">
            <span>Preparação pronta</span>
            <span className="readout strong">{formatGrams(recipe.yieldGrams)}</span>
          </div>
          {recipe.gramsByServing !== undefined && (
            <div className="recipe-row">
              <span>Cada porção</span>
              <span className="readout strong">{formatGrams(recipe.gramsByServing)}</span>
            </div>
          )}
        </div>
        {recipe.estimatedYield && (
          <div className="warning attention mt-3">
            O peso da preparação pronta não foi informado, então a soma dos ingredientes foi
            usada. Cozinhar muda o peso: pese e informe para que a composição seja exata.
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-head">
          <h2 className="card-title">Por 100 g</h2>
        </div>
        {shares && (
          <div className="recipe-ring">
            <MacroRing
              size={96}
              protein={shares.protein}
              carbohydrate={shares.carbohydrate}
              fat={shares.fat}
              label={shares.label}
            >
              <span className="recipe-ring-kcal">{kcalOf(recipe.compositionPor100g)}</span>
              <span className="recipe-ring-unit">kcal</span>
            </MacroRing>
            <ul className="recipe-legend" aria-hidden="true">
              <li className="prot">Proteína</li>
              <li className="carb">Carboidrato</li>
              <li className="fat">Gordura</li>
            </ul>
          </div>
        )}
        <CompositionRows composition={recipe.compositionPor100g} incomplete={incomplete} />
        {someShownIncomplete && (
          <p className="minusculo recipe-note">
            Em itálico: somado a partir de apenas parte dos ingredientes. O valor é um mínimo,
            não um total — algum ingrediente não tem esse nutriente determinado na fonte.
          </p>
        )}
      </div>

      {!someShownIncomplete && incomplete.size > 0 && (
        <div className="card">
          <p className="minusculo recipe-note-only">
            {count(incomplete.size, "nutriente foi somado", "nutrientes foram somados")} a partir
            de apenas parte dos ingredientes, e são mínimos e não totais. Nenhum deles está entre
            os exibidos acima.
          </p>
        </div>
      )}

      {recipe.servingComposition && (
        <div className="card">
          <div className="card-head">
            <h2 className="card-title">Por porção</h2>
          </div>
          <CompositionRows composition={recipe.servingComposition} />
        </div>
      )}
    </>
  );
}

function CompositionRows({
  composition,
  incomplete,
}: {
  composition: Composition;
  incomplete?: Set<string>;
}) {
  return (
    <div className="recipe-rows">
      {MACROS_PRINCIPAIS.map((key) => {
        const missing = incomplete?.has(key) ?? false;
        return (
          <div className={missing ? "recipe-row missing" : "recipe-row"} key={key}>
            <span>{labelDe(key)}</span>
            <span className="readout strong">{formatNutrient(composition[key], unitDe(key))}</span>
          </div>
        );
      })}
    </div>
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
