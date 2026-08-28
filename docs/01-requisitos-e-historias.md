# Requisitos e Histórias de Usuário

Sistema de gestão para consultórios de nutrição (NutriPlan).

Este documento separa deliberadamente **o que já está implementado e coberto por
teste** do que está **especificado para implementação**. A distinção importa: um
requisito listado sem essa marcação sugere entrega que não existe.

Os cenários executáveis de cada história estão em
[04-cenarios-bdd.md](04-cenarios-bdd.md).

---

## 1. Escopo e delimitação

O sistema apoia a rotina clínica de um nutricionista autônomo ou de uma clínica
pequena: cadastro de pacientes, coleta pré-consulta, consulta a tabelas de
composição de alimentos, montagem de plano alimentar, orientação nutricional,
acompanhamento antropométrico, registro de exames laboratoriais, agenda e
controle financeiro.

O foco é a ferramenta do **nutricionista**. O paciente aparece como leitor do
próprio plano, pelo link que recebe — e não como usuário do sistema: não há
conta, aplicativo, diário alimentar nem canal de mensagem. A delimitação é
deliberada: sustentar a atenção diária do paciente é problema de produto
comercial, e resolvê-lo não esclarece o problema central deste trabalho, que é
o trabalho clínico de quem prescreve.

**Fora do escopo** deste trabalho, ainda que existam em produtos comerciais
comparáveis: plataforma de cursos, editor gráfico de peças de marketing,
integração com mensageria, recursos de inteligência artificial e o aplicativo
de acompanhamento do paciente. São módulos periféricos que não caracterizam o
problema central.

### 1.1 Atores

| Ator | Descrição |
|---|---|
| **Nutricionista** | Dono da conta. Acesso total ao próprio consultório. |
| **Secretária** | Acesso operacional (agenda, cadastro), sem prescrição nem financeiro. |
| **Paciente** | Lê o próprio plano pelo link recebido. Não tem conta nem acesso ao sistema. |
| **Administrador** | Operação da plataforma. Não acessa dado clínico. |

---

## 2. Requisitos funcionais

Legenda: ✅ implementado e testado · 🔶 parcial · ⬜ especificado, não implementado

### 2.1 Acesso e conta

| ID | Requisito | Estado |
|---|---|---|
| RF01 | Cadastro autônomo de nutricionista, criando conta e usuário dono | ✅ |
| RF02 | Autenticação por e-mail e senha, com token de sessão | ✅ |
| RF03 | Perfis de acesso distintos por papel | ✅ nutricionista e secretária em uso; paciente e admin seguem sem fluxo |
| RF04 | Planos de assinatura com limites aplicados em tempo de uso | ✅ |
| RF05 | Convite e gestão de usuários secundários (secretária) | ✅ |
| RF06 | Recuperação de senha | ✅ o fluxo inteiro; o envio do e-mail fica atrás de uma interface |

### 2.2 Pacientes

| ID | Requisito | Estado |
|---|---|---|
| RF10 | Cadastrar, consultar, editar e inativar paciente | ✅ |
| RF11 | Buscar paciente por nome, e-mail ou telefone | ✅ |
| RF12 | Calcular idade a partir da data de nascimento | ✅ |
| RF13 | Preservar histórico ao inativar (exclusão lógica) | ✅ |
| RF14 | Importar pacientes de planilha | ✅ |

### 2.3 Base de alimentos

| ID | Requisito | Estado |
|---|---|---|
| RF20 | Consultar alimentos de tabelas de referência | ✅ |
| RF21 | Buscar ignorando acentuação e caixa | ✅ |
| RF21a | **Ordenar a busca por relevância clínica** | ✅ |
| RF22 | Filtrar por grupo de alimentos e por fonte | ✅ |
| RF23 | Exibir a fonte do dado em cada alimento | ✅ |
| RF24 | Cadastrar alimento próprio do consultório | ✅ |
| RF25 | Impedir edição de alimento de tabela de referência | ✅ |
| RF26 | Importar tabela de alimentos em CSV | ✅ |
| RF27 | Identificar produto industrializado por código de barras | ✅ |
| RF28 | Compor receita a partir de outros alimentos | ✅ |

