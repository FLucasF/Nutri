import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

import { criarConta, entrar, type Conta } from "./apoio/conta";
import { LARGURAS, abrir, fixarTema } from "./apoio/aparencia";
import { esperarTelaLimpa, prontaParaOlhar, vigiarConsole } from "./apoio/auditoria";
import { montarCenarioCompleto, type CenarioCompleto } from "./apoio/cenario-completo";

/**
 * Cobertura total das telas: cada rota, em cada estado que ela assume
 * (formulário aberto, gaveta aberta, painel aplicado, modal de confirmação),
 * em quatro larguras — monitor, laptop, tablet e celular.
 *
 * Em cada uma se mede o que uma foto mostraria e uma asserção não dizia:
 * rolagem lateral, elemento fora do quadro, texto vazando por cima do
 * vizinho, texto cortado sem reticências. E o console tem de ficar limpo.
 *
 * O cenário é um consultório com tudo preenchido (apoio/cenario-completo.ts),
 * montado uma vez pela API; os testes só olham.
 */

let conta: Conta;
let c: CenarioCompleto;

test.beforeAll(async () => {
  conta = await criarConta("Nutri da Cobertura");
  c = await montarCenarioCompleto(conta);
});

test.afterAll(async () => {
  await conta.api.dispose();
});

type Estado = {
  nome: string;
  rota: () => string;
  /** O que fazer depois de abrir a rota para chegar ao estado. */
  preparar?: (page: Page, largura: number) => Promise<void>;
};

const clicar =
  (nome: string | RegExp, exact = false) =>
  async (page: Page) => {
    await page.getByRole("button", { name: nome, exact }).first().click();
  };

