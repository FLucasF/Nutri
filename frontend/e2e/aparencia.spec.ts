import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

import { criarConta, entrar, type Conta } from "./apoio/conta";
import {
  CELULAR,
  LARGURAS,
  abrir,
  comHoraOuData,
  corDoToken,
  criarAssistente,
  excessoHorizontal,
  fixarTema,
  montarCenario,
  type Cenario,
} from "./apoio/aparencia";

/**
 * A aparência do NutriPlan — o que o cliente pediu para ver "se está se
 * comportando como você imagina".
 *
 * Os outros arquivos verificam o que o sistema faz; este verifica como ele se
 * mostra: em que largura a barra lateral vira trilho, e o trilho vira gaveta;
 * se nada vaza para o lado no celular; se as cores são as do contrato
 * (scratchpad/spec-design.md) nos dois temas; se o dedo alcança os botões; se
 * o foco aparece; se o contraste passa; e umas fotos de referência para as
 * regressões que nenhuma asserção enumera.
 *
 * Cada teste lê o cenário e não escreve nele — por isso um cenário só serve
 * a todos, montado uma vez pela API.
 */

let conta: Conta;
let cenario: Cenario;

test.beforeAll(async () => {
  conta = await criarConta("Nutri da Aparência");
  cenario = await montarCenario(conta);
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

/** As rotas internas que têm de caber na tela, com o cenário já no lugar. */
function rotasInternas(): string[] {
  const p = cenario.paciente.id;
  return [
    "/patients",
    `/patients/${p}`,
    `/patients/${p}/anthropometry`,
    `/patients/${p}/labtests`,
    `/patients/${p}/anamneses`,
    `/patients/${p}/energy`,
    "/schedule",
    "/finance",
    "/foods",
    "/handouts",
    "/questionnaires",
    "/prescriptions",
    `/prescriptions/${cenario.plano.id}`,
  ];
}

/* ===================================================== 1. estados do shell */

test.describe("shell no desktop (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  test("a barra lateral tem 240px e mostra os rótulos", async ({ page }) => {
    await abrir(page, "/patients");

    const barra = page.locator(".app > .sidebar");
    await expect(barra).toBeVisible();
    const caixa = await barra.boundingBox();
    expect(caixa?.width, "largura da barra lateral").toBe(240);

    const nav = page.getByRole("navigation", { name: "Principal" });
    const rotulo = nav.locator(".nav-label").first();
    await expect(rotulo).toHaveText("Pacientes");
    const caixaRotulo = await rotulo.boundingBox();
    expect(caixaRotulo?.width ?? 0, "o rótulo precisa ocupar espaço de verdade").toBeGreaterThan(20);
  });
});

test.describe("shell no laptop (1024)", () => {
  test.use({ viewport: LARGURAS.laptop });

  test("a barra lateral vira um trilho de 64px sem rótulos visíveis", async ({ page }) => {
    await abrir(page, "/patients");

    const barra = page.locator(".app > .sidebar");
    await expect(barra).toBeVisible();
    const caixa = await barra.boundingBox();
    expect(caixa?.width, "largura do trilho").toBe(64);

    // O rótulo continua no DOM para o leitor de tela; para os olhos ele cabe
    // num pixel. O Playwright chama de visível qualquer caixa não vazia, por
    // isso a medida é feita à mão.
    const nav = page.getByRole("navigation", { name: "Principal" });
    const rotulos = nav.locator(".nav-label");
    expect(await rotulos.count()).toBeGreaterThan(0);
    for (const rotulo of await rotulos.all()) {
      const caixaRotulo = await rotulo.boundingBox();
      expect(caixaRotulo?.width ?? 0, "rótulo do trilho visível").toBeLessThanOrEqual(1);
    }
    // E o item ainda se apresenta ao leitor de tela pelo nome.
    await expect(nav.getByRole("link", { name: "Pacientes" })).toBeVisible();
  });
});

