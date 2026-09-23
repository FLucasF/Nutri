import { expect, request, type Page } from "@playwright/test";

import { acharAlimento, criarAvaliacao, criarConta, criarPaciente, type Conta } from "./conta";

/**
 * O que a suíte de aparência precisa além da conta: um consultório com
 * conteúdo suficiente para as telas terem o que desenhar, e uns atalhos para
 * ler o que o navegador calculou (cor de um token, largura da rolagem).
 *
 * Tudo entra pela API — a aparência é o que está sendo olhado, não o fluxo de
 * cadastro. Cada execução monta o próprio cenário; nada aqui depende da conta
 * de demonstração.
 */

const BASE = process.env.E2E_URL ?? "http://localhost:5173";

/** Os tamanhos de tela em que o shell muda de forma (ver shell.css). */
export const LARGURAS = {
  /** > 1180: barra lateral de 240px com rótulos. */
  desktop: { width: 1440, height: 900 },
  /** 901–1180: trilho de ícones de 64px. */
  laptop: { width: 1024, height: 768 },
  /** 601–900: sem barra lateral; app bar + gaveta. */
  tablet: { width: 820, height: 1180 },
  /** ≤ 600: app bar + barra de atalhos no rodapé. */
  celular: { width: 390, height: 844 },
} as const;

/**
 * As opções do celular. `isMobile` + `hasTouch` fazem o Chromium responder
 * "sim" a `(pointer: coarse)`, que é o que liga as regras de alvo de toque de
 * 44px; só mudar o viewport deixaria essas regras desligadas e o teste de
 * toque passaria ou falharia por motivo nenhum.
 */
export const CELULAR = { viewport: LARGURAS.celular, isMobile: true, hasTouch: true } as const;

export type Cenario = {
  paciente: { id: number; name: string };
  plano: { id: number; publicIdentifier: string };
  /** O identificador público, depois de publicado. */
  identificador: string;
};

/**
 * Um paciente com avaliação e um plano publicado com as seis refeições do
 * dia e alguns alimentos — o bastante para o editor, a lista de prescrições
 * e a página pública terem conteúdo real.
 */
export async function montarCenario(conta: Conta): Promise<Cenario> {
  const paciente = await criarPaciente(conta, { name: "Aparência de Teste" });
  await criarAvaliacao(conta, paciente.id);

  const [arroz, feijao, frango, banana, leite] = await Promise.all([
    acharAlimento(conta, "Arroz, integral"),
    acharAlimento(conta, "Feijão, carioca"),
    acharAlimento(conta, "Frango, peito"),
    acharAlimento(conta, "Banana, prata"),
    acharAlimento(conta, "Leite, de vaca"),
  ]);

  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano de aparência",
      patientId: paciente.id,
      method: "FOODS",
      template: false,
      targetEnergyKcal: 2000,
      targetProteinPct: 25,
      targetCarbohydratePct: 50,
      targetFatPct: 25,
      validityStart: "2026-09-01",
      meals: [
        {
          name: "Café da Manhã",
          time: "07:00",
          items: [
            { foodId: leite, quantity: 200 },
            { foodId: banana, quantity: 80, substitutions: [{ foodId: arroz, description: "Arroz", quantity: 50 }] },
          ],
        },
        { name: "Lanche da Manhã", time: "10:00", items: [{ foodId: banana, quantity: 100 }] },
        {
          name: "Almoço",
          time: "12:30",
          items: [
            { foodId: arroz, quantity: 150 },
            { foodId: feijao, quantity: 100 },
            { foodId: frango, quantity: 120 },
          ],
        },
        { name: "Lanche da Tarde", time: "16:00", items: [{ foodId: leite, quantity: 200 }] },
        {
          name: "Jantar",
          time: "19:30",
          items: [
            { foodId: arroz, quantity: 100 },
            { foodId: frango, quantity: 100 },
          ],
        },
        { name: "Ceia", time: "22:00", items: [{ foodId: leite, quantity: 150 }] },
      ],
    },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const plano = await criado.json();

  const publicado = await conta.api.post(`/api/prescriptions/${plano.id}/publish`, {});
  expect(publicado.ok(), await publicado.text()).toBeTruthy();
  const { publicIdentifier } = await publicado.json();

  return { paciente, plano: { id: plano.id, publicIdentifier }, identificador: publicIdentifier };
}

/**
 * Uma recepcionista do mesmo consultório, já com sessão.
 *
 * O assistente é criado pela nutricionista e entra com a senha combinada; o
 * token que volta é o que o teste põe no navegador.
 */
export async function criarAssistente(conta: Conta): Promise<Conta> {
  const email = `e2e-assist-${Date.now()}-${Math.floor(Math.random() * 1e4)}@exemplo.com`;
  const senha = "recepcao2026";

  const criado = await conta.api.post("/api/users", {
    data: { name: "Recepção E2E", email, initialPassword: senha },
  });
  expect(criado.status(), await criado.text()).toBe(201);

  const anonima = await request.newContext({ baseURL: BASE });
  const login = await anonima.post("/api/auth/login", { data: { email, password: senha } });
  expect(login.ok(), await login.text()).toBeTruthy();
  const { token } = await login.json();
  await anonima.dispose();

  const api = await request.newContext({
    baseURL: BASE,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  return { email, senha, token, api };
}

/** Fixa o tema antes da primeira pintura, como o script do index.html espera. */
export async function fixarTema(page: Page, tema: "light" | "dark" | "system") {
  await page.addInitScript((valor) => {
    if (valor === "system") {
      window.localStorage.removeItem("nutriplan.theme");
    } else {
      window.localStorage.setItem("nutriplan.theme", valor);
    }
  }, tema);
}

/**
 * Abre a rota e espera a tela ficar pronta para ser olhada: sem "Carregando…"
 * e com as fontes resolvidas, para que uma medida ou uma foto não pegue o
 * texto ainda na fonte de reserva.
 */
export async function abrir(page: Page, rota: string) {
  await page.goto(rota);
  await expect(page.locator(".loading")).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
  await expect(page.getByText("Página não encontrada.")).toHaveCount(0);
}

/** Quantos pixels a página excede a largura da janela (0 = sem rolagem lateral). */
export async function excessoHorizontal(page: Page): Promise<number> {
  return page.evaluate(() => {
    const raiz = document.documentElement;
    return Math.max(0, raiz.scrollWidth - raiz.clientWidth);
  });
}

/**
 * A cor em que um token resolve, lida de um elemento de prova: a propriedade
 * crua devolve a expressão (`light-dark(...)`), e é o valor já calculado que
 * interessa.
 */
export async function corDoToken(page: Page, token: string): Promise<string> {
  return page.evaluate((nome) => {
    const prova = document.createElement("span");
    prova.style.color = `var(${nome})`;
    prova.style.position = "absolute";
    document.body.appendChild(prova);
    const cor = getComputedStyle(prova).color;
    prova.remove();
    return cor;
  }, token);
}

/**
 * Elementos cujo texto carrega hora ou data — o que muda entre uma execução e
 * outra e não pode entrar numa foto de referência.
 */
export function comHoraOuData(page: Page) {
  return [
    page.locator("time"),
    page.getByText(/\b\d{1,2}\/\d{1,2}(\/\d{2,4})?\b/),
    page.getByText(/\b\d{1,2}:\d{2}\b/),
    page.getByText(/\bsalvo às\b/i),
  ];
}
