import { test, expect, type Page } from "@playwright/test";

import { abrirPlanoComData, criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * A biblioteca e o cardápio pela tela: alimentos, receitas, orientações e o
 * editor de prescrição do primeiro item à publicação.
 */

let conta: Conta;
let paciente: { id: number; name: string };

const PNG_1X1 = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

async function escolherAlimento(page: Page, campo: ReturnType<Page["getByLabel"]>, termo: string) {
  await campo.fill(termo);
  const sugestao = page.locator(".results-search button").first();
  await expect(sugestao).toBeVisible();
  await sugestao.click();
}

/**
 * Escreve num campo de texto formatado.
 *
 * O editor é um `role="textbox"` com o rótulo em aria-label; quando está
 * fechado (campo sob demanda), o botão "+ escrever" ao lado do rótulo o abre.
 */
async function escrever(page: Page, rotulo: string, texto: string) {
  const fechado = page.locator(".richtext-field", { hasText: rotulo }).getByRole("button", { name: "+ escrever" });
  if (await fechado.count()) await fechado.first().click();
  const area = page.getByRole("textbox", { name: rotulo });
  await area.focus();
  await page.keyboard.type(texto);
}

test.beforeAll(async () => {
  conta = await criarConta("Nutri da Biblioteca");
  paciente = await criarPaciente(conta, { name: "Paciente da Biblioteca" });
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

/* ======================================================= alimentos */

test.describe("alimentos", () => {
  test("cadastra um alimento próprio e o acha na busca com porção calculada", async ({ page }) => {
    await page.goto("/foods");
    await page.getByRole("button", { name: "Novo alimento" }).click();
    await page.getByLabel("Descrição", { exact: true }).fill("Pão de queijo da vovó");
    await page.getByLabel("Marca").fill("Casa");
    await page.getByLabel("Energia (kcal)").fill("300");
    await page.getByLabel("Proteínas (g)").fill("8");
    await page.getByLabel("Carboidratos (g)").fill("35");
    await page.getByLabel("Gorduras (g)").fill("14");
    await page.getByLabel("Descrição da porção").fill("unidade");
    await page.getByLabel("Peso (g)").fill("40");
    await page.getByRole("button", { name: "Cadastrar alimento" }).click();

    await expect(page).toHaveURL(/\/foods\/\d+$/);
    await expect(page.getByRole("heading", { name: "Pão de queijo da vovó" })).toBeVisible();

    // Calcular porção: 2 unidades de 40 g = 80 g → 240 kcal.
    await page.getByLabel("Quantidade").fill("2");
    const medida = page.getByLabel("Medida");
    const unidade = await medida.locator("option", { hasText: "unidade" }).first().getAttribute("value");
    await medida.selectOption(unidade ?? "");
    await expect(page.getByText(/240/).first()).toBeVisible();

    await page.getByLabel("Nova porção").fill("fatia");
    await page.getByLabel("Peso (g)").last().fill("25");
    await page.getByRole("button", { name: "Adicionar" }).click();
    // A porção nova entra no seletor de medidas.
    await expect(page.getByLabel("Medida").locator("option", { hasText: "fatia" })).toHaveCount(1);

    await page.goto("/foods");
    await page.getByLabel("Buscar").fill("queijo da vovó");
    await expect(page.getByText("Pão de queijo da vovó")).toBeVisible();
  });

  test("importa uma tabela de alimentos por CSV", async ({ page }) => {
    await page.goto("/foods");
    await page.getByRole("button", { name: "Importar tabela" }).click();
    await page.getByLabel("Arquivo CSV").setInputFiles({
      name: "tabela.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(
        "descricao,energia_kcal,proteina_g,carboidrato_g,lipideos_g\nBiscoito importado da planilha,450,6,70,15\nSuco importado da planilha,45,0.5,11,0\n",
      ),
    });
    await page.getByRole("button", { name: "Importar", exact: true }).click();
    await expect(page.getByText(/2 alimentos importados|2 importados/i)).toBeVisible();
    await page.getByRole("button", { name: "Fechar", exact: true }).click();
    await page.getByLabel("Buscar").fill("importado da planilha");
    await expect(page.getByText("Biscoito importado da planilha")).toBeVisible();
  });
});

/* ========================================================= receitas */

test.describe("receitas", () => {
  test("monta uma receita pela tela e ela vira alimento com composição", async ({ page }) => {
    await page.goto("/recipes/new");
    await page.getByLabel("Nome da receita").fill("Vitamina de banana pela tela");
    await page.getByLabel("Grupo").fill("Bebidas");
    await escolherAlimento(page, page.getByLabel("Alimento").first(), "Banana, prata");
    await page.getByLabel(/Medida de Banana/).selectOption("");
    await page.getByLabel(/Quantidade de Banana/).fill("100");
    await escolherAlimento(page, page.getByLabel("Alimento").first(), "Leite, de vaca");
    await page.getByLabel(/Medida de Leite/).selectOption("");
    await page.getByLabel(/Quantidade de Leite/).fill("200");
    await page.getByLabel("Peso pronto (g)").fill("300");
    await page.getByLabel("Rende quantas porções").fill("1");
    await escrever(page, "Modo de preparo", "Bata tudo no liquidificador.");
    await page.getByRole("button", { name: "Salvar receita" }).click();

    await expect(page).toHaveURL(/\/recipes\/\d+$/);
    await expect(page.getByRole("heading", { name: "Por 100 g" })).toBeVisible();
    await expect(page.locator(".recipe-aside").getByText("Energia", { exact: true }).first()).toBeVisible();
    await expect(page.locator(".recipe-aside")).toContainText(/\d+ kcal/);

    await page.goto("/foods");
    await page.getByLabel("Buscar").fill("Vitamina de banana pela tela");
    await expect(page.getByText("Vitamina de banana pela tela")).toBeVisible();
  });
});

/* ====================================================== orientações */

test.describe("orientações", () => {
  test("cria com figura, edita, duplica um modelo do sistema e remove", async ({ page }) => {
    await page.goto("/handouts");
    await page.getByRole("button", { name: "Nova orientação" }).click();
    await page.getByLabel("Título").fill("Café da manhã reforçado");
    await escrever(page, "Texto da orientação", "Inclua uma fonte de proteína.");
    await page.getByLabel("Figura").setInputFiles({ name: "figura.png", mimeType: "image/png", buffer: PNG_1X1 });
    await page.getByRole("button", { name: "Salvar", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Café da manhã reforçado" })).toBeVisible();

    const cartao = page.locator(".card, article, li", { hasText: "Café da manhã reforçado" }).first();
    await cartao.getByRole("button", { name: "Editar" }).click();
    await page.getByLabel("Título").fill("Café da manhã reforçado (v2)");
    await page.getByRole("button", { name: "Salvar", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Café da manhã reforçado (v2)" })).toBeVisible();

    const antes = await page.getByRole("button", { name: "Editar" }).count();
    await page.getByRole("button", { name: "Duplicar" }).first().click();
    await expect(page.getByRole("button", { name: "Editar" })).toHaveCount(antes + 1);

    // A cópia leva o mesmo título; remover a original tira uma das duas.
    const iguais = await page.getByRole("heading", { name: "Café da manhã reforçado (v2)" }).count();
    page.once("dialog", (d) => d.accept());
    await page.locator(".card, article, li", { hasText: "Café da manhã reforçado (v2)" }).first()
      .getByRole("button", { name: "Remover" }).click();
    await expect(page.getByRole("heading", { name: "Café da manhã reforçado (v2)" })).toHaveCount(iguais - 1);
  });
});

/* ====================================================== prescrições */

test.describe("prescrições", () => {
  test("monta o cardápio pela tela, salva refeição, publica e o paciente abre o link", async ({
    page,
    browser,
  }) => {
    await page.goto("/prescriptions/new");
    await page.getByLabel("Título do plano").fill("Plano montado pela tela");
    await page.getByLabel("Paciente", { exact: true }).selectOption({ label: paciente.name });
    await page.getByLabel("Meta energética (kcal/dia)").fill("1800");

    const cafe = page.locator(".meal").first();
    await cafe.getByRole("button", { name: "+ Adicionar item" }).click();
    await escolherAlimento(page, cafe.getByLabel("Alimento").first(), "Banana, prata");
    // O alimento entra com a medida caseira padrão; a porção aqui é em gramas.
    await cafe.getByLabel("Medida", { exact: true }).first().selectOption("");
    await cafe.getByLabel("Quantidade", { exact: true }).first().fill("100");
    await cafe.getByRole("button", { name: "+ substituição" }).click();
    await escolherAlimento(page, cafe.getByLabel("Alimento da substituição").first(), "Maçã");
    await cafe.getByRole("button", { name: "+ Separador" }).click();
    await cafe.getByRole("button", { name: "+ Adicionar item" }).click();
    await escolherAlimento(page, cafe.getByLabel("Alimento").first(), "Leite, de vaca");
    await cafe.getByLabel("Medida", { exact: true }).last().selectOption("");
    await cafe.getByLabel("Quantidade", { exact: true }).last().fill("200");
    await cafe.getByRole("button", { name: "Escrever observações" }).click();
    await cafe.getByRole("textbox", { name: /Observaç/ }).first().focus();
    await page.keyboard.type("Pode ser leite vegetal.");

    await page.getByRole("button", { name: "Salvar", exact: true }).click();
    await expect(page).toHaveURL(/\/prescriptions\/\d+$/);
    await expect(page.locator(".totals-card")).toContainText("kcal");
    await expect(cafe).toContainText("100 g");

    // Favoritar a refeição e reinseri-la como outra.
    page.once("dialog", (d) => d.accept("Café salvo pela tela"));
    await cafe.getByRole("button", { name: "Favoritar" }).click();
    await expect(page.getByRole("button", { name: /Refeições salvas \(1\)/ })).toBeVisible();
    await page.getByRole("button", { name: /Refeições salvas \(1\)/ }).click();
    await page.getByRole("button", { name: "Inserir" }).first().click();
    await expect(page.locator(".warning.error")).toHaveCount(0);
    await expect(page.getByLabel("Nome da refeição")).toHaveCount(7);

    // Foto da refeição, só depois de salva.
    await cafe.locator("input[type='file'][accept='image/*']").setInputFiles({
      name: "prato.png",
      mimeType: "image/png",
      buffer: PNG_1X1,
    });
    await expect(page.getByText("Foto anexada").first()).toBeVisible();

    // Orientação anexada, publicação e link.
    const biblioteca = page.getByLabel("Orientação da biblioteca");
    await biblioteca.selectOption({ index: 1 });
    await page.getByRole("button", { name: "Anexar", exact: true }).click();
    await page.getByRole("button", { name: "Publicar plano" }).click();
    await expect(page.getByRole("button", { name: "Copiar link" })).toBeVisible();

    const [pdf] = await Promise.all([
      page.waitForResponse((r) => /\/prescriptions\/\d+\/pdf/.test(r.url())),
      page.getByRole("button", { name: "Plano em PDF" }).click(),
    ]);
    expect(pdf.status()).toBe(200);

    const url = page.url();
    const id = Number(url.match(/\/prescriptions\/(\d+)/)?.[1]);
    const plano = await (await conta.api.get(`/api/prescriptions/${id}`)).json();
    const contexto = await browser.newContext();
    const dele = await contexto.newPage();
    await dele.goto(`/plan/${plano.publicIdentifier}`);
    // Quem abre é o paciente: passa pela data de nascimento.
    await abrirPlanoComData(dele);
    await expect(dele.getByText("Plano montado pela tela")).toBeVisible();
    await expect(dele.getByText(/Banana/).first()).toBeVisible();
    await expect(dele.getByText("Pode ser leite vegetal.")).toBeVisible();
    await contexto.close();
  });

  test("um plano salvo como modelo aparece na aba Modelos e começa um plano novo", async ({ page }) => {
    await page.goto("/prescriptions/new");
    await page.getByLabel("Título do plano").fill("Modelo pela tela");
    await page.getByLabel("Salvar como modelo reaproveitável (sem paciente vinculado)").check();
    // O nome da refeição é um campo, não texto: o Almoço é a terceira das seis.
    const almoco = page.locator(".meal").nth(2);
    await expect(almoco.getByLabel("Nome da refeição")).toHaveValue("Almoço");
    await almoco.getByRole("button", { name: "+ Adicionar item" }).click();
    await escolherAlimento(page, almoco.getByLabel("Alimento").first(), "Arroz, integral, cozido");
    await almoco.getByLabel("Medida", { exact: true }).first().selectOption("");
    await almoco.getByLabel("Quantidade", { exact: true }).first().fill("150");
    await page.getByRole("button", { name: "Salvar", exact: true }).click();
    await expect(page).toHaveURL(/\/prescriptions\/\d+$/);

    await page.goto("/prescriptions");
    await page.getByRole("button", { name: "Modelos" }).click();
    await expect(page.getByText("Modelo pela tela")).toBeVisible();

    await page.goto("/prescriptions/new");
    await page.getByLabel("Título do plano").fill("Plano a partir do modelo");
    await page.getByLabel("Paciente", { exact: true }).selectOption({ label: paciente.name });
    await page.getByLabel("Modelo", { exact: true }).selectOption({ label: "Modelo pela tela" });
    await expect(page.locator(".meal").nth(2)).toContainText(/Arroz/);
  });

  test("remover o plano tira-o da lista", async ({ page }) => {
    const criado = await conta.api.post("/api/prescriptions", {
      data: {
        title: "Plano para remover",
        patientId: paciente.id,
        method: "FOODS",
        meals: [{ name: "Café da Manhã", items: [] }],
      },
    });
    const { id } = await criado.json();
    await page.goto(`/prescriptions/${id}`);
    page.once("dialog", (d) => d.accept());
    await page.getByRole("button", { name: "Remover", exact: true }).last().click();
    await expect(page).toHaveURL(/\/prescriptions$/);
    await expect(page.getByText("Plano para remover")).toHaveCount(0);
  });
});
