import { ErrorApi } from "./client";

/**
 * Translates a failure into what the person needs to know.
 *
 * Each screen used to have its own fallback sentence — "Falha ao salvar.", "Não
 * foi possível concluir a operação." — and there were thirty of them. They all
 * say something went wrong and none says what, nor what to do next.
 *
 * The rule here is: the message names **what was being attempted**, **what
 * prevented it** and, when there is one, **the next step**. Business rule and
 * validation errors already arrive ready from the server and are passed through
 * as they are — whoever wrote the rule can explain it better than a generic
 * sentence on screen.
 *
 * @param action what was being attempted, in the infinitive and from the point
 *               of view of the person using it: "salvar o paciente", "abrir a
 *               agenda". It goes into the sentence.
 */
export function explainError(e: unknown, action: string): string {
  if (e instanceof ErrorApi) {
    switch (e.status) {
      case 400:
      case 404:
      case 405:
      case 422:
        // The server has already explained it: a business rule, an invalid field,
        // an address that does not exist. Rewriting it here would only move the
        // message away from the rule.
        return e.message;

      case 403:
        return `Seu perfil não permite ${action}. Peça a quem administra o consultório.`;

      case 409:
        return `Não deu para ${action}: já existe um registro com esses dados.`;

      case 413:
        return "O arquivo é grande demais. Envie um menor.";

      case 500:
      case 502:
      case 503:
        return `O servidor não conseguiu ${action}. Tente de novo em instantes; se persistir, o problema é do servidor e não do que você preencheu.`;

      default:
        return e.message || `Não deu para ${action}.`;
    }
  }

  if (e instanceof TypeError) {
    // fetch only rejects with a TypeError when the request never left.
    return `Sem resposta do servidor ao tentar ${action}. Verifique sua conexão e tente de novo — nada foi alterado.`;
  }

  return `Não deu para ${action}.`;
}

/** The per-field errors of a validation failure, ready to stick to the field. */
export function errorsByField(e: unknown): Record<string, string> {
  if (!(e instanceof ErrorApi) || !e.fields?.length) return {};
  const map: Record<string, string> = {};
  for (const c of e.fields) {
    // The first error of each field is enough: two warnings on the same field
    // compete for attention and the second rarely adds anything.
    if (!map[c.field]) map[c.field] = c.message;
  }
  return map;
}

/**
 * The hour of right now, the way the day ruler writes it.
 *
 * "Saved" on its own does not answer the question a person really asks after a
 * long edit — *did my last change go in?*. The hour answers it.
 */
export function nowHour(): string {
  return new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

/**
 * Translates the technical path of a field into where the person should look.
 *
 * The server identifies the field by the structure of the request —
 * `meals[0].items[1].quantity` — which is exact and unreadable. In a plan with
 * six meals, what solves it is "Almoço, 2º alimento", because that is how the
 * meal is labelled on screen.
 *
 * @param mealName returns the name of the meal at that position, or nothing
 *                 when the position no longer exists in what is on screen.
 */
export function whereLook(
  path: string,
  mealName: (index: number) => string | undefined,
): string | null {
  const inItem = /^meals\[(\d+)\]\.items\[(\d+)\]/.exec(path);
  if (inItem) {
    const meal = mealName(Number(inItem[1])) ?? `${Number(inItem[1]) + 1}ª refeição`;
    return `${meal}, ${Number(inItem[2]) + 1}º alimento`;
  }
  const inMeal = /^meals\[(\d+)\]/.exec(path);
  if (inMeal) {
    return mealName(Number(inMeal[1])) ?? `${Number(inMeal[1]) + 1}ª refeição`;
  }
  const LABELS: Record<string, string> = {
    title: "Título do plano",
    method: "Método",
    targetEnergyKcal: "Meta de energia",
    validityStart: "Início da vigência",
    validityEnd: "Fim da vigência",
    handouts: "Orientações gerais",
    internalNotes: "Observações internas",
  };
  return LABELS[path] ?? null;
}
