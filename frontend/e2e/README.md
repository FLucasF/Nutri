# Testes de ponta a ponta

Sobem um navegador de verdade contra o front e o back de desenvolvimento, e
percorrem os fluxos do consultório como quem usa: entrar, montar um cardápio,
corrigir uma avaliação, procurar um exame.

## Rodar

```bash
npm run e2e
```

O Playwright reaproveita o Vite que já estiver na porta 5173 e sobe um se não
houver. **O backend precisa estar de pé** — ele não é iniciado pela suíte:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Para acompanhar clique a clique, ou investigar uma falha:

```bash
npm run e2e:ui
```

## O que eles cobrem

| Arquivo | Fluxo |
|---|---|
| `acesso.spec.ts` | Entrar, senha errada, endereço interno sem sessão |
| `plano.spec.ts` | Cardápio: refeições, busca de alimento, substituto, totais em tempo real, PDF |
| `antropometria.spec.ts` | Registrar, corrigir e imprimir a evolução |
| `exames-e-anamnese.spec.ts` | Busca entre os 154 parâmetros; nome do paciente no PDF |
| `energia.spec.ts` | A EER 2023 conferida contra os coeficientes publicados |

## Duas decisões que valem saber

**Cada execução cria a própria conta.** `apoio/conta.ts` faz o cadastro com um
e-mail que carrega o instante. Assim a suíte nunca escreve no consultório de
demonstração que alguém pode estar usando na outra janela, e um teste que suja
o banco não estraga o seguinte.

**O cenário entra pela API; a tela é só o que está sendo verificado.** Montar
por clique um paciente com quatro avaliações levaria minutos e faria cada teste
falhar pelos defeitos dos outros. O login é a única exceção — ele tem teste
próprio, e é lá que a tela de entrada é exercitada.

## Por que não há dublê da API

O que estes testes procuram é justamente o desencontro entre as duas pontas: o
total que só existia depois de salvar, o substituto que não buscava alimento, o
PDF que saía com a versão anterior. Um dublê responderia sempre o que o front
espera ouvir, e nenhum desses três apareceria.
