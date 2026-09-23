import { test, expect } from "@playwright/test";

import { criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * Exames e anamnese — as duas telas que o cliente apontou pelo que faltava
 * nelas: a busca entre os 154 parâmetros, e o nome do paciente no PDF.
 */

let conta: Conta;
let paciente: { id: number; name: string };

test.beforeAll(async () => {
  conta = await criarConta("Nutri dos Exames");
  paciente = await criarPaciente(conta, { name: "Exames de Teste" });
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

test("a busca encurta a lista de exames na hora de solicitar", async ({ page }) => {
  // "Colocar busca em exames." São 154 parâmetros; rolar até achar "TGP" no
  // meio de uma consulta é trabalho que o sistema existe para evitar.
  await page.goto(`/patients/${paciente.id}/labtests`);
  await page.getByRole("button", { name: "Solicitar exames" }).click();

  const marcadores = page.getByRole("checkbox");
  const total = await marcadores.count();
  expect(total, "a lista completa de parâmetros deve estar à mostra").toBeGreaterThan(20);

  await page.getByLabel("Buscar exame").fill("glicose");
  await expect.poll(async () => marcadores.count()).toBeLessThan(total);
  await expect(page.getByText(/Glicose/i).first()).toBeVisible();
});

test("a busca acha sem o acento certo", async ({ page }) => {
  await page.goto(`/patients/${paciente.id}/labtests`);
  await page.getByRole("button", { name: "Solicitar exames" }).click();

  // Quem digita numa consulta não acerta o acento, e não deveria precisar.
  await page.getByLabel("Buscar exame").fill("acido urico");
  await expect(page.getByText(/Ácido úrico/i).first()).toBeVisible();
});

test("o PDF da anamnese se identifica pelo nome do paciente", async ({ page }) => {
  // "Nome do paciente na anamnese (no pdf)." A folha circula fora da tela —
  // impressa, anexada, arquivada — e ali nada mais diz de quem ela é.
  const criada = await conta.api.post("/api/anamneses", {
    data: {
      patientId: paciente.id,
      name: "Anamnese inicial",
      date: "2026-03-10",
      body: JSON.stringify({
        type: "doc",
        content: [{ type: "paragraph", content: [{ type: "text", text: "Primeira consulta." }] }],
      }),
    },
  });
  expect(criada.status(), await criada.text()).toBe(201);
  const anamnese = await criada.json();

  expect(
    anamnese.patientName,
    "a resposta precisa dizer de quem é, ou o PDF não tem de onde tirar",
  ).toBe(paciente.name);

  await page.goto(`/patients/${paciente.id}/anamneses`);
  const [resposta] = await Promise.all([
    page.waitForResponse((r) => r.url().includes(`/anamneses/${anamnese.id}/pdf`)),
    page.getByRole("button", { name: /PDF/ }).first().click(),
  ]);
  expect(resposta.status()).toBe(200);

  // Os bytes vêm pela API e não de `resposta.body()`: o navegador consome o
  // PDF como blob para abrir na outra guia, e aí o corpo já foi lido — o
  // Playwright devolveria vazio e o teste falharia sem haver defeito.
  const folha = await conta.api.get(`/api/anamneses/${anamnese.id}/pdf`);
  expect(folha.status()).toBe(200);
  const bytes = await folha.body();
  expect(bytes.subarray(0, 4).toString()).toBe("%PDF");
});