### 2.4 Medidas caseiras

| ID | Requisito | Estado |
|---|---|---|
| RF30 | Oferecer porções usuais para cada alimento | ✅ |
| RF31 | Cadastrar porção própria sobre alimento de referência | ✅ |
| RF32 | Dar precedência à porção do consultório sobre a do acervo | ✅ |
| RF33 | Calcular composição por porção ou por peso | ✅ |
| RF34 | Derivar porção da embalagem para industrializados | ✅ |

### 2.5 Prescrição

| ID | Requisito | Estado |
|---|---|---|
| RF40 | Montar plano alimentar com refeições e itens | ✅ |
| RF41 | Totalizar nutrientes por refeição e por dia | ✅ |
| RF42 | Sinalizar quando o total é parcial por ausência de dado | ✅ |
| RF43 | Prescrever por equivalentes, com substituições | ✅ |
| RF44 | Prescrever de forma qualitativa, sem quantificar | ✅ |
| RF45 | Reaproveitar planos como modelo | ✅ |
| RF46 | Calcular distribuição energética entre macronutrientes | ✅ |
| RF47 | Comparar o prescrito com a meta energética | ✅ |
| RF48 | Publicar o plano e entregá-lo por link ao paciente | ✅ |
| RF49 | Invalidar o link entregue e gerar outro | ✅ |
| RF50 | Congelar o peso prescrito contra correções posteriores da porção | ✅ |
| RF51 | Gerar o plano em PDF | ✅ |

### 2.6 Antropometria ✅

| ID | Requisito | Estado |
|---|---|---|
| RF60 | Registrar avaliação com peso, altura e data | ✅ |
| RF61 | Registrar dobras cutâneas e circunferências | ✅ |
| RF62 | Calcular IMC e classificá-lo segundo a OMS | ✅ |
| RF63 | Estimar percentual de gordura por protocolo escolhido | ✅ |
| RF64 | Derivar massa gorda e massa magra | ✅ |
| RF65 | Calcular relação cintura-quadril e sinalizar risco | ✅ |
| RF66 | Exibir a evolução das medidas entre avaliações | ✅ |
| RF67 | Estimar gasto energético basal e total | ✅ |
| RF68 | Usar o gasto estimado como meta do plano alimentar | ✅ |
| RF69 | Avaliar criança e adolescente pelas curvas de crescimento da OMS | ✅ |
| RF69a | Classificar o estado nutricional infantil por escore-z, e não pela faixa adulta | ✅ |
| RF69b | Acompanhar a gestante pelo ganho de peso esperado para o IMC pré-gestacional | ✅ |

### 2.7 Agenda ✅

| ID | Requisito | Estado |
|---|---|---|
| RF70 | Agendar atendimento com paciente, data, hora e duração | ✅ |
| RF71 | Impedir dois atendimentos sobrepostos na mesma agenda | ✅ |
| RF72 | Classificar o atendimento por tipo | ✅ |
| RF73 | Acompanhar a situação do atendimento até a conclusão | ✅ |
| RF74 | Listar a agenda por dia e por período | ✅ |
| RF75 | Registrar falta do paciente | ✅ |
| RF76 | Sincronizar com calendário externo | ✅ assinatura iCalendar, de mão única (ver 02, AD-21) |

### 2.8 Financeiro ✅

| ID | Requisito | Estado |
|---|---|---|
| RF80 | Registrar receitas e despesas do consultório | ✅ |
| RF81 | Vincular um lançamento a paciente e a atendimento | ✅ |
| RF82 | Acompanhar recebimento (pendente, pago, cancelado) | ✅ |
| RF83 | Apurar resultado do período | ✅ |
| RF84 | Listar inadimplência | ✅ |
| RF85 | Emitir recibo de atendimento | ✅ |

### 2.9 Questionários e coleta pré-consulta ✅

