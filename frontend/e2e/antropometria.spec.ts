import { test, expect } from "@playwright/test";

import { criarAvaliacao, criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * Avaliação antropométrica: registrar, corrigir e imprimir a evolução.
 *
 * O que estava faltando aqui era o caminho de volta. O servidor sempre aceitou
 * corrigir uma avaliação; a tela só oferecia remover — o que, por causa de um
 * dígito errado no peso, apagava as dobras, as circunferências e a composição
 * junto.
 */

let conta: Conta;
let paciente: { id: number; name: string };

test.beforeAll(async () => {
  conta = await criarConta("Nutri da Antropometria");
  paciente = await criarPaciente(conta, { name: "Antropometria de Teste", sex: "FEMALE" });
  await criarAvaliacao(conta, paciente.id, {
    date: "2026-03-10",
    weightKg: 88.4,
    heightCm: 164,
    circumferences: [{ site: "WAIST", side: "SINGLE", valueCm: 94 }],
  });
  await criarAvaliacao(conta, paciente.id, {
    date: "2026-07-14",
    weightKg: 82.6,
    heightCm: 164,
    circumferences: [{ site: "WAIST", side: "SINGLE", valueCm: 87 }],
  });
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

test("corrige o peso de uma avaliação já registrada, sem apagá-la", async ({ page }) => {
  await page.goto(`/patients/${paciente.id}/anthropometry`);
  await expect(page.getByRole("heading", { name: "Antropometria" })).toBeVisible();

  // A linha mais recente é a primeira do histórico.
  await page.getByRole("button", { name: "Editar" }).first().click();
  await expect(page.getByRole("heading", { name: /Corrigir avaliação/ })).toBeVisible();

  // O formulário abre com o que foi medido, e não em branco: corrigir um campo
  // não pode exigir redigitar os outros quinze.
  await expect(page.getByLabel("Peso (kg)")).toHaveValue("82.6");
  await expect(page.getByLabel("Cintura")).toHaveValue("87");

  await page.getByLabel("Peso (kg)").fill("81,2");
  await page.getByRole("button", { name: "Salvar correção" }).click();

  await expect(page.getByRole("heading", { name: /Corrigir avaliação/ })).toBeHidden();

  // Continuam sendo duas avaliações: corrigir não é criar outra.
  const lista = await conta.api.get(`/api/patients/${paciente.id}/assessments`);
  const avaliacoes = await lista.json();
  expect(avaliacoes).toHaveLength(2);
  expect(Number(avaliacoes[1].weightKg)).toBeCloseTo(81.2, 2);
  // E a circunferência que não foi tocada continua lá.
  expect(avaliacoes[1].circumferences).toHaveLength(1);
});

test("o relatório de evolução sai em PDF", async ({ page }) => {
  // "Adicionar funcionalidade de gerar relatórios com gráficos na antropometria."
  await page.goto(`/patients/${paciente.id}/anthropometry`);

  const [resposta] = await Promise.all([
    page.waitForResponse((r) => r.url().includes("anthropometry-report")),
    page.getByRole("button", { name: /Relatório de evolução/ }).click(),
  ]);

  expect(resposta.status()).toBe(200);
  expect(resposta.headers()["content-type"]).toContain("application/pdf");

  // Os bytes vêm pela API: o navegador já consumiu o corpo como blob para
  // abrir a folha em outra guia.
  const folha = await conta.api.get(`/api/patients/${paciente.id}/anthropometry-report`);
  const bytes = await folha.body();
  expect(bytes.subarray(0, 4).toString()).toBe("%PDF");
  expect(bytes.length).toBeGreaterThan(1000);
});

test("com uma avaliação só, o relatório nem é oferecido", async ({ page }) => {
  const sozinho = await criarPaciente(conta, { name: "Uma Avaliação Só" });
  await criarAvaliacao(conta, sozinho.id, { date: "2026-08-01", weightKg: 70, heightCm: 170 });

  await page.goto(`/patients/${sozinho.id}/anthropometry`);
  await expect(page.getByRole("heading", { name: "Antropometria" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Relatório de evolução/ })).toHaveCount(0);
});
