import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { errorsByField } from "../api/errors";

/**
 * Validation errors stuck to the field that caused them.
 *
 * The server always sent the error per field; it was the screen that joined
 * everything with ". " into a single sentence and showed it at the top of the
 * form. In a form with twenty fields that turns into a hunt: the person reads
 * "O peso deve ser maior que zero" and has to find out for themselves where the
 * weight is.
 *
 * Here each message sits under its own field, the field is marked invalid for
 * screen readers, and the focus goes to the first of them — which is what WCAG
 * asks for in 3.3.1 (identify the error) and 3.3.3 (suggest the correction).
 */
export function useFieldErrors() {
  const [errors, setErrors] = useState<Record<string, string>>({});

  /** The field that should take the focus as soon as the screen shows the error. */
  const focus = useRef<string | null>(null);

  /**
   * Reads the failure and hands the messages out to the fields.
   *
   * @returns true when there was a per-field error — the caller uses that so as
   *          not to also show the general strip, which would repeat the same
   *          information.
   */
  const apply = useCallback((e: unknown) => {
    const map = errorsByField(e);
    setErrors(map);
    focus.current = Object.keys(map)[0] ?? null;
    return Object.keys(map).length > 0;
  }, []);

  /**
   * The focus can only go to the field after the invalid marking exists in the
   * document.
   *
   * Trying it in the same step as `apply` does not work: React has not
   * rendered yet there, and the screen reader would read the field without the
   * message that `aria-describedby` has just pointed at. The effect runs after
   * the paint, which is when both things are already in place.
   */
  useEffect(() => {
    const field = focus.current;
    if (!field) return;
    focus.current = null;
    const target = document.querySelector<HTMLElement>(
      `[name="${CSS.escape(field)}"], #${CSS.escape(field)}`,
    );
    if (!target) return;
    target.focus({ preventScroll: true });
    target.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [errors]);

  const clear = useCallback(() => setErrors({}), []);

  /** The warning disappears as soon as the person touches the field. */
  const clearField = useCallback((field: string) => {
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  }, []);

  const props = useCallback(
    (field: string) => ({
      "aria-invalid": errors[field] ? (true as const) : undefined,
      "aria-describedby": errors[field] ? `erro-${field}` : undefined,
      onInput: () => clearField(field),
    }),
    [errors, clearField],
  );

  return useMemo(
    () => ({ errors, apply, clear, clearField, props, from: (c: string) => errors[c] }),
    [errors, apply, clear, clearField, props],
  );
}

/** The message under the field. Nothing is drawn when the field is right. */
export function FieldError({ field, errors }: { field: string; errors: Record<string, string> }) {
  const message = errors[field];
  if (!message) return null;
  return (
    <span className="field-message" id={`erro-${field}`}>
      {message}
    </span>
  );
}