O paciente responde por link, sem conta — o mesmo mecanismo pelo qual ele lê o
plano. É recurso de tempo do nutricionista, e não de engajamento do paciente:
chegar à consulta com a leitura feita é o que ele compra aqui.

| ID | Requisito | Estado |
|---|---|---|
| RF90 | Manter modelos de questionário editáveis pelo consultório | ✅ |
| RF91 | Enviar um questionário ao paciente por link, antes da consulta | ✅ |
| RF92 | Receber as respostas e vinculá-las ao paciente e ao atendimento | ✅ |
| RF93 | Aplicar questionário com escore e classificar o resultado | ✅ |
| RF94 | Trazer as respostas para a tela do atendimento, sem redigitação | ✅ |
| RF95 | Preservar a versão do questionário respondido | ✅ |

### 2.10 Exames laboratoriais ✅

| ID | Requisito | Estado |
|---|---|---|
| RF100 | Registrar resultado de exame com parâmetro, valor, unidade e data de coleta | ✅ |
| RF101 | Classificar o valor contra a faixa de referência do parâmetro | ✅ |
| RF102 | Manter faixa de referência por sexo e por faixa etária | ✅ |
| RF103 | Gravar com o resultado a faixa de referência usada na classificação | ✅ |
| RF104 | Exibir a série histórica de um parâmetro entre coletas | ✅ |
| RF105 | Anexar o laudo em arquivo ao registro | ✅ |
| RF106 | Registrar a solicitação de exames feita ao paciente | ✅ |

### 2.11 Orientações nutricionais ✅

| ID | Requisito | Estado |
|---|---|---|
| RF110 | Manter biblioteca de orientações do consultório | ✅ |
| RF111 | Partir de um modelo pronto e editá-lo para o paciente | ✅ |
| RF112 | Anexar orientações ao plano entregue ao paciente | ✅ |
| RF113 | Congelar o texto entregue contra edições posteriores do modelo | ✅ |
| RF114 | Incluir imagem na orientação | ✅ |

---

## 3. Requisitos não funcionais

| ID | Requisito | Como é atendido |
|---|---|---|
| RNF01 | **Isolamento entre consultórios.** | Coluna `account_id` obrigatória em toda consulta; verificado por teste que simula acesso cruzado. |
| RNF02 | **Exatidão do cálculo.** | `BigDecimal` em toda a composição; arredondamento explícito em escala fixa. |
| RNF03 | **Honestidade do dado.** | Nutriente não determinado permanece nulo e é omitido da resposta. Nunca exibido como zero. |
| RNF04 | **Rastreabilidade da fonte.** | Todo alimento carrega sua procedência. Toda avaliação antropométrica carrega o protocolo usado. |
| RNF05 | **Esquema versionado.** | Flyway, com validação do mapeamento contra o banco no start. |
| RNF06 | **Portabilidade de banco.** | SQL restrito a subconjunto comum a H2 e PostgreSQL. |
| RNF07 | **Autenticação sem estado.** | Token JWT; usuário recarregado a cada requisição. |
| RNF08 | **Senhas nunca em texto.** | BCrypt, fator de custo 12. |
| RNF09 | **Não revelar registro alheio.** | Acesso cruzado responde 404, nunca 403. |
| RNF10 | **Licenciamento respeitado.** | Cada base usada dentro da própria licença, com atribuição no dado. |
| RNF11 | **Reprodutibilidade do cálculo clínico.** | Toda estimativa guarda o protocolo e as medidas de origem, para que o resultado possa ser reconferido depois. |
| RNF12 | **Clareza da falha.** | Pedido malformado responde 4xx nomeando o campo, e não 500 genérico. Erro de validação chega à tela grudado no campo que o causou, com foco e marcação para leitor de tela (WCAG 3.3.1 e 3.3.3). Ver [02-arquitetura.md](02-arquitetura.md) (AD-23). |
| RNF13 | **Piso de acessibilidade da interface.** | De 320px a 1920px: sem rolagem horizontal (WCAG 1.4.10) e nenhum alvo de toque abaixo de 24px (WCAG 2.5.8). Ver AD-24. |

