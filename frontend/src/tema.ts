/**
 * Escolha de tema, guardada no próprio navegador.
 *
 * São três estados e não dois. Um interruptor claro/escuro obriga o usuário a
 * fixar um dos dois para sempre; "sistema" é o padrão e precisa continuar
 * disponível para quem quiser voltar a acompanhar o aparelho — que troca
 * sozinho ao anoitecer, em boa parte dos celulares.
 *
 * O tema em si mora no atributo `data-tema` do elemento raiz, lido pelo CSS
 * junto com `color-scheme`. Aqui só se decide qual é.
 */
export type Tema = "sistema" | "claro" | "escuro";

/** Também usada pelo script no index.html, que aplica o tema antes da pintura. */
export const CHAVE_TEMA = "nutriplan.tema";

export const TEMAS: { valor: Tema; rotulo: string }[] = [
  { valor: "sistema", rotulo: "Sistema" },
  { valor: "claro", rotulo: "Claro" },
  { valor: "escuro", rotulo: "Escuro" },
];

export function temaGuardado(): Tema {
  try {
    const salvo = localStorage.getItem(CHAVE_TEMA);
    return salvo === "claro" || salvo === "escuro" ? salvo : "sistema";
  } catch {
    // Navegação privativa ou armazenamento bloqueado: segue o sistema.
    return "sistema";
  }
}

export function aplicarTema(tema: Tema): void {
  const raiz = document.documentElement;
  if (tema === "sistema") {
    raiz.removeAttribute("data-tema");
  } else {
    raiz.setAttribute("data-tema", tema);
  }
  try {
    if (tema === "sistema") {
      localStorage.removeItem(CHAVE_TEMA);
    } else {
      localStorage.setItem(CHAVE_TEMA, tema);
    }
  } catch {
    // Sem persistência a escolha vale só para esta aba, o que é melhor que falhar.
  }
}
