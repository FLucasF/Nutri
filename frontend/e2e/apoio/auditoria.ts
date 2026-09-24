import { expect, type Page } from "@playwright/test";

/**
 * A auditoria de uma tela: o que não cabe nela.
 *
 * Quatro coisas que o olho pega numa foto e uma asserção não pegava:
 *
 *  - rolagem lateral: a página é mais larga que a janela;
 *  - fora do quadro: um elemento visível começa antes da borda esquerda ou
 *    termina depois da direita (fora de um contêiner que role de propósito);
 *  - texto vazando: o texto de um elemento é mais largo que o elemento e
 *    escapa por cima do vizinho;
 *  - texto cortado: o texto é mais largo que o elemento e some, sem as
 *    reticências que diriam que foi cortado de propósito.
 *
 * Tudo se lê da tela como ela está, depois que as fontes chegaram e as
 * animações terminaram — uma medida no meio de uma transição mediria a
 * transição, e não a tela.
 */

export type Problema = {
  tipo: "rolagem-lateral" | "fora-do-quadro" | "texto-vazando" | "texto-cortado";
  alvo: string;
  detalhe: string;
};

/** Espera a tela ficar parada: sem "Carregando…", com as fontes e sem animação em curso. */
export async function prontaParaOlhar(page: Page) {
  await expect(page.locator(".loading")).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() =>
    Promise.all(document.getAnimations().map((a) => a.finished.catch(() => undefined))),
  );
  // Um respiro para o que renderiza depois da resposta chegar (gráficos, listas).
  await page.waitForTimeout(150);
}

export async function auditarTela(page: Page): Promise<Problema[]> {
  await prontaParaOlhar(page);
  return page.evaluate(() => {
    const problemas: { tipo: string; alvo: string; detalhe: string }[] = [];
    const raiz = document.documentElement;
    const largura = raiz.clientWidth;

    if (raiz.scrollWidth > largura + 1) {
      problemas.push({
        tipo: "rolagem-lateral",
        alvo: "html",
        detalhe: `a página tem ${raiz.scrollWidth}px numa janela de ${largura}px`,
      });
    }

    const descrever = (el: Element) => {
      const tag = el.tagName.toLowerCase();
      const id = el.id ? `#${el.id}` : "";
      const classes =
        typeof el.className === "string" && el.className.trim()
          ? "." + el.className.trim().split(/\s+/).slice(0, 3).join(".")
          : "";
      const texto = (el.textContent ?? "").trim().replace(/\s+/g, " ").slice(0, 48);
      return `${tag}${id}${classes}${texto ? ` “${texto}”` : ""}`;
    };

    /** Oculto por si ou por um ancestral: não conta. */
    const oculto = (el: Element) => {
      let n: Element | null = el;
      while (n && n !== document.body) {
        const cs = getComputedStyle(n);
        // Também o padrão "só para leitor de tela": caixa de 1px recortada.
        const recortado =
          /rect\(0(px)?[,\s]+0(px)?[,\s]+0(px)?[,\s]+0(px)?\)/.test(cs.clip) ||
          (cs.overflow === "hidden" && n.getBoundingClientRect().width <= 1);
        if (
          cs.display === "none" ||
          cs.visibility === "hidden" ||
          cs.opacity === "0" ||
          n.getAttribute("aria-hidden") === "true" ||
          n.hasAttribute("inert") ||
          n.classList.contains("visually-hidden") ||
          recortado
        ) {
          return true;
        }
        n = n.parentElement;
      }
      return false;
    };

    /** Dentro de algo que rola de lado de propósito (tabela larga, chips). */
    const dentroDeRolagem = (el: Element) => {
      let n = el.parentElement;
      while (n && n !== document.body) {
        const cs = getComputedStyle(n);
        if (/(auto|scroll)/.test(cs.overflowX)) return true;
        n = n.parentElement;
      }
      return false;
    };

    const ignorar = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE"]);
    const semTexto = new Set(["INPUT", "SELECT", "TEXTAREA", "OPTION", "CANVAS", "IMG", "VIDEO", "svg"]);

    for (const el of Array.from(document.body.querySelectorAll("*"))) {
      if (ignorar.has(el.tagName)) continue;
      if (el instanceof SVGElement) continue;
      if (oculto(el)) continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;

      const rolavel = dentroDeRolagem(el);
      if (!rolavel && (r.right > largura + 1 || r.left < -1)) {
        problemas.push({
          tipo: "fora-do-quadro",
          alvo: descrever(el),
          detalhe: `vai de ${Math.round(r.left)} a ${Math.round(r.right)}px numa janela de ${largura}px`,
        });
      }

    }

    /*
     * Texto vazando ou cortado: mede-se o texto em si, e não a caixa. Um
     * elemento com um enfeite posicionado no canto (a bolinha de "não salvo")
     * tem scrollWidth maior que a caixa sem que letra nenhuma tenha saído do
     * lugar; a régua certa é o retângulo do nó de texto contra a caixa que o
     * contém.
     */
    const percorredor = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let no: Node | null;
    while ((no = percorredor.nextNode())) {
      if (!(no.textContent ?? "").trim()) continue;
      const pai = no.parentElement;
      if (!pai || ignorar.has(pai.tagName) || semTexto.has(pai.tagName)) continue;
      if (oculto(pai) || dentroDeRolagem(pai)) continue;
      let caixa: Element | null = pai;
      while (caixa && caixa !== document.body) {
        const d = getComputedStyle(caixa).display;
        if (d !== "inline" && d !== "contents") break;
        caixa = caixa.parentElement;
      }
      if (!caixa) continue;
      const faixa = document.createRange();
      faixa.selectNodeContents(no);
      const t = faixa.getBoundingClientRect();
      if (t.width === 0 || t.height === 0) continue;
      const b = caixa.getBoundingClientRect();
      const excesso = Math.max(t.right - b.right, b.left - t.left);
      if (excesso <= 1) continue;
      const cs = getComputedStyle(caixa);
      const trecho = (no.textContent ?? "").trim().replace(/\s+/g, " ").slice(0, 48);
      if (cs.overflowX === "visible") {
        problemas.push({
          tipo: "texto-vazando",
          alvo: `${descrever(caixa)} › “${trecho}”`,
          detalhe: `o texto passa ${Math.round(excesso)}px da caixa`,
        });
      } else if (cs.textOverflow !== "ellipsis") {
        problemas.push({
          tipo: "texto-cortado",
          alvo: `${descrever(caixa)} › “${trecho}”`,
          detalhe: `o texto passa ${Math.round(excesso)}px da caixa e some sem reticências`,
        });
      }
    }
    return problemas as Problema[];
  });
}

/**
 * Registra os erros do console e os erros de página desde já. O que volta é
 * a lista no momento em que se pergunta.
 */
export function vigiarConsole(page: Page): () => string[] {
  const erros: string[] = [];
  page.on("pageerror", (e) => erros.push(`pageerror: ${e.message}`));
  page.on("console", (msg) => {
    if (msg.type() === "error") erros.push(`console: ${msg.text()}`);
  });
  return () => [...erros];
}

/** A asserção que os testes de tela fazem: nada fora do lugar, nada no console. */
export async function esperarTelaLimpa(page: Page, rotulo: string, erros: () => string[]) {
  const problemas = await auditarTela(page);
  expect(
    problemas,
    `${rotulo}: ${problemas.length} problema(s) de enquadramento\n${JSON.stringify(problemas, null, 2)}`,
  ).toEqual([]);
  expect(erros(), `${rotulo}: erros no console`).toEqual([]);
}