---

## 4. Histórias de usuário

Formato: *Como* \<ator\>, *quero* \<ação\>, *para* \<valor\>.
Os critérios de aceite de cada história viram cenários em
[04-cenarios-bdd.md](04-cenarios-bdd.md).

### Épico 1 — Acesso ✅

**H01 · Criar minha conta** ✅
> Como **nutricionista**, quero criar minha conta informando meus dados, para
> começar a usar o sistema sem depender de instalação ou contato comercial.

**H02 · Entrar no sistema** ✅
> Como **usuário**, quero autenticar com e-mail e senha, para acessar apenas os
> dados do meu consultório.

**H03 · Não enxergar dados de outro consultório** ✅
> Como **nutricionista**, quero certeza de que nenhum outro profissional acessa
> meus pacientes, para cumprir o sigilo a que sou obrigado.

**H04 · Recuperar o acesso** ✅
> Como **nutricionista**, quero redefinir minha senha por um link, para não
> perder o acesso ao consultório quando esquecer a senha.

**Critérios de aceite**
- Peço o link informando o e-mail. A resposta é a mesma exista a conta ou não —
  um endpoint que dissesse "e-mail não encontrado" permitiria varrer endereços
  e descobrir quem tem conta.
- O link vale por uma hora e serve uma vez só.
- Pedir de novo cancela o pedido anterior.
- Redefinir encerra as sessões abertas: quem estava com a senha antiga sai na
  hora, sem esperar o token expirar.
- O envio do e-mail fica atrás de uma interface. Mensageria está fora do escopo
  (§1), e o que este trabalho entrega é o fluxo, não o canal.

---

### Épico 2 — Pacientes ✅

**H10 · Cadastrar um paciente** ✅
**H11 · Encontrar um paciente rapidamente** ✅
**H12 · Encerrar acompanhamento sem perder o histórico** ✅
**H13 · Conhecer os limites do meu plano** ✅

**H05 · Dar acesso à secretária** ✅
> Como **nutricionista**, quero cadastrar minha secretária com acesso próprio,
> para não precisar emprestar a minha senha.

**Critérios de aceite**
- Ela entra com o próprio e-mail e senha.
- Vê agenda e cadastro de pacientes; não vê prescrição, antropometria, exames,
  receitas, orientações nem financeiro.
- O menu não mostra o que ela não pode abrir — exibir para depois recusar seria
  levar a pessoa a uma tela de erro no fim do clique.
- Desativá-la derruba a sessão aberta, e não só o próximo login.
- O nutricionista dono da conta não pode ser desativado.

---

### Épico 3 — Alimentos e porções ✅

**H20 · Consultar a composição de um alimento** ✅
**H21 · Prescrever em porções que o paciente entende** ✅
**H22 · Ajustar uma porção à minha prática** ✅
**H23 · Cadastrar um alimento que não está na base** ✅
**H24 · Trazer minha própria tabela** ✅

---

**H25 · Prescrever uma preparação, e não os ingredientes dela** ✅
> Como **nutricionista**, quero montar uma receita a partir de outros alimentos,
> para prescrever "panqueca de aveia" em vez de listar ovo, aveia e banana
> separados em toda consulta.

**Critérios de aceite**
- Monto a receita com ingredientes do acervo, em gramas ou em medida caseira.
- Informo o peso da preparação pronta; a composição por 100 g é calculada sobre
  ele, e não sobre a soma dos ingredientes.
- Quando não informo o peso pronto, a soma é usada e o sistema diz que o
  rendimento é estimativa — cozinhar muda o peso.
- Informo em quantas porções rende, e a receita ganha a medida "1 porção".
- Depois de salva, a receita aparece na busca e entra num plano como qualquer
  alimento, carregando a procedência "Receita calculada".
- Uma receita pode ser ingrediente de outra; usar a si mesma é recusado.

---

**H26 · Saber o que a soma esconde** ✅
> Como **nutricionista**, quero saber quais nutrientes da receita vieram de
> apenas parte dos ingredientes, para não apresentar como total um valor que é
> piso.

