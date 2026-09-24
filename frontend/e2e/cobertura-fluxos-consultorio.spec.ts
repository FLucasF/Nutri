import { test, expect, request } from "@playwright/test";

import { criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * Os fluxos do consultório pela tela: acesso (criar conta, sair, entrar,
 * recuperar), agenda, financeiro, equipe e tema.
 */

let conta: Conta;
let paciente: { id: number; name: string };

function hojeIso() {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

test.beforeAll(async () => {
  conta = await criarConta("Nutri do Consultório");
  paciente = await criarPaciente(conta, { name: "Paciente do Consultório" });
});

test.afterAll(async () => {
  await conta.api.dispose();
});

/* ========================================================== acesso */

test.describe("acesso", () => {
  test("cria a conta pela tela, sai e entra de novo com a senha escolhida", async ({ page }) => {
    const email = `e2e-conta-${Date.now()}@exemplo.com`;
    await page.goto("/access");
    await page.getByRole("button", { name: "Criar uma conta" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toContainText(/consultório/i);
    await page.getByLabel("Nome completo").fill("Nutri Criada pela Tela");
    await page.getByLabel("E-mail").fill(email);
    await page.getByLabel("Senha", { exact: true }).fill("senhaForte2026");
    await page.getByLabel("CRN (opcional)").fill("CRN-3 12345");
    await page.getByRole("button", { name: "Criar conta" }).click();
    await expect(page).toHaveURL(/\/patients/);
    await expect(page.getByRole("heading", { name: "Pacientes" })).toBeVisible();

    await page.getByRole("button", { name: "Sair" }).click();
    await expect(page).toHaveURL(/\/access/);

    await page.getByLabel("E-mail").fill(email);
    await page.getByLabel("Senha", { exact: true }).fill("senhaForte2026");
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page).toHaveURL(/\/patients/);
  });

  test("a recuperação de senha aceita o e-mail e diz o que vai acontecer", async ({ page }) => {
    await page.goto("/access");
    await page.getByRole("button", { name: "Esqueci minha senha" }).click();
    await expect(page.getByRole("heading", { name: "Recuperar acesso" })).toBeVisible();
    await page.getByLabel("E-mail").fill(conta.email);
    await page.getByRole("button", { name: "Enviar link" }).click();
    await expect(page.locator(".warning.error")).toHaveCount(0);
    // A tela volta ao login ou confirma o envio; nas duas, o formulário continua de pé.
    await expect(page.getByLabel("E-mail")).toBeVisible();
  });
});

/* ========================================================== agenda */

test.describe("agenda", () => {
  test.beforeEach(async ({ page }) => {
    await entrar(page, conta);
  });

  test("agenda pela tela, confirma o atendimento e o vê na semana", async ({ page }) => {
    await page.goto("/schedule");
    await page.getByRole("button", { name: "Novo atendimento" }).click();
    await page.getByLabel("Paciente", { exact: true }).selectOption({ label: paciente.name });
    await page.getByLabel("Data", { exact: true }).fill(hojeIso());
    await page.getByLabel("Início").fill("15:30");
    await page.getByLabel("Duração (min)").fill("45");
    await page.getByLabel("Observação").fill("Trazer os exames.");
    await page.getByRole("button", { name: "Agendar" }).click();

    const cartao = page.locator(".appointment", { hasText: paciente.name }).first();
    await expect(cartao).toBeVisible();
    await expect(cartao).toContainText("15:30");

    await cartao.getByRole("button", { name: "Confirmar" }).click();
    await expect(page.locator(".appointment", { hasText: paciente.name }).first()).toContainText(/Confirmad/);

    await page.getByRole("button", { name: "Semana" }).click();
    await expect(page.getByText(paciente.name).first()).toBeVisible();
  });

  test("o endereço do calendário é gerado e serve o arquivo .ics", async ({ page }) => {
    await page.goto("/schedule");
    await page.getByRole("button", { name: "Ver no meu calendário" }).click();
    const gerar = page.getByRole("button", { name: /Gerar (outro )?endereço/ });
    await gerar.click();
    const endereco = page.getByLabel("Endereço da assinatura");
    await expect(endereco).toHaveValue(/\.ics$/);
    const url = await endereco.inputValue();
    const anonima = await request.newContext();
    const arquivo = await anonima.get(url);
    expect(arquivo.status()).toBe(200);
    expect(arquivo.headers()["content-type"]).toContain("text/calendar");
    await anonima.dispose();
  });
});

/* ====================================================== financeiro */

test.describe("financeiro", () => {
  test.beforeEach(async ({ page }) => {
    await entrar(page, conta);
  });

  test("registra uma receita, dá baixa e emite o recibo", async ({ page }) => {
    await page.goto("/finance");
    await page.getByRole("button", { name: "Novo lançamento" }).click();
    await page.getByRole("button", { name: "Receita" }).click();
    await page.getByLabel("Valor (R$)").fill("150");
    await page.getByLabel("Categoria").selectOption({ index: 1 });
    await page.getByLabel("Forma de pagamento").fill("PIX");
    await page.getByLabel("Competência", { exact: true }).fill(hojeIso());
    await page.getByLabel("Paciente", { exact: true }).selectOption({ label: paciente.name });
    await page.getByLabel("Descrição").fill("Consulta de retorno");
    await page.getByRole("button", { name: "Registrar", exact: true }).click();

    const linha = page.locator("tr, .list-row", { hasText: "Consulta de retorno" }).first();
    await expect(linha).toBeVisible();
    await expect(linha).toContainText("150,00");

    await linha.getByRole("button", { name: "Dar baixa" }).click();
    await expect(linha.getByRole("button", { name: "Recibo" })).toBeVisible();
    await linha.getByRole("button", { name: "Recibo" }).click();
    await expect(page.getByRole("heading", { name: "Recibo" })).toBeVisible();
    await expect(page.getByText(paciente.name).first()).toBeVisible();
    await page.getByRole("button", { name: "Fechar" }).click();
  });

  test("uma despesa entra na apuração como a pagar", async ({ page }) => {
    await page.goto("/finance");
    await page.getByRole("button", { name: "Novo lançamento" }).click();
    await page.getByRole("button", { name: "Despesa" }).click();
    await page.getByLabel("Valor (R$)").fill("40");
    await page.getByLabel("Categoria").selectOption({ index: 1 });
    await page.getByLabel("Competência", { exact: true }).fill(hojeIso());
    await page.getByLabel("Descrição").fill("Papel e toner");
    await page.getByRole("button", { name: "Registrar", exact: true }).click();
    await expect(page.getByText("Papel e toner")).toBeVisible();
    await expect(page.getByText("Despesas a pagar")).toBeVisible();
  });
});

/* ========================================================== equipe */

test.describe("equipe", () => {
  test.beforeEach(async ({ page }) => {
    await entrar(page, conta);
  });

  test("cadastra a secretária, ela entra sem ver o financeiro, e pode ser desativada", async ({
    page,
    browser,
  }) => {
    const email = `e2e-sec-${Date.now()}@exemplo.com`;
    await page.goto("/team");
    await page.getByRole("button", { name: "Cadastrar secretária" }).click();
    await page.getByLabel("Nome", { exact: true }).fill("Secretária pela Tela");
    await page.getByLabel("E-mail").fill(email);
    await page.getByLabel("Senha inicial").fill("recepcao2026");
    await page.getByRole("button", { name: "Cadastrar", exact: true }).click();
    await expect(page.locator(".team-name", { hasText: "Secretária pela Tela" })).toBeVisible();

    // A secretária entra com a senha inicial e não tem o financeiro no menu.
    const anonima = await request.newContext({ baseURL: "http://localhost:5173" });
    const login = await anonima.post("/api/auth/login", { data: { email, password: "recepcao2026" } });
    expect(login.ok(), await login.text()).toBeTruthy();
    const { token } = await login.json();
    await anonima.dispose();

    const contexto = await browser.newContext();
    await contexto.addInitScript((t) => window.localStorage.setItem("nutriplan.token", t), token);
    const dela = await contexto.newPage();
    await dela.goto("/schedule");
    await expect(dela.getByRole("heading", { name: "Agenda" })).toBeVisible();
    await expect(dela.getByRole("link", { name: "Financeiro" })).toHaveCount(0);
    await contexto.close();

    page.once("dialog", (d) => d.accept());
    await page.getByRole("button", { name: "Desativar" }).first().click();
    await expect(page.getByRole("button", { name: "Reativar" }).first()).toBeVisible();
    await page.getByRole("button", { name: "Reativar" }).first().click();
    await expect(page.getByRole("button", { name: "Desativar" }).first()).toBeVisible();
  });
});

/* ============================================================ tema */

test.describe("tema", () => {
  test.beforeEach(async ({ page }) => {
    await entrar(page, conta);
  });

  test("o tema escolhido vale na hora e continua depois de recarregar", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/patients");
    await page.getByRole("button", { name: "Escuro" }).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.reload();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.getByRole("button", { name: "Claro" }).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  });
});
