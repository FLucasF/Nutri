import { test, expect } from "@playwright/test";

import { acharAlimento, criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * O preparo da receita viaja junto para o cardápio.
 *
 * "A observação que eu colocar em uma receita deve aparecer quando eu adicionar
 * a receita no cardápio, para o usuário ler como fazer quando receber o PDF."
 *
 * Antes o modo de preparo ficava só no cadastro da receita: o paciente recebia
 * "Panqueca de banana — 120 g" e nenhuma instrução de como fazê-la.
 */

let conta: Conta;
let paciente: { id: number; name: string };
let receita: { id: number; name: string };

/** O preparo como o editor o grava: documento, não frase. */
const PREPARO = JSON.stringify({
  type: "doc",
  content: [
    {
      type: "paragraph",
      content: [{ type: "text", text: "Amasse a banana com o ovo até ficar homogêneo." }],
    },
    {
      type: "bulletList",
      content: [
        {
          type: "listItem",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "Frigideira bem quente." }] },
          ],
        },
        {
          type: "listItem",
          content: [
            {
              type: "paragraph",
              content: [{ type: "text", text: "Dois minutos de cada lado." }],
            },
          ],
        },
      ],
    },
  ],
});

test.beforeAll(async () => {
  conta = await criarConta("Nutri das Receitas");
  paciente = await criarPaciente(conta, { name: "Receita de Teste" });

  const banana = await acharAlimento(conta, "Banana, prata, crua");
  const ovo = await acharAlimento(conta, "Ovo, de galinha");

  const criada = await conta.api.post("/api/recipes", {
    data: {
      name: "Panqueca de banana",
      yieldGrams: 180,
      servings: 2,
      modeInstructions: PREPARO,
      ingredients: [
        { foodId: banana, quantity: 120 },
        { foodId: ovo, quantity: 60 },
      ],
    },
  });
  expect(criada.status(), await criada.text()).toBe(201);
  receita = await criada.json();
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

test("o preparo da receita chega ao plano do paciente e ao PDF", async ({ page }) => {
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Plano com receita",
      patientId: paciente.id,
      method: "FOODS",
      meals: [
        {
          name: "Café da Manhã",
          items: [{ foodId: receita.id, quantity: 180 }],
        },
      ],
    },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const plano = await criado.json();

  const publicado = await conta.api.post(`/api/prescriptions/${plano.id}/publish`, {});
  expect(publicado.status()).toBe(200);
  const identificador = (await publicado.json()).publicIdentifier;

  // O que o paciente recebe pelo link.
  const visao = await conta.api.get(`/api/public/plans/${identificador}`);
  const corpo = await visao.json();
  expect(corpo.recipes, "a receita usada precisa vir com o preparo").toHaveLength(1);
  expect(corpo.recipes[0].name).toBe("Panqueca de banana");
  expect(corpo.recipes[0].modeInstructions).toContain("Amasse a banana");

  // E o PDF sai com a seção.
  const folha = await conta.api.get(`/api/prescriptions/${plano.id}/pdf`);
  expect(folha.status()).toBe(200);
  const bytes = await folha.body();
  expect(bytes.subarray(0, 4).toString()).toBe("%PDF");

  // A página do paciente mostra o preparo, e não o JSON do documento.
  await page.goto(`/plan/${identificador}`);
  await expect(page.getByRole("heading", { name: "Modo de preparo" })).toBeVisible();
  await expect(page.getByText("Amasse a banana com o ovo")).toBeVisible();
  await expect(page.getByText("Dois minutos de cada lado")).toBeVisible();
  await expect(page.getByText('"type":"doc"')).toHaveCount(0);
});

test("a mesma receita em duas refeições aparece uma vez só", async ({ page }) => {
  const criado = await conta.api.post("/api/prescriptions", {
    data: {
      title: "Receita repetida",
      patientId: paciente.id,
      method: "FOODS",
      meals: [
        { name: "Café da Manhã", items: [{ foodId: receita.id, quantity: 180 }] },
        { name: "Lanche da Tarde", items: [{ foodId: receita.id, quantity: 90 }] },
      ],
    },
  });
  const plano = await criado.json();
  const publicado = await conta.api.post(`/api/prescriptions/${plano.id}/publish`, {});
  const identificador = (await publicado.json()).publicIdentifier;

  const visao = await conta.api.get(`/api/public/plans/${identificador}`);
  const corpo = await visao.json();
  // Repetir o preparo dobraria a folha sem dizer nada de novo.
  expect(corpo.recipes).toHaveLength(1);

  await page.goto(`/plan/${identificador}`);
  await expect(page.getByText("Amasse a banana com o ovo")).toHaveCount(1);
});
