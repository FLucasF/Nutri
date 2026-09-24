import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { CalendarDays, FileQuestion } from "lucide-react";
import { ErrorApi, api } from "../api/client";
import type { PublicItem, PublicMeal, PublicPlan } from "../api/types";
import { MacroRing } from "../components/MacroRing";
import { RichTextView } from "../components/RichText/RichTextView";
import { emptyDoc, parseRichDoc } from "../components/RichText/document";
import { PlanGate, readPlanAccess } from "../components/PlanGate";

/**
 * The plan as the patient receives it.
 *
 * No credential is required: whoever has the link, sees. That is why the page
 * only shows what the server delivers in the public contract — which has no
 * field for the practice's internal notes.
 *
 * The reading is the day ruler: the meals hang from a timeline, with the real
 * hour as the marker, and the gap between breakfast and lunch shows up as a
 * real gap. Inside each item the household measure is the readout the patient
 * reads first — they already know what rice is; what they do not know is how
 * much — and the grams stay beside it, quieter, as the nutritionist's record.
 */
export default function PatientPlan() {
  const { identifier } = useParams();
  const [plan, setPlan] = useState<PublicPlan | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  /** O plano pede a data de nascimento antes de abrir. */
  const [gated, setGated] = useState(false);
  const [access, setAccess] = useState<string | undefined>(() =>
    identifier ? readPlanAccess(identifier) : undefined,
  );
  // Read once: the "current" meal is the one the patient is in when the page
  // opens, and it must not jump while they are reading.
  const [now] = useState(() => new Date());

  useEffect(() => {
    if (!identifier) return;
    setLoading(true);
    // Pergunta antes se o link abre: uma recusa no meio do caminho normal
    // viraria erro no console de todo paciente que abre o plano.
    api
      .publicPlanGate(identifier, access)
      .then(async (gate) => {
        if (!gate.allowed) {
          setGated(true);
          return;
        }
        setPlan(await api.publicPlan(identifier, access));
        setGated(false);
      })
      .catch((e) =>
        setError(
          e instanceof ErrorApi
            ? e.message
            : "Não foi possível abrir o plano. Verifique sua conexão.",
        ),
      )
      .finally(() => setLoading(false));
  }, [identifier, access]);

  useEffect(() => {
    if (plan?.title) {
      document.title = `${plan.title} — NutriPlan`;
    }
  }, [plan]);

  if (loading) {
    return (
      <div className="page-patient">
        <p className="loading">Carregando seu plano…</p>
      </div>
    );
  }

  if (gated && identifier) {
    return <PlanGate identifier={identifier} onOpened={setAccess} />;
  }

  if (error || !plan) {
    return (
      <div className="page-patient">
        <div className="plan-patient plan-patient-empty">
          <div className="card card-centered">
            <span className="empty-icon">
              <FileQuestion aria-hidden="true" />
            </span>
            <h1>Plano não encontrado</h1>
            <p className="discreto">
              {error ?? "Confira o link que você recebeu do seu nutricionista."}
            </p>
          </div>
        </div>
      </div>
    );
  }

  // The practice color personalizes the patient's page. The CSS receives it as
  // --theme-account and decides the final value, because in the dark theme it
  // has to be lightened so as not to disappear against the background.
  const practiceColor = plan.primaryColor || undefined;
  const current = currentMealIndex(plan.meals, now);
  const showSummary = plan.method !== "QUALITATIVE" && plan.summary.energyKcal !== undefined;

  return (
    <div
      className="page-patient"
      style={
        practiceColor
          ? ({ ["--theme-account" as string]: practiceColor } as React.CSSProperties)
          : undefined
      }
    >
      <div className="plan-patient">
        <header className="cover-plan">
          {plan.practiceName && (
            <span className="cover-brand">
              <span className="cover-dot" aria-hidden="true" />
              <span className="eyebrow">{plan.practiceName}</span>
            </span>
          )}
          <h1>{plan.title}</h1>
          <p className="cover-greeting">
            {plan.patientName ? `Olá, ${plan.patientName}. ` : ""}
            {plan.nutritionistName ? (
              <>
                Plano elaborado por {plan.nutritionistName}
                {plan.nutritionistCrn && (
                  <>
                    {" · "}
                    <span className="readout cover-crn">{plan.nutritionistCrn}</span>
                  </>
                )}
                .
              </>
            ) : null}
          </p>
          {plan.validityStart && (
            <p className="validity">
              <CalendarDays aria-hidden="true" />
              <span>
                Vigência: {formatDate(plan.validityStart)}
                {plan.validityEnd ? ` até ${formatDate(plan.validityEnd)}` : " em diante"}
              </span>
            </p>
          )}
        </header>

        {plan.closed && (
          <div className="warning attention">
            <strong>Este plano foi encerrado.</strong> Ele fica disponível para consulta, mas não é
            mais o plano vigente. Procure seu nutricionista para receber o plano atual.
          </div>
        )}

        {!plan.closed && !plan.current && (
          <div className="warning attention">
            Este plano está fora do período de vigência definido pelo seu nutricionista.
          </div>
        )}

        {plan.handouts && (
          <section className="card">
            <div className="card-head">
              <h2 className="card-title">Orientações gerais</h2>
            </div>
            <div className="plan-prose">
              <RichTextView doc={parseRichDoc(plan.handouts) ?? emptyDoc()} empty="" />
            </div>
          </section>
        )}

        {plan.handoutsAttached?.length > 0 && (
          <section className="card">
            {plan.handoutsAttached.map((o, i) => (
              <article className="plan-handout" key={o.id ?? i}>
                <h3>{o.title}</h3>
                <div className="plan-prose">
                  <RichTextView doc={parseRichDoc(o.body) ?? emptyDoc()} empty="" />
                </div>
                {/* Public route: the address arrives ready in the response and
                    the tag fetches on its own, with no credential at all. */}
                {/* No `loading="lazy"`: these are one or two small figures, and
                    the patient tends to print this page. A deferred image is an
                    image that risks coming out blank on paper. */}
                {o.image && (
                  <img
                    className="plan-figure"
                    src={
                      plan.accessToken
                        ? `${o.image}?access=${encodeURIComponent(plan.accessToken)}`
                        : o.image
                    }
                    alt={o.title}
                  />
                )}
              </article>
            ))}
          </section>
        )}

        <ol className="ruler-day" aria-label="Refeições do dia">
          {plan.meals.map((meal, index) => (
            <li
              className="ruler-item"
              key={index}
              style={{ ["--order" as string]: index } as React.CSSProperties}
            >
              {meal.time ? (
                <time
                  className={index === current ? "ruler-hour atual" : "ruler-hour"}
                  dateTime={meal.time}
                >
                  {meal.time.slice(0, 5)}
                </time>
              ) : (
                // A meal with no prescribed time does not get an invented hour.
                <span className="ruler-hour without-hour">livre</span>
              )}

              <div className="ruler-body">
                <section className="meal-patient">
                  <h2>{meal.name}</h2>
                  {meal.notes && (
                    <div className="note-meal plan-note">
                      <RichTextView doc={parseRichDoc(meal.notes) ?? emptyDoc()} empty="" />
                    </div>
                  )}
                  {meal.items.length > 0 && (
                    <ul className="meal-items">
                      {meal.items.map((item, i) => (
                        <Item item={item} key={i} />
                      ))}
                    </ul>
                  )}
                </section>
              </div>
            </li>
          ))}
        </ol>

        {/*
          O modo de preparo das receitas do cardápio, na mesma posição do PDF:
          depois das refeições. Quem acabou de ler o que vai comer é quem
          precisa saber como fazer — antes do cardápio seria instrução para
          uma coisa que ainda não foi apresentada.
        */}
        {plan.recipes?.length > 0 && (
          <section className="card">
            <div className="card-head">
              <h2 className="card-title">Modo de preparo</h2>
            </div>
            {plan.recipes.map((receita) => (
              <article className="recipe-patient" key={receita.foodId}>
                <h3>{receita.name}</h3>
                <div className="plan-prose">
                  <RichTextView
                    doc={parseRichDoc(receita.modeInstructions) ?? emptyDoc()}
                    empty=""
                  />
                </div>
              </article>
            ))}
          </section>
        )}

        {showSummary && (
          <section className="card">
            <div className="card-head">
              <h2 className="card-title">Resumo do dia</h2>
            </div>
            <div className="summary-body">
              <MacroShares summary={plan.summary} />
              <dl className="summary-day">
                <Summary label="Energia" value={plan.summary.energyKcal} unit="kcal" />
                <Summary label="Proteínas" value={plan.summary.proteinG} unit="g" />
                <Summary label="Carboidratos" value={plan.summary.carbohydrateG} unit="g" />
                <Summary label="Gorduras" value={plan.summary.fatG} unit="g" />
              </dl>
            </div>
            <p className="minusculo summary-note">
              Valores estimados a partir das tabelas de composição de alimentos.
            </p>
          </section>
        )}

        <footer className="minusculo plan-footer">
          Em caso de dúvida, fale com seu nutricionista.
          {plan.nutritionistName ? ` — ${plan.nutritionistName}` : ""}
        </footer>
      </div>
    </div>
  );
}

