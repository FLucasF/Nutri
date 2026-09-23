import { test, expect } from "@playwright/test";

import {
  acharAlimento,
  criarConta,
  criarPaciente,
  entrar,
  type Conta,
} from "./apoio/conta";

/**
 * Os endereços que o sistema entrega para fora.
 *
 * O link do plano, o do questionário e o da agenda são montados no navegador,
 * com o caminho escrito à mão numa string. Nada obriga essa string a concordar
 * com a rota declarada — e foi o que aconteceu: a passagem que traduziu as
 * rotas para o inglês renomeou `/plano` para `/plan` e deixou o texto do link
 * para trás. O sistema continuou compilando, os testes continuaram passando, e
 * o paciente recebia um link que abria "Página não encontrada".
 *
 * Este arquivo existe para que isso não volte a passar em silêncio: ele não
 * confere o texto do endereço, confere que o endereço abre.
 */

let conta: Conta;
let paciente: { id: number; name: string };
let arroz: number;

test.beforeAll(async () => {
  conta = await criarConta("Nutri dos Links");
  paciente = await criarPaciente(conta, { name: "Link de Teste" });
  // Um plano sem item nenhum não pode ser publicado: o paciente abriria o
  // link e acharia uma página vazia, que é pior do que não ter link.
  arroz = await acharAlimento(conta, "Arroz, integral");
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

test("o link do plano que o nutricionista copia abre o plano", async ({ page }) => {
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano do link",
      patientId: paciente.id,
      method: "FOODS",
      meals: [{ name: "Café da Manhã", items: [{ foodId: arroz, quantity: 100 }] }],
    },
  });
  const plano = await criado.json();
  await conta.api.post(`/api/prescriptions/${plano.id}/publish`, {});

  await page.goto(`/prescriptions/${plano.id}`);

  // O endereço sai da própria tela, e não de uma constante do teste: é
  // exatamente o que vai para a área de transferência do nutricionista.
  const endereco = await page
    .getByText(/\/plan\/|\/plano\//)
    .first()
    .innerText();
  expect(endereco, "a tela precisa mostrar o link do paciente").toContain("/plan");

  await page.goto(endereco.trim());
  await expect(
    page.getByText("Página não encontrada."),
    "o link entregue ao paciente não pode cair em página inexistente",
  ).toHaveCount(0);
  await expect(page.getByText("Plano do link")).toBeVisible();
});

test("toda rota pública declarada responde", async ({ page }) => {
  // Um plano publicado, para haver o que abrir.
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano das rotas",
      patientId: paciente.id,
      method: "FOODS",
      meals: [{ name: "Almoço", items: [{ foodId: arroz, quantity: 100 }] }],
    },
  });
  const plano = await criado.json();
  const publicado = await conta.api.post(`/api/prescriptions/${plano.id}/publish`, {});
  const identificador = (await publicado.json()).publicIdentifier;

  await page.goto(`/plan/${identificador}`);
  await expect(page.getByText("Página não encontrada.")).toHaveCount(0);
  await expect(page.getByText("Plano das rotas")).toBeVisible();
});

test("o endereço da agenda em .ics é o da rota que serve o arquivo", async () => {
  // O caminho é montado no navegador; aqui ele é conferido contra o servidor.
  const assinatura = await conta.api.post("/api/schedule/subscription", {});
  expect(assinatura.status()).toBe(200);
  const { token } = await assinatura.json();

  const arquivo = await conta.api.get(`/api/public/schedule/${token}.ics`);
  expect(arquivo.status(), "a rota do .ics mudou de lugar").toBe(200);
  expect(await arquivo.text()).toContain("BEGIN:VCALENDAR");
});
