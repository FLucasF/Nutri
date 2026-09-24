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

/**
 * O que o cliente lê no WebDiet e não lia aqui.
 *
 * "Quando o vi só tive resultados como % de gordura e IMC. Não tive o limite
 * de peso superior e inferior de acordo com o IMC. Preciso de todas as
 * informações que o WebDiet me fornece a partir dos dados que eu cadastro."
 * Petroski entra com as quatro dobras dele; a densidade, a soma, a
 * classificação, a faixa de peso e o fracionamento saem da mesma avaliação.
 */
test("Petroski estima a composição, e a avaliação traz faixa de peso, densidade e fracionamento", async () => {
  const alguem = await criarPaciente(conta, {
    name: "Petroski de Teste",
    sex: "FEMALE",
    dateBirth: "1990-01-01",
  });
  const avaliacao = await criarAvaliacao(conta, alguem.id, {
    date: "2026-09-01",
    weightKg: 70,
    heightCm: 170,
    skinfolds: { MEAN_AXILLARY: 15, SUPRAILIAC: 18, THIGH: 25, CALF: 14, TRICEPS: 20 },
    diameterWrist: 5.5,
    diameterFemur: 9.5,
    circumferences: [{ site: "ARM_RELAXED", side: "SINGLE", valueCm: 29 }],
    protocolComposition: "PETROSKI",
  });

  // Mulher de 36 anos, soma 72 mm: D = 1,1954713 − 0,07513507·log10(72) − 0,00041072·36.
  expect(avaliacao.composition.protocol).toBe("PETROSKI");
  expect(Number(avaliacao.composition.skinfoldSumMm)).toBe(72);
  expect(Number(avaliacao.composition.density)).toBeCloseTo(1.0411, 3);
  expect(Number(avaliacao.composition.percentageFat)).toBeCloseTo(25.4, 0);
  // Pollock e Wilmore, mulher de 36 a 45: 24 a 26 é "acima da média"; ideal 16 a 23.
  expect(avaliacao.composition.fatClassification.value).toBe("ABOVE_AVERAGE");
  expect(Number(avaliacao.composition.fatIdealMin)).toBe(16);
  expect(Number(avaliacao.composition.fatIdealMax)).toBe(23);

  // 18,5 e 25 kg/m² para 1,70 m.
  expect(Number(avaliacao.healthyWeight.minimumKg)).toBeCloseTo(53.5, 1);
  expect(Number(avaliacao.healthyWeight.maximumKg)).toBeCloseTo(72.3, 1);

  // Braço relaxado 29 cm menos π vezes 20 mm.
  expect(Number(avaliacao.armMuscle.value.circumferenceCm)).toBeCloseTo(22.72, 1);

  // Osso por Von Döbeln (≈10,9 kg), resíduo de Würch (20,9% de 70 = 14,63), músculo é o resto.
  expect(Number(avaliacao.fractionation.boneMassKg.value)).toBeCloseTo(10.87, 1);
  expect(Number(avaliacao.fractionation.residualMassKg.value)).toBeCloseTo(14.63, 1);
  const musculo = Number(avaliacao.fractionation.muscleMassKg.value);
  expect(musculo).toBeGreaterThan(20);
  expect(musculo).toBeLessThan(35);
});

test("qualquer avaliação do histórico abre para leitura, e a faixa de peso aparece", async ({
  page,
}) => {
  await page.goto(`/patients/${paciente.id}/anthropometry`);
  await expect(page.getByRole("heading", { name: "Última avaliação" })).toBeVisible();
  // 1,64 m: 49,8 a 67,2 kg.
  await expect(page.getByText("Faixa de peso saudável").first()).toBeVisible();
  await expect(page.getByText("49,8 a 67,2")).toBeVisible();

  // A última linha do histórico é a primeira avaliação, de março.
  // exact: "Remover" também contém "ver".
  await page.getByRole("button", { name: "Ver", exact: true }).last().click();
  await expect(page.getByRole("heading", { name: "Avaliação de 10/03/2026" })).toBeVisible();

  await page.getByRole("button", { name: "ver a última", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Última avaliação" })).toBeVisible();
});