test.describe("shell no tablet (820)", () => {
  test.use({ viewport: LARGURAS.tablet });

  test("sem barra lateral; o botão do app bar abre a gaveta, Escape fecha e devolve o foco", async ({
    page,
  }) => {
    await abrir(page, "/patients");

    await expect(page.locator(".app > .sidebar")).toBeHidden();

    const abrirMenu = page.getByRole("button", { name: "Abrir menu" });
    await expect(abrirMenu).toBeVisible();
    await abrirMenu.click();

    const gaveta = page.getByRole("dialog", { name: "Menu" });
    await expect(gaveta).toBeVisible();
    await expect(gaveta.getByRole("link", { name: "Pacientes" })).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(gaveta).toBeHidden();
    await expect(abrirMenu, "o foco tem de voltar para quem abriu a gaveta").toBeFocused();
  });
});

test.describe("shell no celular (390)", () => {
  test.use(CELULAR);

  test("a barra de atalhos aparece com cinco itens", async ({ page }) => {
    await abrir(page, "/patients");

    const atalhos = page.getByRole("navigation", { name: "Atalhos" });
    await expect(atalhos).toBeVisible();
    await expect(atalhos.locator("a, button")).toHaveCount(5);
    await expect(atalhos.getByRole("link", { name: "Pacientes" })).toBeVisible();
    await expect(atalhos.getByRole("button", { name: "Mais" })).toBeVisible();
  });

  test("para a recepção a barra de atalhos mostra só Pacientes, Agenda e Mais", async ({ page }) => {
    // O contrato (spec-design.md, "≤600 phone") lista três itens para o papel
    // ASSISTANT: Pacientes, Agenda, Mais — a recepção não abre prescrições
    // nem alimentos.
    const recepcao = await criarAssistente(conta);
    try {
      await entrar(page, recepcao);
      await abrir(page, "/patients");

      const atalhos = page.getByRole("navigation", { name: "Atalhos" });
      await expect(atalhos).toBeVisible();
      await expect(atalhos.locator("a, button")).toHaveCount(3);
      await expect(atalhos.getByRole("link", { name: "Prescrições" })).toHaveCount(0);
      await expect(atalhos.getByRole("button", { name: "Mais" })).toBeVisible();
    } finally {
      await recepcao.api.dispose();
    }
  });
});

/* ===================================================== 2. rota focada */

