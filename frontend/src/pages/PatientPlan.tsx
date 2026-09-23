import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ErrorApi, api } from "../api/client";
import type { PublicItem, PublicPlan } from "../api/types";
import { RichTextView } from "../components/RichText/RichTextView";
import { emptyDoc, parseRichDoc } from "../components/RichText/document";

/**
 * The plan as the patient receives it.
 *
 * No credential is required: whoever has the link, sees. That is why the page
 * only shows what the server delivers in the public contract — which has no
 * field for the practice's internal notes.
 *
 * The reading is the day ruler: the meals hang from a timeline, with the real
 * hour as the marker, and the gap between breakfast and lunch shows up as a
 * real gap. Inside each item the household measure is the larger text — the
 * patient already knows what rice is; what they do not know is how much.
 */
export default function PatientPlan() {
  const { identifier } = useParams();
  const [plan, setPlan] = useState<PublicPlan | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!identifier) return;
    api
      .publicPlan(identifier)
      .then(setPlan)
      .catch((e) =>
        setError(
          e instanceof ErrorApi
            ? e.message
            : "Não foi possível abrir o plano. Verifique sua conexão.",
        ),
      )
      .finally(() => setLoading(false));
  }, [identifier]);

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

  if (error || !plan) {
    return (
      <div className="page-patient">
        <div className="plan-patient" style={{ paddingTop: "3rem" }}>
          <div className="card" style={{ textAlign: "center", padding: "2.5rem 1.5rem" }}>
            <h1 style={{ fontSize: "1.5rem" }}>Plano não encontrado</h1>
            <p className="discreto" style={{ marginTop: "0.6rem" }}>
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
          {plan.practiceName && <span className="eyebrow">{plan.practiceName}</span>}
          <h1>{plan.title}</h1>
          <p>
            {plan.patientName ? `Olá, ${plan.patientName}. ` : ""}
            {plan.nutritionistName
              ? `Plano elaborado por ${plan.nutritionistName}${plan.nutritionistCrn ? ` · ${plan.nutritionistCrn}` : ""}.`
              : ""}
          </p>
          {plan.validityStart && (
            <p className="validity">
              Vigência: {formatDate(plan.validityStart)}
              {plan.validityEnd ? ` até ${formatDate(plan.validityEnd)}` : " em diante"}
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
          <div className="card">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.5rem" }}>Orientações gerais</h2>
            <RichTextView doc={parseRichDoc(plan.handouts) ?? emptyDoc()} empty="" />
          </div>
        )}

        {plan.handoutsAttached?.length > 0 && (
          <div className="card">
            {plan.handoutsAttached.map((o, i) => (
              <div className="handout-attached" key={o.id ?? i} style={i === 0 ? { marginTop: 0 } : undefined}>
                <h3>{o.title}</h3>
                <RichTextView doc={parseRichDoc(o.body) ?? emptyDoc()} empty="" />
                {/* Public route: the address arrives ready in the response and
                    the tag fetches on its own, with no credential at all. */}
                {/* No `loading="lazy"`: these are one or two small figures, and
                    the patient tends to print this page. A deferred image is an
                    image that risks coming out blank on paper. */}
                {o.image && (
                  <img className="figure-handout" src={o.image} alt={o.title} />
                )}
              </div>
            ))}
          </div>
        )}

        <div className="ruler-day">
          {plan.meals.map((meal, index) => (
            <div
              className="ruler-item"
              key={index}
              style={{ ["--order" as string]: index } as React.CSSProperties}
            >
              {meal.time ? (
                <time className="ruler-hour" dateTime={meal.time}>
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
                    <div className="note-meal">
                      <RichTextView doc={parseRichDoc(meal.notes) ?? emptyDoc()} empty="" />
                    </div>
                  )}
                  {meal.items.map((item, i) => (
                    <Item item={item} key={i} />
                  ))}
                </section>
              </div>
            </div>
          ))}
        </div>

        {/*
          O modo de preparo das receitas do cardápio, na mesma posição do PDF:
          depois das refeições. Quem acabou de ler o que vai comer é quem
          precisa saber como fazer — antes do cardápio seria instrução para
          uma coisa que ainda não foi apresentada.
        */}
        {plan.recipes?.length > 0 && (
          <div className="card">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.5rem" }}>Modo de preparo</h2>
            {plan.recipes.map((receita) => (
              <div className="handout-attached" key={receita.foodId}>
                <h3>{receita.name}</h3>
                <RichTextView
                  doc={parseRichDoc(receita.modeInstructions) ?? emptyDoc()}
                  empty=""
                />
              </div>
            ))}
          </div>
        )}

        {plan.method !== "QUALITATIVE" && plan.summary.energyKcal !== undefined && (
          <div className="card">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.9rem" }}>Resumo do dia</h2>
            <div className="summary-day">
              <Summary label="Energia" value={plan.summary.energyKcal} unit="kcal" />
              <Summary label="Proteínas" value={plan.summary.proteinG} unit="g" />
              <Summary label="Carboidratos" value={plan.summary.carbohydrateG} unit="g" />
              <Summary label="Gorduras" value={plan.summary.fatG} unit="g" />
            </div>
            <p className="minusculo" style={{ marginTop: "0.8rem", marginBottom: 0 }}>
              Valores estimados a partir das tabelas de composição de alimentos.
            </p>
          </div>
        )}

        <footer className="minusculo" style={{ textAlign: "center", paddingTop: "0.5rem" }}>
          Em caso de dúvida, fale com seu nutricionista.
          {plan.nutritionistName ? ` — ${plan.nutritionistName}` : ""}
        </footer>
      </div>
    </div>
  );
}

function Item({ item }: { item: PublicItem }) {
  return (
    <div className="item-patient">
      <span className="name-food">{item.description}</span>
      <span className="serving">
        {item.serving}
        {/* The grams only tag along when the portion is a household measure.
            Repeating "100 g / 100 g" would say nothing. */}
        {item.weightGrams !== undefined && !isWeightAtGrams(item.serving) && (
          <span className="weight">{formatWeight(item.weightGrams)}</span>
        )}
      </span>
      {item.notes && (
        <div className="discreto observacao-item">
          <RichTextView doc={parseRichDoc(item.notes) ?? emptyDoc()} empty="" />
        </div>
      )}
      {item.substitutions.length > 0 && (
        <div className="substitutions">
          <span className="title-sub">Você pode substituir por</span>
          <ul>
            {item.substitutions.map((s, j) => (
              <li key={j}>
                {s.description} — {s.serving}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Summary({ label, value, unit }: { label: string; value?: number; unit: string }) {
  return (
    <div>
      <span className="label">{label}</span>
      <div className="value">
        {value === undefined || value === null ? "—" : `${round(value)} ${unit}`}
      </div>
    </div>
  );
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
