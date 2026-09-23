import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Apple, ChefHat, Package, PenLine, Plus, SearchX, Upload, X } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import type { FoodSummary, DataSource, ResultImport } from "../api/types";
import { count } from "../text";
import { Own, Reference } from "../components/Own";
import { useIsNarrow, useIsPhone } from "../hooks/useMediaQuery";

const SOURCES: { value: DataSource | ""; label: string }[] = [
  { value: "", label: "Todas as fontes" },
  { value: "TACO", label: "TACO — NEPA/Unicamp" },
  { value: "OPEN_FOOD_FACTS", label: "Open Food Facts" },
  { value: "CUSTOM", label: "Cadastro próprio" },
  { value: "RECIPE", label: "Receitas" },
];

/** The page never fetches more than this; the caption says so when there is more. */
const PAGE_SIZE = 40;

export default function Foods() {
  const navigate = useNavigate();
  const phone = useIsPhone();
  const narrow = useIsNarrow();

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
        size: PAGE_SIZE,
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

  function open(a: FoodSummary) {
    /* A recipe opens in its own editor: the food sheet would not show the
       ingredients, which is what is to be checked. */
    navigate(a.source === "RECIPE" ? `/recipes/${a.id}` : `/foods/${a.id}`);
  }

  const importButton = (
    <button
      type="button"
      className="button secundario"
      onClick={() => setPanel(panel === "importAll" ? "none" : "importAll")}
    >
      <Upload aria-hidden="true" />
      Importar tabela
    </button>
  );
  const recipeButton = (
    <button type="button" className="button secundario" onClick={() => navigate("/recipes/new")}>
      <ChefHat aria-hidden="true" />
      Nova receita
    </button>
  );
  const newButton = (
    <button type="button" className="button" onClick={() => setPanel(panel === "novo" ? "none" : "novo")}>
      <Plus aria-hidden="true" />
      Novo alimento
    </button>
  );

  return (
    <div className="page-foods">
      <div className="header-page">
        <div>
          <h1>Alimentos</h1>
          <p>
            {total.toLocaleString("pt-BR")} {total === 1 ? "alimento" : "alimentos"} no acervo
            visível
          </p>
        </div>
        {/* On the phone the primary action comes first and full width; the
            other two share a strip that scrolls sideways. */}
        {phone ? (
          <div className="header-page-actions">
            {newButton}
            <div className="scroll-strip">
              {importButton}
              {recipeButton}
            </div>
          </div>
        ) : (
          <div className="header-page-actions">
            {importButton}
            {recipeButton}
            {newButton}
          </div>
        )}
      </div>

      {panel === "importAll" && <PanelImport onClose={() => setPanel("none")} onImport={find} />}
      {panel === "novo" && (
        <FormNovoFood
          onClose={() => setPanel("none")}
          onCreate={(id) => navigate(`/foods/${id}`)}
        />
      )}

      <div className="card foods-toolbar" role="search">
        <div className="field foods-search">
          <label htmlFor="busca-alimento">Buscar</label>
          <div className="input-search">
            <input
              id="busca-alimento"
              type="search"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Digite sem acento, se preferir: acucar, feijao…"
              autoComplete="off"
            />
          </div>
        </div>
        <div className="field">
          <label htmlFor="filtro-grupo">Grupo</label>
          <select id="filtro-grupo" value={group} onChange={(e) => setGroup(e.target.value)}>
            <option value="">Todos os grupos</option>
            {groups.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="filtro-fonte">Fonte</label>
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

      {error && <div className="warning error">{error}</div>}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : foods.length === 0 ? (
        <div className="card foods-empty">
          <div className="empty">
            <span className="empty-icon">
              <SearchX aria-hidden="true" />
            </span>
            <p className="empty-hint">
              {byCode
                ? `Nenhum produto com o código ${term.trim()}. A base cobre o que o Open Food Facts tem do Brasil — cadastre o produto para tê-lo aqui.`
                : "Nenhum alimento com esses filtros. Tente um termo mais curto ou remova o filtro de fonte."}
            </p>
          </div>
        </div>
      ) : (
        <>
          {total > foods.length && (
            <p className="foods-count">
              Mostrando os {foods.length} primeiros de {total.toLocaleString("pt-BR")} — refine a
              busca para chegar aos demais.
            </p>
          )}
          {narrow ? (
            <div className="list-rows foods-list">
              {foods.map((a) => (
                <button type="button" key={a.id} className="list-row" onClick={() => open(a)}>
                  <span className="list-row-lead">
                    <SourceIcon source={a.source} publicBase={a.publicBase} />
                  </span>
                  <span className="list-row-body">
                    <span className="list-row-title">{a.description}</span>
                    {(a.brand || a.group) && (
                      <span className="list-row-meta">
                        {[a.brand, a.group].filter(Boolean).join(" · ")}
                      </span>
                    )}
                    <span className="readout">
                      {num(a.energyKcal)} kcal · P {num(a.proteinG)} · C {num(a.carbohydrateG)} · G{" "}
                      {num(a.fatG)}
                    </span>
                  </span>
                  <span className="list-row-trail">
                    <SourceTag food={a} />
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <TableScroll className="foods-table">
              <table>
                <thead>
                  <tr>
                    <th>Alimento</th>
                    <th>Grupo</th>
                    <th className="num">kcal</th>
                    <th className="num">Prot.</th>
                    <th className="num">Carb.</th>
                    <th className="num">Gord.</th>
                    <th>Fonte</th>
                  </tr>
                </thead>
                <tbody>
                  {foods.map((a) => (
                    <tr key={a.id} className="clicavel" onClick={() => open(a)}>
                      <td>
                        <strong className="foods-name">{a.description}</strong>
                        {a.brand && <span className="foods-brand">{a.brand}</span>}
                      </td>
                      <td>
                        <div className="foods-group" title={a.group ?? undefined}>
                          {a.group ?? "—"}
                        </div>
                      </td>
                      <td className="num">{num(a.energyKcal)}</td>
                      <td className="num">{num(a.proteinG)}</td>
                      <td className="num">{num(a.carbohydrateG)}</td>
                      <td className="num">{num(a.fatG)}</td>
                      <td>
                        <SourceTag food={a} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableScroll>
          )}
        </>
      )}
      <p className="minusculo foods-note">
        Valores por 100 g. Um traço indica nutriente não determinado na fonte — não é zero.
      </p>
    </div>
  );
}

/*
  A fonte e a propriedade eram a mesma marca, distinguidas só pela cor. Agora
  são duas: de onde o dado veio, e se ele é do consultório — que é o que
  responde "fui eu que criei?".
*/
function SourceTag({ food }: { food: FoodSummary }) {
  if (!food.publicBase) return <Own />;
  return <Reference>{food.source === "OPEN_FOOD_FACTS" ? "OFF" : food.source.toLowerCase()}</Reference>;
}

function SourceIcon({ source, publicBase }: { source: DataSource; publicBase: boolean }) {
  if (source === "RECIPE") return <ChefHat aria-hidden="true" />;
  if (source === "OPEN_FOOD_FACTS") return <Package aria-hidden="true" />;
  if (!publicBase) return <PenLine aria-hidden="true" />;
  return <Apple aria-hidden="true" />;
}

/**
 * The numeric table: scrolls sideways with the first column held, and the
 * right edge fades until the last column is in view (data-scroll-end, see
 * .table-scroll in components.css).
 */
function TableScroll({ className, children }: { className?: string; children: ReactNode }) {
  const box = useRef<HTMLDivElement>(null);
  const [atEnd, setAtEnd] = useState(true);

  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const check = () =>
      setAtEnd(element.scrollWidth - element.clientWidth - element.scrollLeft <= 1);
    check();
    element.addEventListener("scroll", check, { passive: true });
    const observer = new ResizeObserver(check);
    observer.observe(element);
    return () => {
      element.removeEventListener("scroll", check);
      observer.disconnect();
    };
  }, [children]);

  return (
    <div
      ref={box}
      className={className ? `rolagem table-scroll ${className}` : "rolagem table-scroll"}
      data-scroll-end={atEnd ? "true" : "false"}
    >
      {children}
    </div>
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
    <form className="card foods-panel" onSubmit={send}>
      <div className="card-head">
        <div>
          <h2 className="card-title">Importar tabela de alimentos</h2>
          <p className="card-sub">
            As colunas de nutriente são reconhecidas pelo nome: <code>energiaKcal</code>,{" "}
            <code>energia_kcal</code> ou <code>Energia (kcal)</code> chegam todas ao mesmo campo.
            Os alimentos ficam vinculados ao seu consultório e não entram na base comum.
          </p>
        </div>
        <button
          type="button"
          className="button icon ghost pequeno"
          onClick={onClose}
          aria-label="Fechar painel"
          title="Fechar painel"
        >
          <X aria-hidden="true" />
        </button>
      </div>

      {error && <div className="warning error">{error}</div>}

      {result && (
        <div className={`warning ${result.imported > 0 ? "ok" : "attention"}`}>
          <strong>
            {count(result.imported, "alimento importado", "alimentos importados")}
            {result.ignored > 0
              ? `, ${count(result.ignored, "linha ignorada", "linhas ignoradas")}`
              : ""}
            .
          </strong>
          {result.warnings.length > 0 && (
            <ul>
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
          <label htmlFor="imp-sep">Separador</label>
          <select id="imp-sep" value={separator} onChange={(e) => setSeparator(e.target.value)}>
            <option value=",">Vírgula ( , )</option>
            <option value=";">Point e vírgula ( ; )</option>
            <option value={"\t"}>Tabulação</option>
          </select>
        </div>
      </div>

      <div className="row end foods-panel-actions">
        <button type="button" className="button secundario" onClick={onClose}>
          Fechar
        </button>
        <button className="button" type="submit" disabled={sending}>
          <Upload aria-hidden="true" />
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
    <form className="card foods-panel" onSubmit={send}>
      <div className="card-head">
        <div>
          <h2 className="card-title">Novo alimento</h2>
          <p className="card-sub">
            Valores por 100 g. Deixe em branco o que não souber — em branco significa
            “não determinado”, e é diferente de zero.
          </p>
        </div>
        <button
          type="button"
          className="button icon ghost pequeno"
          onClick={onClose}
          aria-label="Fechar painel"
          title="Fechar painel"
        >
          <X aria-hidden="true" />
        </button>
      </div>

      {error && <div className="warning error">{error}</div>}

      <div className="food-form-grid identity">
        <div className="field wide">
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
          <label htmlFor="na-grupo">Grupo</label>
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
          <label htmlFor="na-marca">Marca</label>
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

      <h3 className="foods-panel-section">Composição por 100 g</h3>
      <div className="food-form-grid composition">
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

      <h3 className="foods-panel-section">Porção usual (opcional)</h3>
      <div className="food-form-grid portion">
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
          <label htmlFor="na-med-g">Peso (g)</label>
          <input
            id="na-med-g"
            inputMode="decimal"
            value={measureGrams}
            onChange={(e) => setMeasureGrams(e.target.value)}
          />
        </div>
      </div>

      <div className="row end foods-panel-actions">
        <button type="button" className="button secundario" onClick={onClose}>
          Cancelar
        </button>
        <button className="button" type="submit" disabled={sending}>
          <Plus aria-hidden="true" />
          {sending ? "Salvando…" : "Cadastrar alimento"}
        </button>
      </div>
    </form>
  );
}