test.describe("rota focada no celular (390)", () => {
  test.use(CELULAR);

  test("o editor de plano toma a tela: sem atalhos, sem app bar, cabeçalho fixo com Voltar", async ({
    page,
  }) => {
    await abrir(page, "/prescriptions/new");
    await expect(page.getByRole("heading", { name: "Novo plano" })).toBeVisible();

    await expect(page.getByRole("navigation", { name: "Atalhos" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Abrir menu" })).toHaveCount(0);

    const cabecalho = page.locator(".header-page.sticky");
    await expect(cabecalho).toBeVisible();
    await expect(cabecalho).toHaveCSS("position", "sticky");

    const voltar = cabecalho
      .getByRole("link", { name: "Voltar", exact: true })
      .or(cabecalho.getByRole("button", { name: "Voltar", exact: true }));
    await expect(voltar).toBeVisible();
  });
});

/* ===================================================== 3. sem rolagem lateral */

for (const [nome, largura] of [
  ["celular", CELULAR] as const,
  ["tablet", { viewport: LARGURAS.tablet }] as const,
]) {
  test.describe(`sem rolagem lateral no ${nome}`, () => {
    test.use(largura);

    // As rotas dependem do cenário, que só existe depois do beforeAll; por
    // isso os nomes dos testes carregam o padrão da rota e não o id.
    const padroes = [
      "/patients",
      "/patients/:id",
      "/patients/:id/anthropometry",
      "/patients/:id/labtests",
      "/patients/:id/anamneses",
      "/patients/:id/energy",
      "/schedule",
      "/finance",
      "/foods",
      "/handouts",
      "/questionnaires",
      "/prescriptions",
      "/prescriptions/:id",
    ];

    padroes.forEach((padrao, indice) => {
      test(`${padrao} cabe na largura da tela`, async ({ page }) => {
        await abrir(page, rotasInternas()[indice]);
        expect(await excessoHorizontal(page), `${padrao} excede a largura da janela`).toBe(0);
      });
    });

    test("/plan/:identifier (pública) cabe na largura da tela", async ({ page }) => {
      await abrir(page, `/plan/${cenario.identificador}`);
      await expect(page.getByText("Plano de aparência")).toBeVisible();
      expect(await excessoHorizontal(page), "o plano do paciente excede a largura").toBe(0);
    });
  });
}

/* ===================================================== 4. tokens aplicados */

test.describe("tokens aplicados (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  test("no tema claro, --primary e o fundo são os do contrato", async ({ page }) => {
    await fixarTema(page, "light");
    await abrir(page, "/patients");

    expect(await corDoToken(page, "--primary")).toBe("rgb(18, 122, 91)");
    await expect(page.locator("body")).toHaveCSS("background-color", "rgb(246, 245, 242)");
  });

  test("no tema escuro, --primary e o fundo são os do contrato", async ({ page }) => {
    await fixarTema(page, "dark");
    await abrir(page, "/patients");

    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    expect(await corDoToken(page, "--primary")).toBe("rgb(63, 191, 143)");
    await expect(page.locator("body")).toHaveCSS("background-color", "rgb(18, 20, 17)");
  });

  test("as três famílias tipográficas estão nos lugares certos", async ({ page }) => {
    await abrir(page, `/prescriptions/${cenario.plano.id}`);

    await expect(page.locator("h1").first()).toHaveCSS("font-family", /Instrument Sans/);
    await expect(page.locator("body")).toHaveCSS("font-family", /Inter/);

    // O editor tem leituras (kcal, "P · C · G") em DM Mono; alguma tem de existir.
    const leitura = page.locator(".readout").first();
    await expect(leitura, "o editor precisa de ao menos um .readout").toBeAttached();
    await expect(leitura).toHaveCSS("font-family", /DM Mono/);
  });

  test("nenhuma célula de tabela usa fonte mono", async ({ page }) => {
    // Tabelas são Inter com algarismos tabulares; a mono é só para leituras.
    for (const rota of ["/patients", "/prescriptions", `/patients/${cenario.paciente.id}`]) {
      await abrir(page, rota);
      const celulasMono = await page.evaluate(() =>
        Array.from(document.querySelectorAll("td"))
          .filter((td) => /mono/i.test(getComputedStyle(td).fontFamily))
          .map((td) => td.textContent?.trim().slice(0, 40) ?? ""),
      );
      expect(celulasMono, `células em mono em ${rota}`).toEqual([]);
    }
  });
});

/* ===================================================== 5. seletor de tema */

test.describe("seletor de tema (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  test("Escuro fixa o tema e guarda a escolha; Sistema desfaz", async ({ page }) => {
    await abrir(page, "/patients");
    const grupo = page.getByRole("group", { name: "Tema da interface" });

    await grupo.getByRole("button", { name: "Escuro" }).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    expect(await page.evaluate(() => localStorage.getItem("nutriplan.theme"))).toBe("dark");
    await expect(grupo.getByRole("button", { name: "Escuro" })).toHaveAttribute("aria-pressed", "true");

    await grupo.getByRole("button", { name: "Sistema" }).click();
    await expect(page.locator("html")).not.toHaveAttribute("data-theme", /.+/);
    expect(await page.evaluate(() => localStorage.getItem("nutriplan.theme"))).toBeNull();
  });
});

/* ===================================================== 6. alvos de toque */

