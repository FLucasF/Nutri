import { test, expect, type Page } from "@playwright/test";

import { acharAlimento, criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * O editor de cardápio — a tela em que o nutricionista passa o dia.
 *
 * Os cenários aqui saem da lista que o cliente mandou depois de usar o
 * sistema. Cada um descreve o que ele esperava que acontecesse, e não o que
 * acontece hoje: um teste escrito sobre o comportamento defeituoso passa a
 * defender o defeito.
 */

let conta: Conta;
let paciente: { id: number; name: string };

test.beforeAll(async () => {
  conta = await criarConta("Nutri do Cardápio");
  paciente = await criarPaciente(conta, { name: "Cardápio de Teste" });
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

/** Abre um plano novo já com o paciente escolhido. */
async function abrirPlanoNovo(page: Page) {
  await page.goto("/prescriptions/new");
  await expect(page.getByRole("heading", { name: "Novo plano" })).toBeVisible();
  await page.getByLabel("Título do plano").fill("Plano do teste");
  await page.getByLabel("Paciente", { exact: true }).selectOption({ label: paciente.name });
}

/** Escolhe um alimento da base no primeiro campo de busca disponível. */
async function escolherAlimento(page: Page, campo: ReturnType<Page["getByLabel"]>, termo: string) {
  await campo.fill(termo);
  const sugestao = page.locator(".results-search button").first();
  await expect(sugestao).toBeVisible();
  await sugestao.click();
}

test("o plano novo já vem com as seis refeições do dia", async ({ page }) => {
  await abrirPlanoNovo(page);

  const nomes = page.getByLabel("Nome da refeição");
  await expect(nomes).toHaveCount(6);
  await expect(nomes.first()).toHaveValue("Café da Manhã");
  await expect(nomes.last()).toHaveValue("Ceia");
});

test("dá para acrescentar refeição mesmo depois de remover todas", async ({ page }) => {
  // O cliente relatou não achar o botão. Ele existe, mas some junto com a
  // última refeição — e aí o cardápio fica sem saída.
  await abrirPlanoNovo(page);

  const remover = page.getByRole("button", { name: "Remover" });
  for (let i = 0; i < 6; i++) {
    await remover.first().click();
  }
  await expect(page.getByLabel("Nome da refeição")).toHaveCount(0);

  const adicionar = page.getByRole("button", { name: "+ Adicionar refeição" });
  await expect(adicionar, "sem refeição nenhuma o cardápio vira um beco sem saída").toBeVisible();

  await adicionar.click();
  await expect(page.getByLabel("Nome da refeição")).toHaveCount(1);
});

test("o alimento escolhido na busca entra com gramas e medida caseira", async ({ page }) => {
  await abrirPlanoNovo(page);

  const primeiraRefeicao = page.locator(".meal").first();
  await primeiraRefeicao.getByRole("button", { name: "+ Adicionar item" }).click();

  await primeiraRefeicao.getByLabel("Alimento").first().fill("Arroz, integral");
  const sugestao = page.locator(".results-search button").first();
  await expect(sugestao).toBeVisible();
  // O nome vem da sugestão e não de uma constante: a ordem da busca depende do
  // acervo, e fixar "Arroz, integral, cozido" faria o teste falhar por causa
  // de um alimento novo na base, que não é defeito nenhum.
  const escolhido = (await sugestao.locator("> text, *").first().textContent()) ?? "";
  await sugestao.click();

  await expect(primeiraRefeicao.locator("strong").first()).not.toBeEmpty();
  await expect(primeiraRefeicao.getByLabel("Quantidade").first()).not.toHaveValue("");
  expect(escolhido.length).toBeGreaterThan(0);
});

test("o substituto busca na mesma tabela de alimentos do item", async ({ page }) => {
  // "O substituto não está dando busca na tabela de alimentos, tem que ser a
  // mesma do alimento que está para substituir." Sem alimento ligado o
  // substituto não tem macros, e a troca some do cálculo.
  await abrirPlanoNovo(page);

  const refeicao = page.locator(".meal").first();
  await refeicao.getByRole("button", { name: "+ Adicionar item" }).click();
  await escolherAlimento(page, refeicao.getByLabel("Alimento").first(), "Arroz, integral");

  await refeicao.getByRole("button", { name: "+ substituição" }).click();

  const buscaSubstituto = refeicao.getByLabel("Alimento da substituição").first();
  await expect(
    buscaSubstituto,
    "o substituto precisa da mesma busca do item, não de um texto solto",
  ).toBeVisible();

  await escolherAlimento(page, buscaSubstituto, "Batata, doce");

  // O que se afirma é que um alimento foi escolhido — a busca some e dá lugar
  // ao nome, com o botão de trocar. Fixar o nome exato faria o teste depender
  // da ordem da busca, que muda com o acervo e não é defeito nenhum.
  await expect(refeicao.getByLabel("Alimento da substituição")).toHaveCount(0);
  await expect(refeicao.getByRole("button", { name: "trocar alimento" })).toHaveCount(2);
});

test("o alimento escolhido para o substituto fica escolhido", async ({ page }) => {
  // "Ele pesquisa e quando eu clico ele não deixa escolher, eu fico clicando e
  // não mudando, em um loop."
  //
  // Escolher são duas gravações: o alimento, e a porção que vem depois de
  // buscar as medidas caseiras. A segunda era montada sobre a lista de antes
  // da primeira, e apagava o alimento que acabara de entrar — a busca voltava,
  // e clicar de novo repetia o ciclo.
  await abrirPlanoNovo(page);

  const refeicao = page.locator(".meal").first();
  await refeicao.getByRole("button", { name: "+ Adicionar item" }).click();
  await escolherAlimento(page, refeicao.getByLabel("Alimento").first(), "Arroz, integral");
  await refeicao.getByRole("button", { name: "+ substituição" }).click();

  await escolherAlimento(
    page,
    refeicao.getByLabel("Alimento da substituição").first(),
    "Batata, doce",
  );

  // O campo de busca do substituto tem de sumir e dar lugar ao alimento. Se
  // voltar, é o laço de novo.
  await expect(
    refeicao.getByLabel("Alimento da substituição"),
    "a busca não pode reaparecer depois da escolha",
  ).toHaveCount(0);
  await expect(refeicao.getByRole("button", { name: "trocar alimento" })).toHaveCount(2);

  // E a porção chega junto, com a medida caseira do alimento escolhido.
  await expect(refeicao.getByLabel("Quantidade da substituição")).not.toHaveValue("");

  // O que foi escolhido chega ao servidor, e não só à tela.
  await expect.poll(async () => {
    const id = Number(page.url().split("/").pop());
    if (!Number.isFinite(id)) return null;
    const salvo = await conta.api.get(`/api/prescriptions/${id}`);
    const corpo = await salvo.json();
    return corpo.meals?.[0]?.items?.[0]?.substitutions?.[0]?.foodId ?? null;
  }, { timeout: 10_000 }).not.toBeNull();
});

test("os totais do dia acompanham a edição, sem precisar salvar", async ({ page }) => {
  // "Em vez de salvar para calcular, deve salvar em tempo real os planos de
  // alimento para ver o feedback em tempo real."
  await abrirPlanoNovo(page);

  const refeicao = page.locator(".meal").first();
  await refeicao.getByRole("button", { name: "+ Adicionar item" }).click();
  await escolherAlimento(page, refeicao.getByLabel("Alimento").first(), "Arroz, integral");
  await refeicao.getByLabel("Quantidade").first().fill("200");

  const totais = page.locator(".panel-totals");
  await expect(totais.getByText(/kcal/)).toBeVisible();
  await expect(
    totais.getByText("Salve o plano para calcular."),
    "o total não pode depender de lembrar de salvar",
  ).toHaveCount(0);

  // Dobrar a porção precisa mover o número, e não só quando o plano for gravado.
  const antes = await totais.innerText();
  await refeicao.getByLabel("Quantidade").first().fill("400");
  await expect
    .poll(async () => totais.innerText(), { timeout: 10_000 })
    .not.toBe(antes);
});

test("o PDF sai com o que está na tela, sem clicar em salvar", async ({ page }) => {
  // "Quando gerar um pdf de plano não deve ser necessário salvar antes, pois
  // esta precisando salvar para gerar o pdf corretamente."
  await abrirPlanoNovo(page);

  const refeicao = page.locator(".meal").first();
  await refeicao.getByRole("button", { name: "+ Adicionar item" }).click();
  await escolherAlimento(page, refeicao.getByLabel("Alimento").first(), "Arroz, integral");

  // Espera o plano existir no servidor, mas sem nenhum clique em "Salvar".
  await expect(page.getByRole("button", { name: /Plano em PDF/ })).toBeVisible();
  const endereco = page.url();
  const planoId = Number(endereco.split("/").pop());
  expect(Number.isFinite(planoId)).toBeTruthy();

  // Agora a alteração que não vai ser salva à mão.
  await refeicao.getByLabel("Nome da refeição").fill("Desjejum renomeado");

  const [resposta] = await Promise.all([
    page.waitForResponse((r) => r.url().includes(`/prescriptions/${planoId}/pdf`)),
    page.getByRole("button", { name: /Plano em PDF/ }).click(),
  ]);
  expect(resposta.status()).toBe(200);

  // O que o servidor imprimiu tem de ser o que estava na tela.
  const salvo = await conta.api.get(`/api/prescriptions/${planoId}`);
  const corpo = await salvo.json();
  expect(
    corpo.meals[0].name,
    "o PDF saiu da versão do servidor, que precisa refletir a tela",
  ).toBe("Desjejum renomeado");
});
