import type { QuestionType, QuestionnaireQuestion } from "../api/types";

/** Como as alternativas marcadas de uma múltipla escolha viajam numa string só. */
export const CHOICES_SEPARATOR = "; ";

export function splitChoices(value: string): string[] {
  return value
    .split(";")
    .map((part) => part.trim())
    .filter(Boolean);
}

export function joinChoices(choices: string[]): string {
  return choices.join(CHOICES_SEPARATOR);
}

/** O controle que cada tipo de pergunta pede; a seção não tem controle. */
export function kindOf(type: QuestionType): "text" | "paragraph" | "number" | "date" | "single" | "multiple" | "section" {
  switch (type) {
    case "PARAGRAPH":
      return "paragraph";
    case "NUMBER":
      return "number";
    case "DATE":
      return "date";
    case "CHOICE_SINGLE":
      return "single";
    case "MULTIPLE":
      return "multiple";
    case "SECTION":
      return "section";
    default:
      return "text";
  }
}

/** Até este tanto de alternativas, botões de opção; acima, uma lista. */
const RADIO_LIMIT = 5;

/**
 * O controle de uma pergunta, pelo tipo dela.
 *
 * Um componente só para o formulário do paciente e para a anamnese na
 * consulta: quem respondeu pelo link e quem preenche no consultório veem o
 * mesmo controle, e a resposta viaja com o mesmo formato — uma string, com
 * as alternativas da múltipla escolha separadas por ponto e vírgula.
 */
export function QuestionInput({
  question: q,
  id,
  value,
  onChange,
  describedBy,
  large = false,
}: {
  question: Pick<QuestionnaireQuestion, "type" | "options" | "required" | "statement">;
  id: string;
  value: string;
  onChange: (value: string) => void;
  describedBy?: string;
  /** No formulário do paciente a fonte sobe para 16px, senão o iOS amplia. */
  large?: boolean;
}) {
  const kind = kindOf(q.type);

  if (kind === "section") {
    return null;
  }

  if (kind === "multiple") {
    const chosen = splitChoices(value);
    return (
      <fieldset className="choice-group" aria-describedby={describedBy}>
        <legend className="visually-hidden">{q.statement}</legend>
        {q.options.map((o) => {
          const on = chosen.includes(o.label);
          return (
            <label key={o.label} className="choice-option">
              <input
                type="checkbox"
                checked={on}
                onChange={(e) =>
                  onChange(
                    joinChoices(
                      e.target.checked
                        ? [...q.options.map((x) => x.label).filter((l) => l === o.label || chosen.includes(l))]
                        : chosen.filter((l) => l !== o.label),
                    ),
                  )
                }
              />
              <span>{o.label}</span>
            </label>
          );
        })}
      </fieldset>
    );
  }

  if (kind === "single") {
    if (q.options.length > RADIO_LIMIT) {
      return (
        <select
          id={id}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={q.required}
          aria-describedby={describedBy}
        >
          <option value="">Selecione…</option>
          {q.options.map((o) => (
            <option key={o.label} value={o.label}>
              {o.label}
            </option>
          ))}
        </select>
      );
    }
    return (
      <fieldset className="choice-group" aria-describedby={describedBy}>
        <legend className="visually-hidden">{q.statement}</legend>
        {q.options.map((o) => (
          <label key={o.label} className="choice-option">
            <input
              type="radio"
              name={id}
              value={o.label}
              checked={value === o.label}
              required={q.required}
              onChange={() => onChange(o.label)}
            />
            <span>{o.label}</span>
          </label>
        ))}
      </fieldset>
    );
  }

  if (kind === "number") {
    // Text with a decimal keyboard, not type=number: "1,5" is how the
    // patient writes it, and a number input would refuse the comma.
    return (
      <input
        id={id}
        inputMode="decimal"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={q.required}
        aria-describedby={describedBy}
        className={large ? "question-control" : undefined}
      />
    );
  }

  if (kind === "date") {
    return (
      <input
        id={id}
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={q.required}
        aria-describedby={describedBy}
      />
    );
  }

  if (kind === "paragraph") {
    return (
      <textarea
        id={id}
        rows={large ? 5 : 4}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={q.required}
        aria-describedby={describedBy}
      />
    );
  }

  return (
    <input
      id={id}
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      required={q.required}
      aria-describedby={describedBy}
      maxLength={500}
    />
  );
}
