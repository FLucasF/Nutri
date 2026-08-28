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

export interface PatientSummary {
  id: number;
  name: string;
  email?: string;
  age?: number;
  active: boolean;
}

export interface Patient {
  id: number;
  name: string;
  email?: string;
  phone?: string;
  dateBirth?: string;
  age?: number;
  sex?: Sex;
  cpf?: string;
  occupation?: string;
  goal?: string;
  notes?: string;
  active: boolean;
  hasAccessAoApp: boolean;
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
}

export interface Substitution {
  id?: number;
  foodId?: number;
  description: string;
  serving: string;
  grams?: number;
}

export interface Item {
  id?: number;
  foodId?: number;
  measureId?: number;
  description: string;
  serving: string;
  quantity?: number;
  grams?: number;
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
  items: Item[];
  total: Total;
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
  foodId?: number;
  measureId?: number;
  description?: string;
  quantity?: number;
  notes?: string;
  substitutions?: SubstitutionRequest[];
}

export interface MealRequest {
  name: string;
  time?: string;
  notes?: string;
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
  summary: {
    energyKcal?: number;
    proteinG?: number;
    carbohydrateG?: number;
    fatG?: number;
    meals: number;
  };
}

// ============================================================ anthropometry

export type CompositionProtocol =
  | "FAULKNER"
  | "POLLOCK_3"
  | "POLLOCK_7"
  | "DURNIN_WOMERSLEY";

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
  skinfolds: Record<string, number>;
  circumferences: Record<string, number>;
  bmi?: number;
  classificationBmi: Derived<BmiClassification>;
  ratioWaistHip?: number;
  riskCardiometabolico: Derived<CardiometabolicRisk>;
  composition?: CompositionBody;
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
  circumferences?: Record<string, number>;
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
}

export interface AppointmentRequest {
  patientId: number;
  start: string;
  durationMinutes: number;
  type: AppointmentType;
  notes?: string;
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

export interface LabtestOrder {
  id: number;
  date: string;
  notes?: string;
  labtests: string[];
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

export type QuestionType = "TEXT" | "NUMBER" | "CHOICE_SINGLE" | "MULTIPLE";

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
