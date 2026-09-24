import { expect, request } from "@playwright/test";

import { acharAlimento, criarAvaliacao, criarPaciente, type Conta } from "./conta";

/**
 * Um consultório com tudo preenchido, para que cada tela tenha o que mostrar
 * em todos os seus estados: paciente com TAGs, anotações e anexo; três
 * avaliações; dois cálculos energéticos (um com avisos); anamnese com campos;
 * exames com resultados e pedido; questionário enviado e outro respondido;
 * agenda de hoje; financeiro pago e pendente; parceiro que indicou o
 * paciente; pacote de três encontros na consulta de hoje, já paga; consulta
 * realizada no mês passado (com atestado); receita parcelada em três;
 * orientação anexada ao plano;
 * receita usada no cardápio; refeição salva; plano publicado com item à
 * vontade, separador e substituição; um rascunho; um modelo; uma secretária.
 *
 * Entra tudo pela API. O que se olha depois é a tela.
 */

const BASE = process.env.E2E_URL ?? "http://localhost:5173";

export type CenarioCompleto = {
  paciente: { id: number; name: string };
  outroPaciente: { id: number; name: string };
  planoPublicado: { id: number; publicIdentifier: string };
  rascunho: { id: number };
  modelo: { id: number };
  receitaId: number;
  alimentoId: number;
  formularioEnviado: string;
  formularioRespondido: string;
  calculoComAvisos: number;
  hoje: string;
  parceiro: { id: number; name: string };
  pacote: { id: number; name: string };
  /** A consulta realizada no mês passado: tem atestado e ainda não foi paga. */
  consultaRealizada: number;
};

function docSimples(texto: string, negrito = false) {
  return JSON.stringify({
    type: "doc",
    content: [
      {
        type: "paragraph",
        content: [negrito ? { type: "text", marks: [{ type: "bold" }], text: texto } : { type: "text", text: texto }],
      },
    ],
  });
}