**Critérios de aceite**
- Nutriente que algum ingrediente tem e outro não é somado e sinalizado como
  incompleto.
- Nutriente que nenhum ingrediente determina permanece ausente, e não vira zero.
- A tela só avisa sobre valores incompletos que ela de fato exibe.

### Épico 4 — Prescrição ✅

**H30 · Montar um plano alimentar** ✅
> Como **nutricionista**, quero montar o plano em refeições com horários e itens,
> para entregar ao paciente uma rotina executável.

**H31 · Saber quando o total não é confiável** ✅
> Como **nutricionista**, quero ser avisado quando parte dos itens não tem o
> nutriente determinado na fonte, para não apresentar como exato um número que é
> apenas um piso.

**H32 · Oferecer substituições** ✅
> Como **nutricionista**, quero dar opções equivalentes para cada item, para que
> o paciente tenha variedade sem sair do plano.

**H33 · Entregar o plano ao paciente** ✅
> Como **nutricionista**, quero enviar um link, para que meu paciente consulte o
> plano no celular sem instalar nada nem criar senha.

**H34 · Não expor minhas anotações** ✅
> Como **nutricionista**, quero que minhas anotações internas nunca apareçam no
> link do paciente, para poder registrar observações clínicas com franqueza.

**H35 · Reaproveitar meu trabalho** ✅

---

**H36 · Entregar o plano em papel** ✅
> Como **nutricionista**, quero gerar o plano em PDF, para o paciente sair da
> consulta com a folha na mão.

**Critérios de aceite**
- O PDF traz o consultório, meu nome, meu CRN e a vigência: a folha circula
  solta e precisa se bastar.
- A folha repete a leitura da tela — hora, refeição, e a medida caseira em
  corpo maior que o nome do alimento.
- Rascunho também imprime, e a folha se identifica como rascunho.
- Plano encerrado imprime com a tarja de encerrado.
- O nome do arquivo não tem acento nem espaço.
> Como **nutricionista**, quero salvar um plano como modelo, para não remontar do
> zero em casos parecidos.

---

### Épico 5 — Antropometria ✅

**H40 · Registrar uma avaliação** ✅
> Como **nutricionista**, quero registrar peso, altura, dobras e circunferências
> numa data, para acompanhar objetivamente a evolução do paciente.

**Critérios de aceite**
- Peso e altura são obrigatórios; dobras e circunferências, opcionais.
- Registro medidas em qualquer combinação, sem preencher tudo.
- A data padrão é hoje, e posso registrar avaliação retroativa.
- Uma avaliação futura é recusada.

---

**H41 · Ver o IMC classificado** ✅
> Como **nutricionista**, quero o IMC calculado e classificado, para ter uma
> primeira leitura sem calcular à mão.

**Critérios de aceite**
- O IMC é calculado a partir de peso e altura da própria avaliação.
- A classificação segue as faixas da OMS para adultos.
- Para paciente com menos de 20 anos o sistema não classifica pela faixa adulta,
  e diz por quê — a leitura correta seria por percentil de idade.

---

**H42 · Estimar o percentual de gordura** ✅
> Como **nutricionista**, quero estimar a composição corporal a partir das dobras,
> para acompanhar o que a balança sozinha não mostra.

**Critérios de aceite**
- Escolho o protocolo; cada um exige um conjunto próprio de dobras.
- Faltando qualquer dobra exigida, o sistema não estima e diz qual falta.
- O protocolo usado fica gravado com o resultado.
- Uma avaliação estimada por um protocolo não é comparada com outra estimada por
  protocolo diferente — a comparação seria enganosa.
- Massa gorda e massa magra são derivadas do percentual e do peso.

---

**H43 · Avaliar risco pela circunferência** ✅
> Como **nutricionista**, quero a relação cintura-quadril e a classificação de
> risco, para orientar sobre risco cardiometabólico.

**Critérios de aceite**
- A relação é calculada quando cintura e quadril foram medidos.
- A classificação de risco considera o sexo do paciente.
- Sem sexo informado, o sistema calcula a relação mas não classifica.

