/**
 * Labels and units of the nutrients, for display.
 *
 * It mirrors the backend catalog. The order here is the display order, and the
 * grouping makes it possible to show only the macronutrients on a compact
 * screen and the whole composition when there is room.
 */

export type GroupNutrient = "energy" | "macro" | "lipidio" | "mineral" | "vitamin" | "other";

export interface DefinitionNutrient {
  key: string;
  label: string;
  unit: string;
  group: GroupNutrient;
}

export const NUTRIENTS: DefinitionNutrient[] = [
  { key: "energyKcal", label: "Energia", unit: "kcal", group: "energy" },
  { key: "energyKj", label: "Energia", unit: "kJ", group: "energy" },

  { key: "proteinG", label: "Proteínas", unit: "g", group: "macro" },
  { key: "carbohydrateG", label: "Carboidratos", unit: "g", group: "macro" },
  { key: "sugarsG", label: "Açúcares totais", unit: "g", group: "macro" },
  { key: "sugarsAddedG", label: "Açúcares adicionados", unit: "g", group: "macro" },
  { key: "fiberG", label: "Fibra alimentar", unit: "g", group: "macro" },

  { key: "fatG", label: "Gorduras totais", unit: "g", group: "lipidio" },
  { key: "fatSaturatedG", label: "Gorduras saturadas", unit: "g", group: "lipidio" },
  { key: "fatTransG", label: "Gorduras trans", unit: "g", group: "lipidio" },
  { key: "fatMonounsaturatedG", label: "Monoinsaturadas", unit: "g", group: "lipidio" },
  { key: "fatPolyunsaturatedG", label: "Poli-insaturadas", unit: "g", group: "lipidio" },
  { key: "cholesterolMg", label: "Colesterol", unit: "mg", group: "lipidio" },

  { key: "sodiumMg", label: "Sódio", unit: "mg", group: "mineral" },
  { key: "calciumMg", label: "Cálcio", unit: "mg", group: "mineral" },
  { key: "ironMg", label: "Ferro", unit: "mg", group: "mineral" },
  { key: "magnesiumMg", label: "Magnésio", unit: "mg", group: "mineral" },
  { key: "phosphorusMg", label: "Fósforo", unit: "mg", group: "mineral" },
  { key: "potassiumMg", label: "Potássio", unit: "mg", group: "mineral" },
  { key: "zincMg", label: "Zinco", unit: "mg", group: "mineral" },
  { key: "copperMg", label: "Cobre", unit: "mg", group: "mineral" },
  { key: "manganeseMg", label: "Manganês", unit: "mg", group: "mineral" },

  { key: "vitaminCMg", label: "Vitamina C", unit: "mg", group: "vitamin" },
  { key: "thiaminMg", label: "Tiamina (B1)", unit: "mg", group: "vitamin" },
  { key: "riboflavinMg", label: "Riboflavina (B2)", unit: "mg", group: "vitamin" },
  { key: "niacinMg", label: "Niacina (B3)", unit: "mg", group: "vitamin" },
  { key: "pyridoxineMg", label: "Piridoxina (B6)", unit: "mg", group: "vitamin" },
  { key: "retinolMcg", label: "Retinol", unit: "mcg", group: "vitamin" },
  { key: "reMcg", label: "Equiv. de retinol", unit: "mcg", group: "vitamin" },
  { key: "raeMcg", label: "Equiv. de atividade de retinol", unit: "mcg", group: "vitamin" },

  { key: "moisturePct", label: "Umidade", unit: "%", group: "other" },
  { key: "ashG", label: "Cinzas", unit: "g", group: "other" },
];

export const MACROS_PRINCIPAIS = [
  "energyKcal",
  "proteinG",
  "carbohydrateG",
  "fatG",
  "fiberG",
  "sodiumMg",
];

/**
 * Colors of the energy distribution.
 *
 * They point at theme tokens instead of carrying the value: the three tracks
 * sit side by side in one bar, and in the dark theme they need a different
 * lightness to go on separating from each other.
 */
export const COLORS_MACRO = {
  protein: "var(--macro-protein)",
  carbohydrate: "var(--macro-carbohydrate)",
  lipid: "var(--macro-lipid)",
} as const;

/**
 * Formats a nutrient value.
 *
 * `undefined` means a nutrient not determined in the source, and it is shown as
 * "não informado" — never as zero, which would assert the absence of the
 * nutrient.
 */
export function formatNutrient(value: number | undefined, unit: string): string {
  if (value === undefined || value === null) {
    return "não informado";
  }
  const places = value >= 100 ? 0 : value >= 10 ? 1 : 2;
  return `${value.toFixed(places).replace(".", ",")} ${unit}`;
}

export function labelDe(key: string): string {
  return NUTRIENTS.find((n) => n.key === key)?.label ?? key;
}