function isoLocal(data: Date) {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${data.getFullYear()}-${p(data.getMonth() + 1)}-${p(data.getDate())}`;
}

async function ok(resposta: Awaited<ReturnType<Conta["api"]["post"]>>, o: string) {
  expect(resposta.ok(), `${o}: ${resposta.status()} ${await resposta.text()}`).toBeTruthy();
  const texto = await resposta.text();
  return texto ? JSON.parse(texto) : null;
}

export async function montarCenarioCompleto(conta: Conta): Promise<CenarioCompleto> {
  const api = conta.api;
  const hoje = new Date();
  const hojeIso = isoLocal(hoje);
  const amanha = new Date(hoje.getTime() + 24 * 3600 * 1000);

  // --------------------------------------------------- parceiros e pacotes
  const parceiro = await ok(
    await api.post("/api/partners", {
      data: {
        name: "Academia Corpo Leve",
        kind: "Academia",
        contact: "(11) 98888-7777",
        notes: "Indica alunos do treino funcional.",
      },
    }),
    "parceiro",
  );
  const outroParceiro = await ok(
    await api.post("/api/partners", { data: { name: "Dra. Helena Prado", kind: "Médico" } }),
    "parceiro 2",
  );
  const pacote = await ok(
    await api.post("/api/packages", {
      data: {
        name: "Acompanhamento trimestral",
        amount: 900,
        sessions: 3,
        intervalDays: 30,
        notes: "Três encontros presenciais.",
      },
    }),
    "pacote",
  );
  await ok(await api.post("/api/packages", { data: { name: "Consulta avulsa", amount: 250 } }), "pacote 2");

  // ------------------------------------------------------------ pacientes
  const paciente = await criarPaciente(conta, {
    name: "Aparência Completa da Silva",
    partnerId: parceiro.id,
    sex: "FEMALE",
    dateBirth: "1990-05-20",
    cpf: "123.456.789-09",
    nickname: "Pat",
    email: "aparencia.completa@exemplo.com",
    phone: "(11) 99999-0000",
    goal: "Emagrecer 5 kg com saúde e manter a massa magra.",
    occupation: "Analista de sistemas",
  });
  const outroPaciente = await criarPaciente(conta, {
    name: "Segundo Paciente Longo Nome Para Testar Quebra",
    sex: "MALE",
    dateBirth: "1985-01-15",
  });
  const tags = await ok(await api.get("/api/patient-tags"), "tags");
  if (tags.length >= 2) {
    await ok(
      await api.put(`/api/patients/${paciente.id}/tags`, { data: { tagIds: [tags[0].id, tags[1].id] } }),
      "tags do paciente",
    );
  }
  await ok(
    await api.post(`/api/patients/${paciente.id}/notes`, {
      data: { body: docSimples("Paciente muito motivada; prefere receitas rápidas.") },
    }),
    "anotação",
  );
  await ok(
    await api.post("/api/note-templates", {
      data: { name: "Primeira consulta", body: docSimples("Queixa principal:", true) },
    }),
    "modelo de anotação",
  );
  await ok(
    await api.post(`/api/patients/${paciente.id}/attachments/links`, {
      data: {
        title: "Exames do laboratório anterior",
        url: "https://exemplo.com/laudo-2025.pdf",
        notes: "Laudo de 2025, antes do acompanhamento.",
        referenceDate: "2025-11-10",
      },
    }),
    "anexo",
  );

  // ----------------------------------------------------------- avaliações
  await criarAvaliacao(conta, paciente.id, {
    date: "2026-03-10",
    weightKg: 88.4,
    heightCm: 165,
    circumferences: [
      { site: "WAIST", side: "SINGLE", valueCm: 94 },
      { site: "HIP", side: "SINGLE", valueCm: 108 },
    ],
  });
  const completa = {
    heightCm: 165,
    skinfolds: { MEAN_AXILLARY: 18, SUPRAILIAC: 22, THIGH: 30, CALF: 16, TRICEPS: 24, SUBSCAPULAR: 20 },
    diameterWrist: 5.4,
    diameterFemur: 9.2,
    diameterHumerus: 6.1,
    circumferences: [
      { site: "WAIST", side: "SINGLE", valueCm: 90 },
      { site: "HIP", side: "SINGLE", valueCm: 106 },
      { site: "ARM_RELAXED", side: "RIGHT", valueCm: 31 },
      { site: "ARM_RELAXED", side: "LEFT", valueCm: 30.5 },
      { site: "CALF", side: "SINGLE", valueCm: 38 },
    ],
    biaFatPercentage: 34.2,
    biaFatMassKg: 29.1,
    biaMuscleMassKg: 26.4,
    biaLeanMassKg: 56,
    biaBodyWaterPercentage: 48.5,
    biaVisceralFat: 9,
    biaMetabolicAge: 41,
    protocolComposition: "PETROSKI",
    equationExpenditure: "MIFFLIN_ST_JEOR",
    factorActivity: 1.375,
    notes: docSimples("Medidas colhidas em jejum, pela manhã."),
  };
  await criarAvaliacao(conta, paciente.id, { ...completa, date: "2026-06-14", weightKg: 85.1 });
  await criarAvaliacao(conta, paciente.id, {
    ...completa,
    date: "2026-09-15",
    weightKg: 82.6,
    skinfolds: { MEAN_AXILLARY: 16, SUPRAILIAC: 20, THIGH: 27, CALF: 15, TRICEPS: 22, SUBSCAPULAR: 18 },
  });

  // ------------------------------------------------------ cálculo energético
  await ok(
    await api.post("/api/energy-plans", {
      data: {
        patientId: paciente.id,
        name: "Manutenção",
        date: hojeIso,
        weightKg: 82.6,
        heightCm: 165,
        activityLevel: "LOW_ACTIVE",
        equations: ["EER_2023", "MIFFLIN_ST_JEOR"],
      },
    }),
    "cálculo",
  );
  const agressivo = await ok(
    await api.post("/api/energy-plans", {
      data: {
        patientId: paciente.id,
        name: "Meta agressiva",
        date: hojeIso,
        weightKg: 82.6,
        heightCm: 165,
        activityLevel: "LOW_ACTIVE",
        injuryFactor: 1.1,
        metKcal: 150,
        equations: ["EER_2023"],
        targetWeightKg: 72.6,
        targetDate: isoLocal(new Date(hoje.getTime() + 30 * 24 * 3600 * 1000)),
      },
    }),
    "cálculo com avisos",
  );

  // -------------------------------------------------------------- anamnese
  const campos = await ok(
    await api.put("/api/anamnesis-fields", {
      data: {
        fields: [
          { label: "Queixa principal", showInListing: true },
          { label: "Histórico familiar", showInListing: true },
          { label: "Medicamentos em uso", showInListing: false },
        ],
      },
    }),
    "campos da anamnese",
  );
  await ok(
    await api.post("/api/anamneses", {
      data: {
        patientId: paciente.id,
        name: "Anamnese inicial",
        date: "2026-03-10",
        body: docSimples("Relata cansaço ao fim do dia e sono irregular. Come fora três vezes por semana."),
        values: campos.map((c: { id: number; label: string }, i: number) => ({
          fieldId: c.id,
          value: ["Cansaço e sono irregular", "Mãe hipertensa; pai diabético", i === 2 ? "Nenhum" : ""][i] ?? "",
        })),
      },
    }),
    "anamnese",
  );

  // ---------------------------------------------------------------- exames
  const parametros = await ok(await api.get("/api/labtests/parameters"), "parâmetros");
  const glicose =
    parametros.find((p: { name: string }) => /^glicemia de jejum/i.test(p.name)) ?? parametros[0];
  const colesterol =
    parametros.find((p: { name: string }) => /^colesterol total/i.test(p.name)) ?? parametros[1];
  for (const [data, valor] of [
    ["2026-03-10", 104],
    ["2026-09-15", 92],
  ] as const) {
    await ok(
      await api.post(`/api/patients/${paciente.id}/labtests`, {
        data: { parameterId: glicose.id, dateCollection: data, value: valor, notes: "Laboratório Central" },
      }),
      "resultado de glicose",
    );
  }
  await ok(
    await api.post(`/api/patients/${paciente.id}/labtests`, {
      data: { parameterId: colesterol.id, dateCollection: "2026-09-15", value: 212 },
    }),
    "resultado de colesterol",
  );
  await ok(
    await api.post(`/api/patients/${paciente.id}/requests-from-labtest`, {
      data: {
        date: "2026-09-15",
        items: [
          { parameterId: glicose.id, panelName: "Metabolismo da glicose", active: true },
          { parameterId: colesterol.id, panelName: "Metabolismo lipídico", active: true },
          { parameterId: parametros[2].id, panelName: "Metabolismo lipídico", active: false },
        ],
        notes: "Jejum de 12 horas.",
      },
    }),
    "pedido de exames",
  );

  // ---------------------------------------------------------- questionários
  const questionario = await ok(
    await api.post("/api/questionnaires", {
      data: {
        name: "Hábitos de sono",
        description: "Três perguntas sobre o sono da última semana.",
        scorable: true,
        cutoffRange: "0-2=Bom|3-4=Regular|5-99=Ruim",
        questions: [
          { statement: "Quantas horas você dorme por noite?", type: "NUMBER", required: true, highlight: true },
          {
            statement: "Acorda cansado?",
            type: "CHOICE_SINGLE",
            required: true,
            options: "Nunca=0|Às vezes=1|Sempre=2",
          },
          {
            statement: "O que atrapalha o seu sono?",
            type: "TEXT",
            required: false,
            ajuda: "Barulho, luz, preocupação…",
          },
          { statement: "Sobre a semana", type: "SECTION", ajuda: "Pense nos últimos sete dias." },
          { statement: "Descreva uma noite típica", type: "PARAGRAPH" },
          { statement: "Última noite mal dormida", type: "DATE" },
          { statement: "O que ajuda a dormir?", type: "MULTIPLE", options: "Chá|Leitura|Escuro", highlight: true },
        ],
      },
    }),
    "questionário",
  );
  const envio1 = await ok(
    await api.post(`/api/patients/${paciente.id}/questionnaires`, {
      data: { questionnaireId: questionario.id },
    }),
    "envio 1",
  );
  const envio2 = await ok(
    await api.post(`/api/patients/${paciente.id}/questionnaires`, {
      data: { questionnaireId: questionario.id },
    }),
    "envio 2",
  );
  const anonima = await request.newContext({ baseURL: BASE });
  const formulario = await ok(
    await anonima.get(`/api/public/questionnaires/${envio2.publicIdentifier}`),
    "formulário público",
  );
  await ok(
    await anonima.post(`/api/public/questionnaires/${envio2.publicIdentifier}`, {
      data: {
        answers: formulario.questions
          .filter((q: { type: string }) => q.type !== "SECTION")
          .map((q: { id: number; type: string }) => ({
            questionId: q.id,
            value:
              q.type === "NUMBER"
                ? "6"
                : q.type === "CHOICE_SINGLE"
                  ? "Às vezes"
                  : q.type === "DATE"
                    ? "2026-09-20"
                    : q.type === "MULTIPLE"
                      ? "Chá; Escuro"
                      : q.type === "PARAGRAPH"
                        ? "Deito tarde e acordo cedo; durmo em duas etapas."
                        : "Luz da rua.",
          })),
      },
    }),
    "resposta pública",
  );
  await anonima.dispose();

  // A anamnese pelo modelo, preenchida na consulta, e a pré-consulta importada.
  const pergunta = (statement: string) =>
    (questionario.questions as { id: number; statement: string }[]).find((q) => q.statement === statement)!.id;
  await ok(
    await api.post("/api/anamneses", {
      data: {
        patientId: paciente.id,
        name: "Retorno de junho",
        date: "2026-06-14",
        questionnaireId: questionario.id,
        answers: [
          { questionId: pergunta("Quantas horas você dorme por noite?"), value: "7" },
          { questionId: pergunta("Acorda cansado?"), value: "Nunca" },
          { questionId: pergunta("Descreva uma noite típica"), value: "Dorme às 23h e acorda às 6h." },
          { questionId: pergunta("O que ajuda a dormir?"), value: "Leitura; Escuro" },
        ],
      },
    }),
    "anamnese pelo modelo",
  );
  await ok(await api.post(`/api/anamneses/from-sending/${envio2.id}`, {}), "anamnese da pré-consulta");

  // ------------------------------------------------------------------ agenda
  const deHoje = await ok(
    await api.post("/api/schedule", {
      data: {
        patientId: paciente.id,
        start: `${hojeIso}T09:00:00`,
        durationMinutes: 60,
        type: "FOLLOWUP",
        notes: "Retorno de três meses.",
        packageId: pacote.id,
      },
    }),
    "atendimento de hoje",
  );
  await ok(
    await api.post(`/api/finance/appointments/${deHoje.id}/payment`, {
      data: { paymentMethod: "PIX", documentNumber: "REC-2026-014" },
    }),
    "pagamento da consulta de hoje",
  );
  await ok(
    await api.post("/api/schedule", {
      data: {
        patientId: outroPaciente.id,
        start: `${isoLocal(amanha)}T10:30:00`,
        durationMinutes: 90,
        type: "FIRST_CONSULTATION",
        partnerId: outroParceiro.id,
      },
    }),
    "atendimento de amanhã",
  );
  const mesPassado = new Date(hoje.getFullYear(), hoje.getMonth() - 1, Math.min(hoje.getDate(), 28));
  const realizada = await ok(
    await api.post("/api/schedule", {
      data: {
        patientId: paciente.id,
        start: `${isoLocal(mesPassado)}T14:00:00`,
        durationMinutes: 60,
        type: "FIRST_CONSULTATION",
        notes: "Primeira consulta.",
      },
    }),
    "consulta do mês passado",
  );
  await ok(
    await api.post(`/api/schedule/${realizada.id}/status`, { data: { status: "COMPLETED" } }),
    "consulta realizada",
  );

  // -------------------------------------------------------------- financeiro
  const receita = await ok(
    await api.post("/api/finance/transactions", {
      data: {
        type: "INCOME",
        value: 250,
        accrual: hojeIso,
        due: hojeIso,
        category: "Consulta",
        paymentMethod: "PIX",
        description: "Retorno",
        patientId: paciente.id,
      },
    }),
    "receita",
  );
  await ok(
    await api.post(`/api/finance/transactions/${receita.id}/pay`, { data: { datePayment: hojeIso } }),
    "baixa",
  );
  await ok(
    await api.post("/api/finance/transactions", {
      data: {
        type: "INCOME",
        value: 600,
        accrual: hojeIso,
        due: hojeIso,
        category: "Pacote",
        description: "Pacote parcelado em três",
        patientId: paciente.id,
        packageId: pacote.id,
        documentNumber: "NF 1234",
        installments: 3,
      },
    }),
    "receita parcelada",
  );
  await ok(
    await api.post("/api/finance/transactions", {
      data: {
        type: "EXPENSE",
        value: 80,
        accrual: hojeIso,
        due: isoLocal(new Date(hoje.getTime() + 7 * 24 * 3600 * 1000)),
        category: "Material de escritório",
        description: "Papel e toner",
      },
    }),
    "despesa",
  );

  // ------------------------------------------------------- alimentos e plano
  const [arroz, feijao, frango, banana, leite, alface, ovo] = await Promise.all([
    acharAlimento(conta, "Arroz, integral, cozido"),
    acharAlimento(conta, "Feijão, carioca"),
    acharAlimento(conta, "Frango, peito"),
    acharAlimento(conta, "Banana, prata"),
    acharAlimento(conta, "Leite, de vaca"),
    acharAlimento(conta, "Alface"),
    acharAlimento(conta, "Ovo, de galinha, inteiro"),
  ]);

  const receitaCriada = await ok(
    await api.post("/api/recipes", {
      data: {
        name: "Omelete de forno com queijo",
        group: "Ovos e derivados",
        yieldGrams: 300,
        servings: 2,
        modeInstructions: "Bata os ovos com o leite, tempere e leve ao forno por 20 minutos a 180 °C.",
        ingredients: [
          { foodId: ovo, quantity: 150 },
          { foodId: leite, quantity: 100 },
        ],
      },
    }),
    "receita",
  );
  const receitaId: number = receitaCriada.foodId ?? receitaCriada.id;

  await ok(
    await api.post("/api/meal-favorites", {
      data: {
        name: "Lanche padrão da tarde",
        meal: { name: "Lanche da Tarde", time: "16:00:00", items: [{ foodId: banana, quantity: 100 }] },
      },
    }),
    "refeição salva",
  );

  const orientacao = await ok(
    await api.post("/api/handouts", {
      data: {
        title: "Hidratação ao longo do dia",
        body: docSimples("Beba 2 litros de água por dia, começando ao acordar."),
      },
    }),
    "orientação",
  );

  const meals = [
    {
      name: "Café da Manhã",
      time: "07:00",
      notes: docSimples("Pode trocar o leite por iogurte natural."),
      items: [
        { foodId: leite, quantity: 200 },
        {
          foodId: banana,
          quantity: 80,
          notes: docSimples("Bem madura."),
          substitutions: [{ foodId: arroz, description: "Aveia em flocos", quantity: 30 }],
        },
      ],
    },
    { name: "Lanche da Manhã", time: "10:00", items: [{ foodId: banana, quantity: 100 }] },
    {
      name: "Almoço",
      time: "12:30",
      items: [
        { foodId: arroz, quantity: 150 },
        { foodId: feijao, quantity: 100 },
        { kind: "SEPARATOR" },
        { foodId: frango, quantity: 120 },
        { foodId: alface, adLibitum: true },
      ],
    },
    { name: "Lanche da Tarde", time: "16:00", items: [{ foodId: receitaId, quantity: 150 }] },
    {
      name: "Jantar",
      time: "19:30",
      items: [
        { foodId: arroz, quantity: 100 },
        { foodId: frango, quantity: 100 },
      ],
    },
    {
      name: "Jantar (opção 2)",
      time: "19:30",
      inCalculation: false,
      items: [{ foodId: ovo, quantity: 100 }],
    },
    { name: "Ceia", time: "22:00", items: [{ foodId: leite, quantity: 150 }] },
  ];

  const plano = await ok(
    await api.post("/api/prescriptions", {
      data: {
        title: "Plano de setembro completo",
        patientId: paciente.id,
        method: "FOODS",
        targetEnergyKcal: 1800,
        targetProteinPct: 25,
        targetCarbohydratePct: 50,
        targetFatPct: 25,
        targetWeightKg: 76,
        energyPlanId: agressivo.id,
        validityStart: "2026-09-01",
        validityEnd: "2026-10-31",
        internalNotes: "Rever proteína no retorno.",
        meals,
      },
    }),
    "plano",
  );
  await ok(
    await api.post(`/api/prescriptions/${plano.id}/handouts`, { data: { handoutId: orientacao.id } }),
    "orientação no plano",
  );
  const publicado = await ok(await api.post(`/api/prescriptions/${plano.id}/publish`, {}), "publicar");

  const rascunho = await ok(
    await api.post("/api/prescriptions", {
      data: {
        title: "Rascunho de outubro",
        patientId: paciente.id,
        method: "FOODS",
        meals: [{ name: "Café da Manhã", time: "07:30", items: [{ foodId: leite, quantity: 200 }] }],
      },
    }),
    "rascunho",
  );
  const modelo = await ok(
    await api.post("/api/prescriptions", {
      data: {
        title: "Modelo low carb 1600",
        method: "FOODS",
        template: true,
        targetEnergyKcal: 1600,
        meals: [
          { name: "Café da Manhã", time: "07:00", items: [{ foodId: ovo, quantity: 100 }] },
          { name: "Almoço", time: "12:30", items: [{ foodId: frango, quantity: 150 }, { foodId: alface, adLibitum: true }] },
        ],
      },
    }),
    "modelo",
  );

  // ---------------------------------------------------------------- equipe
  await ok(
    await api.post("/api/users", {
      data: { name: "Secretária de Teste", email: `sec-${Date.now()}@exemplo.com`, initialPassword: "secretaria2026" },
    }),
    "secretária",
  );

  return {
    paciente,
    outroPaciente,
    planoPublicado: { id: plano.id, publicIdentifier: publicado.publicIdentifier },
    rascunho: { id: rascunho.id },
    modelo: { id: modelo.id },
    receitaId,
    alimentoId: arroz,
    formularioEnviado: envio1.publicIdentifier,
    formularioRespondido: envio2.publicIdentifier,
    calculoComAvisos: agressivo.id,
    hoje: hojeIso,
    parceiro: { id: parceiro.id, name: parceiro.name },
    pacote: { id: pacote.id, name: pacote.name },
    consultaRealizada: realizada.id,
  };
}
