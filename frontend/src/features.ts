/**
 * Áreas que existem no sistema mas não aparecem na tela.
 *
 * O cliente não atende gestante e não usa prescrição por equivalentes nem
 * qualitativa. Nenhuma das duas foi apagada: o código está pronto, coberto por
 * teste e apagá-lo custa mais do que mantê-lo fora do caminho — e uma delas ele
 * dispensou com um "se quiser deixar, beleza", não com um pedido de remoção.
 *
 * Ligar de volta é trocar `false` por `true` aqui. Se alguma delas for
 * descartada de vez, o caminho é migration e remoção do módulo, não esta
 * constante.
 */
export const AREAS_HIDDEN = {
  /** Acompanhamento gestacional: semana, peso pré-gestação e faixa do IOM 2009. */
  gestation: false,

  /** Métodos de prescrição por equivalentes e qualitativo. */
  otherPrescriptionMethods: false,
} as const;
