import type {
  Appointment,
  AppointmentRequest,
  FoodDetail,
  FoodSummary,
  Summary,
  Assessment,
  AssessmentRequest,
  Composition,
  ScheduleDay,
  Progress,
  DataSource,
  Transaction,
  TransactionRequest,
  Measure,
  Page,
  Patient,
  PatientSummary,
  PublicPlan,
  PlanRequest,
  PlanResponse,
  PlanSummary,
  CalculatedServing,
  ProtocolInfo,
  Handout,
  PlanHandout,
  LabtestResult,
  LabtestParameter,
  PublicForm,
  Questionnaire,
  Receipt,
  QuestionnaireAnswer,
  AccountUser,
  LabtestSeries,
  LabtestOrder,
  LabtestOrderItemRequest,
  Recipe,
  RecipeRequest,
  RecipeSummary,
  ResultImport,
  AppointmentStatus,
  TransactionStatus,
  TypeAppointmentInfo,
  TransactionType,
  TokenResponse,
  Anamnesis,
  AnamnesisField,
  AnamnesisRequest,
  AnamnesisSummary,
  EnergyOptions,
  FavoriteMeal,
  LabtestPanel,
  PatientAttachment,
  PatientNote,
  NoteTemplate,
  PatientTag,
  FavoriteMealRequest,
  EnergyPlan,
  EnergyPlanRequest,
  EnergyPlanSummary,
} from "./types";

const BASE = "/api";
const KEY_TOKEN = "nutriplan.token";

/** An API error already translated into what the interface needs to show. */
export class ErrorApi extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fields?: { field: string; message: string }[],
  ) {
    super(message);
    this.name = "ErroApi";
  }

  /** A validation error has per-field detail; the others, only the message. */
  get isValidation() {
    return this.status === 400 && !!this.fields?.length;
  }
}

export function readToken(): string | null {
  try {
    return localStorage.getItem(KEY_TOKEN);
  } catch {
    return null;
  }
}

export function storeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(KEY_TOKEN, token);
    else localStorage.removeItem(KEY_TOKEN);
  } catch {
    /* private browsing: the session lasts only as long as the tab */
  }
}

type Options = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  /** Public request: it sends no credential and does not redirect on expiry. */
  withoutAuthentication?: boolean;
  formDate?: FormData;
};

let onExpireSession: (() => void) | null = null;

/** Registered by the authentication context so it can react to a 401. */
export function defineSessionExpiredHandling(callback: () => void) {
  onExpireSession = callback;
}

/**
 * Fetches a file from an authenticated route and returns its temporary URL.
 *
 * The caller is responsible for releasing the URL after using it; without that
 * the file stays held in memory for as long as the tab lives.
 */
