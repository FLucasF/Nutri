# Glossário — português para inglês

Este documento fixa o vocabulário da migração de código para inglês. Ele existe
porque a decisão que mais custa a desfazer não é a mecânica da renomeação, é a
**escolha do substantivo**: trocar `Paciente` por `Patient` é busca e
substituição; trocar `Exam` por `LabTest` depois de duzentos arquivos já usarem
`Exam` é outro dia de trabalho.

## A regra

**Inglês na lógica; português em tudo que a pessoa lê.**

| Em inglês | Em português |
|---|---|
| Caminhos e parâmetros da API | Rótulos e textos de tela |
| Nomes de campo do JSON | Mensagens de erro e de confirmação |
| Classes, métodos, variáveis, pacotes | Mensagens de regra de negócio vindas do servidor |
| Tabelas e colunas do banco | Os documentos em `docs/` |
| **Comentários do código** | |

**Os comentários vão para inglês.** A regra é "só o que a pessoa lê na tela fica
em português, nada mais nada menos", e comentário não aparece na tela.

Duas fronteiras que exigem julgamento, registradas aqui para não virarem
surpresa:

- **Mensagem de erro do backend fica em português.** Ela é repassada literalmente
  para a tela e é lida pelo nutricionista — é texto de interface que por acaso
  nasce no servidor (ver AD-23).
- **Os documentos em `docs/` ficam em português.** São entregáveis do TCC, não
  código.

## O vocabulário

### Núcleo clínico

| Português | Inglês | Nota |
|---|---|---|
| paciente | `patient` | |
| conta | `account` | o consultório, unidade de isolamento |
| usuário | `user` | |
| nutricionista | `nutritionist` | |
| secretária | `assistant` | `secretary` soa a cargo administrativo genérico |

### Alimentos

| Português | Inglês | Nota |
|---|---|---|
| alimento | `food` | |
| medida caseira | `householdMeasure` | termo corrente em nutrição |
| porção | `serving` | `portion` é o pedaço; `serving` é a medida servida |
| composição | `nutrients` | mais direto que `composition` |
| receita | `recipe` | |
| ingrediente | `ingredient` | |
| rendimento | `yield` | peso final da preparação |
| modo de preparo | `instructions` | |

### Prescrição

| Português | Inglês | Nota |
|---|---|---|
| prescrição / plano alimentar | `mealPlan` | **não** `prescription`, que em inglês remete a medicamento |
| refeição | `meal` | |
| item da refeição | `mealItem` | |
| equivalente / substituição | `substitution` | |
| vigência | `validity` (`validFrom` / `validUntil`) | |
| meta energética | `energyTarget` | |

### Antropometria

| Português | Inglês | Nota |
|---|---|---|
| avaliação antropométrica | `assessment` | |
| dobra cutânea | `skinfold` | |
| circunferência | `circumference` | |
| gasto energético | `energyExpenditure` | |
| curva de crescimento | `growthChart` | nomenclatura da OMS em inglês |
| escore-z | `zScore` | |
| gestacional | `gestational` | |

### Atendimento e consultório

| Português | Inglês | Nota |
|---|---|---|
| agenda | `schedule` | |
| agendamento / atendimento | `appointment` | |
| situação | `status` | |
| lançamento financeiro | `transaction` | |
| apuração | `summary` | |
| recibo | `receipt` | |
| inadimplência | `overdue` | |

### Exames e coleta

| Português | Inglês | Nota |
|---|---|---|
| exame laboratorial | `labTest` | **não** `exam`, que em inglês é prova escolar |
| parâmetro de exame | `testParameter` | |
| faixa de referência | `referenceRange` | |
| laudo | `report` | o arquivo do laboratório |
| solicitação | `request` | |
| questionário | `questionnaire` | |
| pergunta / resposta | `question` / `answer` | |
| escore | `score` | |

### Orientações

| Português | Inglês | Nota |
|---|---|---|
| orientação nutricional | `handout` | é o texto que o paciente leva; `guideline` remete a diretriz clínica |
| biblioteca | `library` | |
| modelo do sistema | `builtIn` | |

## Quatro escolhas que aceitam discussão

Registradas aqui porque são as que eu reverteria primeiro se você discordar, e
porque reverter agora é barato:

1. **`mealPlan` e não `prescription`.** Em software de nutrição, *meal plan* é
   o termo de mercado; *prescription* leva a medicamento.
2. **`labtest` e não `exam`.** Em inglês, *exam* é prova; *lab test* é o exame
   laboratorial. No código ficou como uma palavra só — `Labtest`,
   `labtestId` —, para não haver duas grafias do mesmo termo.
3. **`handout` e não `guideline`.** *Guideline* é diretriz clínica (o que a
   sociedade de nutrição publica), e não o texto que se entrega ao paciente.
4. **`assistant` e não `secretary`.** Descreve o papel sem a conotação de
   cargo que *secretary* carrega em inglês.

## O que fica igual

Nomes próprios e siglas de fonte não se traduzem: `TACO`, `IBGE`, `POF`,
`OpenFoodFacts`, `CRN`, `CPF`, `IMC` (que vira `bmi`, por ser sigla técnica
consagrada em inglês), `OMS` (que vira `who`, pelo mesmo motivo).

## O que a migração encontrou

A renomeação em si foi mecânica. O que ela custou de verdade foram os pontos em
que **o mesmo contrato está escrito em dois lugares**, sem nada que obrigue os
dois a concordarem. Traduzir um lado e não o outro compila, passa nos testes, e
quebra em execução.

Quatro casos apareceram, e cada um virou uma verificação automática:

| O que quebrou | Como aparecia | O que verifica agora |
|---|---|---|
| Cliente chamando rota que o servidor não serve mais | 404 em tudo; os 284 testes de backend passavam | `verificar_api` compara os caminhos do cliente com as rotas do servidor |
| Seletor de CSS apontando para valor que o TypeScript deixou de escrever | O tema escuro simplesmente não existia | `verificar_estilos` cruza `[data-theme="…"]` com a união `Theme` |
| Enum do servidor divergindo da união de strings do cliente | Dois dos cinco tipos de atendimento não casavam | `verificar_enums` compara os enums compartilhados |
| Passe de tradução encostando na prosa | `"sem"` virou `"without"` dentro de uma frase em português | `verificar_comentarios` exige que todo comentário seja o original ou uma tradução declarada |

Há ainda `verificar_texto`, que prova que **nenhum texto de tela mudou**: os
trechos soltos de JSX são comparados byte a byte com o commit anterior. Foi ele
que revelou o pior estrago do caminho — 161 frases traduzidas pela metade, do
tipo *"Este plan foi closed"*, na página que o paciente abre. Texto entre tags
não tem aspas, e por isso nenhum dos mascaramentos o protegia.

### Defeitos que já existiam

Três não vieram da tradução; ela só os expôs:

- `AppointmentType` tinha duas constantes escritas em português **com acento** —
  `Avaliação` e `Orientação` — no meio de um enum em SCREAMING_CASE. O passe de
  identificadores não as alcançava, porque `ç` e `ã` não são caracteres de
  identificador para ele.
- `Plan` documentava que os limites eram aplicados por um `LimitePlanoService`
  que não existe. Quem aplica é `PatientService.checkPlanLimit`.
- O documento de modelo de dados citava `imagem_orientacao` e
  `token_recuperacao`; o esquema tem `imagem_de_orientacao` e
  `token_de_recuperacao`.
