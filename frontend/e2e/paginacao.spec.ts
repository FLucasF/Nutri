import { test, expect } from "@playwright/test";

import { criarConta, entrar, type Conta } from "./apoio/conta";

/**
 * As listas que o servidor pagina andam de página em página.
 *
 * Antes, pacientes paravam no quinquagésimo e alimentos nos primeiros 40, e o
 * resto não aparecia em lugar nenhum. Os seletores (paciente na agenda, no
 * financeiro, no editor) liam só a primeira página de 200.
 */

let conta: Conta;

test.beforeAll(async () => {
  conta = await criarConta("Nutri da Paginação");
  // O plano de teste admite cinco pacientes ativos: cadastra e desativa em
  // levas, para chegar a 51 no total sem esbarrar no limite.
  for (let i = 1; i <= 51; i++) {
    const nome = `Paginado ${String(i).padStart(2, "0")}`;
    const criado = await conta.api.post("/api/patients", { data: { name: nome } });
    expect(criado.status(), await criado.text()).toBe(201);
    const { id } = await criado.json();
    const desativado = await conta.api.delete(`/api/patients/${id}`);
    expect(desativado.ok()).toBeTruthy();
  }
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

test("a lista de pacientes passa para a segunda página e volta", async ({ page }) => {
  await page.goto("/patients");
  await page.getByLabel("Somente ativos").uncheck();
  const paginas = page.getByRole("navigation", { name: "Páginas de pacientes" });
  await expect(paginas).toContainText("1–50 de 51 pacientes");
  await expect(paginas.getByRole("button", { name: "Anterior" })).toBeDisabled();
  await expect(page.getByText("Paginado 51")).toHaveCount(0);

  await paginas.getByRole("button", { name: "Próxima" }).click();
  await expect(paginas).toContainText("51–51 de 51 pacientes");
  await expect(page.getByText("Paginado 51").first()).toBeVisible();
  await expect(paginas.getByRole("button", { name: "Próxima" })).toBeDisabled();

  await paginas.getByRole("button", { name: "Anterior" }).click();
  await expect(paginas).toContainText("1–50 de 51");

  // Uma busca nova recomeça da primeira página, e com um resultado só a navegação some.
  await paginas.getByRole("button", { name: "Próxima" }).click();
  await page.getByLabel("Buscar").fill("Paginado 07");
  await expect(page.getByText("Paginado 07").first()).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Páginas de pacientes" })).toHaveCount(0);
});

test("o acervo de alimentos anda de página em página", async ({ page }) => {
  await page.goto("/foods");
  const paginas = page.getByRole("navigation", { name: "Páginas de alimentos" });
  await expect(paginas).toContainText(/^1–40 de [\d.]+ alimentos/);
  const primeiro = await page.locator(".foods-table tbody tr, .foods-list .list-row").first().textContent();

  await paginas.getByRole("button", { name: "Próxima" }).click();
  await expect(paginas).toContainText(/^41–80 de/);
  await expect(page.locator(".foods-table tbody tr, .foods-list .list-row").first()).not.toHaveText(
    primeiro ?? "",
  );

  // Trocar o filtro volta para a primeira página.
  await page.getByLabel("Fonte").selectOption({ index: 1 });
  await expect(page.getByRole("navigation", { name: "Páginas de alimentos" })).toContainText(/^1–/);
});
