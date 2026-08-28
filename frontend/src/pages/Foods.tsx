import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import type { FoodSummary, DataSource, ResultImport } from "../api/types";
import { count } from "../text";

const SOURCES: { value: DataSource | ""; label: string }[] = [
  { value: "", label: "Todas as fontes" },
  { value: "TACO", label: "TACO — NEPA/Unicamp" },
  { value: "OPEN_FOOD_FACTS", label: "Open Food Facts" },
  { value: "CUSTOM", label: "Cadastro próprio" },
  { value: "RECIPE", label: "Receitas" },
];

export default function Foods() {
  const navigate = useNavigate();

  const [foods, setFoods] = useState<FoodSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [groups, setGroups] = useState<string[]>([]);
  const [term, setTerm] = useState("");
  const [group, setGroup] = useState("");
  const [source, setSource] = useState<DataSource | "">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [panel, setPanel] = useState<"none" | "novo" | "importAll">("none");
  const [byCode, setByCode] = useState(false);

  useEffect(() => {
    api.foods.groups().then(setGroups).catch(() => setGroups([]));
  }, []);

  const find = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // A sequence of digits alone with the length of an EAN is a barcode, and
      // not a food name: the text search would return empty, because no product
      // is called "7890000110266". Whoever types that has the package in hand.
      const code = term.replace(/[\s.-]/g, "");
      if (/^\d{8,14}$/.test(code)) {
        const found = await api.foods.byBarcodeCode(code);
        setByCode(true);
        setFoods(
          found.map((a) => ({
            id: a.id,
            description: a.description,
            group: a.group,
            source: a.source,
            brand: a.brand,
            publicBase: a.publicBase,
            energyKcal: a.composition.energyKcal,
            proteinG: a.composition.proteinG,
            carbohydrateG: a.composition.carbohydrateG,
            fatG: a.composition.fatG,
          })),
        );
        setTotal(found.length);
        return;
      }

      setByCode(false);
      const page = await api.foods.find({
        term: term.trim() || undefined,
        group: group || undefined,
        source: source || undefined,
        size: 40,
      });
      setFoods(page.content);
      setTotal(page.totalElements);
    } catch (e) {
      setError(explainError(e, "buscar alimentos"));
    } finally {
      setLoading(false);
    }
  }, [term, group, source]);

  useEffect(() => {
    const clock = setTimeout(find, term ? 300 : 0);
    return () => clearTimeout(clock);
  }, [find, term]);

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Foods</h1>
          <p>
            {total.toLocaleString("pt-BR")} {total === 1 ? "food" : "foods"} no acervo
            visível
          </p>
        </div>
        <div className="row">
          <button className="button secundario" onClick={() => setPanel(panel === "importAll" ? "none" : "importAll")}>
            Importar tabela
          </button>
          <button className="button secundario" onClick={() => navigate("/recipes/new")}>
            Nova receita
          </button>
          <button className="button" onClick={() => setPanel(panel === "novo" ? "none" : "novo")}>
            Novo alimento
          </button>
        </div>
      </div>

      {panel === "importAll" && <PanelImport onClose={() => setPanel("none")} onImport={find} />}
      {panel === "novo" && (
        <FormNovoFood
          onClose={() => setPanel("none")}
          onCreate={(id) => navigate(`/foods/${id}`)}
        />
      )}

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="row">
          <div className="field" style={{ flex: 2, minWidth: 220 }}>
            <label htmlFor="busca-alimento">Find</label>
            <input
              id="busca-alimento"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Digite sem acento, se preferir: acucar, feijao…"
            />
          </div>
          <div className="field" style={{ flex: 1, minWidth: 170 }}>
            <label htmlFor="filtro-grupo">Group</label>
            <select id="filtro-grupo" value={group} onChange={(e) => setGroup(e.target.value)}>
              <option value="">Todos os grupos</option>
              {groups.map((g) => (
                <option key={g} value={g}>
                  {g}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ flex: 1, minWidth: 170 }}>
            <label htmlFor="filtro-fonte">Source</label>
            <select
              id="filtro-fonte"
              value={source}
              onChange={(e) => setSource(e.target.value as DataSource | "")}
            >
              {SOURCES.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {error && (
        <div className="warning error" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      {loading ? (
        <p className="loading">Loading…</p>
      ) : foods.length === 0 ? (
        <div className="card empty">
          {byCode
            ? `Nenhum produto com o código ${term.trim()}. A base cobre o que o Open Food Facts tem do Brasil — cadastre o produto para tê-lo aqui.`
            : "Nenhum alimento com esses filtros. Tente um termo mais curto ou remova o filtro de fonte."}
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Food</th>
                <th>Group</th>
                <th className="num">kcal</th>
                <th className="num">Prot.</th>
                <th className="num">Carb.</th>
                <th className="num">Gord.</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              {foods.map((a) => (
                <tr
                  key={a.id}
                  className="clicavel"
                  onClick={() =>
                    /* A recipe opens in its own editor: the food sheet would not
                       show the ingredients, which is what is to be checked. */
                    navigate(a.source === "RECIPE" ? `/recipes/${a.id}` : `/foods/${a.id}`)
                  }
                >
                  <td>
                    <strong>{a.description}</strong>
                    {a.brand && <div className="minusculo">{a.brand}</div>}
                  </td>
                  <td className="discreto">{a.group ?? "—"}</td>
                  <td className="num">{num(a.energyKcal)}</td>
                  <td className="num">{num(a.proteinG)}</td>
                  <td className="num">{num(a.carbohydrateG)}</td>
                  <td className="num">{num(a.fatG)}</td>
                  <td>
                    <span className={`tag ${a.publicBase ? "" : "verde"}`}>
                      {a.source === "OPEN_FOOD_FACTS" ? "OFF" : a.source.toLowerCase()}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="minusculo" style={{ marginTop: "0.7rem" }}>
        Valores por 100 g. Um traço indica nutriente não determinado na fonte — não é zero.
      </p>
    </>
  );
}

function num(value?: number) {
  return value === undefined || value === null ? "—" : value.toFixed(value >= 100 ? 0 : 1).replace(".", ",");
}

function PanelImport({
  onClose,
  onImport,
}: {
  onClose: () => void;
  onImport: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [source, setSource] = useState<DataSource>("CUSTOM");
  const [separator, setSeparator] = useState(",");
  const [result, setResult] = useState<ResultImport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    const file = input.current?.files?.[0];
    if (!file) {
      setError("Escolha um arquivo CSV.");
      return;
    }
    setError(null);
    setResult(null);
    setSending(true);
    try {
      const output = await api.foods.importAll(file, source, separator);
      setResult(output);
      onImport();
    } catch (e) {
      setError(explainError(e, "importar o arquivo"));
    } finally {
      setSending(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Importar tabela de alimentos</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.9rem" }}>
        As colunas de nutriente são reconhecidas pelo nome: <code>energyKcal</code>,{" "}
        <code>energia_kcal</code> ou <code>Energy (kcal)</code> chegam todas ao mesmo campo.
        Os alimentos ficam vinculados ao seu consultório e não entram na base comum.
      </p>

      {error && <div className="warning error" style={{ marginBottom: "0.85rem" }}>{error}</div>}

      {result && (
        <div className={`warning ${result.imported > 0 ? "ok" : "attention"}`} style={{ marginBottom: "0.85rem" }}>
          <strong>
            {count(result.imported, "alimento importado", "alimentos importados")}
            {result.ignored > 0
              ? `, ${count(result.ignored, "linha ignorada", "linhas ignoradas")}`
              : ""}
            .
          </strong>
          {result.warnings.length > 0 && (
            <ul style={{ margin: "0.4rem 0 0", paddingLeft: "1.1rem" }}>
              {result.warnings.slice(0, 6).map((warning, i) => (
                <li key={i}>{warning}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="grid three">
        <div className="field">
          <label htmlFor="imp-arquivo">Arquivo CSV</label>
          <input id="imp-arquivo" type="file" accept=".csv,text/csv" ref={input} />
        </div>
        <div className="field">
          <label htmlFor="imp-fonte">Procedência</label>
          <select
            id="imp-fonte"
            value={source}
            onChange={(e) => setSource(e.target.value as DataSource)}
          >
            <option value="CUSTOM">Cadastro próprio</option>
            <option value="TBCA">TBCA — FoRC/USP</option>
            <option value="IBGE">IBGE — POF</option>
            <option value="MANUFACTURER">Rótulo do fabricante</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="imp-sep">Separator</label>
          <select id="imp-sep" value={separator} onChange={(e) => setSeparator(e.target.value)}>
            <option value=",">Vírgula ( , )</option>
            <option value=";">Point e vírgula ( ; )</option>
            <option value={"\t"}>Tabulação</option>
          </select>
        </div>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="button secundario" onClick={onClose}>
          Close
        </button>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Importando…" : "Importar"}
        </button>
      </div>
    </form>
  );
}

function FormNovoFood({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: (id: number) => void;
}) {
  const [description, setDescription] = useState("");
  const [group, setGroup] = useState("");
  const [brand, setBrand] = useState("");
  const [values, setValues] = useState<Record<string, string>>({});
  const [measureDescription, setMeasureDescription] = useState("");
  const [measureGrams, setMeasureGrams] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fieldErrors = useFieldErrors();
  const feedback = useFeedback();

  const fields = [
    { key: "energyKcal", label: "Energia (kcal)" },
    { key: "proteinG", label: "Proteínas (g)" },
    { key: "carbohydrateG", label: "Carboidratos (g)" },
    { key: "fatG", label: "Gorduras (g)" },
    { key: "fiberG", label: "Fibra (g)" },
    { key: "sugarsG", label: "Açúcares (g)" },
    { key: "fatSaturatedG", label: "Saturadas (g)" },
    { key: "sodiumMg", label: "Sódio (mg)" },
  ];

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      // A blank field stays out of the body: absence is "not determined",
      // and sending zero would assert that the food does not contain the
      // nutrient.
      const composition: Record<string, number> = {};
      Object.entries(values).forEach(([key, text]) => {
        const clean = text.replace(",", ".").trim();
        if (clean !== "" && !Number.isNaN(Number(clean))) {
          composition[key] = Number(clean);
        }
      });

      const measures =
        measureDescription.trim() && measureGrams.trim()
          ? [
              {
                description: measureDescription.trim(),
                grams: Number(measureGrams.replace(",", ".")),
                standard: true,
              },
            ]
          : [];

      const created = await api.foods.create({
        description: description.trim(),
        group: group.trim() || undefined,
        brand: brand.trim() || undefined,
        composition,
        measures,
      });
      feedback.confirm(`"${created.description}" entrou na sua base de alimentos.`);
      onCreate(created.id);
    } catch (e) {
      if (!fieldErrors.apply(e)) {
        setError(explainError(e, "cadastrar o alimento"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Novo alimento</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.9rem" }}>
        Valores por 100 g. Deixe em branco o que não souber — em branco significa
        “não determinado”, e é diferente de zero.
      </p>

      {error && <div className="warning error" style={{ marginBottom: "0.85rem" }}>{error}</div>}

      <div className="grid three">
        <div className="field" style={{ gridColumn: "span 2" }}>
          <label htmlFor="na-desc">Descrição</label>
          <input
            id="na-desc"
            name="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            {...fieldErrors.props("description")}
          />
          <FieldError field="description" errors={fieldErrors.errors} />
        </div>
        <div className="field">
          <label htmlFor="na-grupo">Group</label>
          <input
            id="na-grupo"
            name="group"
            value={group}
            onChange={(e) => setGroup(e.target.value)}
            {...fieldErrors.props("group")}
          />
          <FieldError field="group" errors={fieldErrors.errors} />
        </div>
        <div className="field">
          <label htmlFor="na-marca">Brand</label>
          <input
            id="na-marca"
            name="brand"
            value={brand}
            onChange={(e) => setBrand(e.target.value)}
            {...fieldErrors.props("brand")}
          />
          <FieldError field="brand" errors={fieldErrors.errors} />
        </div>
      </div>

      <h3 style={{ margin: "1rem 0 0.5rem" }}>Composição por 100 g</h3>
      <div className="grid three">
        {fields.map((field) => (
          <div className="field" key={field.key}>
            <label htmlFor={`na-${field.key}`}>{field.label}</label>
            <input
              id={`na-${field.key}`}
              inputMode="decimal"
              value={values[field.key] ?? ""}
              onChange={(e) => setValues((v) => ({ ...v, [field.key]: e.target.value }))}
            />
          </div>
        ))}
      </div>

      <h3 style={{ margin: "1rem 0 0.5rem" }}>Porção usual (optional)</h3>
      <div className="grid two">
        <div className="field">
          <label htmlFor="na-med">Descrição da porção</label>
          <input
            id="na-med"
            placeholder="colher de sopa, fatia, unidade…"
            value={measureDescription}
            onChange={(e) => setMeasureDescription(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="na-med-g">Weight (g)</label>
          <input
            id="na-med-g"
            inputMode="decimal"
            value={measureGrams}
            onChange={(e) => setMeasureGrams(e.target.value)}
          />
        </div>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="button secundario" onClick={onClose}>
          Cancel
        </button>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Cadastrar alimento"}
        </button>
      </div>
    </form>
  );
}
