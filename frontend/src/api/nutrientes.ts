/**
 * Rótulos e unidades dos nutrientes, para exibição.
 *
 * Espelha o catálogo do backend. A ordem aqui é a ordem de exibição, e o
 * agrupamento permite mostrar só os macronutrientes numa tela compacta e a
 * composição inteira quando houver espaço.
 */

export type GrupoNutriente = "energia" | "macro" | "lipidio" | "mineral" | "vitamina" | "outro";

export interface DefinicaoNutriente {
  chave: string;
  rotulo: string;
  unidade: string;
  grupo: GrupoNutriente;
}

export const NUTRIENTES: DefinicaoNutriente[] = [
  { chave: "energiaKcal", rotulo: "Energia", unidade: "kcal", grupo: "energia" },
  { chave: "energiaKj", rotulo: "Energia", unidade: "kJ", grupo: "energia" },

  { chave: "proteinaG", rotulo: "Proteínas", unidade: "g", grupo: "macro" },
  { chave: "carboidratoG", rotulo: "Carboidratos", unidade: "g", grupo: "macro" },
  { chave: "acucaresG", rotulo: "Açúcares totais", unidade: "g", grupo: "macro" },
  { chave: "acucaresAdicionadosG", rotulo: "Açúcares adicionados", unidade: "g", grupo: "macro" },
  { chave: "fibraG", rotulo: "Fibra alimentar", unidade: "g", grupo: "macro" },

  { chave: "lipideosG", rotulo: "Gorduras totais", unidade: "g", grupo: "lipidio" },
  { chave: "gordurasSaturadasG", rotulo: "Gorduras saturadas", unidade: "g", grupo: "lipidio" },
  { chave: "gordurasTransG", rotulo: "Gorduras trans", unidade: "g", grupo: "lipidio" },
  { chave: "gordurasMonoinsaturadasG", rotulo: "Monoinsaturadas", unidade: "g", grupo: "lipidio" },
  { chave: "gordurasPoliinsaturadasG", rotulo: "Poli-insaturadas", unidade: "g", grupo: "lipidio" },
  { chave: "colesterolMg", rotulo: "Colesterol", unidade: "mg", grupo: "lipidio" },

  { chave: "sodioMg", rotulo: "Sódio", unidade: "mg", grupo: "mineral" },
  { chave: "calcioMg", rotulo: "Cálcio", unidade: "mg", grupo: "mineral" },
  { chave: "ferroMg", rotulo: "Ferro", unidade: "mg", grupo: "mineral" },
  { chave: "magnesioMg", rotulo: "Magnésio", unidade: "mg", grupo: "mineral" },
  { chave: "fosforoMg", rotulo: "Fósforo", unidade: "mg", grupo: "mineral" },
  { chave: "potassioMg", rotulo: "Potássio", unidade: "mg", grupo: "mineral" },
  { chave: "zincoMg", rotulo: "Zinco", unidade: "mg", grupo: "mineral" },
  { chave: "cobreMg", rotulo: "Cobre", unidade: "mg", grupo: "mineral" },
  { chave: "manganesMg", rotulo: "Manganês", unidade: "mg", grupo: "mineral" },

  { chave: "vitaminaCMg", rotulo: "Vitamina C", unidade: "mg", grupo: "vitamina" },
  { chave: "tiaminaMg", rotulo: "Tiamina (B1)", unidade: "mg", grupo: "vitamina" },
  { chave: "riboflavinaMg", rotulo: "Riboflavina (B2)", unidade: "mg", grupo: "vitamina" },
  { chave: "niacinaMg", rotulo: "Niacina (B3)", unidade: "mg", grupo: "vitamina" },
  { chave: "piridoxinaMg", rotulo: "Piridoxina (B6)", unidade: "mg", grupo: "vitamina" },
  { chave: "retinolMcg", rotulo: "Retinol", unidade: "mcg", grupo: "vitamina" },
  { chave: "reMcg", rotulo: "Equiv. de retinol", unidade: "mcg", grupo: "vitamina" },
  { chave: "raeMcg", rotulo: "Equiv. de atividade de retinol", unidade: "mcg", grupo: "vitamina" },

  { chave: "umidadePct", rotulo: "Umidade", unidade: "%", grupo: "outro" },
  { chave: "cinzasG", rotulo: "Cinzas", unidade: "g", grupo: "outro" },
];

export const MACROS_PRINCIPAIS = [
  "energiaKcal",
  "proteinaG",
  "carboidratoG",
  "lipideosG",
  "fibraG",
  "sodioMg",
];

/**
 * Cores da distribuição energética.
 *
 * Apontam para tokens do tema em vez de trazerem o valor: as três faixas ficam
 * lado a lado numa barra, e no tema escuro elas precisam de outra claridade
 * para continuarem se separando.
 */
export const CORES_MACRO = {
  proteina: "var(--macro-proteina)",
  carboidrato: "var(--macro-carboidrato)",
  lipideo: "var(--macro-lipideo)",
} as const;

/**
 * Formata um valor de nutriente.
 *
 * `undefined` significa nutriente não determinado na fonte, e é exibido como
 * "não informado" — nunca como zero, que afirmaria ausência do nutriente.
 */
export function formatarNutriente(valor: number | undefined, unidade: string): string {
  if (valor === undefined || valor === null) {
    return "não informado";
  }
  const casas = valor >= 100 ? 0 : valor >= 10 ? 1 : 2;
  return `${valor.toFixed(casas).replace(".", ",")} ${unidade}`;
}

export function rotuloDe(chave: string): string {
  return NUTRIENTES.find((n) => n.chave === chave)?.rotulo ?? chave;
}