---

**H44 · Acompanhar a evolução** ✅
> Como **nutricionista**, quero ver a variação entre avaliações, para mostrar ao
> paciente o que mudou.

**Critérios de aceite**
- Vejo a série de avaliações em ordem cronológica.
- Cada avaliação mostra a variação frente à anterior e frente à primeira.
- Variação só aparece para medidas presentes nas duas avaliações comparadas.

---

**H45 · Estimar a necessidade energética** ✅
> Como **nutricionista**, quero estimar o gasto energético do paciente, para
> partir de uma meta fundamentada ao montar o plano.

**Critérios de aceite**
- ✅ O gasto basal é estimado por equação preditiva, com o fator de atividade
  informado por mim.
- ✅ A equação usada fica registrada com o resultado.
- ✅ Posso levar a estimativa para a meta do plano alimentar em um passo (RF68):
  a avaliação abre um plano novo com a meta já preenchida.

---

**H46 · Avaliar uma criança** ✅
> Como **nutricionista**, quero avaliar criança e adolescente pelas curvas da
> OMS, para não ficar sem leitura em toda uma faixa etária.

**Critérios de aceite**
- A classificação usa escore-z de estatura-para-idade, peso-para-idade e
  IMC-para-idade, conforme a faixa da OMS aplicável à idade.
- A faixa adulta do IMC não é usada abaixo de 20 anos — hoje o sistema apenas
  recusa a classificação nesse caso, o que resolve pela metade.
- A curva usada fica gravada com o resultado, como já ocorre com o protocolo de
  dobras (RNF11).

---

**H47 · Acompanhar uma gestante** ✅
> Como **nutricionista**, quero acompanhar o ganho de peso da gestante, para
> saber se está dentro do esperado para a semana gestacional.

**Critérios de aceite**
- Informo a semana gestacional e o peso pré-gestacional.
- O ganho acumulado é comparado à faixa recomendada para o IMC pré-gestacional.
- O resultado diz se o ganho está abaixo, dentro ou acima do esperado.
- Sem o peso pré-gestacional o sistema não classifica, e diz por quê.

---

### Épico 6 — Agenda ✅

**H50 · Agendar um atendimento** ✅
> Como **nutricionista**, quero marcar consulta para um paciente em data e hora,
> para organizar meu dia.

**Critérios de aceite**
- Informo paciente, data, hora de início e duração.
- Classifico o atendimento por tipo.
- Agendamento no passado é aceito apenas como registro de atendimento já ocorrido.

---

**H51 · Não marcar dois pacientes no mesmo horário** ✅
> Como **nutricionista**, quero ser impedido de sobrepor atendimentos, para não
> descobrir o conflito com o paciente na sala.

**Critérios de aceite**
- Um novo agendamento que se sobreponha a outro é recusado, indicando o conflito.
- Atendimento cancelado não bloqueia o horário.
- Encostar o fim de um no início de outro não é sobreposição.

---

**H52 · Acompanhar o dia** ✅
> Como **nutricionista**, quero ver a agenda do dia e da semana, para saber quem
> atendo e quando.

**Critérios de aceite**
- ✅ Vejo os atendimentos de um dia em ordem de horário, com o resumo de
  realizados e faltas.
- ✅ Vejo a semana: os sete dias empilhados, cada um com a sua régua, e o dia
  sem atendimento aparece como livre em vez de sumir da lista.

---

**H53 · Registrar o desfecho** ✅
> Como **nutricionista**, quero marcar o atendimento como realizado, cancelado ou
> falta, para ter histórico verdadeiro do acompanhamento.

**Critérios de aceite**
- Um atendimento percorre situações válidas e não retrocede de realizado para
  agendado.
- A falta fica registrada no histórico do paciente.

---

**H54 · Ver a agenda no calendário que eu já uso** ✅
> Como **nutricionista**, quero acompanhar meus atendimentos no meu calendário,
> para não ter duas agendas para conferir.