function estadosInternos(): Estado[] {
  const ficha = () => `/patients/${c.paciente.id}`;
  return [
    { nome: "pacientes: lista", rota: () => "/patients" },
    { nome: "pacientes: novo paciente", rota: () => "/patients", preparar: clicar("Novo paciente") },
    { nome: "pacientes: importar planilha", rota: () => "/patients", preparar: clicar("Importar planilha") },
    { nome: "pacientes: só inativos", rota: () => "/patients", preparar: async (page) => {
        await page.getByLabel("Somente ativos").uncheck();
      } },

    { nome: "ficha do paciente", rota: ficha },
    { nome: "ficha: editando o cadastro", rota: ficha, preparar: clicar("Editar", true) },
    { nome: "ficha: TAGs abertas", rota: ficha, preparar: clicar("alterar") },
    { nome: "ficha: consulta aberta", rota: ficha, preparar: clicar("Consulta") },
    { nome: "ficha: pagamento pela ficha", rota: ficha, preparar: clicar("Registrar pagamento") },
    { nome: "ficha: recibo pela ficha", rota: ficha, preparar: clicar("Recibo") },
    { nome: "ficha: anexar arquivo", rota: ficha, preparar: clicar("Anexar arquivo") },
    { nome: "ficha: guardar link", rota: ficha, preparar: clicar("Guardar link") },
    { nome: "ficha: respostas do questionário", rota: ficha, preparar: async (page) => {
        const detalhes = page.locator("details.qz-answers summary").first();
        if (await detalhes.count()) await detalhes.click();
      } },

    { nome: "anamnese: lista", rota: () => `${ficha()}/anamneses` },
    { nome: "anamnese: nova", rota: () => `${ficha()}/anamneses`, preparar: clicar("Nova anamnese") },
    { nome: "anamnese: editando", rota: () => `${ficha()}/anamneses`, preparar: clicar("Editar", true) },
    { nome: "anamnese: campos de destaque", rota: () => `${ficha()}/anamneses`, preparar: clicar("Campos de destaque") },
    { nome: "anamnese: confirmar exclusão", rota: () => `${ficha()}/anamneses`, preparar: clicar("Excluir", true) },

    { nome: "energia: lista", rota: () => `${ficha()}/energy` },
    { nome: "energia: novo cálculo", rota: () => `${ficha()}/energy`, preparar: clicar("Novo cálculo") },
    { nome: "energia: cálculo com avisos", rota: () => `${ficha()}/energy`, preparar: async (page) => {
        await page.locator(".plano-energia", { hasText: "Meta agressiva" }).getByRole("button", { name: "Abrir" }).click();
      } },

    { nome: "antropometria: resumo", rota: () => `${ficha()}/anthropometry` },
    { nome: "antropometria: nova avaliação", rota: () => `${ficha()}/anthropometry`, preparar: clicar("Nova avaliação") },
    { nome: "antropometria: corrigindo", rota: () => `${ficha()}/anthropometry`, preparar: clicar("Editar", true) },
    { nome: "antropometria: avaliação antiga", rota: () => `${ficha()}/anthropometry`, preparar: async (page) => {
        await page.getByRole("button", { name: "Ver", exact: true }).last().click();
      } },

    { nome: "exames: lista", rota: () => `${ficha()}/labtests` },
    { nome: "exames: registrar resultado", rota: () => `${ficha()}/labtests`, preparar: clicar("Registrar resultado") },
    { nome: "exames: solicitar com painel aplicado", rota: () => `${ficha()}/labtests`, preparar: async (page) => {
        await clicar("Solicitar exames")(page);
        await page.locator(".paineis-botoes button").first().click();
      } },
    { nome: "exames: pedido em edição", rota: () => `${ficha()}/labtests`, preparar: async (page) => {
        await page.locator(".labtest-orders").getByRole("button", { name: "Editar" }).first().click();
      } },

    { nome: "agenda: dia", rota: () => "/schedule" },
    { nome: "agenda: semana", rota: () => "/schedule", preparar: clicar("Semana") },
    { nome: "agenda: novo atendimento", rota: () => "/schedule", preparar: clicar("Novo atendimento") },
    { nome: "agenda: assinatura do calendário", rota: () => "/schedule", preparar: clicar("Ver no meu calendário") },
    { nome: "agenda: mês", rota: () => "/schedule", preparar: clicar("Mês") },
    { nome: "agenda: pagamento da consulta na semana", rota: () => "/schedule", preparar: async (page) => {
      await clicar("Semana")(page);
      await clicar("Registrar pagamento")(page);
    } },
    { nome: "agenda: recibo da consulta", rota: () => "/schedule", preparar: clicar("Recibo") },

    { nome: "financeiro: lista", rota: () => "/finance" },
    { nome: "financeiro: novo lançamento", rota: () => "/finance", preparar: clicar("Novo lançamento") },
    { nome: "financeiro: recibo", rota: () => "/finance", preparar: clicar("Recibo") },
    { nome: "financeiro: lançamento parcelado", rota: () => "/finance", preparar: async (page) => {
      await clicar("Novo lançamento")(page);
      await page.getByLabel("Valor (R$)").fill("900");
      await page.getByLabel("Parcelas").fill("3");
    } },
    { nome: "financeiro: vindo da ficha", rota: () => `/finance?patientId=${c.paciente.id}` },

    { nome: "parceiros: lista e relatório", rota: () => "/partners" },
    { nome: "parceiros: novo parceiro", rota: () => "/partners", preparar: clicar("Novo parceiro") },
    { nome: "parceiros: editando", rota: () => "/partners", preparar: clicar("Editar", true) },
    { nome: "pacotes: lista", rota: () => "/packages" },
    { nome: "pacotes: novo pacote", rota: () => "/packages", preparar: clicar("Novo pacote") },
    { nome: "estatísticas: 6 meses", rota: () => "/statistics" },
    { nome: "estatísticas: 12 meses", rota: () => "/statistics", preparar: clicar("12 meses") },

    { nome: "alimentos: lista", rota: () => "/foods" },
    { nome: "alimentos: busca", rota: () => "/foods", preparar: async (page) => {
        await page.getByLabel("Buscar").fill("arroz integral");
        await page.waitForTimeout(700);
      } },
    { nome: "alimentos: novo alimento", rota: () => "/foods", preparar: clicar("Novo alimento") },
    { nome: "alimentos: importar tabela", rota: () => "/foods", preparar: clicar("Importar tabela") },
    { nome: "alimento: detalhe", rota: () => `/foods/${c.alimentoId}` },
    { nome: "receita: editor", rota: () => `/recipes/${c.receitaId}` },
    { nome: "receita: nova", rota: () => "/recipes/new" },

    { nome: "orientações: lista", rota: () => "/handouts" },
    { nome: "orientações: nova", rota: () => "/handouts", preparar: clicar("Nova orientação") },
    { nome: "orientações: ler tudo", rota: () => "/handouts", preparar: clicar("Ler tudo") },

    { nome: "questionários: lista", rota: () => "/questionnaires" },
    { nome: "questionários: perguntas abertas", rota: () => "/questionnaires", preparar: clicar("Ver perguntas") },

    { nome: "equipe: lista", rota: () => "/team" },
    { nome: "equipe: cadastrar secretária", rota: () => "/team", preparar: clicar("Cadastrar secretária") },

    { nome: "prescrições: planos", rota: () => "/prescriptions" },
    { nome: "prescrições: modelos", rota: () => "/prescriptions", preparar: clicar("Modelos") },

    { nome: "editor: plano novo", rota: () => "/prescriptions/new" },
    { nome: "editor: plano publicado", rota: () => `/prescriptions/${c.planoPublicado.id}` },
    { nome: "editor: refeições salvas", rota: () => `/prescriptions/${c.planoPublicado.id}`, preparar: clicar(/Refeições salvas/) },
    { nome: "editor: observação da refeição aberta", rota: () => `/prescriptions/${c.planoPublicado.id}`, preparar: clicar("Escrever observações") },
    { nome: "editor: totais na gaveta", rota: () => `/prescriptions/${c.planoPublicado.id}`, preparar: async (page, largura) => {
        if (largura <= 900) await page.locator("[aria-haspopup='dialog']").first().click();
      } },
    { nome: "editor: rascunho", rota: () => `/prescriptions/${c.rascunho.id}` },
    { nome: "editor: modelo", rota: () => `/prescriptions/${c.modelo.id}` },
  ];
}

