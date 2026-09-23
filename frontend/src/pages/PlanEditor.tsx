import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  ArrowDown,
  ArrowLeftRight,
  ArrowUp,
  Calculator,
  Camera,
  CameraOff,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  CircleAlert,
  CircleDot,
  Clock,
  Copy,
  ExternalLink,
  FileText,
  GripVertical,
  LoaderCircle,
  Lock,
  PenLine,
  Plus,
  Send,
  Star,
  Trash2,
  TriangleAlert,
  X,
} from "lucide-react";
import { MacroRing } from "../components/MacroRing";
import { Sheet } from "../components/Sheet";
import { useIsCompact } from "../hooks/useMediaQuery";
import { useScrolled } from "../hooks/useScrolled";
import FoodSearch from "../components/FoodSearch";
import { QuickFood, QuickMeasure } from "../components/QuickFood";
import { RichTextEditor } from "../components/RichText/LazyEditor";
import { RichTextView } from "../components/RichText/RichTextView";
import { emptyDoc, isEmptyDoc, parseRichDoc } from "../components/RichText/document";
import { ErrorApi, api } from "../api/client";
import { explainError, whereLook } from "../api/errors";
import { useFeedback } from "../components/Feedback";
import { MACROS_PRINCIPAIS, NUTRIENTS, formatNutrient, labelDe } from "../api/nutrients";
import { count } from "../text";
import { NotesField, NotesView } from "../components/RichText/NotesField";
import type {
  AdequacyBand,
  Composition,
  FavoriteMeal,
  FoodDetail,
  FoodSummary,
  Measure,
  PrescriptionMethod,
  Handout,
  PlanHandout,
  MacroComparison,
  MealItemKind,
  PatientSummary,
  PlanRequest,
  PlanResponse,
  PlanSummary,
  Total,
} from "../api/types";

/**
 * Uma substituição em edição.
 *
 * Tem os mesmos campos do item porque é a mesma coisa: uma porção de um
 * alimento. Era só descrição e gramas, e por isso a troca não entrava em
 * conta nenhuma — o substituto não tinha alimento, logo não tinha macro.
 */
interface SubstitutionEdit {
  key: string;
  foodId?: number;
  measureId?: number;
  description: string;
  quantity: string;
  measures: { id: number; description: string; grams: number; standard: boolean }[];
}

/** Uma alteração de item: o que mudar, ou como calcular isso do estado atual. */
type ChangeItem = Partial<ItemEdit> | ((current: ItemEdit) => Partial<ItemEdit>);

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
  substitutions: SubstitutionEdit[];
  /** FOOD ou SEPARATOR. O separador é uma barra entre alimentos. */
  kind: MealItemKind;
}

interface MealEdit {
  key: string;
  /** Id no servidor. Ausente enquanto a refeição não foi salva. */
  id?: number;
  name: string;
  time: string;
  notes: string;
  /** Se a refeição soma no dia. Desligada, vira opção substituta. */
  inCalculation: boolean;
  /** Só visual: resumir a refeição para revisar o cardápio inteiro. */
  collapsed: boolean;
  /** Nome do arquivo da foto, quando há uma. */
  photoName?: string;
  /** Só visual: o editor da observação só é montado quando aberto. */
  editingNotes?: boolean;
  items: ItemEdit[];
}

let counter = 0;
const novaKey = () => `k${++counter}`;

/**
 * As seis refeições com que um plano em branco nasce.
 *
 * Sem horário, e com a inicial maiúscula em cada palavra — é como o cliente
 * escreve na página 32, e ele preenche o horário quando é o caso.
 */