async function download(path: string): Promise<{ url: string; name: string }> {
  const token = readToken();
  const answer = await fetch(`${BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!answer.ok) {
    if (answer.status === 401) {
      onExpireSession?.();
    }
    throw new ErrorApi(answer.status, "Não foi possível gerar o arquivo.");
  }

  const disposition = answer.headers.get("Content-Disposition") ?? "";
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? "arquivo.pdf";
  return { url: URL.createObjectURL(await answer.blob()), name };
}

async function request<T>(path: string, options: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = readToken();

  if (!options.withoutAuthentication && token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const answer = await fetch(`${BASE}${path}`, {
    method: options.method ?? "GET",
    headers: headers,
    body: options.formDate ?? (options.body !== undefined ? JSON.stringify(options.body) : undefined),
  });

  if (answer.status === 204) {
    return undefined as T;
  }

  const text = await answer.text();
  const data = text ? JSON.parse(text) : null;

  if (!answer.ok) {
    if (answer.status === 401 && !options.withoutAuthentication) {
      onExpireSession?.();
    }
    throw new ErrorApi(
      answer.status,
      data?.message ?? "Não foi possível completar a operação.",
      data?.fields,
    );
  }
  return data as T;
}

function query(params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const api = {
  // ----------------------------------------------------------- authentication
  login: (email: string, password: string) =>
    request<TokenResponse>("/auth/login", {
      method: "POST",
      body: { email, password },
      withoutAuthentication: true,
    }),

  register: (data: { name: string; email: string; password: string; crn?: string; phone?: string }) =>
    request<TokenResponse>("/auth/signup", {
      method: "POST",
      body: data,
      withoutAuthentication: true,
    }),

  eu: () => request<TokenResponse["user"]>("/auth/eu"),

  // ------------------------------------------------------------------- patients
  patients: {
    list: (params: { term?: string; active?: boolean; page?: number; size?: number }) =>
      request<Page<PatientSummary>>(`/patients${query(params)}`),

    find: (id: number) => request<Patient>(`/patients/${id}`),

    /** As TAGs disponíveis: as do consultório primeiro, depois as do sistema. */
    tags: () => request<PatientTag[]>("/patient-tags"),

    createTag: (name: string) =>
      request<PatientTag>("/patient-tags", { method: "POST", body: { name } }),

    tagsOf: (patientId: number) => request<PatientTag[]>(`/patients/${patientId}/tags`),

    /** O conjunto inteiro de uma vez: marcar e desmarcar é uma operação só. */
    setTags: (patientId: number, tagIds: number[]) =>
      request<PatientTag[]>(`/patients/${patientId}/tags`, {
        method: "PUT",
        body: { tagIds },
      }),

    notes: (patientId: number) => request<PatientNote[]>(`/patients/${patientId}/notes`),

    attachments: (patientId: number) =>
      request<PatientAttachment[]>(`/patients/${patientId}/attachments`),

    /**
     * Anexa um arquivo ao prontuário.
     *
     * O título vai junto e é obrigatório: "documento(1).pdf" não diz nada daqui
     * a um ano, e é daqui a um ano que alguém vai procurar.
     */
    attachFile: (
      patientId: number,
      file: File,
      meta: { title: string; notes?: string; referenceDate?: string },
    ) => {
      const data = new FormData();
      data.append("file", file);
      data.append("title", meta.title);
      if (meta.notes) data.append("notes", meta.notes);
      if (meta.referenceDate) data.append("referenceDate", meta.referenceDate);
      return request<PatientAttachment>(`/patients/${patientId}/attachments`, {
        method: "POST",
        formDate: data,
      });
    },

    attachLink: (
      patientId: number,
      data: { title: string; url: string; notes?: string; referenceDate?: string },
    ) =>
      request<PatientAttachment>(`/patients/${patientId}/attachments/links`, {
        method: "POST",
        body: data,
      }),

    /** Baixa o arquivo autenticado e devolve um endereço local para abrir. */
    attachmentFile: (patientId: number, attachmentId: number) =>
      download(`/patients/${patientId}/attachments/${attachmentId}/file`),

    removeAttachment: (patientId: number, attachmentId: number) =>
      request<void>(`/patients/${patientId}/attachments/${attachmentId}`, {
        method: "DELETE",
      }),

    addNote: (patientId: number, body: string) =>
      request<PatientNote>(`/patients/${patientId}/notes`, { method: "POST", body: { body } }),

    removeNote: (patientId: number, noteId: number) =>
      request<void>(`/patients/${patientId}/notes/${noteId}`, { method: "DELETE" }),

    /** Os modelos de anotação do consultório, em ordem alfabética. */
    noteTemplates: () => request<NoteTemplate[]>("/note-templates"),

    /** Guarda um modelo; o mesmo nome sobrescreve. */
    saveNoteTemplate: (name: string, body: string) =>
      request<NoteTemplate>("/note-templates", { method: "POST", body: { name, body } }),

    removeNoteTemplate: (id: number) =>
      request<void>(`/note-templates/${id}`, { method: "DELETE" }),

    create: (data: Partial<Patient>) =>
      request<Patient>("/patients", { method: "POST", body: data }),

    update: (id: number, data: Partial<Patient>) =>
      request<Patient>(`/patients/${id}`, { method: "PUT", body: data }),

    deactivate: (id: number) => request<void>(`/patients/${id}`, { method: "DELETE" }),

    reactivate: (id: number) =>
      request<Patient>(`/patients/${id}/reactivate`, { method: "POST" }),

    importAll: (file: File, separator = ",") => {
      const data = new FormData();
      data.append("file", file);
      data.append("separator", separator);
      return request<ResultImport>("/patients/import", {
        method: "POST",
        formDate: data,
      });
    },
  },

  // ---------------------------------------------------------------------- foods
  foods: {
    find: (params: { term?: string; group?: string; source?: DataSource; page?: number; size?: number }) =>
      request<Page<FoodSummary>>(`/foods${query(params)}`),

    groups: () => request<string[]>("/foods/groups"),

    detail: (id: number) => request<FoodDetail>(`/foods/${id}`),

    /**
     * Searches by barcode. It returns a list because the code is not a key: the
     * same EAN shows up more than once in the collaborative base, and the
     * practice may have registered its own product with it.
     */
    byBarcodeCode: (code: string) =>
      request<FoodDetail[]>(`/foods/barcode/${encodeURIComponent(code)}`),

    serving: (id: number, quantity: number, measureId?: number) =>
      request<CalculatedServing>(`/foods/${id}/serving${query({ quantity, measureId })}`),

    create: (data: {
      description: string;
      group?: string;
      brand?: string;
      codeBarcode?: string;
      composition: Composition;
      measures?: unknown[];
    }) =>
      request<FoodDetail>("/foods", { method: "POST", body: data }),

    update: (id: number, data: unknown) =>
      request<FoodDetail>(`/foods/${id}`, { method: "PUT", body: data }),

    addMeasure: (id: number, data: { description: string; grams: number; standard: boolean }) =>
      request<Measure>(`/foods/${id}/measures`, { method: "POST", body: data }),

    removeMeasure: (id: number, measureId: number) =>
      request<void>(`/foods/${id}/measures/${measureId}`, { method: "DELETE" }),

    importAll: (file: File, source?: DataSource, separator = ",") => {
      const data = new FormData();
      data.append("file", file);
      if (source) data.append("source", source);
      data.append("separator", separator);
      return request<ResultImport>("/foods/import", {
        method: "POST",
        formDate: data,
      });
    },
  },

  // -------------------------------------------------------------- prescriptions
  prescriptions: {
    list: (params: { patientId?: number; template?: boolean; term?: string; page?: number; size?: number }) =>
      request<Page<PlanSummary>>(`/prescriptions${query(params)}`),

    detail: (id: number) => request<PlanResponse>(`/prescriptions/${id}`),

    create: (data: PlanRequest) =>
      request<PlanResponse>("/prescriptions", { method: "POST", body: data }),

    update: (id: number, data: PlanRequest) =>
      request<PlanResponse>(`/prescriptions/${id}`, { method: "PUT", body: data }),

    publish: (id: number) =>
      request<PlanResponse>(`/prescriptions/${id}/publish`, { method: "POST" }),

    close: (id: number) =>
      request<PlanResponse>(`/prescriptions/${id}/close`, { method: "POST" }),

    backToDraft: (id: number) =>
      request<PlanResponse>(`/prescriptions/${id}/draft`, { method: "POST" }),

    regenerateLink: (id: number) =>
      request<PlanResponse>(`/prescriptions/${id}/regenerate-link`, { method: "POST" }),

    duplicate: (id: number, patientId?: number, title?: string) =>
      request<PlanResponse>(`/prescriptions/${id}/duplicate${query({ patientId, title })}`, {
        method: "POST",
      }),

    remove: (id: number) => request<void>(`/prescriptions/${id}`, { method: "DELETE" }),

    /**
     * Downloads the plan as a PDF.
     *
     * It goes by fetch, and not by a direct link: the route requires
     * authentication, and a plain anchor does not carry the token header.
     */
    pdf: (id: number) => download(`/prescriptions/${id}/pdf`),
  },

  // ------------------------------------------------------------------- recipes
  recipes: {
    list: (params: { term?: string; page?: number; size?: number }) =>
      request<Page<RecipeSummary>>(`/recipes${query(params)}`),

    detail: (id: number) => request<Recipe>(`/recipes/${id}`),

    create: (data: RecipeRequest) =>
      request<Recipe>("/recipes", { method: "POST", body: data }),

    update: (id: number, data: RecipeRequest) =>
      request<Recipe>(`/recipes/${id}`, { method: "PUT", body: data }),

    remove: (id: number) => request<void>(`/recipes/${id}`, { method: "DELETE" }),
  },

  // --------------------------------------------------------- questionnaires
  questionnaires: {
    list: () => request<Questionnaire[]>("/questionnaires"),

    detail: (id: number) => request<Questionnaire>(`/questionnaires/${id}`),

    duplicate: (id: number) =>
      request<Questionnaire>(`/questionnaires/${id}/duplicate`, { method: "POST" }),

    remove: (id: number) => request<void>(`/questionnaires/${id}`, { method: "DELETE" }),

    forPatient: (patientId: number) =>
      request<QuestionnaireAnswer[]>(`/patients/${patientId}/questionnaires`),

    send: (patientId: number, data: { questionnaireId: number; appointmentId?: number }) =>
      request<QuestionnaireAnswer>(`/patients/${patientId}/questionnaires`, {
        method: "POST",
        body: data,
      }),

    cancelSending: (id: number) =>
      request<void>(`/questionnaires/sendings/${id}`, { method: "DELETE" }),

    /** The form as the patient sees it: with no credential at all. */
    form: (identifier: string) =>
      request<PublicForm>(`/public/questionnaires/${identifier}`, {
        withoutAuthentication: true,
      }),

    answer: (identifier: string, answers: { questionId: number; value: string }[]) =>
      request<void>(`/public/questionnaires/${identifier}`, {
        method: "POST",
        body: { answers },
        withoutAuthentication: true,
      }),
  },

  // ------------------------------------------------------------------ team
  users: {
    list: () => request<AccountUser[]>("/users"),

    createAssistant: (data: {
      name: string;
      email: string;
      initialPassword: string;
      phone?: string;
    }) => request<AccountUser>("/users", { method: "POST", body: data }),

    deactivate: (id: number) => request<void>(`/users/${id}`, { method: "DELETE" }),

    reactivate: (id: number) =>
      request<AccountUser>(`/users/${id}/reactivate`, { method: "POST" }),
  },

  // ---------------------------------------------------------------- access
  access: {
    /**
     * Asks for the reset link.
     *
     * It answers the same whether the email exists or not: an endpoint that
     * said "email not found" would let someone sweep addresses and discover who
     * has an account.
     */
    recoverPassword: (email: string) =>
      request<void>("/auth/recover-password", {
        method: "POST",
        body: { email },
        withoutAuthentication: true,
      }),

    resetPassword: (token: string, novaPassword: string) =>
      request<void>("/auth/reset-password", {
        method: "POST",
        body: { token, novaPassword },
        withoutAuthentication: true,
      }),
  },

  // --------------------------------------------------------------- lab tests
  labtests: {
    parameters: () => request<LabtestParameter[]>("/labtests/parameters"),

    /**
     * Os painéis de biomarcadores — os botões que preenchem o pedido.
     *
     * Os do consultório vêm primeiro e marcados, para a tela poder separá-los
     * dos 25 que o sistema traz.
     */
    panels: () => request<LabtestPanel[]>("/labtests/panels"),

    createPanel: (data: { name: string; parameterIds: number[] }) =>
      request<LabtestPanel>("/labtests/panels", { method: "POST", body: data }),

    duplicatePanel: (id: number) =>
      request<LabtestPanel>(`/labtests/panels/${id}/duplicate`, { method: "POST" }),

    removePanel: (id: number) =>
      request<void>(`/labtests/panels/${id}`, { method: "DELETE" }),

    createParameter: (data: {
      name: string;
      unitStandard: string;
      group?: string;
      minimum?: number;
      maximum?: number;
    }) => request<LabtestParameter>("/labtests/parameters", { method: "POST", body: data }),

    forPatient: (patientId: number) =>
      request<LabtestResult[]>(`/patients/${patientId}/labtests`),

    entry: (
      patientId: number,
      data: {
        parameterId: number;
        dateCollection: string;
        value?: number;
        unit?: string;
        notes?: string;
      },
    ) =>
      request<LabtestResult>(`/patients/${patientId}/labtests`, {
        method: "POST",
        body: data,
      }),

    series: (patientId: number, parameterId: number) =>
      request<LabtestSeries>(`/patients/${patientId}/labtests/series/${parameterId}`),

    remove: (id: number) => request<void>(`/labtests/${id}`, { method: "DELETE" }),

    attachReport: (id: number, file: File) => {
      const data = new FormData();
      data.append("file", file);
      return request<void>(`/labtests/${id}/report`, { method: "POST", formDate: data });
    },

    report: (id: number) => download(`/labtests/${id}/report`),

    requests: (patientId: number) =>
      request<LabtestOrder[]>(`/patients/${patientId}/requests-from-labtest`),

    request: (
      patientId: number,
      data: { date: string; items: LabtestOrderItemRequest[]; notes?: string },
    ) =>
      request<LabtestOrder>(`/patients/${patientId}/requests-from-labtest`, {
        method: "POST",
        body: data,
      }),

    /** Religa, desliga ou troca os exames de um pedido já entregue. */
    updateRequest: (
      patientId: number,
      orderId: number,
      data: { items: LabtestOrderItemRequest[]; notes?: string },
    ) =>
      request<LabtestOrder>(`/patients/${patientId}/requests-from-labtest/${orderId}`, {
        method: "PUT",
        body: data,
      }),

    /** O pedido em PDF, agrupado por painel. Só os exames ligados saem. */
    requestPdf: (patientId: number, orderId: number) =>
      download(`/patients/${patientId}/requests-from-labtest/${orderId}/pdf`),
  },

  // ---------------------------------------------------------------- handouts
  mealPhotos: {
    /** Anexa a foto do prato. A refeição precisa já estar salva. */
    send: (planId: number, mealId: number, file: File) => {
      const data = new FormData();
      data.append("file", file);
      return request<void>(`/prescriptions/${planId}/meals/${mealId}/photo`, {
        method: "POST",
        formDate: data,
      });
    },

    /** Baixa a foto autenticada e devolve um endereço local para o `src`. */
    open: (planId: number, mealId: number) =>
      download(`/prescriptions/${planId}/meals/${mealId}/photo`),

    remove: (planId: number, mealId: number) =>
      request<void>(`/prescriptions/${planId}/meals/${mealId}/photo`, { method: "DELETE" }),
  },

  mealFavorites: {
    list: () => request<FavoriteMeal[]>("/meal-favorites"),

    detail: (id: number) => request<FavoriteMeal>(`/meal-favorites/${id}`),

    /**
     * Salva a refeição inteira, e não o id de uma já gravada.
     *
     * No editor a refeição pode ainda não ter sido salva, e ele não deveria
     * precisar salvar o plano para guardar a própria refeição.
     */
    save: (data: FavoriteMealRequest) =>
      request<FavoriteMeal>("/meal-favorites", { method: "POST", body: data }),

    remove: (id: number) => request<void>(`/meal-favorites/${id}`, { method: "DELETE" }),
  },

  energyPlans: {
    /**
     * As equações e os níveis de atividade que a tela pode oferecer.
     *
     * Vêm do servidor, e não de uma lista escrita aqui: uma equação que a tela
     * oferecesse e o cálculo não conhecesse viraria erro na hora de salvar.
     */
    options: () => request<EnergyOptions>("/energy-plans/options"),

    ofPatient: (patientId: number) =>
      request<EnergyPlanSummary[]>(`/patients/${patientId}/energy-plans`),

    detail: (id: number) => request<EnergyPlan>(`/energy-plans/${id}`),

    create: (data: EnergyPlanRequest) =>
      request<EnergyPlan>("/energy-plans", { method: "POST", body: data }),

    update: (id: number, data: EnergyPlanRequest) =>
      request<EnergyPlan>(`/energy-plans/${id}`, { method: "PUT", body: data }),

    remove: (id: number) => request<void>(`/energy-plans/${id}`, { method: "DELETE" }),
  },

  anamneses: {
    /** Os campos de destaque do consultório. */
    fields: () => request<AnamnesisField[]>("/anamnesis-fields"),

    /**
     * Redefine a lista inteira de campos.
     *
     * A ordem é propriedade do conjunto, então o conjunto viaja junto. O que
     * sair da lista é desativado no servidor, nunca apagado: anamneses já
     * escritas apontam para ele.
     */
    saveFields: (fields: { label: string; showInListing: boolean }[]) =>
      request<AnamnesisField[]>("/anamnesis-fields", { method: "PUT", body: { fields } }),

    ofPatient: (patientId: number) =>
      request<AnamnesisSummary[]>(`/patients/${patientId}/anamneses`),

    detail: (id: number) => request<Anamnesis>(`/anamneses/${id}`),

    create: (data: AnamnesisRequest) =>
      request<Anamnesis>("/anamneses", { method: "POST", body: data }),

    update: (id: number, data: AnamnesisRequest) =>
      request<Anamnesis>(`/anamneses/${id}`, { method: "PUT", body: data }),

    duplicate: (id: number) =>
      request<Anamnesis>(`/anamneses/${id}/duplicate`, { method: "POST" }),

    remove: (id: number) => request<void>(`/anamneses/${id}`, { method: "DELETE" }),

    /**
     * Baixa o PDF autenticado e devolve um endereço temporário.
     *
     * O token viaja no cabeçalho, então um href direto para a rota voltaria
     * 401. O cliente busca, vira blob, e a guia abre esse endereço local.
     */
    pdf: (id: number) => download(`/anamneses/${id}/pdf`),
  },

  handouts: {
    list: (params: { term?: string; page?: number; size?: number }) =>
      request<Page<Handout>>(`/handouts${query(params)}`),

    detail: (id: number) => request<Handout>(`/handouts/${id}`),

    create: (data: { title: string; body: string }) =>
      request<Handout>("/handouts", { method: "POST", body: data }),

    duplicate: (id: number) =>
      request<Handout>(`/handouts/${id}/duplicate`, { method: "POST" }),

    update: (id: number, data: { title: string; body: string }) =>
      request<Handout>(`/handouts/${id}`, { method: "PUT", body: data }),

    remove: (id: number) => request<void>(`/handouts/${id}`, { method: "DELETE" }),

    sendImage: (id: number, file: File) => {
      const data = new FormData();
      data.append("file", file);
      return request<void>(`/handouts/${id}/image`, {
        method: "POST",
        formDate: data,
      });
    },

    /**
     * Downloads the figure and returns a temporary URL for the `src`.
     *
     * The route requires a credential and the `img` tag sends no header — which
     * is why the file comes by `fetch`. The caller releases the URL on unmount.
     */
    image: (id: number) => download(`/handouts/${id}/image`),

    imageNoPlan: (planId: number, attachmentId: number) =>
      download(`/prescriptions/${planId}/handouts/${attachmentId}/image`),

    forPlan: (planId: number) =>
      request<PlanHandout[]>(`/prescriptions/${planId}/handouts`),

    attach: (planId: number, data: { handoutId?: number; title?: string; body?: string }) =>
      request<PlanHandout>(`/prescriptions/${planId}/handouts`, {
        method: "POST",
        body: data,
      }),

    editNoPlan: (planId: number, attachmentId: number, data: { title: string; body: string }) =>
      request<PlanHandout>(`/prescriptions/${planId}/handouts/${attachmentId}`, {
        method: "PUT",
        body: data,
      }),

    detach: (planId: number, attachmentId: number) =>
      request<void>(`/prescriptions/${planId}/handouts/${attachmentId}`, { method: "DELETE" }),
  },

  // -------------------------------------------------------------- anthropometry
  anthropometry: {
    protocols: () => request<ProtocolInfo[]>("/anthropometry/protocols"),

    list: (patientId: number) =>
      request<Assessment[]>(`/patients/${patientId}/assessments`),

    progress: (patientId: number) =>
      request<Progress>(`/patients/${patientId}/progress`),

    create: (patientId: number, data: AssessmentRequest) =>
      request<Assessment>(`/patients/${patientId}/assessments`, {
        method: "POST",
        body: data,
      }),

    detail: (id: number) => request<Assessment>(`/assessments/${id}`),

    update: (id: number, data: AssessmentRequest) =>
      request<Assessment>(`/assessments/${id}`, { method: "PUT", body: data }),

    remove: (id: number) => request<void>(`/assessments/${id}`, { method: "DELETE" }),

    /** O relatório de evolução, com os gráficos. Precisa de duas avaliações. */
    report: (patientId: number) =>
      download(`/patients/${patientId}/anthropometry-report`),
  },

  // ------------------------------------------------------------------- schedule
  schedule: {
    types: () => request<TypeAppointmentInfo[]>("/schedule/types"),

    forDay: (date: string) => request<ScheduleDay>(`/schedule/day${query({ date })}`),

    inRange: (from: string, to: string, status?: AppointmentStatus) =>
      request<Appointment[]>(`/schedule${query({ from, to, status })}`),

    forPatient: (patientId: number) =>
      request<Appointment[]>(`/schedule/patient/${patientId}`),

    schedule: (data: AppointmentRequest) =>
      request<Appointment>("/schedule", { method: "POST", body: data }),

    reschedule: (id: number, data: AppointmentRequest) =>
      request<Appointment>(`/schedule/${id}`, { method: "PUT", body: data }),

    changeStatus: (id: number, status: AppointmentStatus, reason?: string) =>
      request<Appointment>(`/schedule/${id}/status`, {
        method: "POST",
        body: { status, reason },
      }),

    remove: (id: number) => request<void>(`/schedule/${id}`, { method: "DELETE" }),

    /**
     * Subscription to the schedule from an external calendar.
     *
     * A missing `token` means the address has not been created yet — it is not
     * an error, it is the initial state.
     */
    subscription: () => request<{ token?: string }>("/schedule/subscription"),

    generateSubscription: () =>
      request<{ token: string }>("/schedule/subscription", { method: "POST" }),

    revokeSubscription: () =>
      request<void>("/schedule/subscription", { method: "DELETE" }),
  },

  // -------------------------------------------------------------------- finance
  finance: {
    list: (params: {
      from?: string;
      to?: string;
      type?: TransactionType;
      status?: TransactionStatus;
      patientId?: number;
      page?: number;
      size?: number;
    }) => request<Page<Transaction>>(`/finance/transactions${query(params)}`),

    overdue: (reference?: string) =>
      request<Transaction[]>(`/finance/overdue${query({ reference })}`),

    settle: (from: string, to: string) =>
      request<Summary>(`/finance/summary${query({ from, to })}`),

    create: (data: TransactionRequest) =>
      request<Transaction>("/finance/transactions", { method: "POST", body: data }),

    update: (id: number, data: TransactionRequest) =>
      request<Transaction>(`/finance/transactions/${id}`, { method: "PUT", body: data }),

    pay: (id: number, datePayment?: string) =>
      request<Transaction>(`/finance/transactions/${id}/pay`, {
        method: "POST",
        body: { datePayment },
      }),

    refund: (id: number) =>
      request<Transaction>(`/finance/transactions/${id}/refund`, { method: "POST" }),

    cancel: (id: number) =>
      request<Transaction>(`/finance/transactions/${id}/cancel`, { method: "POST" }),

    receipt: (id: number) => request<Receipt>(`/finance/transactions/${id}/receipt`),

    remove: (id: number) =>
      request<void>(`/finance/transactions/${id}`, { method: "DELETE" }),
  },

  /** A plan opened by the patient: with no credential. */
  publicPlan: (identifier: string) =>
    request<PublicPlan>(`/public/plans/${identifier}`, { withoutAuthentication: true }),
};
