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

/**
 * Modelos de anotação.
 *
 * "Eu tenho que ter a possibilidade de salvar modelos, e adicionar ele no
 * bloco de anotações para um paciente hipotético, e com a MESMA FORMATAÇÃO do
 * cadastro." O modelo guarda o documento do editor inteiro; escolher o modelo
 * na ficha começa a anotação com ele, negrito incluído.
 */
test("um modelo de anotação começa a anotação de qualquer paciente, com a formatação", async ({
  page,
}) => {
  const corpo = JSON.stringify({
    type: "doc",
    content: [
      {
        type: "paragraph",
        content: [
          { type: "text", marks: [{ type: "bold" }], text: "Queixa principal:" },
          { type: "text", text: " " },
        ],
      },
    ],
  });
  const salvo = await conta.api.post("/api/note-templates", {
    data: { name: "Primeira consulta", body: corpo },
  });
  expect(salvo.status(), await salvo.text()).toBe(201);

  // O mesmo nome sobrescreve, em vez de duplicar.
  const denovo = await conta.api.post("/api/note-templates", {
    data: { name: "primeira consulta", body: corpo },
  });
  expect(denovo.status(), await denovo.text()).toBe(201);
  const lista = await (await conta.api.get("/api/note-templates")).json();
  expect(lista.filter((t: { name: string }) => /primeira consulta/i.test(t.name))).toHaveLength(1);

  await page.goto(`/patients/${paciente.id}`);
  await page.getByLabel("Modelo").selectOption({ label: "primeira consulta" });
  // O texto do modelo aparece no editor, em negrito.
  await expect(page.locator(".patient-notes strong", { hasText: "Queixa principal:" })).toBeVisible();

  await page.getByRole("button", { name: "Anotar" }).click();
  await expect(
    page.locator(".feed-anotacoes strong", { hasText: "Queixa principal:" }),
  ).toBeVisible();
});

/**
 * O pedido de exames com grupos, interruptor e PDF.
 *
 * "Digamos que eu apertei Marcadores Glicídicos e aparecem uns 6 fatores, eu
 * tenho que ter a liberdade de retirar quantos eu quiser, e os que forem
 * ficando lá é o que irão para o PDF, separados pelos agrupamentos deles. E
 * não é para excluir diretamente [...] mas que ele fique inativo."
 */
test("o pedido guarda o painel de origem, desliga sem apagar e sai em PDF", async () => {
  const catalogo = await (await conta.api.get("/api/labtests/parameters")).json();
  expect(catalogo.length).toBeGreaterThan(3);
  const [a, b, c] = catalogo;

  const criado = await conta.api.post(`/api/patients/${paciente.id}/requests-from-labtest`, {
    data: {
      date: "2026-09-01",
      items: [
        { parameterId: a.id, panelName: "Marcadores glicídicos", active: true },
        { parameterId: b.id, panelName: "Marcadores glicídicos", active: false },
        { parameterId: c.id },
      ],
      notes: "Jejum de 8 horas.",
    },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const pedido = await criado.json();

  // Os três ficam no pedido; só dois estão ligados, e são os que vão para o PDF.
  expect(pedido.items).toHaveLength(3);
  expect(pedido.items[1].active).toBe(false);
  expect(pedido.items[0].panelName).toBe("Marcadores glicídicos");
  expect(pedido.labtests).toEqual([a.name, c.name]);

  const folha = await conta.api.get(
    `/api/patients/${paciente.id}/requests-from-labtest/${pedido.id}/pdf`,
  );
  expect(folha.status(), await folha.text()).toBe(200);
  expect(folha.headers()["content-type"]).toContain("application/pdf");
  const bytes = await folha.body();
  expect(bytes.subarray(0, 4).toString()).toBe("%PDF");

  // Religar não refaz o pedido: é o mesmo, com o exame de volta.
  const religado = await conta.api.put(
    `/api/patients/${paciente.id}/requests-from-labtest/${pedido.id}`,
    {
      data: {
        items: pedido.items.map((i: { parameterId: number; panelName?: string }) => ({
          parameterId: i.parameterId,
          panelName: i.panelName,
          active: true,
        })),
        notes: "Jejum de 8 horas.",
      },
    },
  );
  expect(religado.status(), await religado.text()).toBe(200);
  expect((await religado.json()).labtests).toHaveLength(3);
});

test("na tela, o painel entra no pedido e cada exame tem o seu interruptor", async ({ page }) => {
  await page.goto(`/patients/${paciente.id}/labtests`);
  await page.getByRole("button", { name: "Solicitar exames" }).click();

  // Um clique no primeiro painel do sistema traz os exames dele para o pedido.
  const painel = page.locator(".paineis-botoes button").first();
  await expect(painel).toBeVisible();
  await painel.click();

  const pedido = page.getByRole("region", { name: "O pedido" });
  await expect(pedido).toBeVisible();
  const interruptores = pedido.getByRole("checkbox");
  const total = await interruptores.count();
  expect(total).toBeGreaterThan(0);

  // Desligar um deles não o tira do pedido: a linha fica, riscada.
  await interruptores.first().uncheck();
  await expect(pedido.locator("li.inativo")).toHaveCount(1);
  await expect(pedido.getByRole("checkbox")).toHaveCount(total);

  const enviar = page.getByRole("button", { name: /^Solicitar \d+ exame/ });
  await expect(enviar).toContainText(String(total - 1));
  await enviar.click();

  // O pedido entregue aparece com o PDF à mão.
  await expect(page.getByRole("heading", { name: "Solicitações" })).toBeVisible();
  await expect(page.getByRole("button", { name: "PDF" }).first()).toBeVisible();
  await expect(page.getByText("1 desligado")).toBeVisible();
});
