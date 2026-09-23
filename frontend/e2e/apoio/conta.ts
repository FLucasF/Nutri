import { expect, request, type APIRequestContext, type Page } from "@playwright/test";

/**
 * A conta que cada execução usa, e os atalhos para preparar o cenário.
 *
 * O teste entra pela tela quando a tela é o que está sendo verificado. O
 * cenário — o paciente que já existe, a avaliação de três meses atrás — entra
 * pela API, porque montar isso clicando levaria minutos e faria cada teste
 * falhar pelos defeitos dos outros.
 */

export type Conta = {
  email: string;
  senha: string;
  token: string;
  api: APIRequestContext;
};

const BASE = process.env.E2E_URL ?? "http://localhost:5173";

/** Cria uma conta nova. O e-mail carrega o instante para nunca colidir. */
export async function criarConta(nome = "Nutri E2E"): Promise<Conta> {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e4)}@exemplo.com`;
  const senha = "playwright2026";

  const anonima = await request.newContext({ baseURL: BASE });
  const resposta = await anonima.post("/api/auth/signup", {
    data: { name: nome, email, password: senha, crn: "CRN-3 00000" },
  });
  expect(resposta.status(), await resposta.text()).toBe(201);
  const { token } = await resposta.json();
  await anonima.dispose();

  const api = await request.newContext({
    baseURL: BASE,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  return { email, senha, token, api };
}

/**
 * Põe a sessão no navegador sem passar pela tela de login.
 *
 * O login tem teste próprio. Repeti-lo no começo de cada cenário só somaria
 * um ponto de falha que não é o que o teste quer olhar.
 */
export async function entrar(page: Page, conta: Conta) {
  await page.addInitScript((valor) => {
    window.localStorage.setItem("nutriplan.token", valor);
  }, conta.token);
}

/** Um paciente pronto, para o teste começar de onde importa. */
export async function criarPaciente(
  conta: Conta,
  dados: Record<string, unknown> = {},
): Promise<{ id: number; name: string }> {
  const corpo = {
    name: "Paciente de Teste",
    sex: "FEMALE",
    dateBirth: "1990-05-20",
    ...dados,
  };
  const resposta = await conta.api.post("/api/patients", { data: corpo });
  expect(resposta.status(), await resposta.text()).toBe(201);
  return resposta.json();
}

/** Uma avaliação antropométrica já registrada. */
export async function criarAvaliacao(
  conta: Conta,
  patientId: number,
  dados: Record<string, unknown> = {},
) {
  const corpo = {
    date: "2026-06-10",
    weightKg: 72.5,
    heightCm: 165,
    ...dados,
  };
  const resposta = await conta.api.post(`/api/patients/${patientId}/assessments`, {
    data: corpo,
  });
  expect(resposta.status(), await resposta.text()).toBe(201);
  return resposta.json();
}

/** O identificador de um alimento da TACO, pelo começo do nome. */
export async function acharAlimento(conta: Conta, termo: string): Promise<number> {
  const resposta = await conta.api.get(
    `/api/foods?term=${encodeURIComponent(termo)}&source=TACO&size=5`,
  );
  expect(resposta.ok(), await resposta.text()).toBeTruthy();
  const { content } = await resposta.json();
  expect(content.length, `nenhum alimento para "${termo}"`).toBeGreaterThan(0);
  return content[0].id;
}