test.describe("alvos de toque no celular (390)", () => {
  test.use(CELULAR);

  /**
   * A regra: no celular todo controle que se toca tem pelo menos 44×44 —
   * botões e links-botão do cabeçalho da página, os itens da barra de
   * atalhos e os ícones do app bar. A única concessão é para botões de texto
   * em linha dentro de cards (`.card .button.pequeno`, `.card .link`), onde
   * 40px de altura ainda separa um do outro; eles não são medidos aqui.
   */
  const MINIMO = 44;

  test("o navegador responde a (pointer: coarse)", async ({ page }) => {
    // Sem isto as regras de 44px não ligam e o teste seguinte mediria a
    // versão de mouse dos botões.
    await abrir(page, "/patients");
    expect(await page.evaluate(() => matchMedia("(pointer: coarse)").matches)).toBe(true);
  });

  test("os botões do cabeçalho da página têm ao menos 44×44", async ({ page }) => {
    await abrir(page, "/patients");
    const botoes = page.locator(".header-page button, .header-page a.button");
    const total = await botoes.count();
    expect(total, "o cabeçalho de Pacientes tem botões").toBeGreaterThan(0);

    for (const botao of await botoes.all()) {
      if (!(await botao.isVisible())) continue;
      const caixa = await botao.boundingBox();
      const texto = (await botao.textContent())?.trim() || (await botao.getAttribute("aria-label"));
      expect(caixa?.width ?? 0, `largura de "${texto}"`).toBeGreaterThanOrEqual(MINIMO);
      expect(caixa?.height ?? 0, `altura de "${texto}"`).toBeGreaterThanOrEqual(MINIMO);
    }
  });

  test("os itens da barra de atalhos e do app bar têm ao menos 44×44", async ({ page }) => {
    await abrir(page, "/patients");

    const itens = page.getByRole("navigation", { name: "Atalhos" }).locator("a, button");
    await expect(itens).toHaveCount(5);
    for (const item of await itens.all()) {
      const caixa = await item.boundingBox();
      const texto = (await item.textContent())?.trim();
      expect(caixa?.width ?? 0, `largura de "${texto}"`).toBeGreaterThanOrEqual(MINIMO);
      expect(caixa?.height ?? 0, `altura de "${texto}"`).toBeGreaterThanOrEqual(MINIMO);
    }

    for (const botao of await page.locator(".appbar button").all()) {
      const caixa = await botao.boundingBox();
      const nome = await botao.getAttribute("aria-label");
      expect(caixa?.width ?? 0, `largura de "${nome}"`).toBeGreaterThanOrEqual(MINIMO);
      expect(caixa?.height ?? 0, `altura de "${nome}"`).toBeGreaterThanOrEqual(MINIMO);
    }
  });
});

/* ===================================================== 7. anel de foco */

test.describe("anel de foco (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  test("o primeiro item da navegação mostra um contorno de ao menos 2px ao receber foco pelo teclado", async ({
    page,
  }) => {
    await abrir(page, "/patients");
    const primeiro = page.getByRole("navigation", { name: "Principal" }).locator(".nav-item").first();

    // Tab até chegar nele — a marca vem antes; nada mais deveria vir.
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press("Tab");
      if (await primeiro.evaluate((el) => el === document.activeElement)) break;
    }
    await expect(primeiro).toBeFocused();

    const contorno = await primeiro.evaluate((el) => {
      const estilo = getComputedStyle(el);
      return { estilo: estilo.outlineStyle, largura: parseFloat(estilo.outlineWidth) };
    });
    expect(contorno.estilo, "outline-style").not.toBe("none");
    expect(contorno.largura, "outline-width em px").toBeGreaterThanOrEqual(2);
  });
});

/* ===================================================== 8. contraste */

