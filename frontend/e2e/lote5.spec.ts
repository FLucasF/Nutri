import { test, expect } from "@playwright/test";

import { abrirPlanoComData, acharAlimento, criarConta, criarPaciente, criarAvaliacao, entrar, type Conta } from "./apoio/conta";
import { auditarTela } from "./apoio/auditoria";

/**
 * Lote 5: a data de nascimento no link do plano, as fórmulas por massa magra,
 * os micronutrientes contra as DRI e a página de favoritos.
 */

let conta: Conta;
let paciente: { id: number; name: string };
let plano: { id: number; publicIdentifier: string };

test.beforeAll(async () => {
  conta = await criarConta("Nutri do Lote 5");
  paciente = await criarPaciente(conta, { name: "Paciente do Lote Cinco", dateBirth: "1990-05-20" });
  const leite = await acharAlimento(conta, "Leite, de vaca, integral");
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano do lote cinco",
      patientId: paciente.id,
      method: "FOODS",
      meals: [{ name: "Café da Manhã", time: "07:00", items: [{ foodId: leite, quantity: 500 }] }],
    },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const { id } = await criado.json();
  const publicado = await (await conta.api.post(`/api/prescriptions/${id}/publish`, {})).json();
  plano = { id, publicIdentifier: publicado.publicIdentifier };
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test("o link do plano pede a data de nascimento, recusa a errada e lembra da certa", async ({ browser }) => {
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  await page.goto(`/plan/${plano.publicIdentifier}`);
  await expect(page.getByRole("heading", { name: "Seu plano alimentar" })).toBeVisible();
  // Antes da data, nada do plano: nem o título, nem o nome.
  await expect(page.getByText("Plano do lote cinco")).toHaveCount(0);

  await page.getByLabel("Data de nascimento").fill("1990-05-21");
  await page.getByRole("button", { name: "Abrir o plano" }).click();
  await expect(page.getByRole("alert")).toContainText("não confere");
  await expect(page.getByRole("alert")).toContainText("Restam 4 tentativas");
  // A recusa é do servidor (422) e vai ao console por natureza; o que se
  // confere aqui é o enquadramento da tela com o aviso, no celular também.
  expect(await auditarTela(page)).toEqual([]);
  await page.setViewportSize({ width: 390, height: 800 });
  expect(await auditarTela(page)).toEqual([]);
  await page.setViewportSize({ width: 1280, height: 800 });

  await abrirPlanoComData(page, "1990-05-20");
  await expect(page.getByRole("heading", { name: "Plano do lote cinco" })).toBeVisible();

  // Recarregar a guia não pede de novo.
  await page.reload();
  await expect(page.getByRole("heading", { name: "Plano do lote cinco" })).toBeVisible();
  await contexto.close();
});

test("o nutricionista logado abre o link sem digitar a data", async ({ page }) => {
  await entrar(page, conta);
  await page.goto(`/plan/${plano.publicIdentifier}`);
  await expect(page.getByRole("heading", { name: "Plano do lote cinco" })).toBeVisible();
});

test("o editor mostra os micronutrientes do dia contra as DRI da paciente", async ({ page }) => {
  await entrar(page, conta);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`/prescriptions/${plano.id}`);
  const painel = page.getByRole("button", { name: /Micronutrientes × DRI/ });
  await expect(painel).toContainText("Mulher, 31 a 50 anos");
  await painel.click();
  await expect(page.getByRole("img", { name: /^Cálcio: \d+% da referência, abaixo/ })).toBeVisible();
  await expect(page.getByText(/1\.000 mg \(RDA\)/)).toBeVisible();
  await expect(page.getByRole("img", { name: /^Sódio: .*dentro do limite/ })).toBeVisible();
});

test("Katch-McArdle pede a massa magra e calcula com ela", async ({ page }) => {
  await entrar(page, conta);
  const homem = await criarPaciente(conta, { name: "Paciente da Massa Magra", sex: "MALE", dateBirth: "1996-01-10" });
  await criarAvaliacao(conta, homem.id, { date: "2026-09-01", weightKg: 80, heightCm: 180 });
  await page.goto(`/patients/${homem.id}/energy`);
  await page.getByRole("button", { name: "Novo cálculo" }).click();
  await page.getByLabel("Peso (kg)").fill("80");
  await page.getByLabel("Altura (cm)").fill("180");
  await page.getByRole("checkbox", { name: "EER (2023)" }).uncheck();
  await page.getByRole("checkbox", { name: "Katch-McArdle (massa magra)" }).check();
  await expect(page.getByText("Obrigatória para as equações por massa magra escolhidas.")).toBeVisible();
  await page.getByRole("button", { name: "Calcular e salvar" }).click();
  await expect(page.getByRole("alert").first()).toContainText("massa magra");

  await page.getByLabel("Massa magra (kg)").fill("64");
  await page.getByRole("button", { name: "Calcular e salvar" }).click();
  // Salvo, volta para a lista: 370 + 21,6 × 64 = 1.752,4 de basal, vezes 1,2 do sedentário.
  await expect(page.locator(".plano-energia", { hasText: "Katch-McArdle" }).first()).toContainText("2.103");
});

test("os favoritos mostram as refeições salvas e os painéis do consultório, e removem", async ({ page }) => {
  await entrar(page, conta);
  const banana = await acharAlimento(conta, "Banana, prata");
  const salva = await conta.api.post("/api/meal-favorites", {
    data: { name: "Lanche do lote cinco", meal: { name: "Lanche da Tarde", time: "16:00:00", items: [{ foodId: banana, quantity: 100 }] } },
  });
  expect(salva.ok(), await salva.text()).toBeTruthy();
  const parametros = await (await conta.api.get("/api/labtests/parameters")).json();
  const painel = await conta.api.post("/api/labtests/panels", {
    data: { name: "Painel do lote cinco", parameterIds: [parametros[0].id, parametros[1].id] },
  });
  expect(painel.ok(), await painel.text()).toBeTruthy();

  await page.goto("/favorites");
  await expect(page.getByRole("heading", { name: "Favoritos" })).toBeVisible();
  await expect(page.getByText("Lanche do lote cinco")).toBeVisible();
  await expect(page.getByText("Painel do lote cinco")).toBeVisible();

  page.once("dialog", (d) => d.accept());
  await page.getByRole("button", { name: "Remover a refeição salva Lanche do lote cinco" }).click();
  await expect(page.getByText("Lanche do lote cinco")).toHaveCount(0);
  await expect(page.getByText("Painel do lote cinco")).toBeVisible();
});
