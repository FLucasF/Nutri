import { test, expect, type Page } from "@playwright/test";

import { criarConta, criarPaciente, entrar, type Conta } from "./apoio/conta";

/**
 * Os fluxos clínicos pela tela, do começo ao fim: cadastro do paciente, ficha,
 * anamnese, cálculo energético, antropometria, exames e questionário.
 *
 * Cada teste faz o que o nutricionista faria com o mouse e o teclado, e
 * confere o que ele veria depois. O que já tem teste próprio (correção da
 * avaliação, relatório de evolução, EER) não se repete aqui.
 */

let conta: Conta;
let paciente: { id: number; name: string };
let questionarioId: number;

function hojeIso() {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function daquiADias(n: number) {
  const d = new Date(Date.now() + n * 24 * 3600 * 1000);
  const p = (x: number) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/**
 * Escreve num campo de texto formatado.
 *
 * O editor é um `role="textbox"` com o rótulo em aria-label; quando está
 * fechado (campo sob demanda), o botão "+ escrever" ao lado do rótulo o abre.
 */
async function escrever(page: Page, rotulo: string, texto: string) {
  const fechado = page.locator(".richtext-field", { hasText: rotulo }).getByRole("button", { name: "+ escrever" });
  if (await fechado.count()) await fechado.first().click();
  const area = page.getByRole("textbox", { name: rotulo });
  await area.focus();
  await page.keyboard.type(texto);
}

test.beforeAll(async () => {
  conta = await criarConta("Nutri dos Fluxos Clínicos");
  paciente = await criarPaciente(conta, { name: "Paciente dos Fluxos", sex: "FEMALE", dateBirth: "1988-08-08" });
  const q = await conta.api.post("/api/questionnaires", {
    data: {
      name: "Sono da semana",
      scorable: false,
      questions: [
        { statement: "Quantas horas você dorme por noite?", type: "NUMBER", required: true },
        { statement: "Acorda cansado?", type: "CHOICE_SINGLE", required: true, options: "Nunca|Às vezes|Sempre" },
        { statement: "O que atrapalha o seu sono?", type: "TEXT", required: false },
      ],
    },
  });
  expect(q.status(), await q.text()).toBe(201);
  questionarioId = (await q.json()).id;
});

test.afterAll(async () => {
  await conta.api.dispose();
});

test.beforeEach(async ({ page }) => {
  await entrar(page, conta);
});

/* ====================================================== pacientes */

test.describe("pacientes", () => {
  test("cadastra pela tela com CPF, apelido e TAG, e a ficha mostra tudo", async ({ page }) => {
    await page.goto("/patients");
    await page.getByRole("button", { name: "Novo paciente" }).click();

    await page.getByLabel("Nome", { exact: true }).fill("Clara Fluxo Completo");
    await page.getByLabel("E-mail").fill("clara.fluxo@exemplo.com");
    await page.getByLabel("Telefone").fill("(11) 98888-7777");
    await page.getByLabel("Data de nascimento").fill("1992-03-04");
    await page.getByLabel("Sexo").selectOption("FEMALE");
    await page.getByLabel("Objetivo").fill("Ganho de massa magra");
    await page.getByLabel("CPF").fill("987.654.321-00");
    await page.getByLabel("Apelido").fill("Clarinha");

    const tag = page.locator(".np-tags .tag-escolha").first();
    const nomeDaTag = (await tag.textContent())?.trim() ?? "";
    expect(nomeDaTag.length).toBeGreaterThan(0);
    await tag.click();

    await page.getByRole("button", { name: "Cadastrar" }).click();

    await expect(page).toHaveURL(/\/patients\/\d+$/);
    await expect(page.getByRole("heading", { name: "Clara Fluxo Completo" })).toBeVisible();
    await expect(page.getByText("987.654.321-00")).toBeVisible();
    await expect(page.getByText("Clarinha").first()).toBeVisible();
    await expect(page.locator(".tag-paciente", { hasText: nomeDaTag })).toBeVisible();
  });

  test("edita o cadastro e a mudança fica", async ({ page }) => {
    await page.goto(`/patients/${paciente.id}`);
    await page.getByRole("button", { name: "Editar", exact: true }).click();
    await page.getByLabel("Profissão").fill("Professora de ioga");
    await page.getByRole("button", { name: "Salvar alterações" }).click();
    await expect(page.getByText("Professora de ioga")).toBeVisible();
    await page.reload();
    await expect(page.getByText("Professora de ioga")).toBeVisible();
  });

  test("a TAG marcada na ficha continua depois de recarregar", async ({ page }) => {
    await page.goto(`/patients/${paciente.id}`);
    await page.getByRole("button", { name: "alterar" }).click();
    const opcao = page.locator(".escolha-tags .tag-escolha:not(.ativa):not(.nova)").first();
    const nome = (await opcao.textContent())?.trim() ?? "";
    await opcao.click();
    await expect(page.locator(".tag-paciente", { hasText: nome })).toBeVisible();
    await page.reload();
    await expect(page.locator(".tag-paciente", { hasText: nome })).toBeVisible();
  });

  test("um anexo por link entra na lista e sai com remover", async ({ page }) => {
    await page.goto(`/patients/${paciente.id}`);
    await page.getByRole("button", { name: "Guardar link" }).click();
    await page.getByLabel("Título").fill("Laudo antigo");
    await page.getByLabel("Endereço").fill("https://exemplo.com/laudo.pdf");
    await page.getByRole("button", { name: "Guardar", exact: true }).click();
    await expect(page.getByText("Laudo antigo")).toBeVisible();

    page.once("dialog", (d) => d.accept());
    await page.getByRole("button", { name: "Remover" }).first().click();
    await expect(page.getByText("Laudo antigo")).toHaveCount(0);
  });

  test("a consulta agendada da ficha aparece na agenda do dia", async ({ page }) => {
    await page.goto(`/patients/${paciente.id}`);
    await page.getByRole("button", { name: "Consulta" }).click();
    await page.getByLabel("Data", { exact: true }).fill(hojeIso());
    await page.getByLabel("Hora").fill("14:00");
    await page.getByRole("button", { name: "Agendar" }).click();

    await page.goto("/schedule");
    await expect(page.getByText(paciente.name).first()).toBeVisible();
    await expect(page.getByText("14:00").first()).toBeVisible();
  });

  test("a busca acha pelo nome, e inativar tira da lista de ativos", async ({ page }) => {
    const alguem = await criarPaciente(conta, { name: "Zuleica Para Inativar" });
    await page.goto("/patients");
    await page.getByLabel("Buscar").fill("Zuleica");
    await expect(page.getByText("Zuleica Para Inativar")).toBeVisible();

    await page.goto(`/patients/${alguem.id}`);
    page.once("dialog", (d) => d.accept());
    await page.getByRole("button", { name: "Inativar paciente" }).click();
    await expect(page.getByRole("button", { name: "Reativar paciente" })).toBeVisible();

    await page.goto("/patients");
    await page.getByLabel("Buscar").fill("Zuleica");
    await expect(page.getByText("Zuleica Para Inativar")).toHaveCount(0);
    await page.getByLabel("Somente ativos").uncheck();
    await expect(page.getByText("Zuleica Para Inativar")).toBeVisible();
  });

  test("importa uma planilha de pacientes", async ({ page }) => {
    await page.goto("/patients");
    await page.getByRole("button", { name: "Importar planilha" }).click();
    await page.getByLabel("Arquivo CSV").setInputFiles({
      name: "pacientes.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("nome,email,telefone\nImportada Um,um@exemplo.com,11911111111\nImportada Dois,,\n"),
    });
    await page.getByRole("button", { name: "Importar", exact: true }).click();
    await expect(page.getByText(/2 pacientes importados/)).toBeVisible();
    await page.getByRole("button", { name: "Fechar" }).click();
    await page.getByLabel("Buscar").fill("Importada");
    await expect(page.getByText("Importada Um")).toBeVisible();
    await expect(page.getByText("Importada Dois")).toBeVisible();
  });
});

/* ======================================================= anamnese */

test.describe("anamnese", () => {
  test("define um campo de destaque, registra a anamnese e imprime o PDF", async ({ page }) => {
    await page.goto(`/patients/${paciente.id}/anamneses`);
    await page.getByRole("button", { name: "Campos de destaque" }).click();
    await page.getByRole("button", { name: "Adicionar campo" }).click();
    await page.getByLabel(/Rótulo do campo \d+/).last().fill("Objetivo da consulta");
    await page.getByRole("button", { name: "Salvar campos" }).click();

    await page.getByRole("button", { name: "Nova anamnese" }).click();
    await page.getByLabel("Nome", { exact: true }).fill("Primeira consulta");
    await page.getByLabel("Data", { exact: true }).fill("2026-09-20");
    await page.getByLabel("Objetivo da consulta").fill("Perder gordura mantendo o treino");
    await escrever(page, "Registro da consulta", "Dorme pouco e pula o café da manhã.");
    await page.getByRole("button", { name: "Salvar", exact: true }).click();

    await expect(page.getByText("Primeira consulta").first()).toBeVisible();
    await expect(page.getByText("Perder gordura mantendo o treino")).toBeVisible();

    const [resposta] = await Promise.all([
      page.waitForResponse((r) => /\/anamneses\/\d+\/pdf/.test(r.url())),
      page.getByRole("button", { name: "PDF" }).first().click(),
    ]);
    expect(resposta.status()).toBe(200);
    expect(resposta.headers()["content-type"]).toContain("application/pdf");
  });

  test("duplica e exclui digitando a palavra de confirmação", async ({ page }) => {
    const criada = await conta.api.post("/api/anamneses", {
      data: { patientId: paciente.id, name: "Anamnese para duplicar", date: "2026-09-21" },
    });
    expect(criada.status(), await criada.text()).toBe(201);
    await page.goto(`/patients/${paciente.id}/anamneses`);
    await expect(page.getByRole("button", { name: "Duplicar" }).first()).toBeVisible();
    const antes = await page.getByRole("button", { name: "Duplicar" }).count();
    expect(antes).toBeGreaterThan(0);
    await page.getByRole("button", { name: "Duplicar" }).first().click();
    // A cópia abre pronta para editar; fechar o formulário devolve a lista.
    await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible();
    await page.getByRole("button", { name: "Cancelar" }).first().click();
    await expect(page.getByRole("button", { name: "Duplicar" })).toHaveCount(antes + 1);

    await page.getByRole("button", { name: "Excluir", exact: true }).first().click();
    const confirmacao = page.getByLabel("Digite DELETAR para confirmar");
    await confirmacao.fill("DELETAR");
    // O "Excluir" que confirma é o que está na mesma caixa da palavra.
    await confirmacao
      .locator("xpath=ancestor::*[.//button[normalize-space()='Cancelar']][1]")
      .getByRole("button", { name: "Excluir", exact: true })
      .click();
    await expect(page.getByRole("button", { name: "Duplicar" })).toHaveCount(antes);
  });
});

/* ================================================ cálculo energético */

test.describe("cálculo energético", () => {
  test("calcula pela tela, avisa da programação agressiva e leva a meta ao cardápio", async ({ page }) => {
    await page.goto(`/patients/${paciente.id}/energy`);
    await page.getByRole("button", { name: "Novo cálculo" }).click();
    await page.getByLabel("Nome", { exact: true }).fill("Corte de setembro");
    await page.getByLabel("Peso (kg)").fill("82.6");
    await page.getByLabel("Altura (cm)").fill("165");
    await page.getByLabel("Nível de atividade física").selectOption("LOW_ACTIVE");
    const eer = page.getByLabel(/EER \(2023\)/);
    if (!(await eer.isChecked())) await eer.check();
    // 6 kg em 30 dias: 1.540 kcal/dia a menos. Mais de 1 kg por semana, e o
    // prescrito cai abaixo do basal — dois avisos, e o número continua.
    await page.getByLabel("Peso desejado (kg)").fill("76.6");
    await page.getByLabel("Até a data").fill(daquiADias(30));
    await page.getByRole("button", { name: "Calcular e salvar" }).click();

    // Salvo, o cálculo volta para a lista; abre-se o cartão para ler o resultado.
    await page.locator(".plano-energia", { hasText: "Corte de setembro" }).getByRole("button", { name: "Abrir" }).click();
    await expect(page.getByText("Prescrito").first()).toBeVisible();
    await expect(page.getByText(/kg por semana/)).toBeVisible();
    await expect(page.getByText(/abaixo do gasto basal/)).toBeVisible();

    await page.getByRole("button", { name: "Montar cardápio com esta meta" }).click();
    await expect(page).toHaveURL(/\/prescriptions\/new/);
    const meta = page.getByLabel("Meta energética (kcal/dia)");
    await expect(meta).not.toHaveValue("");
    expect(Number(await meta.inputValue())).toBeGreaterThan(0);
  });
});

/* ===================================================== antropometria */

test.describe("antropometria", () => {
  test("registra pela tela com Petroski e lê densidade, faixa de peso e músculo", async ({ page }) => {
    const alguem = await criarPaciente(conta, { name: "Antropometria pela Tela", sex: "FEMALE", dateBirth: "1990-01-01" });
    await page.goto(`/patients/${alguem.id}/anthropometry`);
    await page.getByRole("button", { name: "Nova avaliação" }).click();

    await page.getByLabel("Peso (kg)").fill("70");
    await page.getByLabel("Altura (cm)").fill("170");
    await page.getByLabel("Braço relaxado (D)").fill("29");
    await page.getByLabel("Diâmetro do punho").fill("5.5");
    await page.getByLabel("Diâmetro do fêmur").fill("9.5");
    for (const [dobra, valor] of [
      ["MEAN_AXILLARY", "15"],
      ["SUPRAILIAC", "18"],
      ["THIGH", "25"],
      ["CALF", "14"],
      ["TRICEPS", "20"],
    ]) {
      await page.locator(`#dobra-${dobra}`).fill(valor);
    }
    await page.getByLabel("Estimar composição por").selectOption("PETROSKI");
    await page.getByRole("button", { name: "Registrar avaliação" }).click();

    await expect(page.getByRole("heading", { name: "Última avaliação" })).toBeVisible();
    await expect(page.getByText("Petroski").first()).toBeVisible();
    await expect(page.getByText("Densidade corporal")).toBeVisible();
    await expect(page.getByText("Faixa de peso saudável").first()).toBeVisible();
    await expect(page.getByText("53,5 a 72,3")).toBeVisible();
    await expect(page.getByText("Massa muscular")).toBeVisible();
    await expect(page.getByText("Circ. muscular do braço (direito)")).toBeVisible();
  });
});

/* ============================================================ exames */

test.describe("exames", () => {
  test("registra um resultado pela tela, anexa o laudo e vê a evolução", async ({ page }) => {
    const parametros = await (await conta.api.get("/api/labtests/parameters")).json();
    const glicose = parametros.find((p: { name: string }) => /^glicemia de jejum/i.test(p.name)) ?? parametros[0];

    await page.goto(`/patients/${paciente.id}/labtests`);
    for (const [data, valor] of [
      ["2026-06-01", "101"],
      ["2026-09-01", "93"],
    ]) {
      await page.getByRole("button", { name: "Registrar resultado" }).click();
      await page.getByLabel("Exame", { exact: true }).selectOption(String(glicose.id));
      await page.getByLabel("Data da coleta").fill(data);
      await page.getByLabel("Resultado", { exact: true }).fill(valor);
      await page.getByRole("button", { name: "Registrar", exact: true }).click();
      await expect(page.getByText(valor, { exact: false }).first()).toBeVisible();
    }

    await page.getByRole("button", { name: "Anexar" }).first().click({ trial: true });
    await page.locator("input[type='file'][accept='.pdf,image/*']").first().setInputFiles({
      name: "laudo.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF"),
    });
    await expect(page.getByRole("button", { name: "Abrir" }).first()).toBeVisible();

    await page.getByTitle("Ver a evolução deste exame").first().click();
    await expect(page.getByText(/ao longo do tempo/)).toBeVisible();
    await expect(page.getByText(/2 coletas/)).toBeVisible();
  });
});

/* ====================================================== questionário */

test.describe("questionário", () => {
  test("enviado da ficha, respondido pelo link, e a resposta volta à ficha", async ({ page, browser }) => {
    await page.goto(`/patients/${paciente.id}`);
    await page.getByLabel("Enviar questionário").selectOption(String(questionarioId));
    await page.getByRole("button", { name: "Gerar link" }).click();
    await expect(page.getByText("aguardando resposta").first()).toBeVisible();

    const envios = await (await conta.api.get(`/api/patients/${paciente.id}/questionnaires`)).json();
    const pendente = envios.find((e: { pending: boolean }) => e.pending);
    expect(pendente).toBeTruthy();

    // O paciente, sem sessão nenhuma, responde pelo link.
    const contexto = await browser.newContext();
    const formulario = await contexto.newPage();
    await formulario.goto(`/form/${pendente.publicIdentifier}`);
    await expect(formulario.getByRole("heading", { name: "Sono da semana" })).toBeVisible();
    await formulario.getByLabel(/Quantas horas/).fill("6");
    await formulario.getByRole("radio", { name: "Às vezes" }).check();
    await formulario.getByLabel(/O que atrapalha/).fill("Luz da rua.");
    await formulario.getByRole("button", { name: "Enviar respostas" }).click();
    await expect(formulario.getByRole("heading", { name: "Respostas enviadas" })).toBeVisible();
    await contexto.close();

    await page.reload();
    await expect(page.getByText("respondido em").first()).toBeVisible();
    await page.locator("details.qz-answers summary").first().click();
    await expect(page.getByText("Luz da rua.")).toBeVisible();
  });
});

/* ============================================= anamnese por questionário */

test.describe("anamnese por questionário", () => {
  test("monta o modelo no construtor, preenche a anamnese por ele e o destaque vai à listagem", async ({
    page,
  }) => {
    await page.goto("/questionnaires");
    await page.getByRole("button", { name: "Novo questionário" }).click();
    await page.getByLabel("Nome do questionário").fill("Anamnese pela tela");
    await page.getByLabel("Pergunta 1", { exact: true }).fill("Queixa principal");
    await page.getByLabel("Destacar na listagem de anamneses").first().check();
    await page.getByRole("button", { name: "Adicionar seção" }).click();
    await page.getByLabel("Título da seção 2").fill("Sintomas digestivos");
    await page.getByRole("button", { name: "Adicionar pergunta" }).click();
    await page.getByLabel("Pergunta 3", { exact: true }).fill("O que sente?");
    await page.getByLabel("Tipo da pergunta 3").selectOption("MULTIPLE");
    await page.getByLabel("Alternativas da pergunta 3").fill("Azia\nRefluxo\nConstipação");
    await page.getByRole("button", { name: "Adicionar pergunta" }).click();
    await page.getByLabel("Pergunta 4", { exact: true }).fill("Rotina alimentar");
    await page.getByLabel("Tipo da pergunta 4").selectOption("PARAGRAPH");
    await page.getByRole("button", { name: "Salvar questionário" }).click();
    await expect(page.locator(".handout", { hasText: "Anamnese pela tela" })).toContainText("3 perguntas");

    await page.goto(`/patients/${paciente.id}/anamneses`);
    await page.getByRole("button", { name: "Nova anamnese" }).click();
    await page.getByLabel("Modelo de anamnese").selectOption({ label: "Anamnese pela tela" });
    await page.getByLabel(/^Queixa principal/).fill("Cansaço no fim da tarde");
    await page.getByLabel("Azia").check();
    await page.getByLabel("Refluxo").check();
    await page.getByLabel(/^Rotina alimentar/).fill("Pula o café da manhã.");
    await page.getByRole("button", { name: "Salvar", exact: true }).click();

    const cartao = page.locator(".card-anamnese", { hasText: "Anamnese pela tela" }).first();
    await expect(cartao).toContainText("Queixa principal");
    await expect(cartao).toContainText("Cansaço no fim da tarde");

    // Reabrir traz as respostas, com as caixas marcadas.
    await cartao.getByRole("button", { name: "Editar" }).click();
    await expect(page.getByLabel(/^Queixa principal/)).toHaveValue("Cansaço no fim da tarde");
    await expect(page.getByLabel("Azia")).toBeChecked();
    await expect(page.getByLabel("Constipação")).not.toBeChecked();
  });

  test("a pré-consulta enviada da anamnese volta respondida e vira anamnese", async ({ page, browser }) => {
    await page.goto(`/patients/${paciente.id}/anamneses`);
    await page.getByLabel("Questionário para o paciente").selectOption(String(questionarioId));
    await page.getByRole("button", { name: "Gerar link" }).click();
    await expect(page.getByText("aguardando resposta").first()).toBeVisible();

    const envios = await (await conta.api.get(`/api/patients/${paciente.id}/questionnaires`)).json();
    const pendente = envios.find((e: { pending: boolean }) => e.pending);
    expect(pendente).toBeTruthy();

    const contexto = await browser.newContext();
    const formulario = await contexto.newPage();
    await formulario.goto(`/form/${pendente.publicIdentifier}`);
    await formulario.getByLabel(/Quantas horas/).fill("5");
    await formulario.getByRole("radio", { name: "Sempre" }).check();
    await formulario.getByLabel(/O que atrapalha/).fill("Barulho.");
    await formulario.getByRole("button", { name: "Enviar respostas" }).click();
    await expect(formulario.getByRole("heading", { name: "Respostas enviadas" })).toBeVisible();
    await contexto.close();

    await page.reload();
    await page.getByRole("button", { name: "Importar para anamnese" }).first().click();
    await expect(page.getByRole("heading", { name: "Editar anamnese" })).toBeVisible();
    await expect(page.getByLabel(/Quantas horas/)).toHaveValue("5");
    await expect(page.getByRole("radio", { name: "Sempre" })).toBeChecked();
    await page.getByRole("button", { name: "Salvar", exact: true }).click();

    const importada = page.locator(".card-anamnese", { hasText: "(pré-consulta)" }).first();
    await expect(importada).toContainText("respondida pelo paciente");
    await expect(page.getByRole("button", { name: "Abrir anamnese" }).first()).toBeVisible();
  });
});