**Critérios de aceite**
- Gero um endereço e o assino no Google Agenda, no Apple Calendar ou no Outlook;
  eles passam a buscar a agenda sozinhos.
- O horário chega certo em qualquer leitor, e o mesmo atendimento não vira
  evento novo a cada atualização.
- O endereço pode ser trocado, e trocá-lo invalida o anterior na hora.
- É de mão única, e a tela diz isso: evento criado no meu calendário não vira
  atendimento aqui.

---

### Épico 7 — Financeiro ✅

**H60 · Registrar o que entra e o que sai** ✅
> Como **nutricionista**, quero lançar receitas e despesas, para saber se o
> consultório fecha no azul.

**Critérios de aceite**
- Todo lançamento tem tipo, valor, data e categoria.
- Valor precisa ser maior que zero; o que distingue entrada de saída é o tipo.
- Posso vincular a um paciente e a um atendimento.

---

**H61 · Saber quem me deve** ✅
> Como **nutricionista**, quero ver os lançamentos pendentes, para cobrar quem
> está em atraso.

**Critérios de aceite**
- Listo o que está pendente, com vencimento e paciente.
- Um lançamento vencido e não pago aparece destacado.
- Marcar como pago registra a data do recebimento.

---

**H62 · Apurar o período** ✅
> Como **nutricionista**, quero o resultado de um período, para entender meu
> faturamento.

**Critérios de aceite**
- Vejo total recebido, total a receber, despesas e resultado.
- O resultado considera apenas lançamentos efetivados, e o previsto aparece à parte.

---

**H63 · Emitir recibo** ✅
> Como **nutricionista**, quero emitir recibo de um atendimento pago, para
> entregar ao paciente.

---

### Épico 8 — Questionários e coleta pré-consulta ✅

**H70 · Preparar o meu questionário** ✅
> Como **nutricionista**, quero montar meus próprios questionários, para coletar
> o que a minha abordagem exige e não o que um formulário fixo pergunta.

**Critérios de aceite**
- Crio um questionário com perguntas de texto, escolha única, múltipla escolha e
  número.
- Marco quais perguntas são obrigatórias.
- Salvo como modelo e reaproveito em outros pacientes.
- Editar um modelo não altera respostas já recebidas (RF95).

---

**H71 · Receber o paciente já conhecendo o caso** ✅
> Como **nutricionista**, quero enviar o questionário antes da consulta, para
> chegar ao atendimento com a leitura feita.

**Critérios de aceite**
- Gero um link para o paciente, que responde sem precisar de conta — o mesmo
  mecanismo do plano público (RF48).
- O link é de uso único por envio e pode ser invalidado.
- As respostas ficam vinculadas ao paciente e, quando houver, ao atendimento.
- Vejo as respostas na tela do atendimento sem redigitar nada.

---

**H72 · Aplicar um questionário com escore** ✅
> Como **nutricionista**, quero aplicar questionários que produzem pontuação,
> para classificar o paciente por um critério publicado, e não por impressão.

**Critérios de aceite**
- O escore é somado pelas regras do instrumento, e não à mão.
- A classificação vem da faixa de corte do próprio instrumento.
- O instrumento e a versão usados ficam gravados com o resultado (RNF11).
- Faltando resposta obrigatória, o escore não é calculado, e o sistema diz qual
  falta — mesma regra das dobras cutâneas (H42).

---

### Épico 9 — Exames laboratoriais ✅

**H80 · Registrar um resultado** ✅
> Como **nutricionista**, quero registrar os exames do paciente, para ter o
> quadro bioquímico junto do resto do prontuário.

**Critérios de aceite**
- Registro parâmetro, valor, unidade e data de coleta.
- A data de coleta pode ser retroativa; futura é recusada.
- Anexo o laudo em arquivo, quando houver.
- Um exame sem valor determinado permanece sem valor, e não como zero (RNF03).

---

**H81 · Saber o que está fora da faixa** ✅
> Como **nutricionista**, quero o valor classificado contra a referência, para
> localizar rapidamente o que está alterado.

