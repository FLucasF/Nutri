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