function Item({ item }: { item: PublicItem }) {
  const showWeight = item.weightGrams !== undefined && !isWeightAtGrams(item.serving);
  return (
    <li className="item-patient">
      <div className="plan-item-row">
        <span className="name-food">{item.description}</span>
        <span className="serving">
          <span className="readout strong">{item.serving}</span>
          {/* The grams only tag along when the portion is a household measure.
              Repeating "100 g / 100 g" would say nothing. */}
          {showWeight && <span className="readout weight">{formatWeight(item.weightGrams!)}</span>}
        </span>
      </div>
      {item.notes && (
        <div className="discreto observacao-item plan-note">
          <RichTextView doc={parseRichDoc(item.notes) ?? emptyDoc()} empty="" />
        </div>
      )}
      {item.substitutions.length > 0 && (
        <div className="substitutions">
          <span className="title-sub visually-hidden">Você pode substituir por</span>
          <ul>
            {item.substitutions.map((s, j) => (
              <li key={j} className="plan-item-row sub-row">
                <span className="name-food">
                  <span className="sub-ou" aria-hidden="true">
                    ou
                  </span>{" "}
                  {s.description}
                </span>
                <span className="serving">
                  <span className="readout">{s.serving}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </li>
  );
}

function Summary({ label, value, unit }: { label: string; value?: number; unit: string }) {
  return (
    <div className="stat">
      <dt className="stat-label">{label}</dt>
      <dd className="stat-value">
        {value === undefined || value === null ? "—" : `${round(value)} ${unit}`}
      </dd>
    </div>
  );
}

/**
 * The day's energy split as the ring: proteins and carbohydrates at 4 kcal/g,
 * fat at 9. Shown only when the three are known — a ring with a missing arc
 * would say the day has no fat, which is a different statement from "unknown".
 */
function MacroShares({ summary }: { summary: PublicPlan["summary"] }) {
  const { proteinG, carbohydrateG, fatG, energyKcal } = summary;
  if (proteinG === undefined || carbohydrateG === undefined || fatG === undefined) return null;
  const prot = proteinG * 4;
  const carb = carbohydrateG * 4;
  const fat = fatG * 9;
  const total = prot + carb + fat;
  if (total <= 0) return null;
  const pct = (v: number) => Math.round((v / total) * 100);
  const label = `Distribuição energética: ${pct(prot)}% proteínas, ${pct(carb)}% carboidratos, ${pct(fat)}% gorduras`;
  return (
    <div className="summary-ring">
      <MacroRing size={96} protein={prot} carbohydrate={carb} fat={fat} label={label}>
        <span>{energyKcal === undefined ? "—" : round(energyKcal)}</span>
        <span className="summary-ring-unit">kcal</span>
      </MacroRing>
      <ul className="summary-legend" aria-hidden="true">
        <li className="prot">P {pct(prot)}%</li>
        <li className="carb">C {pct(carb)}%</li>
        <li className="fat">G {pct(fat)}%</li>
      </ul>
    </div>
  );
}

/**
 * The meal the patient is in right now: the latest prescribed hour that has
 * already passed. Before the first meal there is no current one. Meals with no
 * hour never qualify — "livre" has no place on the clock.
 */
function currentMealIndex(meals: PublicMeal[], now: Date): number {
  const minutes = now.getHours() * 60 + now.getMinutes();
  let best = -1;
  let bestMinutes = -1;
  meals.forEach((meal, index) => {
    if (!meal.time) return;
    const [h = NaN, m = NaN] = meal.time.split(":").map(Number);
    if (Number.isNaN(h) || Number.isNaN(m)) return;
    const at = h * 60 + m;
    if (at <= minutes && at > bestMinutes) {
      best = index;
      bestMinutes = at;
    }
  });
  return best;
}

/** The portion already is the weight itself when there is no household measure, e.g. "100 g". */
function isWeightAtGrams(serving: string) {
  return /^\s*[\d.,]+\s*g\s*$/i.test(serving);
}

function formatWeight(grams: number) {
  const number = Number.isInteger(grams) ? grams : Number(grams.toFixed(1));
  return `${number.toLocaleString("pt-BR")} g`;
}

/**
 * Rounds while preserving the order of magnitude.
 *
 * Rounding 0.23 g to "0 g" would tell the patient the meal has no fat — which
 * is a different statement from "it has little". Small values get decimal
 * places; large ones stay whole, which is how a calorie is read.
 */
function round(value: number): string {
  if (value >= 100) return Math.round(value).toLocaleString("pt-BR");
  if (value >= 10) return value.toFixed(1).replace(".", ",");
  return value.toFixed(2).replace(".", ",");
}

function formatDate(iso: string) {
  const [year, month, day] = iso.split("-");
  return `${day}/${month}/${year}`;
}