**Critérios de aceite**
- A faixa de referência considera sexo e idade do paciente.
- A faixa usada fica gravada junto do resultado — laboratórios publicam faixas
  diferentes conforme o método, e reclassificar um exame antigo por uma
  referência nova mudaria o passado (RNF11).
- Sem faixa cadastrada para o parâmetro, o valor é registrado sem classificação.

---

**H82 · Acompanhar um parâmetro ao longo do tempo** ✅
> Como **nutricionista**, quero ver a série de um parâmetro entre coletas, para
> distinguir tendência de valor isolado.

**Critérios de aceite**
- Vejo os valores em ordem cronológica, com a data de cada coleta.
- Valores em unidades diferentes não são comparados na mesma série.

---

### Épico 10 — Orientações nutricionais ✅

**H90 · Reaproveitar o que eu já escrevi** ✅
> Como **nutricionista**, quero manter minhas orientações numa biblioteca, para
> não reescrever a mesma explicação a cada paciente.

**Critérios de aceite**
- Crio orientações próprias e parto de modelos prontos para editar.
- Anexo uma figura à orientação (RF114) — um prato dividido diz mais que o
  parágrafo que o descreve. Ela acompanha o texto no link e no PDF.
- A biblioteca é do consultório, e não do sistema — a mesma separação dos
  alimentos próprios frente às tabelas de referência (RF24, RF25).

---

**H91 · Entregar a orientação junto do plano** ✅
> Como **nutricionista**, quero anexar orientações ao plano do paciente, para
> que ele receba a explicação junto da prescrição.

**Critérios de aceite**
- Anexo uma ou mais orientações ao plano antes de publicar.
- O paciente as vê na mesma página do plano, sem outra credencial.
- O texto entregue é congelado **no anexo**, e não na publicação: editar o
  modelo depois não altera o que o paciente já recebeu, e o texto anexado pode
  ser adaptado a este paciente sem sujar o modelo — mesma regra do peso
  prescrito (RF50).

---

## 5. Rastreabilidade

| Requisito | Verificado em |
|---|---|
| RF01, RF02, RNF07, RNF08 | `AuthenticationFlowTest` |
| RF06, RNF07, RNF08 | `PasswordRecoveryTest` |
| RF03, RF05, RNF01, RNF09 | `PracticeTeamTest` |
| RF90–RF95, RNF01 | `QuestionnaireTest` |
| RF10–RF13, RF04, RNF01 | `PatientTest` |
| RF14 | `PatientsImportTest` |
| RF20–RF27, RNF02, RNF03 | `FoodTest` |
| RF28, RNF02, RNF03 | `RecipeTest` |
| RF30–RF34, RNF01 | `HouseholdMeasureTest` |
| RF40–RF50, RNF01, RNF02 | `PrescriptionTest` |
| RF51, RNF01 | `PlanPdfTest` |
| RF60–RF67 | `AnthropometryTest` |
| RF68 (cliente), RF69–RF69b, RNF11 | `GrowthAndPregnancyTest` |
| RF70–RF75 | `ScheduleTest` |
| RF76, RNF01 | `ScheduleSubscriptionTest` |
| RF80–RF85 | `FinanceTest` |
| RF100–RF106, RNF01, RNF03, RNF11 | `LabtestTest` |
| RF110–RF114, RNF01 | `HandoutTest` |
| — | `PluralMeasureTest` (concordância da medida caseira na entrega ao paciente) |
| RNF12 | `ApiErrorsTest` (o que a API responde quando o pedido está errado) |

Todos os requisitos funcionais aparecem nesta tabela. A exceção é o que vive no
cliente, explicada abaixo.

**RF68 e RF74 vivem no cliente.** A meta que vem da avaliação e a visão de
semana usam API já coberta por teste, mas o passo que liga uma tela à outra é
código de interface, e a interface não tem suíte automatizada — é a dívida
registrada em [02-arquitetura.md](02-arquitetura.md#7-dívidas-conhecidas).

**Situação atual: 284 testes, todos passando.**
