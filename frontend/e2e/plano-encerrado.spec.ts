import { test, expect } from "@playwright/test";

import { acharAlimento, criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * O plano encerrado.
 *
 * "Quando eu apago uma refeição não consigo mais adicionar uma nova; se eu
 * apagar tudo e deixar apenas café da manhã não consigo adicionar mais."
 *
 * O plano em que isso aconteceu estava encerrado. A tela travava só a área das
 * refeições: o botão de acrescentar sumia, mas o cabeçalho, a vigência, as
 * metas e os dois editores continuavam aceitando digitação. Quem usava via um
 * lado do editor respondendo e o outro não, sem nada explicar a diferença ali
 * onde estava olhando.
 *
 * Um plano encerrado tem que estar encerrado inteiro — e dizer como sair disso.
 */

let conta: Conta;
let paciente: { id: number; name: string };
let arroz: number;

test.beforeAll(async () => {
  conta = await criarConta("Nutri do Encerrado");
  paciente = await criarPaciente(conta, { name: "Encerrado de Teste" });
  arroz = await acharAlimento(conta, "Arroz, integral");
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

/** Um plano publicado e depois encerrado, como o do relato. */
async function planoEncerrado() {
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano encerrado",
      patientId: paciente.id,
      method: "FOODS",
      meals: [
        { name: "Café da Manhã", items: [{ foodId: arroz, quantity: 100 }] },
        { name: "Almoço", items: [{ foodId: arroz, quantity: 150 }] },
      ],
    },
  });
  const plano = await criado.json();
  await conta.api.post(`/api/prescriptions/${plano.id}/publish`, {});
  const encerrado = await conta.api.post(`/api/prescriptions/${plano.id}/close`, {});
  expect(encerrado.status(), await encerrado.text()).toBe(200);
  return plano.id as number;
}

test("o plano encerrado trava inteiro, e não pela metade", async ({ page }) => {
  const id = await planoEncerrado();
  await page.goto(`/prescriptions/${id}`);
  await expect(page.getByText("Este plano está encerrado")).toBeVisible();

  // Nada do cabeçalho aceita digitação: era o que deixava a tela ambígua.
  await expect(page.getByLabel("Título do plano")).toBeDisabled();
  await expect(page.getByLabel("Paciente", { exact: true })).toBeDisabled();
  await expect(page.getByLabel("Meta energética (kcal/dia)")).toBeDisabled();
  await expect(page.getByLabel("Início da vigência")).toBeDisabled();
  await expect(page.getByLabel("Fim da vigência")).toBeDisabled();

  // E o editor de texto não aparece com a barra de formatação: só o que ficou
  // escrito, para ler.
  await expect(page.locator(".richtext-area")).toHaveCount(0);

  // As refeições também não têm como ser mexidas.
  await expect(page.getByRole("button", { name: "+ Adicionar refeição" })).toHaveCount(0);
  await expect(page.locator(".meal").getByRole("button", { name: "Remover" })).toHaveCount(0);
});

test("o aviso de encerrado traz a saída junto", async ({ page }) => {
  const id = await planoEncerrado();
  await page.goto(`/prescriptions/${id}`);

  // A saída existia, mas no painel da direita — longe de onde a pessoa estava
  // tentando editar. Um aviso que diz o que não dá sem dizer o que fazer é um
  // beco sem saída.
  const aviso = page.locator(".warning.attention").first();
  await expect(aviso.getByRole("button", { name: "Reabrir como rascunho" })).toBeVisible();
  await expect(aviso.getByRole("button", { name: "Duplicar" })).toBeVisible();

  await aviso.getByRole("button", { name: "Reabrir como rascunho" }).click();

  // Reaberto, tudo volta a responder — inclusive acrescentar refeição.
  await expect(page.getByText("Este plano está encerrado")).toHaveCount(0);
  await expect(page.getByLabel("Título do plano")).toBeEnabled();
  await expect(page.getByRole("button", { name: "+ Adicionar refeição" })).toBeVisible();
});

test("no plano aberto, remover e acrescentar refeição funcionam nos dois sentidos", async ({
  page,
}) => {
  // O caminho que o cliente esperava, num plano que aceita alteração.
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano aberto",
      patientId: paciente.id,
      method: "FOODS",
      meals: [
        { name: "Café da Manhã", items: [{ foodId: arroz, quantity: 100 }] },
        { name: "Almoço", items: [{ foodId: arroz, quantity: 150 }] },
        { name: "Jantar", items: [{ foodId: arroz, quantity: 120 }] },
      ],
    },
  });
  const plano = await criado.json();

  await page.goto(`/prescriptions/${plano.id}`);
  const refeicoes = page.getByLabel("Nome da refeição");
  await expect(refeicoes).toHaveCount(3);

  await page.locator(".meal").getByRole("button", { name: "Remover" }).first().click();
  await expect(refeicoes).toHaveCount(2);

  const adicionar = page.getByRole("button", { name: "+ Adicionar refeição" });
  await expect(adicionar, "remover não pode tirar o caminho de acrescentar").toBeVisible();
  await adicionar.click();
  await expect(refeicoes).toHaveCount(3);

  // Até a última: sobrar uma refeição não pode fechar a porta.
  await page.locator(".meal").getByRole("button", { name: "Remover" }).first().click();
  await page.locator(".meal").getByRole("button", { name: "Remover" }).first().click();
  await expect(refeicoes).toHaveCount(1);
  await expect(adicionar).toBeVisible();
  await adicionar.click();
  await expect(refeicoes).toHaveCount(2);
});