test.describe("contraste (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  async function semFalhasDeContraste(page: Page, rotulo: string) {
    const resultado = await new AxeBuilder({ page }).withRules(["color-contrast"]).analyze();
    const violacoes = resultado.violations.flatMap((v) =>
      v.nodes.map((n) => ({ alvo: n.target.join(" "), resumo: n.failureSummary })),
    );
    expect(violacoes, `${rotulo}: falhas de contraste\n${JSON.stringify(violacoes, null, 2)}`).toEqual([]);
  }

  for (const tema of ["light", "dark"] as const) {
    test(`/patients passa no contraste no tema ${tema === "light" ? "claro" : "escuro"}`, async ({ page }) => {
      await fixarTema(page, tema);
      await abrir(page, "/patients");
      await expect(page.getByText("Aparência de Teste")).toBeVisible();
      await semFalhasDeContraste(page, `/patients (${tema})`);
    });

    test(`/prescriptions/:id passa no contraste no tema ${tema === "light" ? "claro" : "escuro"}`, async ({
      page,
    }) => {
      await fixarTema(page, tema);
      await abrir(page, `/prescriptions/${cenario.plano.id}`);
      await expect(page.getByRole("heading", { name: "Plano de aparência" })).toBeVisible();
      await semFalhasDeContraste(page, `/prescriptions/:id (${tema})`);
    });

    test(`/plan/:identifier passa no contraste no tema ${tema === "light" ? "claro" : "escuro"}`, async ({
      page,
    }) => {
      await fixarTema(page, tema);
      await abrir(page, `/plan/${cenario.identificador}`);
      await expect(page.getByText("Plano de aparência")).toBeVisible();
      await semFalhasDeContraste(page, `/plan/:identifier (${tema})`);
    });
  }
});

/* ===================================================== 9. altura do editor */

test.describe("altura do editor no celular (390)", () => {
  test.use(CELULAR);

  test("o plano com seis refeições fica abaixo de 6000px de altura", async ({ page }) => {
    // Seis refeições abertas com todos os campos à mostra passavam de duas
    // dezenas de telas de rolagem; o orçamento é o teto do redesenho.
    await abrir(page, `/prescriptions/${cenario.plano.id}`);
    await expect(page.getByRole("heading", { name: "Plano de aparência" })).toBeVisible();
    await expect(page.getByLabel("Nome da refeição")).toHaveCount(6);

    const altura = await page.evaluate(() => document.documentElement.scrollHeight);
    expect(altura, "altura total do editor").toBeLessThan(6000);
  });
});

/* ===================================================== 10. fotos de referência */

/**
 * As fotos de referência ficam em e2e/aparencia.spec.ts-snapshots/ e são
 * geradas com `npx playwright test e2e/aparencia.spec.ts --update-snapshots`.
 * As que estão no repositório saíram do estado das telas DURANTE o redesenho;
 * quando ele terminar, rode o comando de novo para trocá-las pelas finais.
 * O nome do arquivo carrega o sistema (win32/linux): uma foto tirada num
 * sistema não serve de referência para outro.
 */
const FOTO = {
  fullPage: true,
  animations: "disabled",
  maxDiffPixelRatio: 0.02,
} as const;

test.describe("fotos de referência no celular (390)", () => {
  test.use(CELULAR);

  test("/patients no tema claro", async ({ page }) => {
    await fixarTema(page, "light");
    await abrir(page, "/patients");
    await expect(page.getByText("Aparência de Teste")).toBeVisible();
    await expect(page).toHaveScreenshot("pacientes-390.png", { ...FOTO, mask: comHoraOuData(page) });
  });

  test("/plan/:identifier no tema claro", async ({ page }) => {
    await fixarTema(page, "light");
    await abrir(page, `/plan/${cenario.identificador}`);
    await expect(page.getByText("Plano de aparência")).toBeVisible();
    await expect(page).toHaveScreenshot("plano-publico-390.png", { ...FOTO, mask: comHoraOuData(page) });
  });
});

test.describe("fotos de referência no desktop (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  test("/patients no tema claro", async ({ page }) => {
    await fixarTema(page, "light");
    await abrir(page, "/patients");
    await expect(page.getByText("Aparência de Teste")).toBeVisible();
    await expect(page).toHaveScreenshot("pacientes-1440.png", { ...FOTO, mask: comHoraOuData(page) });
  });
});
