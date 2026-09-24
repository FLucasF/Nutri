/** API contracts, mirroring the backend DTOs. */

export type Role = "NUTRITIONIST" | "ASSISTANT" | "PATIENT" | "ADMIN";
export type Plan = "EXPERIMENTAL" | "UNDERGRADUATE" | "PREMIUM" | "BLACK";
export type Sex = "FEMALE" | "MALE";

export type DataSource =
  | "TACO"
  | "TBCA"
  | "IBGE"
  | "OPEN_FOOD_FACTS"
  | "MANUFACTURER"
  | "CUSTOM"
  | "RECIPE";

export type PrescriptionMethod = "FOODS" | "SUBSTITUTIONS" | "QUALITATIVE";
export type PlanStatus = "DRAFT" | "ACTIVE" | "CLOSED";

export interface UserSummary {
  id: number;
  name: string;
  email: string;
  role: Role;
  accountId: number;
  plan: Plan;
}

export interface TokenResponse {
  token: string;
  type: string;
  expiresAtSeconds: number;
  user: UserSummary;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PatientTag {
  id: number;
  name: string;
  /** TAG do sistema: ponto de partida, comum a todos. */
  systemTag: boolean;
  own: boolean;
}

export interface PatientNote {
  id: number;
  body: string;
  createdAt: string;
  createdBy?: string;
}

/** Um texto-base para começar anotações, com a formatação do editor. */
export interface NoteTemplate {
  id: number;
  name: string;
  body: string;
  updatedAt: string;
}

/** Só preenchida para mulher; a ausência diz "nenhuma das duas". */
export type BiologicalCondition = "PREGNANT" | "LACTATING";

export interface PatientSummary {
  id: number;
  name: string;
  email?: string;
  age?: number;
  active: boolean;
  /** As TAGs vêm na listagem porque é nela que elas servem para varrer. */
  tags: string[];
}

export interface Patient {
  id: number;
  name: string;
  nickname?: string;
  biologicalCondition?: BiologicalCondition;
  email?: string;
  phone?: string;
  dateBirth?: string;
  age?: number;
  sex?: Sex;
  cpf?: string;
  occupation?: string;
  goal?: string;
  notes?: string;
  /** Pela última altura medida; ausente sem avaliação, sem altura ou para criança. */
  healthyWeight?: HealthyWeight;
  lastWeightKg?: number;
  lastAssessmentDate?: string;
  /** Quem indicou o paciente, quando foi indicação. */
  partnerId?: number;
  partnerName?: string;
  active: boolean;
  hasAccessToApp: boolean;
  createdAt: string;
}

/**
 * Nutritional composition. An absent key means a nutrient not determined in the
 * source — it should appear as "não informado", never as zero.
 */
export type Composition = Record<string, number | undefined>;

export interface FoodSummary {
  id: number;
  description: string;
  group?: string;
  source: DataSource;
  brand?: string;
  energyKcal?: number;
  proteinG?: number;
  carbohydrateG?: number;
  fatG?: number;
  publicBase: boolean;
}

export interface Measure {
  id: number;
  description: string;
  grams: number;
  standard: boolean;
  forCatalogBase: boolean;
  editable: boolean;
}

export interface FoodDetail {
  id: number;
  description: string;
  group?: string;
  source: DataSource;
  sourceDescription: string;
  codeSource?: string;
  /** EAN of the processed product. Absent in the reference tables. */
  codeBarcode?: string;
  brand?: string;
  publicBase: boolean;
  editable: boolean;
  composition: Composition;
  measures: Measure[];
}

export interface CalculatedServing {
  foodId: number;
  description: string;
  grams: number;
  measureUsed: string;
  composition: Composition;
}

export interface ResultImport {
  imported: number;
  ignored: number;
  warnings: string[];
}

export interface Distribution {
  proteinPct: number;
  carbohydratePct: number;
  lipidPct: number;
  calculatedEnergyKcal: number;
}

export interface Total {
  composition: Composition;
  itemsInCalculation: number;
  itemsOutsideCalculation: number;
  nutrientsIncomplete: string[];
  nutrientsWithoutDatum: string[];
  reliable: boolean;
  distribution?: Distribution;
  adequacyEnergyPct?: number;
  energyBand?: AdequacyBand;
  /** Prescrito × teórico × diferença. Vazio quando não há meta energética. */
  comparison: MacroComparison[];
}

/** Onde o prescrito caiu em relação ao planejado. 95 a 105% é dentro. */
export type AdequacyBand = "BELOW" | "WITHIN" | "ABOVE";

export interface MacroComparison {
  macro: "protein" | "carbohydrate" | "fat";
  description: string;
  prescribedG?: number;
  theoreticalG?: number;
  differenceG?: number;
  prescribedKcal?: number;
  theoreticalKcal?: number;
  adequacyPct?: number;
  band?: AdequacyBand;
  /** g/kg do peso programado. Ele não usa na tela, mas quer no relatório. */
  prescribedPerKg?: number;
  theoreticalPerKg?: number;
}

/** Linha de alimento ou a barra que separa alimentos dentro da refeição. */
export type MealItemKind = "FOOD" | "SEPARATOR";

export interface Substitution {
  id?: number;
  foodId?: number;
  description: string;
  serving: string;
  grams?: number;
}

export interface Item {
  id?: number;
  kind?: MealItemKind;
  foodId?: number;
  measureId?: number;
  description: string;
  serving: string;
  quantity?: number;
  grams?: number;
  /** "À vontade": sem quantidade e fora do somatório do dia. */
  adLibitum?: boolean;
  order?: number;
  notes?: string;
  substitutions: Substitution[];
}

export interface MealResponse {
  id?: number;
  name: string;
  time?: string;
  order?: number;
  notes?: string;
  /** Desligada, a refeição não soma no dia: é opção substituta. */
  inCalculation: boolean;
  hasPhoto: boolean;
  photoName?: string;
  items: Item[];
  total: Total;
  /** Peso do que entra na conta, em gramas. */
  weightGrams?: number;
  /** kcal por grama e a faixa em que cai: muito baixa, baixa, média, alta. */
  energyDensity?: number;
  energyDensityBand?: "VERY_LOW" | "LOW" | "MEDIUM" | "HIGH";
  energyDensityDescription?: string;
  /** Quanto do dia esta refeição representa, em %. */
  shareOfDayPct?: number;
}

export interface PlanResponse {
  id: number;
  title: string;
  patientId?: number;
  patientName?: string;
  method: PrescriptionMethod;
  methodDescription: string;
  status: PlanStatus;
  statusDescription: string;
  publicIdentifier: string;
  validityStart?: string;
  validityEnd?: string;
  handouts?: string;
  internalNotes?: string;
  targetEnergyKcal?: number;
  targetProteinPct?: number;
  targetCarbohydratePct?: number;
  targetFatPct?: number;
  targetWeightKg?: number;
  energyPlanId?: number;
  template: boolean;
  meals: MealResponse[];
  dayTotal: Total;
  createdAt: string;
  updatedAt?: string;
}

export interface PlanSummary {
  id: number;
  title: string;
  patientId?: number;
  patientName?: string;
  method: PrescriptionMethod;
  status: PlanStatus;
  validityStart?: string;
  validityEnd?: string;
  template: boolean;
  meals: number;
  items: number;
  energyKcal?: number;
  updatedAt?: string;
}

// -------- writing side ----

export interface SubstitutionRequest {
  foodId?: number;
  measureId?: number;
  description: string;
  quantity?: number;
}

export interface ItemRequest {
  /** FOOD por omissão. SEPARATOR desenha a barra entre alimentos. */
  kind?: MealItemKind;
  foodId?: number;
  measureId?: number;
  description?: string;
  quantity?: number;
  /** "À vontade": sem quantidade e fora do somatório do dia. */
  adLibitum?: boolean;
  notes?: string;
  substitutions?: SubstitutionRequest[];
}

export interface MealRequest {
  name: string;
  time?: string;
  notes?: string;
  /** Nulo vale como sim. Desligada, a refeição não soma no dia. */
  inCalculation?: boolean;
  items: ItemRequest[];
}

export interface PlanRequest {
  title: string;
  patientId?: number;
  method: PrescriptionMethod;
  validityStart?: string;
  validityEnd?: string;
  handouts?: string;
  internalNotes?: string;
  targetEnergyKcal?: number;
  /** Distribuição planejada, em porcentagem da energia. Precisa somar 100. */
  targetProteinPct?: number;
  targetCarbohydratePct?: number;
  targetFatPct?: number;
  /** Peso programado, base do g/kg do relatório. */
  targetWeightKg?: number;
  energyPlanId?: number;
  template: boolean;
  meals: MealRequest[];
}

// --------- patient side ----

export interface PublicItem {
  description: string;
  /** Ready-made text of the household measure, e.g. "4 colheres de sopa". */
  serving: string;
  /** Weight of the portion. Absent when the item was prescribed without a defined weight. */
  weightGrams?: number;
  notes?: string;
  substitutions: { description: string; serving: string }[];
}

export interface PublicMeal {
  name: string;
  time?: string;
  notes?: string;
  items: PublicItem[];
}

export interface PublicHandout {
  id: number;
  title: string;
  body: string;
  /** Address of the figure, ready for the `src`. Absent when there is no figure. */
  image?: string;
}

export interface PublicRecipe {
  foodId: number;
  name: string;
  modeInstructions: string;
}

export interface PublicPlan {
  title: string;
  patientName?: string;
  nutritionistName?: string;
  nutritionistCrn?: string;
  practiceName?: string;
  primaryColor?: string;
  logoUrl?: string;
  method: PrescriptionMethod;
  current: boolean;
  closed: boolean;
  validityStart?: string;
  validityEnd?: string;
  handouts?: string;
  /** Handouts attached, in the text frozen at the moment of attaching. */
  handoutsAttached: PublicHandout[];
  meals: PublicMeal[];
  /** O modo de preparo das receitas usadas, para o paciente saber como fazer. */
  recipes: PublicRecipe[];
  summary: {
    energyKcal?: number;
    proteinG?: number;
    carbohydrateG?: number;
    fatG?: number;
    meals: number;
  };
  /** O passe do link, quando o plano pede a data de nascimento: vai no endereço das figuras. */
  accessToken?: string;
}

// ------------------------------------------------------- adequação (DRI)

export type AdequacyStatus = "BELOW" | "ADEQUATE" | "ABOVE" | "WITHIN_LIMIT" | "ABOVE_LIMIT" | "NO_DATA";

export interface AdequacyRow {
  nutrient: string;
  label: string;
  unit: string;
  /** O que o dia entrega; ausente quando nenhum alimento informou. */
  intake?: number;
  reference: number;
  kind: "RDA" | "AI" | "LIMIT";
  kindDescription: string;
  percent?: number;
  status: AdequacyStatus;
  /** Só parte dos alimentos informou: o total subestima. */
  incomplete: boolean;
}

export interface AdequacyResponse {
  available: boolean;
  unavailableBecause?: string;
  /** "Mulher, 31 a 50 anos". */
  reference?: string;
  ageYears?: number;
  rows: AdequacyRow[];
}

// ============================================================ anthropometry

export type CompositionProtocol =
  | "FAULKNER"
  | "POLLOCK_3"
  | "POLLOCK_7"
  | "DURNIN_WOMERSLEY"
  | "PETROSKI"
  | "GUEDES";

/** As faixas de Pollock e Wilmore (1993), da menor gordura para a maior. */
export type FatClassification =
  | "VERY_LOW"
  | "EXCELLENT"
  | "GOOD"
  | "ABOVE_AVERAGE"
  | "AVERAGE"
  | "BELOW_AVERAGE"
  | "POOR"
  | "VERY_POOR";

export type EquationExpenditure = "MIFFLIN_ST_JEOR" | "HARRIS_BENEDICT";

export type BmiClassification =
  | "LOW_WEIGHT"
  | "NORMAL"
  | "OVERWEIGHT"
  | "OBESITY_I"
  | "OBESITY_II"
  | "OBESITY_III";

export type CardiometabolicRisk = "LOW" | "MODERATE" | "HIGH";

/**
 * A derived value with the reason when it could not be calculated.
 * Null alone would force the interface to guess whether the datum is missing
 * because the measurement was not taken or because the rule prevents the
 * calculation.
 */
export interface Derived<T> {
  value?: T;
  unavailableBecause?: string;
}

export interface CompositionBody {
  protocol: CompositionProtocol;
  protocolDescription: string;
  percentageFat?: number;
  massFatKg?: number;
  massLeanKg?: number;
  /** Soma das dobras que o protocolo lê, em mm. */
  skinfoldSumMm?: number;
  /** Densidade corporal (g/cm³). Ausente no Faulkner, que não passa por ela. */
  density?: number;
  fatClassification: Derived<FatClassification>;
  fatClassificationDescription?: string;
  /** Faixa ideal para o sexo e a idade, em %. */
  fatIdealMin?: number;
  fatIdealMax?: number;
  fatReference: string;
}

/** Gordura, osso, resíduo e músculo: o modelo de quatro compartimentos. */
export interface Fractionation {
  boneMassKg: Derived<number>;
  residualMassKg: Derived<number>;
  muscleMassKg: Derived<number>;
  /** "dobras" ou "bioimpedância": de onde veio a massa gorda. */
  fatSource?: string;
}

export interface ArmMuscle {
  circumferenceCm: number;
  side: CircumferenceValue["side"];
  /** Preenchido quando só um lado foi medido. */
  sideDescription?: string;
}

export interface ExpenditureEnergy {
  equation: EquationExpenditure;
  equationDescription: string;
  factorActivity?: number;
  basalKcal?: number;
  totalKcal?: number;
}

export interface Assessment {
  id: number;
  patientId: number;
  patientName: string;
  date: string;
  weightKg?: number;
  heightCm?: number;
  /** Idade na data da avaliação. A tela usa para saber se trava a altura. */
  ageYears?: number;
  skinfolds: Record<string, number>;
  circumferences: CircumferenceValue[];
  diameterHumerus?: number;
  diameterWrist?: number;
  diameterFemur?: number;
  heightSittingCm?: number;
  heightKneeCm?: number;
  biaFatPercentage?: number;
  biaFatMassKg?: number;
  biaMusclePercentage?: number;
  biaMuscleMassKg?: number;
  biaLeanMassKg?: number;
  biaBoneMassKg?: number;
  biaVisceralFat?: number;
  biaBodyWaterPercentage?: number;
  biaMetabolicAge?: number;
  bmi?: number;
  classificationBmi: Derived<BmiClassification>;
  /** Faixa de peso pela altura, para adulto. */
  healthyWeight?: HealthyWeight;
  ratioWaistHip?: number;
  riskCardiometabolico: Derived<CardiometabolicRisk>;
  armMuscle: Derived<ArmMuscle>;
  composition?: CompositionBody;
  fractionation?: Fractionation;
  expenditureEnergy?: ExpenditureEnergy;
  /** Present when the patient is 19 or younger. */
  childGrowth?: Derived<ChildGrowth>;
  /** Present when the assessment reports a gestational week. */
  pregnancy?: Derived<Pregnancy>;
  notes?: string;
  createdAt: string;
}

export type GrowthIndicator = "BMI_TO_AGE" | "HEIGHT_TO_AGE";

export interface ChildIndicator {
  indicator: GrowthIndicator;
  indicatorDescription: string;
  scoreZ?: number;
  classification?: string;
  classificationDescription?: string;
  requiresAttention: boolean;
  /** Which WHO curve was used: they are distinct references. */
  reference: string;
}

export interface ChildGrowth {
  ageAtMonths: number;
  indicators: ChildIndicator[];
}

export interface Pregnancy {
  gestationalWeek: number;
  weightGestationalPreKg: number;
  bmiGestationalPre: number;
  range: string;
  rangeDescription: string;
  gainAteNow: number;
  expectedMin: number;
  expectedMax: number;
  status?: "BELOW" | "ADEQUATE" | "ABOVE";
  statusDescription?: string;
  totalGainRecommendedMin: number;
  totalGainRecommendedMax: number;
}

export interface AssessmentRequest {
  date: string;
  weightKg?: number;
  heightCm?: number;
  skinfolds?: Record<string, number>;
  circumferences?: CircumferenceValue[];
  diameterHumerus?: number;
  diameterWrist?: number;
  diameterFemur?: number;
  heightSittingCm?: number;
  heightKneeCm?: number;
  biaFatPercentage?: number;
  biaFatMassKg?: number;
  biaMusclePercentage?: number;
  biaMuscleMassKg?: number;
  biaLeanMassKg?: number;
  biaBoneMassKg?: number;
  biaVisceralFat?: number;
  biaBodyWaterPercentage?: number;
  biaMetabolicAge?: number;
  protocolComposition?: CompositionProtocol;
  equationExpenditure?: EquationExpenditure;
  factorActivity?: number;
  notes?: string;
  gestationalWeek?: number;
  weightGestationalPreKg?: number;
}

export interface Change {
  measure: string;
  label: string;
  current?: number;
  previous?: number;
  difference?: number;
  comparable: boolean;
  notes?: string;
}

export interface ProgressPoint {
  assessmentId: number;
  date: string;
  weightKg?: number;
  bmi?: number;
  percentageFat?: number;
  protocol?: CompositionProtocol;
  changesPreviousFront: Change[];
  changesFrontFirst: Change[];
  /** Para os gráficos da consulta. */
  massLeanKg?: number;
  massFatKg?: number;
  waistCm?: number;
}

export interface Progress {
  patientId: number;
  patientName: string;
  assessmentsTotal: number;
  points: ProgressPoint[];
}

export interface ProtocolInfo {
  protocol: CompositionProtocol;
  description: string;
  requiresSex: boolean;
  requiresAge: boolean;
  skinfoldsFemale: string[];
  skinfoldsMale: string[];
}

// ================================================================== schedule

export type AppointmentType =
  | "FIRST_CONSULTATION"
  | "FOLLOWUP"
  | "ASSESSMENT"
  | "COUNSELING"
  | "OTHER";

export type AppointmentStatus =
  | "SCHEDULED"
  | "CONFIRMED"
  | "COMPLETED"
  | "NOSHOW"
  | "CANCELED";

export interface Appointment {
  id: number;
  patientId: number;
  patientName?: string;
  start: string;
  end: string;
  durationMinutes: number;
  type: AppointmentType;
  typeDescription: string;
  status: AppointmentStatus;
  statusDescription: string;
  transitionsAllowed: AppointmentStatus[];
  notes?: string;
  reasonOutcome?: string;
  /** Parceiro a quem a consulta se atribui, quando difere de quem indicou o paciente. */
  partnerId?: number;
  partnerName?: string;
  /** Pacote de trabalho de que a consulta faz parte. */
  packageId?: number;
  packageName?: string;
  packageAmount?: number;
  /** O lançamento ligado à consulta, quando há um não cancelado. */
  payment?: AppointmentPayment;
  /** Só na resposta de um agendamento com série. */
  seriesCreated?: number;
  seriesSkipped?: string[];
}

export interface AppointmentPayment {
  transactionId: number;
  status: TransactionStatus;
  statusDescription: string;
  value: number;
  datePayment?: string;
}

export interface AppointmentRequest {
  patientId: number;
  start: string;
  durationMinutes: number;
  type: AppointmentType;
  notes?: string;
  partnerId?: number;
  packageId?: number;
  /** Marcar também os próximos encontros do pacote. */
  createSeries?: boolean;
}

export interface ScheduleDay {
  date: string;
  appointmentsTotal: number;
  completed: number;
  noshows: number;
  appointments: Appointment[];
}

export interface TypeAppointmentInfo {
  type: AppointmentType;
  description: string;
  durationSuggestedMinutes: number;
}

// =================================================================== finance

export type TransactionType = "INCOME" | "EXPENSE";
export type TransactionStatus = "PENDING" | "PAID" | "CANCELED";

export interface Transaction {
  id: number;
  type: TransactionType;
  typeDescription: string;
  status: TransactionStatus;
  statusDescription: string;
  value: number;
  accrual: string;
  due?: string;
  datePayment?: string;
  category: string;
  paymentMethod?: string;
  description?: string;
  patientId?: number;
  patientName?: string;
  appointmentId?: number;
  overdue: boolean;
  /** Número do recibo ou da nota emitida fora do sistema. */
  documentNumber?: string;
  installmentIndex?: number;
  installmentCount?: number;
  installmentGroup?: string;
  packageId?: number;
  packageName?: string;
}

export interface TransactionRequest {
  type: TransactionType;
  value: number;
  accrual: string;
  due?: string;
  category: string;
  paymentMethod?: string;
  description?: string;
  patientId?: number;
  appointmentId?: number;
  documentNumber?: string;
  packageId?: number;
  /** Em quantas parcelas dividir o valor; só ao criar. */
  installments?: number;
}

/** Pagamento registrado a partir da consulta; o valor vem do pacote quando omitido. */
export interface AppointmentPaymentRequest {
  value?: number;
  paymentMethod?: string;
  datePayment?: string;
  documentNumber?: string;
  category?: string;
}

export interface TotalByCategory {
  category: string;
  type: TransactionType;
  total: number;
  transactions: number;
}

export interface Summary {
  from: string;
  to: string;
  totalReceived: number;
  totalReceive: number;
  expensesPaid: number;
  expensesPay: number;
  resultEfetivado: number;
  resultExpected: number;
  transactions: number;
  byCategory: TotalByCategory[];
}

export interface Receipt {
  transactionId: number;
  practiceName?: string;
  profissionalName?: string;
  profissionalCrn?: string;
  payerName?: string;
  value: number;
  valueByWords: string;
  datePayment: string;
  related: string;
  emitidoAt: string;
  documentNumber?: string;
  /** "2/6" quando o lançamento é uma parcela. */
  installment?: string;
}

// ================================================================== partners

export interface Partner {
  id: number;
  name: string;
  kind?: string;
  contact?: string;
  notes?: string;
  active: boolean;
  /** Quantos pacientes do consultório foram indicados por ele. */
  patientsReferred: number;
}

export interface PartnerRequest {
  name: string;
  kind?: string;
  contact?: string;
  notes?: string;
}

export interface ReferralRow {
  partnerId: number;
  partnerName: string;
  kind?: string;
  patientsReferred: number;
  appointments: number;
  completed: number;
  noshows: number;
  revenuePaid: number;
}

export interface ReferralReport {
  from: string;
  to: string;
  rows: ReferralRow[];
  totalPatients: number;
  totalAppointments: number;
  totalRevenue: number;
}

// ================================================================== packages

export interface ServicePackage {
  id: number;
  name: string;
  amount: number;
  /** Encontros presenciais incluídos; ausente quando o pacote não é por sessões. */
  sessions?: number;
  intervalDays?: number;
  notes?: string;
  active: boolean;
}

export interface PackageRequest {
  name: string;
  amount: number;
  sessions?: number;
  intervalDays?: number;
  notes?: string;
}

// ================================================================ statistics

export interface MonthRow {
  /** "2026-09", para ordenar. */
  month: string;
  /** "set/2026", para ler. */
  label: string;
  appointments: number;
  completed: number;
  noshows: number;
  canceled: number;
  firstConsultations: number;
  followups: number;
  newPatients: number;
  assessments: number;
  plans: number;
  revenuePaid: number;
}

export interface InactivePatient {
  id: number;
  name: string;
  lastVisit?: string;
  daysSince: number;
}

export interface StatisticsResponse {
  from: string;
  to: string;
  activePatients: number;
  newPatients: number;
  appointments: number;
  completed: number;
  noshows: number;
  revenuePaid: number;
  months: MonthRow[];
  inactivityDays: number;
  withoutVisit: InactivePatient[];
}

// ----- recipes ----

export interface RecipeIngredient {
  id?: number;
  foodId: number;
  description: string;
  sourceDescription: string;
  measureId?: number;
  /** Ready-made text: "2 tablespoons" or "150 g". */
  quantity: string;
  grams: number;
}

export interface Recipe {
  id: number;
  name: string;
  group?: string;
  modeInstructions?: string;
  /** Final weight used in the calculation. */
  yieldGrams: number;
  /** True when the final weight was not reported and the sum was presumed. */
  estimatedYield: boolean;
  ingredientsWeight: number;
  servings?: number;
  gramsByServing?: number;
  compositionPor100g: Composition;
  servingComposition?: Composition;
  /** Nutrients summed from only part of the ingredients: they are a floor, not a total. */
  nutrientsIncomplete: string[];
  ingredients: RecipeIngredient[];
}

export interface RecipeSummary {
  id: number;
  name: string;
  group?: string;
  ingredientsTotal: number;
  yieldGrams?: number;
  servings?: number;
  energyKcalPor100g?: number;
}

export interface IngredientRequest {
  foodId: number;
  measureId?: number;
  quantity: number;
}

export interface RecipeRequest {
  name: string;
  group?: string;
  yieldGrams?: number;
  servings?: number;
  modeInstructions?: string;
  ingredients: IngredientRequest[];
}

// ---------- nutrition handouts ----

export interface Handout {
  id: number;
  title: string;
  body: string;
  /** A template that ships with the system: a starting point, not editable. */
  systemTemplate: boolean;
  editable: boolean;
  hasImage: boolean;
  imageName?: string;
}

export interface PlanHandout {
  id: number;
  /** Provenance in the library. Absent when the text was written on the spot. */
  handoutId?: number;
  title: string;
  body: string;
  order: number;
  /** The copy delivered in this plan, and not the library's figure. */
  hasImage: boolean;
}

// --------------- lab tests ----

export type LabtestClassification = "BELOW" | "NORMAL" | "ABOVE";

export interface ReferenceRange {
  sex?: Sex;
  ageMin?: number;
  ageMax?: number;
  minimum?: number;
  maximum?: number;
  text: string;
}

export interface LabtestParameter {
  id: number;
  name: string;
  unitStandard: string;
  group?: string;
  doSystemCatalog: boolean;
  editable: boolean;
  ranges: ReferenceRange[];
}

export interface LabtestResult {
  id: number;
  parameterId: number;
  parameter: string;
  group?: string;
  dateCollection: string;
  /** Absent means ordered and not yet determined — never zero. */
  value?: number;
  unit: string;
  classification?: LabtestClassification;
  classificationDescription?: string;
  /** The range used at entry, and not the one registered today. */
  reference?: string;
  notes?: string;
  hasReport: boolean;
  reportName?: string;
}

export interface SeriesPoint {
  dateCollection: string;
  value?: number;
  unit: string;
  classification?: LabtestClassification;
  change?: number;
}

export interface LabtestSeries {
  parameterId: number;
  parameter: string;
  unit: string;
  points: SeriesPoint[];
  /** True when there are collections in different units: they are not comparable. */
  unitsMixed: boolean;
}

/** Um exame dentro do pedido: de que painel veio, e se está ligado. */
export interface LabtestOrderItem {
  id: number;
  parameterId: number;
  name: string;
  unit?: string;
  group?: string;
  panelName?: string;
  /** Desligado, fica no pedido e sai do PDF. */
  active: boolean;
}

export interface LabtestOrderItemRequest {
  parameterId: number;
  panelName?: string;
  active?: boolean;
}

export interface LabtestOrder {
  id: number;
  date: string;
  notes?: string;
  /** Só os ligados, pelo nome: é o que vai para o PDF. */
  labtests: string[];
  items: LabtestOrderItem[];
}

// ------------ practice team ----

export interface AccountUser {
  id: number;
  name: string;
  email: string;
  role: Role;
  roleDescription: string;
  active: boolean;
  createdAt?: string;
}

// --- questionnaires ----

export type QuestionType =
  | "TEXT"
  | "PARAGRAPH"
  | "NUMBER"
  | "DATE"
  | "CHOICE_SINGLE"
  | "MULTIPLE"
  | "SECTION";

export interface AnswerOption {
  label: string;
  points?: number;
}

export interface QuestionnaireQuestion {
  id: number;
  statement: string;
  type: QuestionType;
  typeDescription: string;
  required: boolean;
  order: number;
  ajuda?: string;
  options: AnswerOption[];
  /** Aparece na listagem de anamneses sem abrir o registro. */
  highlight: boolean;
}

export interface QuestionRequest {
  statement: string;
  type: QuestionType;
  required?: boolean;
  /** "Nunca=0|Às vezes=1|Sempre=2"; a pontuação é opcional. */
  options?: string;
  ajuda?: string;
  highlight?: boolean;
}

export interface QuestionnaireRequest {
  name: string;
  description?: string;
  instrument?: string;
  version?: string;
  scorable?: boolean;
  /** "0-5=Baixo|6-10=Moderado|11-99=Alto". */
  cutoffRange?: string;
  questions: QuestionRequest[];
}

export interface Questionnaire {
  id: number;
  name: string;
  description?: string;
  instrument?: string;
  version?: string;
  scorable: boolean;
  cutoffRange?: string;
  templateVersion: number;
  systemTemplate: boolean;
  editable: boolean;
  questions: QuestionnaireQuestion[];
}

export interface ItemAnswered {
  question: string;
  value?: string;
  points?: number;
}

export interface QuestionnaireAnswer {
  id: number;
  questionnaireId: number;
  questionnaire: string;
  /** The edition of the form the patient saw. */
  templateVersion: number;
  publicIdentifier: string;
  sentAt: string;
  answeredAt?: string;
  pending: boolean;
  score?: number;
  classification?: string;
  items: ItemAnswered[];
}

export interface PublicForm {
  practice?: string;
  title: string;
  description?: string;
  alreadyAnswered: boolean;
  questions: QuestionnaireQuestion[];
}

// ------------------------------------------------------------------ anamnese

/**
 * Um ponto que o consultório quer em toda anamnese.
 *
 * A lista é do consultório, não do sistema: foi o que o cliente pediu ao
 * responder que queria escolher os pontos — "um textbox com um label
 * informando o propósito da consulta".
 */
export interface AnamnesisField {
  id: number;
  label: string;
  order: number;
  /** Se o valor aparece na listagem, sem precisar abrir a anamnese. */
  showInListing: boolean;
}

export interface AnamnesisValue {
  /** Nulo quando o campo saiu da lista do consultório depois desta anamnese. */
  fieldId: number | null;
  /** Congelado no preenchimento: renomear o campo não reescreve o passado. */
  label: string;
  value: string | null;
  order: number;
}

export interface AnamnesisSummary {
  id: number;
  name: string;
  date: string;
  highlights: AnamnesisValue[];
  /** O questionário de que a anamnese nasceu, quando nasceu de um. */
  questionnaireName?: string;
  /** O envio pré-consulta de que foi importada, quando foi. */
  sendingId?: number;
}

/** A resposta a uma pergunta do questionário da anamnese, com o enunciado congelado. */
export interface AnamnesisAnswer {
  questionId: number | null;
  statement: string;
  type: QuestionType;
  value: string | null;
  highlight: boolean;
  order: number;
}

export interface Anamnesis {
  id: number;
  patientId: number;
  name: string;
  date: string;
  /** Documento do editor, em JSON. Nulo enquanto estiver em branco. */
  body: string | null;
  values: AnamnesisValue[];
  questionnaireId?: number;
  questionnaireName?: string;
  templateVersion?: number;
  sendingId?: number;
  answers: AnamnesisAnswer[];
}

export interface AnamnesisRequest {
  patientId: number;
  name: string;
  date: string;
  body?: string;
  values?: { fieldId: number; value: string }[];
  questionnaireId?: number;
  answers?: { questionId: number; value: string }[];
}

// -------------------------------------------------------- cálculo energético

export type ActivityLevel = "INACTIVE" | "LOW_ACTIVE" | "ACTIVE" | "VERY_ACTIVE";

export type EnergyEquationName =
  | "HARRIS_BENEDICT_1984"
  | "MIFFLIN_ST_JEOR"
  | "FAO_WHO_2004"
  | "EER_IOM_2005"
  | "EER_2023"
  | "KATCH_MCARDLE"
  | "CUNNINGHAM"
  | "TINSLEY_WEIGHT"
  | "TINSLEY_LEAN";

export interface EquationOption {
  equation: EnergyEquationName;
  description: string;
  /** Se o nível de atividade já está dentro do resultado da equação. */
  total: boolean;
  /** Menor idade para a qual a equação foi publicada. */
  ageMinimum: number;
  /** Parte da massa magra, e não do peso. */
  requiresLeanMass: boolean;
}

export interface ActivityOption {
  level: ActivityLevel;
  description: string;
}

export interface EnergyOptions {
  equations: EquationOption[];
  activityLevels: ActivityOption[];
}

export interface EquationResult {
  equation: EnergyEquationName;
  description: string;
  /** Ausente quando a equação já responde o gasto do dia inteiro. */
  basalKcal?: number;
  totalKcal: number;
}

/** A faixa de peso que mantém o adulto em eutrofia, pela altura. */
export interface HealthyWeight {
  minimumKg: number;
  maximumKg: number;
  bmiMinimum: number;
  bmiMaximum: number;
}

export interface EnergyPlan {
  id: number;
  patientId: number;
  name: string;
  date: string;
  assessmentId?: number;
  weightKg: number;
  heightCm: number;
  /** Massa livre de gordura usada pelas equações que partem dela. */
  leanMassKg?: number;
  ageYears: number;
  sex: Sex;
  activityLevel: ActivityLevel;
  activityDescription: string;
  injuryFactor: number;
  metKcal?: number;
  targetWeightKg?: number;
  targetDate?: string;
  /** Negativo para perda de peso. */
  adjustmentKcal?: number;
  averageKcal: number;
  prescribedKcal: number;
  equations: EquationResult[];
  healthyWeight?: HealthyWeight;
  /** O que merece um segundo olhar antes de usar o prescrito. Vazio quando não há nada. */
  warnings: string[];
  notes?: string;
}

export interface EnergyPlanSummary {
  id: number;
  name: string;
  date: string;
  prescribedKcal: number;
  equations: string[];
}

export interface EnergyPlanRequest {
  patientId: number;
  name?: string;
  date: string;
  assessmentId?: number;
  weightKg?: number;
  heightCm?: number;
  leanMassKg?: number;
  activityLevel: ActivityLevel;
  injuryFactor?: number;
  metKcal?: number;
  equations: EnergyEquationName[];
  targetWeightKg?: number;
  targetDate?: string;
  notes?: string;
}


/** Onde uma circunferência é medida. Sete dos treze locais têm lado. */
export type CircumferenceSite =
  | "NECK"
  | "SHOULDER"
  | "CHEST"
  | "WAIST"
  | "ABDOMEN"
  | "HIP"
  | "ARM_RELAXED"
  | "ARM_CONTRACTED"
  | "FOREARM"
  | "THIGH_PROXIMAL"
  | "THIGH_MEDIAL"
  | "THIGH_DISTAL"
  | "CALF";

/**
 * SINGLE não é preenchimento: significa que a medida não tem lado, porque o
 * local não tem ou porque quem mediu não registrou qual era.
 */
export type Side = "SINGLE" | "RIGHT" | "LEFT";

export interface CircumferenceValue {
  site: CircumferenceSite;
  side: Side;
  valueCm: number;
  /** Só vem na resposta, para a tela não repetir a tradução. */
  description?: string;
}


// ----------------------------------------------------- refeições favoritas

export interface FavoriteMeal {
  id: number;
  /** O nome sob o qual ele salvou. */
  name: string;
  /** O nome da refeição em si — "Café da Manhã". */
  mealName: string;
  notes?: string;
  items: Item[];
  energyKcal?: number;
  itemsTotal: number;
}

export interface FavoriteMealRequest {
  name: string;
  meal: MealRequest;
}


// ------------------------------------------------- painéis de biomarcadores

export interface PanelParameter {
  id: number;
  name: string;
  unit?: string;
  group?: string;
}

export interface LabtestPanel {
  id: number;
  name: string;
  /** Painel do sistema não é editável: é acervo compartilhado. */
  systemPanel: boolean;
  /** A TAG que separa os do consultório dos 25 que o sistema traz. */
  own: boolean;
  parameters: PanelParameter[];
}


// ------------------------------------------------------------ arquivos anexos

export type AttachmentKind = "FILE" | "LINK";

export interface PatientAttachment {
  id: number;
  title: string;
  kind: AttachmentKind;
  kindDescription: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  url?: string;
  notes?: string;
  /** A data do documento, que raramente é a data em que ele foi anexado. */
  referenceDate?: string;
  createdAt: string;
}
