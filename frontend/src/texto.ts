/**
 * Concordância de número no texto da interface.
 *
 * Existe para tirar "(s)" da tela. A abreviação escrita não se lê em voz alta e
 * transfere ao leitor um trabalho que o código sabe fazer: quando o sistema já
 * tem o número na mão, ele tem tudo o que precisa para escrever a frase certa.
 */
export function plural(quantidade: number, singular: string, plural: string): string {
  return quantidade === 1 ? singular : plural;
}

/** O mesmo, já com o número na frente: `contar(3, "falta", "faltas")`. */
export function contar(quantidade: number, singular: string, pluralForma: string): string {
  return `${quantidade.toLocaleString("pt-BR")} ${plural(quantidade, singular, pluralForma)}`;
}