const MEALS_DEFAULT = [
  "Café da Manhã",
  "Lanche da Manhã",
  "Almoço",
  "Lanche da Tarde",
  "Jantar",
  "Ceia",
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
  /**
   * Distribuição planejada e peso programado.
   *
   * É a coluna "teórico" da aba de distribuição. O peso é o programado, não o
   * atual — resposta dele à pergunta 1.
   */
  const [targetProtein, setTargetProtein] = useState("");
  const [targetCarbohydrate, setTargetCarbohydrate] = useState("");
  const [targetFat, setTargetFat] = useState("");
  const [targetWeight, setTargetWeight] = useState("");
  const [energyPlanId, setEnergyPlanId] = useState<number | undefined>();
  const [validityStart, setValidityStart] = useState("");
  const [validityEnd, setValidityEnd] = useState("");
  const [handouts, setHandouts] = useState("");
  const [internalNotes, setInternalNotes] = useState("");
  const [meals, setMeals] = useState<MealEdit[]>([]);
  const [favorites, setFavorites] = useState<FavoriteMeal[]>([]);
  const [templates, setTemplates] = useState<PlanSummary[]>([]);
  const [chosenTemplate, setChosenTemplate] = useState("");
  const [showingFavorites, setShowingFavorites] = useState(false);
  /**
   * Qual item abriu o cadastro rápido, e de quê.
   *
   * Guardado aqui e não dentro da linha porque o que ele cadastra volta para
   * o item que pediu — o alimento novo já entra escolhido, a medida nova já
   * entra selecionada. Sem isso ele cadastraria e teria que procurar de novo.
   */
  const [quick, setQuick] = useState<
    { mealKey: string; itemKey: string; what: "food" | "measure" } | null
  >(null);

  const [loading, setLoading] = useState(!!planId);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const planWarnings = useFeedback();
  /** Validation errors of the plan, one per row and already located on screen. */
  const [problems, setProblems] = useState<string[]>([]);
  const [dirty, setDirty] = useState(false);
  /**
   * Quantas alterações houve, para o salvamento automático se reprogramar.
   *
   * Um booleano não serve de gatilho: `touch()` com o valor já em true
   * não redesenha, o efeito não roda de novo, e o temporizador dispararia no
   * meio da digitação em vez de esperar a pausa.
   */
  const [revision, setRevision] = useState(0);
  const savingRef = useRef(false);
  /**
   * A revisão que a gravação em curso está levando.
   *
   * Sem ela, uma tecla digitada enquanto o pedido está no ar seria esquecida:
   * a gravação termina, limpa o "há o que salvar", e a alteração só voltaria a
   * ser notada na tecla seguinte — que pode nunca vir, se a pessoa parou ali.
   */
  const revisionSaving = useRef(0);
  /**
   * A revisão mais recente, fora do closure.
   *
   * `revision` dentro de save() é o valor daquele render e não muda enquanto o
   * pedido está no ar — comparar um com o outro daria sempre igual. A ref é o
   * que enxerga o que foi digitado durante a gravação.
   */
  const revisionLatest = useRef(0);
  /** Reprograma o salvamento quando ele encontra outro já em curso. */
  const [retry, setRetry] = useState(0);
  const [savedAt, setSavedAt] = useState<Date | null>(null);

  /** Marca que há o que gravar e reprograma o salvamento. */
  const touch = useCallback(() => {
    setDirty(true);
    revisionLatest.current += 1;
    setRevision(revisionLatest.current);
  }, []);

  useEffect(() => {
    api.patients
      .list({ active: true, size: 200 })
      .then((p) => setPatients(p.content))
      .catch(() => setPatients([]));
  }, []);

  useEffect(() => {
    api.mealFavorites
      .list()
      .then(setFavorites)
      .catch(() => setFavorites([]));
  }, []);

  useEffect(() => {
    if (planId) return;
    api.prescriptions
      .list({ template: true, size: 100 })
      .then((page) => setTemplates(page.content))
      .catch(() => setTemplates([]));
  }, [planId]);

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
    setTargetProtein(data.targetProteinPct ? String(data.targetProteinPct) : "");
    setTargetCarbohydrate(
      data.targetCarbohydratePct ? String(data.targetCarbohydratePct) : "",
    );
    setTargetFat(data.targetFatPct ? String(data.targetFatPct) : "");
    setTargetWeight(data.targetWeightKg ? String(data.targetWeightKg) : "");
    setEnergyPlanId(data.energyPlanId);
    setValidityStart(data.validityStart ?? "");
    setValidityEnd(data.validityEnd ?? "");
    setHandouts(data.handouts ?? "");
    setInternalNotes(data.internalNotes ?? "");
    setMeals(
      data.meals.map((r) => ({
        key: novaKey(),
        id: r.id,
        name: r.name,
        time: r.time?.slice(0, 5) ?? "",
        notes: r.notes ?? "",
        inCalculation: r.inCalculation,
        collapsed: false,
        photoName: r.photoName,
        items: r.items.map((i) => ({
          key: novaKey(),
          kind: i.kind ?? "FOOD",
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
            measures: [],
          })),
        })),
      })),
    );
    setDirty(false);
    void reloadMeasures(data);
  }, [reloadMeasures]);

  /**
   * Um planejamento em branco já nasce com as seis refeições.
   *
   * "Primeiramente eu quero que ao abrir um planejamento em branco, já venha 6
   * refeições sem horários." Só vale para plano novo: abrir um plano salvo tem
   * que mostrar o que foi salvo, inclusive quando são zero refeições.
   */
  useEffect(() => {
    if (planId) return;
    setMeals(MEALS_DEFAULT.map((name) => newMeal(name)));
    // Semeia uma vez, na abertura. Depois disso as refeições são do usuário.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planId]);

  /**
   * O título já vem com o nome do paciente, e continua editável.
   *
   * "quero que por função o nome do planejamento seja o nome do paciente
   * completo ou o primeiro e o último nome, mas editável, é só pra adiantar
   * meu lado."
   */
  useEffect(() => {
    if (planId || template || title.trim() || !patientId) return;
    const chosen = patients.find((p) => String(p.id) === patientId);
    if (chosen) setTitle(chosen.name);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientId, patients, planId, template]);

  /**
   * Carrega o plano ao abrir pelo endereço.
   *
   * Sai fora quando o plano que está na tela já é este. Isso acontece logo
   * depois do primeiro salvamento automático: ele cria o plano e troca o
   * endereço de /new para /:id, e buscar de novo aqui remontaria as refeições
   * a partir do servidor — jogando fora o que foi digitado durante a gravação
   * e tirando o cursor do campo.
   */
  useEffect(() => {
    if (!planId) return;
    if (plan?.id === planId) return;
    setLoading(true);
    api.prescriptions
      .detail(planId)
      .then(applyPlan)
      .catch((e) => setError(explainError(e, "abrir o plano")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planId, applyPlan]);

  /** A soma das três metas, para a tela avisar antes do servidor recusar. */
  const sumOfTargets = useMemo(() => {
    const parts = [targetProtein, targetCarbohydrate, targetFat].map(numberOrUndefined);
    if (parts.every((value) => value === undefined)) return undefined;
    return parts.reduce<number>((sum, value) => sum + (value ?? 0), 0);
  }, [targetProtein, targetCarbohydrate, targetFat]);

  /**
   * Traz a meta do último cálculo energético do paciente.
   *
   * É o "Importar Dados da Aba de Cálculo" da página 31. Traz a energia e o
   * peso programado; a distribuição em macros continua sendo decisão dele,
   * porque o cálculo energético não opina sobre ela.
   */
  async function importFromEnergy() {
    if (!patientId) return;
    try {
      const plans = await api.energyPlans.ofPatient(Number(patientId));
      const latest = plans[0];
      if (!latest) {
        setError("Este paciente ainda não tem cálculo energético para importar.");
        return;
      }
      const full = await api.energyPlans.detail(latest.id);
      setTargetEnergy(String(Math.round(full.prescribedKcal)));
      if (full.targetWeightKg) setTargetWeight(String(full.targetWeightKg));
      setEnergyPlanId(full.id);
      touch();
      planWarnings.confirm(`Meta de ${Math.round(full.prescribedKcal)} kcal importada.`);
    } catch (e) {
      setError(explainError(e, "importar o cálculo energético"));
    }
  }

  /**
   * Abre em outra guia o PDF da anamnese mais recente do paciente.
   *
   * "Um botão que ao apertar abra em outra guia o PDF da anamnese do
   * paciente." É consulta durante a montagem do cardápio, não entrega — por
   * isso abre ao lado em vez de navegar para fora do editor.
   */
  async function openAnamnesisPdf() {
    if (!patientId) return;
    try {
      const list = await api.anamneses.ofPatient(Number(patientId));
      const latest = list[0];
      if (!latest) {
        setError("Este paciente ainda não tem anamnese registrada.");
        return;
      }
      const { url } = await api.anamneses.pdf(latest.id);
      window.open(url, "_blank", "noopener");
    } catch (e) {
      setError(explainError(e, "abrir a anamnese"));
    }
  }

  /** O alimento recém-cadastrado entra no item que pediu, já escolhido. */
  function useCreatedFood(food: FoodDetail) {
    if (!quick) return;
    setMeals((current) =>
      current.map((r) =>
        r.key !== quick.mealKey
          ? r
          : {
              ...r,
              items: r.items.map((i) =>
                i.key !== quick.itemKey
                  ? i
                  : {
                      ...i,
                      foodId: food.id,
                      description: food.description,
                      measureId: undefined,
                      measures: food.measures.map((m) => ({
                        id: m.id,
                        description: m.description,
                        grams: m.grams,
                        standard: m.standard,
                      })),
                    },
              ),
            },
      ),
    );
    touch();
    setQuick(null);
  }

  /** A medida recém-cadastrada entra na lista do item e já fica selecionada. */
  function useCreatedMeasure(measure: Measure) {
    if (!quick) return;
    setMeals((current) =>
      current.map((r) =>
        r.key !== quick.mealKey
          ? r
          : {
              ...r,
              items: r.items.map((i) =>
                i.key !== quick.itemKey
                  ? i
                  : {
                      ...i,
                      measureId: measure.id,
                      measures: [
                        ...i.measures,
                        {
                          id: measure.id,
                          description: measure.description,
                          grams: measure.grams,
                          standard: measure.standard,
                        },
                      ],
                    },
              ),
            },
      ),
    );
    touch();
    setQuick(null);
  }

  /**
   * Anexa a foto do prato à refeição.
   *
   * Só depois de salva: a foto vai para um endereço que depende do id da
   * refeição, e uma refeição que ainda não foi salva não tem id. O botão diz
   * isso em vez de falhar no envio.
   */
  async function sendPhoto(key: string, file: File) {
    const meal = meals.find((r) => r.key === key);
    if (!plan || !meal?.id) return;
    try {
      await api.mealPhotos.send(plan.id, meal.id, file);
      setMeals((current) =>
        current.map((r) => (r.key === key ? { ...r, photoName: file.name } : r)),
      );
      planWarnings.confirm("Foto anexada. Ela sai no PDF do cardápio.");
    } catch (e) {
      setError(explainError(e, "anexar a foto"));
    }
  }

  async function removePhoto(key: string) {
    const meal = meals.find((r) => r.key === key);
    if (!plan || !meal?.id) return;
    try {
      await api.mealPhotos.remove(plan.id, meal.id);
      setMeals((current) =>
        current.map((r) => (r.key === key ? { ...r, photoName: undefined } : r)),
      );
    } catch (e) {
      setError(explainError(e, "remover a foto"));
    }
  }

  /**
   * Carrega as refeições de um modelo para dentro do plano em branco.
   *
   * "aparecem modelos pré-prontos, que podem ser selecionados e com isso irá
   * aparecer lá o cardápio que foi salvo. Não teremos nenhum por padrão, mas
   * preciso que exista a função de salvar os meus."
   *
   * Só troca as refeições. Título, paciente e meta já foram preenchidos, e
   * sobrescrevê-los apagaria escolhas que ele acabou de fazer.
   */
  async function applyTemplate(templateId: string) {
    setChosenTemplate(templateId);
    if (!templateId) {
      setMeals(MEALS_DEFAULT.map((name) => newMeal(name)));
      touch();
      return;
    }
    try {
      const model = await api.prescriptions.detail(Number(templateId));
      setMeals(
        model.meals.map((r) => ({
          key: novaKey(),
          name: r.name,
          time: r.time?.slice(0, 5) ?? "",
          notes: r.notes ?? "",
          inCalculation: r.inCalculation,
          collapsed: false,
          items: r.items.map((i) => ({
            key: novaKey(),
            kind: i.kind ?? "FOOD",
            foodId: i.foodId,
            measureId: i.measureId,
            description: i.description,
            quantity:
              i.quantity !== undefined && i.quantity !== null ? String(i.quantity) : "",
            notes: i.notes ?? "",
            measures: [],
            substitutions: i.substitutions.map((e) => ({
              key: novaKey(),
              description: e.description,
              quantity: "",
              foodId: e.foodId,
              measures: [],
            })),
          })),
        })),
      );
      touch();
      void reloadMeasures(model);
    } catch (e) {
      setError(explainError(e, "abrir o modelo"));
    }
  }

  /** Uma refeição do editor no formato que a API espera. */
  function mealToRequest(r: MealEdit) {
    return {
      name: r.name.trim() || "Refeição",
      time: r.time ? `${r.time}:00` : undefined,
      notes: r.notes.trim() || undefined,
      inCalculation: r.inCalculation,
      items: r.items
        .filter((i) => i.kind === "SEPARATOR" || i.foodId || i.description.trim())
        .map((i) => ({
          kind: i.kind,
          foodId: i.foodId,
          measureId: i.measureId,
          description: i.description.trim() || undefined,
          quantity: i.quantity ? Number(i.quantity.replace(",", ".")) : undefined,
          notes: i.notes.trim() || undefined,
          substitutions:
            method !== "QUALITATIVE"
              ? i.substitutions
                  .filter((e) => e.description.trim())
                  .map((e) => ({
                    foodId: e.foodId,
                    measureId: e.measureId,
                    description: e.description.trim(),
                    quantity: e.quantity
                      ? Number(e.quantity.replace(",", "."))
                      : undefined,
                  }))
              : undefined,
        })),
    };
  }

  /**
   * Salva a refeição como favorita.
   *
   * Manda a refeição inteira, então funciona com o plano ainda não salvo — que
   * é quando ele costuma decidir que aquele almoço vale guardar.
   */
  async function favoriteMeal(key: string) {
    const meal = meals.find((r) => r.key === key);
    if (!meal) return;
    const name = window.prompt("Nome para achar esta refeição depois:", meal.name.trim());
    if (!name?.trim()) return;
    try {
      await api.mealFavorites.save({ name: name.trim(), meal: mealToRequest(meal) });
      planWarnings.confirm(`"${name.trim()}" salva nas suas refeições.`);
      setFavorites(await api.mealFavorites.list());
    } catch (e) {
      setError(explainError(e, "salvar a refeição"));
    }
  }

  /** Traz uma refeição salva para o fim do cardápio, como uma nova refeição. */
  async function insertFavorite(id: number) {
    try {
      const favorite = await api.mealFavorites.detail(id);
      setMeals((current) => [
        ...current,
        {
          key: novaKey(),
          name: favorite.mealName,
          time: "",
          notes: favorite.notes ?? "",
          inCalculation: true,
          collapsed: false,
          items: favorite.items.map((i) => ({
            key: novaKey(),
            kind: i.kind ?? "FOOD",
            foodId: i.foodId,
            measureId: i.measureId,
            description: i.description,
            quantity:
              i.quantity !== undefined && i.quantity !== null ? String(i.quantity) : "",
            notes: i.notes ?? "",
            measures: [],
            substitutions: i.substitutions.map((e) => ({
              key: novaKey(),
              description: e.description,
              quantity: "",
              foodId: e.foodId,
              measures: [],
            })),
          })),
        },
      ]);
      touch();
      setShowingFavorites(false);
    } catch (e) {
      setError(explainError(e, "trazer a refeição salva"));
    }
  }

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
      targetProteinPct: numberOrUndefined(targetProtein),
      targetCarbohydratePct: numberOrUndefined(targetCarbohydrate),
      targetFatPct: numberOrUndefined(targetFat),
      targetWeightKg: numberOrUndefined(targetWeight),
      energyPlanId,
      meals: meals.map(mealToRequest),
    };
  }

  /**
   * Grava o plano.
   *
   * `auto` é o salvamento que acontece sozinho enquanto se digita. Ele difere
   * em dois pontos, e os dois importam: não anuncia nada — um aviso de "plano
   * salvo" a cada pausa vira ruído — e não remonta as refeições a partir da
   * resposta. Remontar recriaria as linhas com chaves novas no meio da
   * digitação, e o cursor sairia do campo a cada pausa. Da resposta ele
   * aproveita só o que o servidor sabe melhor: os totais e os identificadores.
   */
  async function save(options: { auto?: boolean } = {}) {
    if (savingRef.current) return;
    savingRef.current = true;
    revisionSaving.current = revisionLatest.current;
    setError(null);
    setSaving(true);
    try {
      const body = buildBody();
      const saved = planId
        ? await api.prescriptions.update(planId, body)
        : await api.prescriptions.create(body);

      if (options.auto) {
        setPlan(saved);
        adoptIds(saved);
      } else {
        applyPlan(saved);
      }
      // Só declara tudo gravado se nada mudou desde que o pedido saiu.
      if (revisionLatest.current === revisionSaving.current) {
        setDirty(false);
      }
      setSavedAt(new Date());
      setProblems([]);
      if (!options.auto) {
        planWarnings.confirm("Plano salvo.");
      }
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
      savingRef.current = false;
      setSaving(false);
    }
  }

  /**
   * Adota os identificadores que o servidor devolveu, sem tocar no resto.
   *
   * A refeição precisa do id para receber foto. O casamento é por posição
   * porque é assim que ela vai e volta: o servidor remonta o cardápio na ordem
   * em que o corpo chegou.
   */
  function adoptIds(saved: PlanResponse) {
    setMeals((current) =>
      current.map((meal, index) => {
        const equivalent = saved.meals[index];
        return equivalent ? { ...meal, id: equivalent.id, photoName: equivalent.photoName } : meal;
      }),
    );
  }

  /**
   * O plano tem o mínimo para ser gravado?
   *
   * Sem isto o salvamento automático bateria na validação a cada tecla
   * enquanto o título ainda está vazio, e encheria a tela de erro por algo que
   * quem está digitando já sabe que falta.
   */
  /**
   * O plano tem o minimo para ser gravado?
   *
   * So fala do conteudo. Se o plano aceita alteracao ou nao e outra pergunta,
   * e misturar as duas fazia o aviso dizer "falta titulo ou paciente" num
   * plano encerrado que tinha os dois.
   */
  function saveable() {
    return title.trim().length > 0 && (template || !!patientId);
  }

  /** Grava sozinho so quando pode gravar e ha o que gravar. */
  function autoSaveable() {
    return podeEditNow() && saveable();
  }

  function podeEditNow() {
    return !plan || plan.status !== "CLOSED";
  }

  /**
   * Salva sozinho depois da pausa.
   *
   * "Em vez de salvar para calcular, deve salvar em tempo real os plano de
   * alimento para ver o feedback em tempo real." O total sai da mesma conta
   * que o PDF e o link do paciente usam — refazê-la aqui em TypeScript daria
   * um número que concorda hoje e diverge na primeira correção feita de um
   * lado só.
   *
   * A espera é de 900 ms: longa o bastante para não gravar a cada tecla, curta
   * o bastante para o número chegar enquanto ainda se está olhando para ele.
   */
  useEffect(() => {
    if (revision === 0 || !dirty || !autoSaveable()) return;
    // Reprograma enquanto uma gravação estiver no ar: save() sai fora quando
    // encontra outra em curso, e sem a nova tentativa a alteração ficaria
    // esperando a próxima tecla.
    const clock = setTimeout(() => {
      if (savingRef.current) {
        setRetry((r) => r + 1);
        return;
      }
      void save({ auto: true });
    }, 900);
    return () => clearTimeout(clock);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision, retry]);

  /** Garante que o que está na tela já chegou ao servidor. */
  async function flush() {
    if (dirty && autoSaveable()) {
      await save({ auto: true });
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
    touch();
    setMeals((current) => current.map((r) => (r.key === key ? { ...r, ...change } : r)));
  }

  /**
   * Altera um item da refeição.
   *
   * A alteração pode ser uma função do item corrente, e não só um objeto
   * pronto. A diferença importa quando ela chega depois de uma ida ao
   * servidor: o objeto pronto teria sido montado sobre o item de antes da ida,
   * e gravá-lo desfaria o que mudou nesse meio-tempo.
   */
  function changeItem(
    keyMeal: string,
    keyItem: string,
    change: ChangeItem,
  ) {
    touch();
    setMeals((current) =>
      current.map((r) =>
        r.key !== keyMeal
          ? r
          : {
              ...r,
              items: r.items.map((i) =>
                i.key === keyItem
                  ? { ...i, ...(typeof change === "function" ? change(i) : change) }
                  : i,
              ),
            },
      ),
    );
  }

  function newMeal(name = "Nova refeição", time = ""): MealEdit {
    return {
      key: novaKey(),
      name,
      time,
      notes: "",
      inCalculation: true,
      collapsed: false,
      items: [],
    };
  }

  function addMeal(name = "Nova refeição", time = "") {
    touch();
    setMeals((current) => [...current, newMeal(name, time)]);
  }

  function removeMeal(key: string) {
    touch();
    setMeals((current) => current.filter((r) => r.key !== key));
  }

  /** Duplica a refeição logo abaixo da original, com os itens junto. */
  function duplicateMeal(key: string) {
    touch();
    setMeals((current) => {
      const index = current.findIndex((r) => r.key === key);
      const source = current[index];
      if (!source) return current;
      const copy: MealEdit = {
        ...source,
        key: novaKey(),
        // A cópia é outra refeição: id e foto são do original.
        id: undefined,
        photoName: undefined,
        items: source.items.map((i) => ({
          ...i,
          key: novaKey(),
          substitutions: i.substitutions.map((e) => ({ ...e, key: novaKey() })),
        })),
      };
      return [...current.slice(0, index + 1), copy, ...current.slice(index + 1)];
    });
  }

  function newItem(kind: MealItemKind = "FOOD"): ItemEdit {
    return {
      key: novaKey(),
      kind,
      description: "",
      quantity: "",
      notes: "",
      measures: [],
      substitutions: [],
    };
  }

  function addItem(keyMeal: string, kind: MealItemKind = "FOOD") {
    touch();
    setMeals((current) =>
      current.map((r) =>
        r.key !== keyMeal ? r : { ...r, items: [...r.items, newItem(kind)] },
      ),
    );
  }

  /** Move um item uma posição para cima ou para baixo dentro da refeição. */
  function moveItem(keyMeal: string, keyItem: string, by: number) {
    touch();
    setMeals((current) =>
      current.map((r) => {
        if (r.key !== keyMeal) return r;
        const index = r.items.findIndex((i) => i.key === keyItem);
        const here = r.items[index];
        const there = r.items[index + by];
        if (!here || !there) return r;
        const items = [...r.items];
        items[index] = there;
        items[index + by] = here;
        return { ...r, items };
      }),
    );
  }

  function removeItem(keyMeal: string, keyItem: string) {
    touch();
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

  // Below 900px the rail lives in a bottom sheet and the page header is the
  // only bar on screen; both hooks stay above the early return.
  const compact = useIsCompact();
  const scrolled = useScrolled();
  const [sheetOpen, setSheetOpen] = useState(false);

  if (loading) return <p className="loading">Carregando…</p>;

  const podeEdit = !plan || plan.status !== "CLOSED";
  const kcalDay = totalSaved?.composition.energyKcal;
  const distributionDay = totalSaved?.distribution;

  /*
    The rail is one set of cards rendered once: beside the meals on the desk,
    inside a bottom sheet on tablet and phone. Rendering it twice would double
    every text node the tests and screen readers see.
  */
  const rail = (
    <>
      <PanelTotals total={totalSaved} dirty={dirty} target={targetEnergy} />
      <PanelDistribution
        comparison={totalSaved?.comparison ?? []}
        weightKg={plan?.targetWeightKg}
      />
      {plan && <PanelHandouts planId={plan.id} onlyRead={!podeEdit} />}
      {plan && !plan.template && (
        <PanelPublication plan={plan} onRun={action} onFlush={flush} />
      )}
      {plan && (
        <div className="card">
          <div className="card-head">
            <h3 className="card-title">Ações</h3>
          </div>
          <div className="cluster">
            {compact && patientId && !template && (
              <button
                type="button"
                className="button secundario pequeno"
                onClick={() => void openAnamnesisPdf()}
                title="Abre em outra guia"
              >
                <FileText aria-hidden="true" />
                Ver anamnese
              </button>
            )}
            <button
              type="button"
              className="button secundario pequeno"
              onClick={() =>
                action(
                  () => api.prescriptions.duplicate(plan.id, plan.patientId ?? undefined),
                  "Cópia criada como rascunho.",
                ).then(() => navigate("/prescriptions"))
              }
            >
              <Copy aria-hidden="true" />
              Duplicar
            </button>
            <button
              type="button"
              className="button perigo pequeno"
              onClick={async () => {
                if (!confirm("Remover este plano definitivamente?")) return;
                await api.prescriptions.remove(plan.id);
                navigate("/prescriptions");
              }}
            >
              <Trash2 aria-hidden="true" />
              Remover
            </button>
          </div>
        </div>
      )}
    </>
  );

  const savingState = (
    <SavingState
      dirty={dirty}
      saving={saving}
      savedAt={savedAt}
      saveable={saveable()}
      closed={!podeEdit}
    />
  );

  return (
    <div className="editor-page">
      {/*
        The header is sticky at every width: the save state is the answer to
        "did my last change go in?", and it has to be on screen wherever the
        person is in a plan of seven meals.
      */}
      <header
        className="header-page sticky editor-header"
        data-scrolled={scrolled ? "true" : "false"}
      >
        {compact && (
          <Link
            to="/prescriptions"
            className="button icon ghost header-page-back"
            aria-label="Voltar"
            title="Voltar"
          >
            <ChevronLeft aria-hidden="true" />
          </Link>
        )}
        <div className="editor-header-main">
          {!compact && (
            <Link to="/prescriptions" className="migalha">
              <ChevronLeft aria-hidden="true" />
              Prescrições
            </Link>
          )}
          <h1>{planId ? title || "Plano" : "Novo plano"}</h1>
          <p className="editor-header-sub">
            <span>
              {plan ? (
                <>
                  {plan.statusDescription}
                  {plan.patientName ? ` · ${plan.patientName}` : ""}
                </>
              ) : (
                "Monte as refeições. Os totais vão sendo calculados enquanto você edita."
              )}
            </span>
            {compact && savingState}
          </p>
        </div>
        <div className="header-page-actions editor-header-actions">
          {!compact && savingState}
          {!compact && patientId && !template && (
            <button
              type="button"
              className="button secundario"
              onClick={() => void openAnamnesisPdf()}
              title="Abre em outra guia"
            >
              <FileText aria-hidden="true" />
              Ver anamnese
            </button>
          )}
          <button
            type="button"
            className={`button${dirty && podeEdit ? " has-dot" : ""}`}
            onClick={() => void save()}
            disabled={saving || !podeEdit}
          >
            {saving ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </header>

      {error && <div className="warning error mb-2">{error}</div>}

      {problems.length > 0 && (
        <div className="warning error mb-2" role="alert">
          <strong>O plano não foi salvo. Corrija {problems.length === 1 ? "isto" : "estes pontos"}:</strong>
          <ul className="problems-list">
            {problems.map((p, i) => (
              <li key={i}>{p}</li>
            ))}
          </ul>
        </div>
      )}
      {!podeEdit && (
        <div className="warning attention mb-2">
          <div className="closed-notice">
            <span>Este plano está encerrado e não aceita alterações.</span>
            {/*
              A saída fica no mesmo aviso que dá a notícia. Ela existia, mas no
              painel da direita, longe de onde a pessoa está tentando editar —
              e um aviso que diz o que não dá para fazer sem dizer o que fazer
              é um beco.
            */}
            {plan && (
              <div className="cluster">
                <button
                  type="button"
                  className="button secundario pequeno"
                  onClick={() =>
                    void action(
                      () => api.prescriptions.backToDraft(plan.id),
                      "Plano reaberto como rascunho.",
                    )
                  }
                >
                  Reabrir como rascunho
                </button>
                <button
                  type="button"
                  className="button secundario pequeno"
                  onClick={() =>
                    void action(
                      () => api.prescriptions.duplicate(plan.id),
                      "Plano duplicado.",
                    )
                  }
                >
                  Duplicar
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      <div className="editor-plan">
        <div className="editor-main">
          {/* ------------------------------------------------------- plan header */}
          <div className="card plan-settings">
            <div className="plan-grid">
              <div className="field span-6">
                <label htmlFor="pl-titulo">Título do plano</label>
                <input
                  id="pl-titulo"
                  disabled={!podeEdit}
                  value={title}
                  onChange={(e) => {
                    setTitle(e.target.value);
                    touch();
                  }}
                  placeholder="Plano de emagrecimento — fase 1"
                  required
                />
              </div>

              <div className={`field ${!planId && !template ? "span-3" : "span-6"}`}>
                <label htmlFor="pl-paciente">Paciente</label>
                <select
                  id="pl-paciente"
                  value={patientId}
                  disabled={template || !podeEdit}
                  onChange={(e) => {
                    setPatientId(e.target.value);
                    touch();
                  }}
                >
                  <option value="">Selecione…</option>
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
                {template && <span className="field-hint">Modelo não pertence a um paciente.</span>}
              </div>

              {!planId && !template && (
                <div className="field span-3">
                  <label htmlFor="pl-modelo">Modelo</label>
                  <select
                    id="pl-modelo"
                    value={chosenTemplate}
                    onChange={(e) => void applyTemplate(e.target.value)}
                  >
                    <option value="">Planejamento em Branco (Sem modelo)</option>
                    {templates.map((model) => (
                      <option key={model.id} value={model.id}>
                        {model.title}
                      </option>
                    ))}
                  </select>
                  {templates.length === 0 && (
                    <span className="field-hint">
                      Você ainda não salvou modelos. Marque “Salvar como modelo” num plano.
                    </span>
                  )}
                </div>
              )}

              <div className="field span-3">
                <label htmlFor="pl-meta">Meta energética (kcal/dia)</label>
                <input
                  id="pl-meta"
                  disabled={!podeEdit}
                  inputMode="decimal"
                  value={targetEnergy}
                  onChange={(e) => {
                    setTargetEnergy(e.target.value);
                    touch();
                  }}
                  placeholder="2000"
                />
                {patientId && !template && (
                  <button
                    type="button"
                    className="button link pequeno plan-import"
                    onClick={() => void importFromEnergy()}
                  >
                    <Calculator aria-hidden="true" />
                    Importar do cálculo energético
                  </button>
                )}
              </div>

              <div className="field span-3">
                <label htmlFor="pl-peso">Peso programado (kg)</label>
                <input
                  id="pl-peso"
                  disabled={!podeEdit}
                  inputMode="decimal"
                  value={targetWeight}
                  onChange={(e) => {
                    setTargetWeight(e.target.value);
                    touch();
                  }}
                  placeholder="80"
                />
                <span className="field-hint">Base do g/kg no relatório.</span>
              </div>

              <div className="field span-3 plan-dist">
                <label>Distribuição planejada (% da energia)</label>
                <div className="distribuicao-metas">
                  <label htmlFor="pl-ptn">
                    PTN
                    <input
                      id="pl-ptn"
                      disabled={!podeEdit}
                      inputMode="decimal"
                      value={targetProtein}
                      onChange={(e) => {
                        setTargetProtein(e.target.value);
                        touch();
                      }}
                      placeholder="30"
                    />
                  </label>
                  <label htmlFor="pl-cho">
                    CHO
                    <input
                      id="pl-cho"
                      disabled={!podeEdit}
                      inputMode="decimal"
                      value={targetCarbohydrate}
                      onChange={(e) => {
                        setTargetCarbohydrate(e.target.value);
                        touch();
                      }}
                      placeholder="50"
                    />
                  </label>
                  <label htmlFor="pl-lip">
                    LIP
                    <input
                      id="pl-lip"
                      disabled={!podeEdit}
                      inputMode="decimal"
                      value={targetFat}
                      onChange={(e) => {
                        setTargetFat(e.target.value);
                        touch();
                      }}
                      placeholder="20"
                    />
                  </label>
                  <span className={`soma-metas${sumOfTargets === 100 ? " certa" : ""}`}>
                    {sumOfTargets === undefined
                      ? "sem meta"
                      : `soma ${sumOfTargets.toLocaleString("pt-BR")}%`}
                  </span>
                </div>
              </div>

              <div className="field span-3 plan-dates">
                <label>Vigência</label>
                <div className="dates-par">
                  <input
                    type="date"
                    value={validityStart}
                    onChange={(e) => {
                      setValidityStart(e.target.value);
                      touch();
                    }}
                    aria-label="Início da vigência"
                    disabled={!podeEdit}
                  />
                  <span className="dates-sep" aria-hidden="true">
                    –
                  </span>
                  <input
                    type="date"
                    value={validityEnd}
                    onChange={(e) => {
                      setValidityEnd(e.target.value);
                      touch();
                    }}
                    aria-label="Fim da vigência"
                    disabled={!podeEdit}
                  />
                </div>
              </div>
            </div>

            <label className="plan-template-toggle">
              <input
                type="checkbox"
                checked={template}
                disabled={!podeEdit}
                onChange={(e) => {
                  setTemplate(e.target.checked);
                  touch();
                }}
              />
              <span>Salvar como modelo reaproveitável (sem paciente vinculado)</span>
            </label>

            <div className="plan-texts">
              <NotesField
                label="Orientações ao paciente"
                value={handouts}
                onChange={(next) => {
                  setHandouts(next);
                  touch();
                }}
                minHeight="7rem"
                help="Aparece no plano que o paciente abre e no PDF."
                onDemand
                disabled={!podeEdit}
              />

              <NotesField
                label="Anotações internas"
                value={internalNotes}
                onChange={(next) => {
                  setInternalNotes(next);
                  touch();
                }}
                minHeight="7rem"
                help="Nunca sai no link do paciente."
                onDemand
                disabled={!podeEdit}
              />
            </div>
          </div>

          {quick && (
            <div className="mb-2">
              {quick.what === "food" ? (
                <QuickFood onCreated={useCreatedFood} onClose={() => setQuick(null)} />
              ) : (
                (() => {
                  const item = meals
                    .find((r) => r.key === quick.mealKey)
                    ?.items.find((i) => i.key === quick.itemKey);
                  if (!item?.foodId) return null;
                  return (
                    <QuickMeasure
                      foodId={item.foodId}
                      foodName={item.description}
                      onCreated={useCreatedMeasure}
                      onClose={() => setQuick(null)}
                    />
                  );
                })()
              )}
            </div>
          )}

          {/* ----------------------------------------------------------- meals */}
          {meals.length === 0 && podeEdit && (
            <div className="card meals-empty">
              <p className="discreto">Nenhuma refeição neste plano.</p>
              <button
                type="button"
                className="button secundario"
                onClick={() => MEALS_DEFAULT.forEach((name) => addMeal(name))}
              >
                Trazer as seis refeições
              </button>
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
              onDuplicate={() => duplicateMeal(meal.key)}
              onFavorite={() => void favoriteMeal(meal.key)}
              onPhoto={(file) => void sendPhoto(meal.key, file)}
              onRemovePhoto={() => void removePhoto(meal.key)}
              savedPlan={!!plan}
              onAddItem={() => addItem(meal.key)}
              onAddSeparator={() => addItem(meal.key, "SEPARATOR")}
              onChangeItem={(keyItem, change) => changeItem(meal.key, keyItem, change)}
              onRemoveItem={(keyItem) => removeItem(meal.key, keyItem)}
              onMoveItem={(keyItem, by) => moveItem(meal.key, keyItem, by)}
              onQuick={(keyItem, what) => setQuick({ mealKey: meal.key, itemKey: keyItem, what })}
              onChooseFood={(keyItem, summary) =>
                chooseFood(meal.key, keyItem, summary)
              }
            />
          ))}

          {/*
            Sem a condição de haver refeição: era ela que criava o beco sem
            saída. Quem apagava a última perdia junto o botão de criar a
            próxima, e o cardápio só voltava recarregando a página.
          */}
          {podeEdit && (
            <div className="meals-actions">
              <button type="button" className="button secundario" onClick={() => addMeal()}>
                + Adicionar refeição
              </button>
              <button
                type="button"
                className="button secundario"
                onClick={() => setShowingFavorites((v) => !v)}
              >
                <Star aria-hidden="true" />
                Refeições salvas ({favorites.length})
              </button>
            </div>
          )}

          {showingFavorites && podeEdit && (
            <div className="card mt-3">
              <div className="card-head">
                <h3 className="card-title">Refeições salvas</h3>
              </div>
              {favorites.length === 0 ? (
                <p className="empty">
                  Você ainda não salvou nenhuma. Use “Favoritar” numa refeição montada.
                </p>
              ) : (
                <div className="stack tight">
                  {favorites.map((favorite) => (
                    <div className="favorita" key={favorite.id}>
                      <div className="favorita-text">
                        <strong>{favorite.name}</strong>
                        <span className="minusculo">
                          {favorite.mealName} · {count(favorite.itemsTotal, "item", "itens")}
                          {favorite.energyKcal !== undefined &&
                            ` · ${Math.round(favorite.energyKcal)} kcal`}
                        </span>
                      </div>
                      <div className="favorita-actions">
                        <button
                          type="button"
                          className="button secundario pequeno"
                          onClick={() => void insertFavorite(favorite.id)}
                        >
                          Inserir
                        </button>
                        <button
                          type="button"
                          className="button perigo pequeno"
                          onClick={async () => {
                            if (!confirm(`Remover "${favorite.name}" das salvas?`)) return;
                            await api.mealFavorites.remove(favorite.id);
                            setFavorites(await api.mealFavorites.list());
                          }}
                        >
                          Remover
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {/* ------------------------------------------------------- totals rail */}
        {!compact && <aside className="panel-totals">{rail}</aside>}
      </div>

      {compact && (
        <>
          <div className="editor-bottom-bar">
            <button
              type="button"
              className="editor-readout"
              onClick={() => setSheetOpen(true)}
              aria-haspopup="dialog"
              aria-expanded={sheetOpen}
            >
              <MacroRing
                size={24}
                stroke={4}
                protein={distributionDay?.proteinPct ?? 0}
                carbohydrate={distributionDay?.carbohydratePct ?? 0}
                fat={distributionDay?.lipidPct ?? 0}
              />
              <span className="editor-readout-text">
                <span className="readout strong">
                  {kcalDay === undefined ? "— kcal" : `${Math.round(kcalDay)} kcal`}
                  {totalSaved?.adequacyEnergyPct !== undefined
                    ? ` · ${Math.round(totalSaved.adequacyEnergyPct)}% da meta`
                    : ""}
                </span>
                <span className="editor-readout-label">Totais e entrega</span>
              </span>
              <ChevronUp aria-hidden="true" />
            </button>
          </div>
          <Sheet
            open={sheetOpen}
            onClose={() => setSheetOpen(false)}
            title="Totais e entrega"
            side="bottom"
            className="editor-sheet"
          >
            <div className="panel-totals">{rail}</div>
          </Sheet>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- subcomponents

/**
 * Uma refeição do cardápio.
 *
 * O cabeçalho carrega o que o cliente lista na página 32: horário, nome
 * editável que aceita frase, os somatórios em PTN/LIP/CHO/Kcal, e os botões de
 * resumir, calcular, duplicar e excluir.
 */
function BlockMeal({
  meal,
  method,
  total,
  onlyRead,
  onChange,
  onRemove,
  onDuplicate,
  onFavorite,
  onPhoto,
  onRemovePhoto,
  savedPlan,
  onAddItem,
  onAddSeparator,
  onChangeItem,
  onRemoveItem,
  onMoveItem,
  onChooseFood,
  onQuick,
}: {
  meal: MealEdit;
  method: PrescriptionMethod;
  total?: Total;
  onlyRead: boolean;
  onChange: (change: Partial<MealEdit>) => void;
  onRemove: () => void;
  onDuplicate: () => void;
  onFavorite: () => void;
  onPhoto: (file: File) => void;
  onRemovePhoto: () => void;
  savedPlan: boolean;
  onAddItem: () => void;
  onAddSeparator: () => void;
  onChangeItem: (keyItem: string, change: ChangeItem) => void;
  onRemoveItem: (keyItem: string) => void;
  onMoveItem: (keyItem: string, by: number) => void;
  onChooseFood: (keyItem: string, summary: FoodSummary) => void;
  onQuick: (keyItem: string, what: "food" | "measure") => void;
}) {
  const quantifies = method !== "QUALITATIVE";
  const notesEmpty = isEmptyDoc(parseRichDoc(meal.notes) ?? emptyDoc());
  const toggleLabel = meal.collapsed ? "Expandir refeição" : "Resumir refeição";

  return (
    <div
      className={`meal${meal.inCalculation ? "" : " fora-da-conta"}${
        meal.collapsed ? " collapsed" : ""
      }`}
    >
      <div className="meal-top">
        <button
          type="button"
          className="button icon ghost meal-toggle"
          aria-expanded={!meal.collapsed}
          aria-label={toggleLabel}
          title={toggleLabel}
          onClick={() => onChange({ collapsed: !meal.collapsed })}
        >
          {meal.collapsed ? (
            <ChevronRight aria-hidden="true" />
          ) : (
            <ChevronDown aria-hidden="true" />
          )}
        </button>
        {/* The hour chip of the day ruler, with the time input inside it. */}
        <span className="meal-hora-chip">
          <Clock aria-hidden="true" />
          <input
            type="time"
            value={meal.time}
            onChange={(e) => onChange({ time: e.target.value })}
            disabled={onlyRead}
            aria-label="Horário"
            className="meal-hora"
          />
        </span>
        <input
          type="text"
          className="meal-name"
          value={meal.name}
          onChange={(e) => onChange({ name: e.target.value })}
          disabled={onlyRead}
          aria-label="Nome da refeição"
          maxLength={250}
        />
        <MacroTags composition={total?.composition} />
        {!onlyRead && (
          <div className="meal-acoes">
            <label className="switch meal-calc" title="Desligue para prescrever uma opção substituta">
              <input
                type="checkbox"
                checked={meal.inCalculation}
                onChange={(e) => onChange({ inCalculation: e.target.checked })}
              />
              <span>Calcular</span>
            </label>
            <button
              type="button"
              className="button icon ghost"
              onClick={onFavorite}
              aria-label="Favoritar"
              title="Favoritar: salvar esta refeição para usar em outros cardápios"
            >
              <Star aria-hidden="true" />
            </button>
            <MealPhotoButton
              meal={meal}
              savedPlan={savedPlan}
              onPhoto={onPhoto}
              onRemovePhoto={onRemovePhoto}
            />
            <button
              type="button"
              className="button icon ghost"
              onClick={onDuplicate}
              aria-label="Duplicar"
              title="Duplicar"
            >
              <Copy aria-hidden="true" />
            </button>
            <button
              type="button"
              className="button icon ghost perigo"
              onClick={onRemove}
              aria-label="Remover"
              title="Remover"
            >
              <Trash2 aria-hidden="true" />
            </button>
          </div>
        )}
      </div>

      {!meal.inCalculation && (
        <p className="aviso-substituta">
          <TriangleAlert aria-hidden="true" />
          Fora da contabilização do dia. Os totais desta refeição continuam sendo calculados.
        </p>
      )}

      {!meal.collapsed && (
        <>
          {meal.items.length === 0 ? (
            <p className="empty meal-empty">Busque um alimento abaixo para montar esta refeição.</p>
          ) : (
            meal.items.map((item, index) =>
              item.kind === "SEPARATOR" ? (
                <div className="separador-item" key={item.key}>
                  <span />
                  {!onlyRead && (
                    <div className="item-acoes">
                      <button
                        type="button"
                        className="button icon ghost pequeno"
                        aria-label="Subir separador"
                        title="Subir separador"
                        disabled={index === 0}
                        onClick={() => onMoveItem(item.key, -1)}
                      >
                        <ArrowUp aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="button icon ghost pequeno"
                        aria-label="Descer separador"
                        title="Descer separador"
                        disabled={index === meal.items.length - 1}
                        onClick={() => onMoveItem(item.key, 1)}
                      >
                        <ArrowDown aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="button icon ghost pequeno perigo"
                        aria-label="Remover"
                        title="Remover separador"
                        onClick={() => onRemoveItem(item.key)}
                      >
                        <X aria-hidden="true" />
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <RowItem
                  key={item.key}
                  item={item}
                  quantifies={quantifies}
                  admitsSubstitutions={quantifies}
                  onlyRead={onlyRead}
                  first={index === 0}
                  last={index === meal.items.length - 1}
                  onChange={(change) => onChangeItem(item.key, change)}
                  onRemove={() => onRemoveItem(item.key)}
                  onMove={(by) => onMoveItem(item.key, by)}
                  onChooseFood={(summary) => onChooseFood(item.key, summary)}
                  onNewFood={() => onQuick(item.key, "food")}
                  onNewMeasure={() => onQuick(item.key, "measure")}
                />
              ),
            )
          )}

          <MealNotes meal={meal} onlyRead={onlyRead} onChange={onChange} />

          {!onlyRead && (
            <div className="meal-foot">
              <button type="button" className="button ghost meal-add" onClick={onAddItem}>
                + Adicionar item
              </button>
              <button
                type="button"
                className="button ghost pequeno"
                onClick={onAddSeparator}
                title="Separa o que deve ser comido junto"
              >
                + Separador
              </button>
              {notesEmpty && !meal.editingNotes && (
                <button
                  type="button"
                  className="button link pequeno meal-notes-open"
                  onClick={() => onChange({ editingNotes: true })}
                >
                  <PenLine aria-hidden="true" />
                  Escrever observações
                </button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}

/**
 * A observação da refeição.
 *
 * "Abaixo dos alimentos estará um bloco de texto que eu poderei colocar minhas
 * observações." É o mesmo editor do resto do sistema.
 *
 * O editor só é montado quando ele abre a observação. Um plano em branco tem
 * seis refeições, e seis instâncias do ProseMirror vivas ao mesmo tempo
 * custariam mais do que o cardápio inteiro — a leitura usa o renderizador
 * leve, que é React puro.
 */
function MealNotes({
  meal,
  onlyRead,
  onChange,
}: {
  meal: MealEdit;
  onlyRead: boolean;
  onChange: (change: Partial<MealEdit>) => void;
}) {
  const doc = useMemo(() => parseRichDoc(meal.notes) ?? emptyDoc(), [meal.notes]);
  const empty = isEmptyDoc(doc);

  // Nothing written and nobody writing: the footer of the meal offers
  // "Escrever observações", and this block stays out of the way.
  if (empty && !(meal.editingNotes && !onlyRead)) return null;

  return (
    <div className="observacao-refeicao">
      {meal.editingNotes && !onlyRead ? (
        <>
          <RichTextEditor
            value={doc}
            label={`Observações de ${meal.name}`}
            minHeight="7rem"
            onChange={(next) =>
              onChange({ notes: isEmptyDoc(next) ? "" : JSON.stringify(next) })
            }
          />
          <button
            type="button"
            className="button link pequeno"
            onClick={() => onChange({ editingNotes: false })}
          >
            Fechar observações
          </button>
        </>
      ) : (
        <div className="observacao-view">
          <div className="observacao-text">
            <RichTextView doc={doc} />
          </div>
          {!onlyRead && (
            <button
              type="button"
              className="button link pequeno"
              onClick={() => onChange({ editingNotes: true })}
            >
              <PenLine aria-hidden="true" />
              Editar
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * O anexo da foto do prato.
 *
 * "Adicionar uma foto, eu poderia subir uma/umas foto de como eu quero que
 * fique e aparecerá lá no PDF." Depende de a refeição já existir no servidor,
 * e o botão diz isso em vez de deixar o envio falhar.
 */
function MealPhotoButton({
  meal,
  savedPlan,
  onPhoto,
  onRemovePhoto,
}: {
  meal: MealEdit;
  savedPlan: boolean;
  onPhoto: (file: File) => void;
  onRemovePhoto: () => void;
}) {
  const inputId = `foto-${meal.key}`;
  const available = savedPlan && meal.id !== undefined;

  if (meal.photoName) {
    return (
      <button
        type="button"
        className="button icon ghost"
        onClick={onRemovePhoto}
        aria-label="Tirar foto"
        title={`Tirar foto: ${meal.photoName}`}
      >
        <CameraOff aria-hidden="true" />
      </button>
    );
  }

  return (
    <>
      <label
        className={`button icon ghost${available ? "" : " desabilitado"}`}
        htmlFor={inputId}
        aria-label="Foto"
        title={
          available
            ? "Foto: sai no PDF do cardápio"
            : "Foto: salve o plano para poder anexar a foto"
        }
      >
        <Camera aria-hidden="true" />
      </label>
      <input
        id={inputId}
        type="file"
        accept="image/*"
        className="visually-hidden"
        disabled={!available}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onPhoto(file);
          e.target.value = "";
        }}
      />
    </>
  );
}

/**
 * Os somatórios da refeição, abreviados como ele pede.
 *
 * "é bom para esses labels colocar uma sigla abreviando sobre o que é (PTN,
 * LIP, CHO, Kcal)".
 */
function MacroTags({ composition }: { composition?: Composition }) {
  if (!composition) return null;
  const grams = (value?: number) => (value === undefined ? "\u2014" : Math.round(value));
  return (
    <div className="macro-tags">
      <span className="readout macro-readout" title="Proteínas · Lipídeos · Carboidratos">
        PTN {grams(composition.proteinG)} · LIP {grams(composition.fatG)} · CHO{" "}
        {grams(composition.carbohydrateG)}
      </span>
      <span className="readout strong macro-kcal" title="Energia">
        {composition.energyKcal === undefined ? "\u2014" : Math.round(composition.energyKcal)} kcal
      </span>
    </div>
  );
}

function RowItem({
  item,
  quantifies,
  admitsSubstitutions,
  onlyRead,
  first,
  last,
  onChange,
  onRemove,
  onMove,
  onChooseFood,
  onNewFood,
  onNewMeasure,
}: {
  item: ItemEdit;
  quantifies: boolean;
  admitsSubstitutions: boolean;
  onlyRead: boolean;
  first: boolean;
  last: boolean;
  onChange: (change: ChangeItem) => void;
  onRemove: () => void;
  onMove: (by: number) => void;
  onChooseFood: (summary: FoodSummary) => void;
  onNewFood: () => void;
  onNewMeasure: () => void;
}) {
  const measureEscolhida = item.measures.find((m) => m.id === item.measureId);
  const quantity = Number(item.quantity.replace(",", "."));
  const grams =
    measureEscolhida && Number.isFinite(quantity)
      ? measureEscolhida.grams * quantity
      : Number.isFinite(quantity)
        ? quantity
        : undefined;
  const showSubstitutions = admitsSubstitutions && !!item.foodId;

  return (
    <div className="item-row">
      <span className="item-handle" aria-hidden="true">
        <GripVertical />
      </span>

      <div className="description-item">
        {item.foodId ? (
          <strong>{item.description}</strong>
        ) : (
          <>
            <FoodSearch
              freeValue={item.description}
              onFreeType={(text) => onChange({ description: text })}
              onChoose={onChooseFood}
              disabled={onlyRead}
            />
            {!onlyRead && (
              <button
                type="button"
                className="button link pequeno"
                onClick={onNewFood}
                title="Cadastrar sem sair do cardápio"
              >
                Não achei: cadastrar alimento
              </button>
            )}
          </>
        )}
      </div>

      {quantifies ? (
        <>
          <input
            className="item-qty"
            inputMode="decimal"
            value={item.quantity}
            onChange={(e) => onChange({ quantity: e.target.value })}
            placeholder="qtd"
            disabled={onlyRead}
            aria-label="Quantidade"
          />
          <select
            className="item-unit"
            value={item.measureId ?? ""}
            onChange={(e) =>
              onChange({ measureId: e.target.value ? Number(e.target.value) : undefined })
            }
            disabled={onlyRead || item.measures.length === 0}
            aria-label="Medida"
          >
            <option value="">gramas</option>
            {item.measures.map((m) => (
              <option key={m.id} value={m.id}>
                {m.description}
              </option>
            ))}
          </select>
          {!onlyRead && item.foodId ? (
            <button
              type="button"
              className="button icon ghost pequeno item-measure"
              onClick={onNewMeasure}
              aria-label="+ medida"
              title="+ medida: cadastrar uma medida caseira para este alimento"
            >
              <Plus aria-hidden="true" />
            </button>
          ) : (
            <span className="item-measure" />
          )}
          <span className="readout item-grams">
            {grams !== undefined && grams > 0 ? `${round(grams)} g` : "—"}
          </span>
        </>
      ) : (
        <span className="discreto item-free">à vontade / sem quantificar</span>
      )}

      {!onlyRead ? (
        <div className="item-acoes">
          <button
            type="button"
            className="button icon ghost pequeno"
            aria-label="Subir item"
            title="Subir item"
            disabled={first}
            onClick={() => onMove(-1)}
          >
            <ArrowUp aria-hidden="true" />
          </button>
          <button
            type="button"
            className="button icon ghost pequeno"
            aria-label="Descer item"
            title="Descer item"
            disabled={last}
            onClick={() => onMove(1)}
          >
            <ArrowDown aria-hidden="true" />
          </button>
          <button
            type="button"
            className="button icon ghost pequeno perigo"
            onClick={onRemove}
            aria-label="Remover item"
            title="Remover item"
          >
            <X aria-hidden="true" />
          </button>
        </div>
      ) : (
        <span className="item-acoes" />
      )}

      <div className="item-extra">
        {!onlyRead && item.foodId && (
          <button
            type="button"
            className="button link pequeno"
            onClick={() => onChange({ foodId: undefined, measureId: undefined, measures: [] })}
          >
            trocar alimento
          </button>
        )}
        <ItemNotes item={item} onlyRead={onlyRead} onChange={onChange} />
        {showSubstitutions && (
          <Substitutions item={item} onlyRead={onlyRead} onChange={onChange} />
        )}
      </div>
    </div>
  );
}

/**
 * A observação de um alimento — "depois do ovo, não antes".
 *
 * O campo existia no servidor e ia parar no PDF, mas a tela nunca deu como
 * escrevê-lo: era um dado que só a API alcançava. Agora tem o mesmo editor do
 * resto do sistema, com negrito, lista e tabela.
 *
 * O editor só é montado quando alguém abre a observação. Uma refeição com dez
 * itens não deve pagar por dez instâncias do ProseMirror vivas ao mesmo tempo;
 * a leitura usa o renderizador leve, que é React puro.
 */
function ItemNotes({
  item,
  onlyRead,
  onChange,
}: {
  item: ItemEdit;
  onlyRead: boolean;
  onChange: (change: ChangeItem) => void;
}) {
  if (onlyRead) {
    return item.notes.trim() ? (
      <div className="minusculo">
        <NotesView value={item.notes} />
      </div>
    ) : null;
  }

  return (
    <NotesField
      label="Observação deste alimento"
      value={item.notes}
      onChange={(next) => onChange({ notes: next })}
      minHeight="6rem"
      onDemand
    />
  );
}

function Substitutions({
  item,
  onlyRead,
  onChange,
}: {
  item: ItemEdit;
  onlyRead: boolean;
  onChange: (change: ChangeItem) => void;
}) {
  /**
   * Altera uma substituição sem tocar nas outras.
   *
   * Lê a lista do item corrente, e não da cópia que este render recebeu: a
   * segunda chamada de `choose` acontece depois da busca das medidas, e montar
   * a lista sobre a cópia antiga apagava o alimento que a primeira acabara de
   * gravar.
   */
  function patch(key: string, change: Partial<SubstitutionEdit>) {
    onChange((current) => ({
      substitutions: current.substitutions.map((x) =>
        x.key === key ? { ...x, ...change } : x,
      ),
    }));
  }

  /**
   * Escolhe o alimento do substituto.
   *
   * É o mesmo caminho do item — mesma busca, mesmas medidas caseiras, mesma
   * porção padrão. Sem alimento ligado o substituto não tinha macro nenhum, e
   * a troca que o paciente de fato faz sumia da conta.
   */
  async function choose(key: string, summary: FoodSummary) {
    patch(key, { foodId: summary.id, description: summary.description });
    try {
      const detail: FoodDetail = await api.foods.detail(summary.id);
      const standard = detail.measures.find((m) => m.standard) ?? detail.measures[0];
      patch(key, {
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
      patch(key, { measures: [], quantity: "100" });
    }
  }

  if (onlyRead && item.substitutions.length === 0) return null;

  return (
    <div className="subs">
      {item.substitutions.map((substitution) => (
        <div className="sub-row" key={substitution.key}>
          <span className="sub-mark" aria-hidden="true">
            <ArrowLeftRight />
            ou
          </span>
          <div className="sub-food">
            {substitution.foodId ? (
              <>
                <strong>{substitution.description}</strong>
                {!onlyRead && (
                  <button
                    type="button"
                    className="button link pequeno"
                    onClick={() =>
                      patch(substitution.key, {
                        foodId: undefined,
                        measureId: undefined,
                        measures: [],
                      })
                    }
                  >
                    trocar alimento
                  </button>
                )}
              </>
            ) : (
              <FoodSearch
                label="Alimento da substituição"
                freeValue={substitution.description}
                onFreeType={(text) => patch(substitution.key, { description: text })}
                onChoose={(summary) => void choose(substitution.key, summary)}
                disabled={onlyRead}
              />
            )}
          </div>
          <input
            className="sub-qty"
            inputMode="decimal"
            value={substitution.quantity}
            placeholder="qtd"
            disabled={onlyRead}
            aria-label="Quantidade da substituição"
            onChange={(e) => patch(substitution.key, { quantity: e.target.value })}
          />
          <select
            className="sub-unit"
            value={substitution.measureId ?? ""}
            disabled={onlyRead || substitution.measures.length === 0}
            aria-label="Medida da substituição"
            onChange={(e) =>
              patch(substitution.key, {
                measureId: e.target.value ? Number(e.target.value) : undefined,
              })
            }
          >
            <option value="">gramas</option>
            {substitution.measures.map((m) => (
              <option key={m.id} value={m.id}>
                {m.description}
              </option>
            ))}
          </select>
          {!onlyRead ? (
            <button
              type="button"
              className="button icon ghost pequeno perigo sub-remove"
              aria-label="Remover substituição"
              title="Remover substituição"
              onClick={() =>
                onChange((current) => ({
                  substitutions: current.substitutions.filter(
                    (x) => x.key !== substitution.key,
                  ),
                }))
              }
            >
              <X aria-hidden="true" />
            </button>
          ) : (
            <span className="sub-remove" />
          )}
        </div>
      ))}
      {!onlyRead && (
        <button
          type="button"
          className="button link pequeno"
          onClick={() =>
            onChange((current) => ({
              substitutions: [
                ...current.substitutions,
                { key: novaKey(), description: "", quantity: "", measures: [] },
              ],
            }))
          }
        >
          + substituição
        </button>
      )}
    </div>
  );
}

/**
 * Prescrito × teórico × diferença, com a cor da faixa.
 *
 * É a aba que o cliente descreve na página 32: "tenho uma coluna do que é
 * prescrito, do teórico... e a diferença, que é o que eu analiso para ver se é
 * pra retirar, se é pra adicionar. E um ponto essencial, se ficar entre 95 a
 * 105% do teórico eu estaria dentro do planejado, quero que a cor do label
 * mude".
 *
 * A faixa vem classificada do servidor, e não é recalculada aqui: o corte de
 * 95 a 105% é regra de negócio, e mantê-la em dois lugares é como as duas
 * pontas passam a discordar sem ninguém notar.
 */
function PanelDistribution({
  comparison,
  weightKg,
}: {
  comparison: MacroComparison[];
  weightKg?: number;
}) {
  if (comparison.length === 0) return null;

  const gram = (value?: number) =>
    value === undefined || value === null ? "\u2014" : `${Math.round(value)} g`;

  return (
    <div className="card">
      <div className="card-head">
        <div>
          <h3 className="card-title">Distribuição</h3>
          <p className="card-sub">Prescrito contra o planejado. Dentro de 95 a 105% está no alvo.</p>
        </div>
      </div>

      {/* Numeric-table rule B: on a narrow rail the four columns do not fit,
          so the table scrolls sideways with the macro column held. */}
      <div className="table-scroll">
        <table className="tabela-distribuicao">
          <thead>
            <tr>
              <th>Macro</th>
              <th className="num">Prescrito</th>
              <th className="num">Teórico</th>
              <th className="num">Diferença</th>
            </tr>
          </thead>
          <tbody>
            {comparison.map((row) => (
              <tr key={row.macro}>
                <td>
                  {row.description}
                  {weightKg !== undefined && row.prescribedPerKg !== undefined && (
                    <span className="readout dist-perkg">
                      {row.prescribedPerKg.toLocaleString("pt-BR")} g/kg
                    </span>
                  )}
                </td>
                <td className="num">{gram(row.prescribedG)}</td>
                <td className="num discreto">{gram(row.theoreticalG)}</td>
                <td className="num">
                  <span className={`faixa ${bandClass(row.band)}`}>
                    {row.differenceG === undefined || row.differenceG === null
                      ? "\u2014"
                      : `${row.differenceG > 0 ? "+" : ""}${Math.round(row.differenceG)} g`}
                    {row.adequacyPct !== undefined && (
                      <span className="faixa-pct"> {Math.round(row.adequacyPct)}%</span>
                    )}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {weightKg !== undefined && (
        <p className="minusculo dist-note">
          g/kg sobre o peso programado de {weightKg.toLocaleString("pt-BR")} kg.
        </p>
      )}
    </div>
  );
}

/** A classe de cor da faixa. Sem faixa não pinta: não há conclusão a mostrar. */
function bandClass(band?: AdequacyBand): string {
  if (band === "WITHIN") return "dentro";
  if (band === "ABOVE") return "acima";
  if (band === "BELOW") return "abaixo";
  return "";
}

/**
 * O estado da gravação, no lugar do antigo "alterações não salvas".
 *
 * Com o salvamento automático, o aviso deixa de ser um alerta e passa a ser
 * uma confirmação: quem edita precisa saber que o trabalho está guardado, não
 * ser cobrado por não ter clicado em nada.
 */
function SavingState({
  dirty,
  saving,
  savedAt,
  saveable,
  closed,
}: {
  dirty: boolean;
  saving: boolean;
  savedAt: Date | null;
  saveable: boolean;
  closed: boolean;
}) {
  // O plano encerrado vem primeiro: é a razão mais forte para nada estar sendo
  // gravado, e dizer "falta título" no lugar dela manda procurar o problema
  // onde ele não está.
  if (closed) {
    return (
      <span className="tag saving-state">
        <Lock aria-hidden="true" />
        plano encerrado
      </span>
    );
  }
  if (saving) {
    return (
      <span className="tag saving-state">
        <LoaderCircle aria-hidden="true" className="spin" />
        salvando…
      </span>
    );
  }
  if (dirty && !saveable) {
    return (
      <span className="tag ambar saving-state">
        <CircleAlert aria-hidden="true" />
        falta título ou paciente para salvar
      </span>
    );
  }
  if (dirty) {
    return (
      <span className="tag saving-state">
        <CircleDot aria-hidden="true" />
        alterações pendentes
      </span>
    );
  }
  if (savedAt) {
    return (
      <span className="tag verde saving-state">
        <Check aria-hidden="true" />
        salvo às{" "}
        {savedAt.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
      </span>
    );
  }
  return null;
}

function PanelTotals({ total, dirty, target }: { total?: Total; dirty: boolean; target: string }) {
  if (!total) {
    return (
      <div className="card totals-card">
        <div className="card-head">
          <h3 className="card-title">Totais do dia</h3>
        </div>
        {/*
          O total agora acompanha a edição sozinho. Quando ele não está aqui é
          porque falta o que identifica o plano — e é isso que a frase precisa
          dizer, em vez de mandar salvar.
        */}
        <p className="discreto totals-empty">
          Dê um título ao plano e escolha o paciente para os totais começarem a
          aparecer.
        </p>
      </div>
    );
  }

  const distribution = total.distribution;
  const targetNumber = target ? Number(target.replace(",", ".")) : undefined;
  const kcal = total.composition.energyKcal;
  const delta =
    targetNumber !== undefined && Number.isFinite(targetNumber) && kcal !== undefined
      ? kcal - targetNumber
      : undefined;

  return (
    <div className="card totals-card">
      <div className="card-head">
        <h3 className="card-title">Totais do dia</h3>
      </div>

      {dirty && (
        <div className="warning attention totals-dirty">
          Há alterações não salvas. Os números abaixo referem-se à última versão salva.
        </div>
      )}

      <div className="totals-hero">
        <MacroRing
          size={96}
          stroke={9}
          protein={distribution?.proteinPct ?? 0}
          carbohydrate={distribution?.carbohydratePct ?? 0}
          fat={distribution?.lipidPct ?? 0}
          label={
            distribution
              ? `Distribuição energética: ${distribution.proteinPct}% proteína, ${distribution.carbohydratePct}% carboidrato, ${distribution.lipidPct}% gordura`
              : undefined
          }
        >
          <span className="totals-kcal">{kcal === undefined ? "\u2014" : Math.round(kcal)}</span>
          <span className="totals-unit" aria-hidden="true" />
        </MacroRing>
        <div className="totals-lines">
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
      </div>

      {distribution && (
        <div className="legenda-macro">
          <span>
            <i className="sw sw-prot" />
            Prot. {distribution.proteinPct}%
          </span>
          <span>
            <i className="sw sw-carb" />
            Carb. {distribution.carbohydratePct}%
          </span>
          <span>
            <i className="sw sw-fat" />
            Gord. {distribution.lipidPct}%
          </span>
        </div>
      )}

      {/* The reading against the goal: what the nutritionist adjusts against. */}
      {delta !== undefined && targetNumber !== undefined && (
        <p className="totals-meta readout">
          <span>meta {targetNumber.toLocaleString("pt-BR")}</span>
          <span className={`totals-delta ${bandClass(total.energyBand)}`}>
            {delta > 0 ? "+" : ""}
            {Math.round(delta)}
          </span>
        </p>
      )}

      {total.adequacyEnergyPct !== undefined && targetNumber ? (
        <p className="discreto totals-adequacy">
          <strong>{total.adequacyEnergyPct}%</strong> da meta de {targetNumber} kcal.
        </p>
      ) : null}

      {/*
        The caveat matters: when some of the items have no determined value for
        the nutrient in the source, the total is a floor, and showing it as an
        exact number would mislead the professional.
      */}
      {total.nutrientsIncomplete.length > 0 && (
        <div className="warning attention totals-partial">
          <strong>Total parcial.</strong> Nem todos os itens têm dado para{" "}
          {total.nutrientsIncomplete.slice(0, 4).map(labelDe).join(", ")}
          {total.nutrientsIncomplete.length > 4
            ? ` e mais ${total.nutrientsIncomplete.length - 4}`
            : ""}
          . Os valores são um piso, não um total exato.
        </div>
      )}

      {total.itemsOutsideCalculation > 0 && (
        <p className="minusculo totals-outside">
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
function PanelHandouts({
  planId,
  onlyRead,
}: {
  planId: number;
  /** Plano encerrado: as orientações já anexadas continuam à vista, e só. */
  onlyRead: boolean;
}) {
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
      <div className="card-head">
        <h3 className="card-title">Orientações</h3>
      </div>

      {error && <div className="warning error mb-2">{error}</div>}

      {attached.length === 0 ? (
        <p className="minusculo handout-none">
          Nenhuma anexada. O paciente recebe estes textos junto do plano, no link e no PDF.
        </p>
      ) : (
        attached.map((a) => (
          <div className="handout-row" key={a.id}>
            <span>{a.title}</span>
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

      <div className={`handout-attach${onlyRead ? " hidden" : ""}`}>
        <select
          value={escolhida}
          disabled={onlyRead}
          onChange={(e) => setEscolhida(e.target.value)}
          aria-label="Orientação da biblioteca"
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
          className="button secundario"
          onClick={attach}
          disabled={!escolhida}
        >
          Anexar
        </button>
      </div>
    </div>
  );
}

function PanelPublication({
  plan,
  onRun,
  onFlush,
}: {
  plan: PlanResponse;
  onRun: (run: () => Promise<PlanResponse>, message: string) => Promise<void>;
  onFlush: () => Promise<void>;
}) {
  const [copied, setCopied] = useState(false);
  const [generatingPdf, setGeneratingPdf] = useState(false);
  const warnings = useFeedback();
  // O endereço tem de casar com a rota declarada em App.tsx. A passagem que
  // traduziu as rotas para o inglês renomeou a rota e deixou este texto para
  // trás, e o link que o paciente recebia caía em "Página não encontrada".
  const address = `${window.location.origin}/plan/${plan.publicIdentifier}`;

  async function openPdf() {
    setGeneratingPdf(true);
    try {
      // "Quando gerar um pdf de plano não deve ser necessário salvar antes."
      // O que ele não quer é ter que lembrar de clicar em Salvar — e uma folha
      // que não confere com a tela é pior do que um segundo de espera. Então o
      // botão grava o que está pendente e só depois imprime.
      await onFlush();
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
      <div className="card-head">
        <h3 className="card-title">Entrega ao paciente</h3>
      </div>

      {/* The printout comes before the link: the patient has no account in the
          system, and leaves the appointment with the sheet in hand. */}
      <button
        type="button"
        className="button secundario w-full"
        onClick={openPdf}
        disabled={generatingPdf}
      >
        <FileText aria-hidden="true" />
        {generatingPdf ? "Gerando…" : "Plano em PDF"}
      </button>

      {plan.status === "DRAFT" ? (
        <>
          <p className="discreto publication-note">
            O plano ainda é um rascunho: quem abrir o link não encontra nada, e o PDF sai marcado
            como rascunho.
          </p>
          <button
            type="button"
            className="button w-full"
            onClick={() => onRun(() => api.prescriptions.publish(plan.id), "Plano publicado.")}
          >
            <Send aria-hidden="true" />
            Publicar plano
          </button>
        </>
      ) : (
        <>
          <code className="readout link-address">{address}</code>
          <div className="publication-actions">
            <button type="button" className="button secundario pequeno" onClick={copy}>
              <Copy aria-hidden="true" />
              {copied ? "Copiado!" : "Copiar link"}
            </button>
            <a
              className="button secundario pequeno"
              href={address}
              target="_blank"
              rel="noreferrer"
            >
              <ExternalLink aria-hidden="true" />
              Abrir
            </a>
          </div>

          <div className="publication-actions">
            {plan.status === "ACTIVE" && (
              <button
                type="button"
                className="button secundario pequeno"
                onClick={() =>
                  onRun(() => api.prescriptions.close(plan.id), "Plano encerrado.")
                }
              >
                Encerrar
              </button>
            )}
            <button
              type="button"
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
            type="button"
            className="button perigo pequeno w-full publication-regenerate"
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

/** Texto do formulário para número, aceitando vírgula. Vazio vira undefined. */
function numberOrUndefined(text: string): number | undefined {
  const clean = text.trim().replace(",", ".");
  if (!clean) return undefined;
  const value = Number(clean);
  return Number.isFinite(value) ? value : undefined;
}

function round(value: number) {
  return (Math.round(value * 100) / 100).toString().replace(".", ",");
}