function estadosPublicos(): Estado[] {
  return [
    { nome: "acesso: entrar", rota: () => "/access" },
    { nome: "acesso: criar conta", rota: () => "/access", preparar: clicar("Criar uma conta") },
    { nome: "acesso: esqueci a senha", rota: () => "/access", preparar: clicar("Esqueci minha senha") },
    { nome: "plano do paciente", rota: () => `/plan/${c.planoPublicado.publicIdentifier}` },
    { nome: "questionário do paciente", rota: () => `/form/${c.formularioEnviado}` },
    { nome: "questionário já respondido", rota: () => `/form/${c.formularioRespondido}` },
  ];
}

async function conferir(page: Page, estado: Estado, largura: number) {
  const erros = vigiarConsole(page);
  await abrir(page, estado.rota());
  await prontaParaOlhar(page);
  if (estado.preparar) {
    await estado.preparar(page, largura);
  }
  await esperarTelaLimpa(page, `${estado.nome} (${largura}px)`, erros);
}

for (const [rotulo, viewport] of Object.entries(LARGURAS)) {
  test.describe(`telas com sessão · ${rotulo} (${viewport.width}px)`, () => {
    test.use({ viewport });

    test.beforeEach(async ({ page }) => {
      await entrar(page, conta);
    });

    for (const estado of estadosInternos()) {
      test(estado.nome, async ({ page }) => {
        await conferir(page, estado, viewport.width);
      });
    }
  });

  test.describe(`telas sem sessão · ${rotulo} (${viewport.width}px)`, () => {
    test.use({ viewport });

    for (const estado of estadosPublicos()) {
      test(estado.nome, async ({ page }) => {
        await conferir(page, estado, viewport.width);
      });
    }
  });
}

/* ================================================ contraste em toda tela */

test.describe("contraste em todas as telas (1440)", () => {
  test.use({ viewport: LARGURAS.desktop });

  function rotasInternas(): string[] {
    const p = c.paciente.id;
    return [
      "/patients",
      `/patients/${p}`,
      `/patients/${p}/anamneses`,
      `/patients/${p}/energy`,
      `/patients/${p}/anthropometry`,
      `/patients/${p}/labtests`,
      "/schedule",
      "/finance",
      "/partners",
      "/packages",
      "/statistics",
      "/foods",
      `/foods/${c.alimentoId}`,
      `/recipes/${c.receitaId}`,
      "/handouts",
      "/questionnaires",
      "/team",
      "/prescriptions",
      `/prescriptions/${c.planoPublicado.id}`,
    ];
  }

  async function semFalhasDeContraste(page: Page, rotulo: string) {
    await prontaParaOlhar(page);
    const resultado = await new AxeBuilder({ page }).withRules(["color-contrast"]).analyze();
    const violacoes = resultado.violations.flatMap((v) =>
      v.nodes.map((n) => ({ alvo: n.target.join(" "), resumo: n.failureSummary })),
    );
    expect(violacoes, `${rotulo}: falhas de contraste\n${JSON.stringify(violacoes, null, 2)}`).toEqual([]);
  }

  for (const tema of ["light", "dark"] as const) {
    test(`todas as rotas internas passam no contraste no tema ${tema === "light" ? "claro" : "escuro"}`, async ({
      page,
    }) => {
      test.setTimeout(180_000);
      await entrar(page, conta);
      await fixarTema(page, tema);
      for (const rota of rotasInternas()) {
        await abrir(page, rota);
        await semFalhasDeContraste(page, `${rota} (${tema})`);
      }
    });

    test(`as rotas públicas passam no contraste no tema ${tema === "light" ? "claro" : "escuro"}`, async ({
      page,
    }) => {
      test.setTimeout(120_000);
      await fixarTema(page, tema);
      for (const rota of ["/access", `/plan/${c.planoPublicado.publicIdentifier}`, `/form/${c.formularioEnviado}`]) {
        await abrir(page, rota);
        await semFalhasDeContraste(page, `${rota} (${tema})`);
      }
    });
  }
});
