/**
 * Number agreement in interface text.
 *
 * It exists to get "(s)" off the screen. The written abbreviation cannot be
 * read aloud and hands the reader work the code knows how to do: when the
 * system already has the number in hand, it has everything it needs to write
 * the right sentence.
 */
export function plural(quantity: number, singular: string, plural: string): string {
  return quantity === 1 ? singular : plural;
}

/** The same, already with the number in front: `count(3, "falta", "faltas")`. */
/** "R$ 1.234,56" */
export function currency(value: number): string {
  return value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

export function count(quantity: number, singular: string, pluralForm: string): string {
  return `${quantity.toLocaleString("pt-BR")} ${plural(quantity, singular, pluralForm)}`;
}
