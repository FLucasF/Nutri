import { test, expect } from "@playwright/test";

import { criarConta, criarPaciente, type Conta } from "./apoio/conta";

/**
 * A EER de 2023, conferida contra a publicação.
 *
 * O cliente relatou que o cálculo estava errado. Estes casos fixam o que a
 * DRI 2023 publica para adultos, nos quatro níveis de atividade, com os
 * coeficientes da fonte escritos aqui no teste. Se alguém mexer na equação —
 * ou se ela estiver errada — é aqui que aparece, com o número esperado ao lado
 * do obtido, em vez de um "está errado" sem medida.
 *
 * Mulher, 35 anos, 164 cm, 80,3 kg.
 */

const IDADE = 35;
const ALTURA = 164;
const PESO = 80.3;

const CASOS: { nivel: string; intercepto: number; idade: number; altura: number; peso: number }[] = [
  { nivel: "INACTIVE", intercepto: 584.9, idade: 7.01, altura: 5.72, peso: 11.71 },
  { nivel: "LOW_ACTIVE", intercepto: 575.77, idade: 7.01, altura: 6.6, peso: 12.14 },
  { nivel: "ACTIVE", intercepto: 710.25, idade: 7.01, altura: 6.54, peso: 12.34 },
  { nivel: "VERY_ACTIVE", intercepto: 511.83, idade: 7.01, altura: 9.07, peso: 12.56 },
];

let conta: Conta;
let paciente: { id: number; name: string };

test.beforeAll(async () => {
  conta = await criarConta("Nutri da Energia");
  paciente = await criarPaciente(conta, {
    name: "Energia de Teste",
    sex: "FEMALE",
    dateBirth: "1991-03-14",
  });
});

test.afterAll(async () => {
  await conta.api.dispose();
});

for (const caso of CASOS) {
  test(`a EER 2023 bate com a publicação em ${caso.nivel}`, async () => {
    const criado = await conta.api.post("/api/energy-plans", {
      data: {
        patientId: paciente.id,
        name: `conferência ${caso.nivel}`,
        date: "2026-09-22",
        weightKg: PESO,
        heightCm: ALTURA,
        activityLevel: caso.nivel,
        equations: ["EER_2023"],
      },
    });
    expect(criado.status(), await criado.text()).toBe(201);
    const plano = await criado.json();

    expect(plano.ageYears, "a idade usada na equação").toBe(IDADE);

    const esperado =
      caso.intercepto - caso.idade * IDADE + caso.altura * ALTURA + caso.peso * PESO;
    const obtido = Number(plano.equations[0].totalKcal);
    expect(obtido).toBeCloseTo(esperado, 1);

    // A EER responde o gasto do dia inteiro: aplicar de novo um fator de
    // atividade por fora contaria a atividade duas vezes.
    expect(plano.equations[0].basalKcal ?? null).toBeNull();

    await conta.api.delete(`/api/energy-plans/${plano.id}`);
  });
}

/**
 * O "cardápio de 10 kcal".
 *
 * O cliente programou um peso alvo com data próxima e a meta importada no
 * editor veio zerada: a programação de peso é peso × 7 700 kcal dividido
 * pelos dias, e nada segurava o desconto. Ele pediu para não limitar — a
 * decisão é dele — e sim avisar. Então o número continua o mesmo, e vem com
 * os avisos que o explicam.
 */
test("a programação de peso agressiva vem com os avisos que a explicam", async () => {
  const criado = await conta.api.post("/api/energy-plans", {
    data: {
      patientId: paciente.id,
      name: "meta agressiva",
      date: "2026-09-22",
      weightKg: PESO,
      heightCm: ALTURA,
      activityLevel: "LOW_ACTIVE",
      equations: ["EER_2023"],
      // 10 kg em 30 dias: 2 567 kcal por dia a menos, mais do que o gasto.
      targetWeightKg: PESO - 10,
      targetDate: "2026-10-22",
    },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const plano = await criado.json();

  // O cálculo não muda: o prescrito ainda zera.
  expect(Number(plano.prescribedKcal)).toBe(0);
  // Mas ele não sai mais sozinho.
  expect(plano.warnings.join(" ")).toContain("kg por semana");
  expect(plano.warnings.join(" ")).toContain("ficou em zero");

  await conta.api.delete(`/api/energy-plans/${plano.id}`);
});

test("um cálculo comum não tem aviso nenhum", async () => {
  const criado = await conta.api.post("/api/energy-plans", {
    data: {
      patientId: paciente.id,
      name: "sem programação",
      date: "2026-09-22",
      weightKg: PESO,
      heightCm: ALTURA,
      activityLevel: "LOW_ACTIVE",
      equations: ["EER_2023"],
    },
  });
  expect(criado.status(), await criado.text()).toBe(201);
  const plano = await criado.json();
  expect(plano.warnings).toEqual([]);
  await conta.api.delete(`/api/energy-plans/${plano.id}`);
});
