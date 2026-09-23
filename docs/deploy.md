# Subir o NutriPlan para o cliente testar

Ambiente de avaliação, em plano gratuito. **Não é produção** — leia a última
seção antes de pôr qualquer dado real ali dentro.

O GitHub você já tem — o projeto aponta para
[FLucasF/Nutri](https://github.com/FLucasF/Nutri). Faltam **duas contas**, as
duas sem cartão:

| Conta | Para quê |
|---|---|
| [Neon](https://neon.com) | o PostgreSQL |
| [Render](https://render.com) | a aplicação |

**Não cadastre forma de pagamento em nenhuma.** Sem cartão, o pior caso é o
serviço parar até o mês virar — não existe caminho para uma fatura chegar. Com
cartão, o Render cobra excedente de banda de saída.

---

## Como ficou montado

Um serviço só. O `dist/` do front entra no JAR como recurso estático e o
próprio Spring o devolve (`SpaConfig`). Isso tira do caminho a configuração de
CORS, um segundo domínio e a possibilidade de a tela estar no ar com a API
dormindo — que, num plano que hiberna, apareceria como tela branca sem
explicação.

```
  navegador ──→ Render (Docker: Spring + tela) ──→ Neon (PostgreSQL)
```

---

## 1. GitHub — publicar o branch

O repositório já existe e o remoto já está configurado. Hoje só existe `main`
lá; o trabalho todo está no branch local `projeto-do-cliente`, ainda sem
commit. Depois de commitar:

```bash
git push -u origin projeto-do-cliente
```

O Render lê o branch que você apontar, então não é preciso mexer na `main`
agora. O repositório pode continuar privado — o Render acessa pela conexão
com o GitHub que você autoriza no passo 3.

---

## 2. Neon — o banco

1. Entre em [neon.com](https://neon.com) e crie a conta.
2. **Create project**. Nome `nutriplan`, região **AWS us-east-1** ou
   **sa-east-1** (São Paulo). Escolha a mesma região do Render depois; banco e
   aplicação em continentes diferentes somam latência a cada consulta.
3. Na tela seguinte ele mostra a **connection string**. Copie e guarde — algo
   como:

```
postgresql://nutriplan_owner:SENHA@ep-algo-123.us-east-1.aws.neon.tech/nutriplan?sslmode=require
```

4. Dessa linha você vai precisar de três pedaços separados:

| Variável | O que é |
|---|---|
| `DB_URL` | `jdbc:postgresql://ep-algo-123...neon.tech/nutriplan?sslmode=require` |
| `DB_USER` | o que vem antes dos `:` — ex. `nutriplan_owner` |
| `DB_PASSWORD` | o que vem entre `:` e `@` |

> A `DB_URL` começa com **`jdbc:postgresql://`** e **não leva usuário nem
> senha dentro**. O driver do Java quer assim; colar a linha do Neon inteira é
> o erro mais comum aqui, e o sintoma é a aplicação não subir com
> `driver claims to not accept jdbcUrl`.

Não precisa criar tabela nenhuma. O Flyway constrói o banco inteiro no
primeiro boot, e as bases de alimentos entram dos CSVs — os importadores
checam antes e não repetem nas subidas seguintes.

---

## 3. Render — a aplicação

1. Entre em [render.com](https://render.com), crie a conta e conecte o GitHub.
2. **New → Web Service** e escolha o repositório.
3. Preencha:

| Campo | Valor |
|---|---|
| Language | **Docker** |
| Branch | `projeto-do-cliente` |
| Region | a mesma do Neon |
| Instance Type | **Free** |

4. Em **Environment Variables**, acrescente:

| Chave | Valor |
|---|---|
| `DB_URL` | a montada acima, com `jdbc:postgresql://` |
| `DB_USER` | o usuário do Neon |
| `DB_PASSWORD` | a senha do Neon |
| `NUTRIPLAN_JWT_SECRET` | um segredo seu, **32 caracteres ou mais** |
| `APP_URL` | a URL que o Render te der, ex. `https://nutriplan.onrender.com` |

Para o segredo, gere um de verdade em vez de inventar uma frase:

```bash
openssl rand -base64 48
```

> `NUTRIPLAN_JWT_SECRET` **não tem valor padrão em produção**, de propósito. Se
> faltar, a aplicação não sobe. É melhor que subir assinando token com a chave
> de desenvolvimento, que está no repositório — com ela, qualquer pessoa forja
> um token e entra em qualquer conta.

5. **Create Web Service**. A primeira construção leva vários minutos: ela
   compila o front, compila o JAR e baixa as dependências das duas coisas.

### O que foi medido antes

Tudo isto foi verificado localmente, com a mesma imagem e o mesmo limite de
memória do plano gratuito (`docker run -m 512m`):

| | Medido | Limite do plano |
|---|---|---|
| Imagem | 419 MB | — |
| Subida da aplicação | 10,9 s | — |
| Memória, inclusive durante a carga | **350 MB** | 512 MB |
| Mortes por falta de memória | **0** | — |
| Migrações | as 31, num PostgreSQL 16 | — |
| Banco depois da carga | **29 MB** | 512 MB |
| Alimentos carregados | 23.945 | — |

A primeira subida importa as bases de alimentos dos CSVs e leva **cerca de
três minutos a mais**. Nas seguintes os importadores encontram os dados e
pulam — o serviço volta nos ~11 s.

Se a construção no Render estourar o tempo por causa dessa carga, me avise:
dá para carregar a base daqui direto no Neon, uma vez, e aí o contêiner sobe
com ela pronta.

---

## 4. Conferir

Abra a URL. Deve aparecer a tela de acesso.

Crie a conta do seu cliente por **Criar uma conta** na própria tela. A primeira
conta de um consultório nasce no plano EXPERIMENTAL, que **limita a 5
pacientes ativos** — se ele for testar com mais, me avise que a gente resolve.

Se a tela não abrir, olhe **Logs** no Render. Os dois erros que aparecem aqui:

| No log | O que é |
|---|---|
| `driver claims to not accept jdbcUrl` | a `DB_URL` está sem `jdbc:` na frente |
| `Could not resolve placeholder 'NUTRIPLAN_JWT_SECRET'` | faltou a variável |

---

## O que seu cliente vai estranhar

**A primeira tela demora de 30 a 60 segundos.** O plano gratuito derruba o
contêiner depois de 15 minutos sem acesso, e o tempo é o contêiner inteiro
subindo — não os 6 segundos da aplicação. Avise ele antes, ou o relato vai ser
"não abre".

Depois da primeira, enquanto ele estiver usando, responde normal.

Para tirar a espera: no Render, **Instance Type → Starter** (~US$ 7/mês)
mantém o serviço sempre de pé. Para alguém avaliando seu trabalho, costuma
valer mais que a economia.

---

## Os limites, e o que acontece ao bater neles

| | Limite | Ao estourar |
|---|---|---|
| Render | 750 h de instância/mês | suspende até o mês virar — nunca cobra |
| Render | banda de saída | **cobra, se houver cartão**; sem cartão, suspende |
| Neon | 0,5 GB de armazenamento | gravação falha, leitura continua — nunca cobra |
| Neon | 100 CU-h/mês | banco suspenso até o próximo ciclo — nunca cobra |

O mês tem ~730 horas e o serviço hiberna sozinho, então as 750 não são um
limite de verdade aqui. As 100 CU-h dão ~400 horas de banco ativo. O
armazenamento: a base de alimentos ocupa dezenas de megabytes dos 500.

---

## Antes de pôr dado real

Isto é sistema clínico. Num ambiente de avaliação gratuito:

- **Combine com seu cliente de usar apenas dados fictícios.** Paciente real
  aqui é tratamento de dado de saúde — LGPD — e nenhum plano gratuito oferece
  contrato para isso.
- Não há backup configurado. O plano gratuito do Neon não faz cópia que você
  possa restaurar sozinho.
- O `/docs` (Swagger) fica acessível. Para um ambiente de avaliação tudo bem;
  antes de produção, feche.

Quando virar produção de verdade, o que muda: instância paga, banco pago com
backup, `/docs` fechado, domínio próprio e um acordo de tratamento de dados.
