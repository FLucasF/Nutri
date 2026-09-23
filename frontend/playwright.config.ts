import { defineConfig, devices } from "@playwright/test";

/**
 * Os testes de ponta a ponta do NutriPlan.
 *
 * Eles sobem contra o back e o front de desenvolvimento, porque é o fluxo do
 * consultório que está sendo verificado — não um componente isolado. Um teste
 * que dubla a API responderia sempre o que o front espera ouvir, e é
 * justamente o desencontro entre os dois que aparece aqui: o total que só
 * existe depois de salvar, o substituto que não busca alimento, o PDF que sai
 * com a versão anterior.
 *
 * Cada execução cria a própria conta (ver e2e/apoio/conta.ts). Assim os testes
 * não escrevem no consultório de demonstração que alguém pode estar usando na
 * outra janela, e um teste que suja o banco não estraga o seguinte.
 */
export default defineConfig({
  testDir: "./e2e",
  // O front e o back de desenvolvimento são um recurso só, compartilhado: dois
  // testes editando o mesmo plano ao mesmo tempo falhariam um ao outro sem que
  // houvesse defeito nenhum no sistema.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  timeout: 45_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: process.env.E2E_URL ?? "http://localhost:5173",
    locale: "pt-BR",
    timezoneId: "America/Sao_Paulo",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  // Reaproveita o que já estiver de pé: em desenvolvimento os dois servidores
  // costumam estar rodando, e subir outro só para o teste tomaria a porta.
  webServer: [
    {
      command: "npm run dev",
      url: "http://localhost:5173",
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
});
