import { test, expect } from "@playwright/test";

import { criarConta, type Conta } from "./apoio/conta";

/**
 * A porta de entrada.
 *
 * É o único teste que digita a senha na tela: os outros entram pelo token,
 * porque o que eles verificam vem depois do login.
 */

let conta: Conta;

test.beforeAll(async () => {
  conta = await criarConta("Nutri do Acesso");
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test("entra com e-mail e senha e chega na lista de pacientes", async ({ page }) => {
  await page.goto("/");

  await page.getByLabel("E-mail").fill(conta.email);
  await page.getByLabel("Senha").fill(conta.senha);
  await page.getByRole("button", { name: "Entrar" }).click();

  await expect(page.getByRole("heading", { name: "Pacientes" })).toBeVisible();
  await expect(page.getByText("Nutri do Acesso")).toBeVisible();
});

test("senha errada explica o que houve sem derrubar o formulário", async ({ page }) => {
  await page.goto("/");

  await page.getByLabel("E-mail").fill(conta.email);
  await page.getByLabel("Senha").fill("senha-que-nao-e-a-dele");
  await page.getByRole("button", { name: "Entrar" }).click();

  // A mensagem precisa aparecer na tela: um erro só no console deixa quem
  // está usando sem saber se clicou ou não.
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("E-mail")).toHaveValue(conta.email);
});

test("sem sessão, qualquer endereço interno cai no login", async ({ page }) => {
  await page.goto("/patients/1");
  await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();
});
