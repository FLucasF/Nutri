# Cenários BDD

Comportamento esperado do sistema, escrito em Gherkin.

Os cenários são a especificação executável das histórias de
[01-requisitos-e-historias.md](01-requisitos-e-historias.md). Estão em
português porque a linguagem do domínio é a da nutrição clínica, e traduzir
"dobra cutânea" para inglês afastaria o texto de quem valida o requisito.

**Convenção de estado**

- ✅ **Coberto** — existe teste automatizado que verifica este cenário.
- 🔶 **Parcial** — o grupo tem cenários cobertos e cenários ainda especificados.
- ⬜ **Especificado** — o cenário define o que deve ser construído.

Os números dos cenários não são ilustrativos: são os valores que o teste
confere. Um cenário sem número verificável não prova nada.

---

## Contexto comum

```gherkin
Contexto:
  Dado que existe o consultório "Clínica Nutrir" com a nutricionista "Helena"
  E que existe o consultório "Espaço Vida" com a nutricionista "Bruna"
  E que "Helena" tem a paciente "Marina Duarte", de 34 anos, sexo feminino
```

---

# 1. Acesso e isolamento ✅

```gherkin
Funcionalidade: Autenticação

  Cenário: Criar conta e já entrar ✅
    Dado que eu não tenho conta
    Quando eu me cadastro com nome, e-mail e uma senha de 12 caracteres
    Então minha conta é criada no plano experimental
    E eu recebo um token válido, sem precisar fazer login em seguida

  Cenário: Recusar e-mail já cadastrado ✅
    Dado que existe uma conta com o e-mail "helena@clinica.com"
    Quando eu tento me cadastrar com o mesmo e-mail
    Então recebo o erro 422
    E a mensagem diz que já existe usuário com este e-mail

  Cenário: Recusar senha incorreta ✅
    Dado que existe a conta "helena@clinica.com"
    Quando eu tento entrar com a senha errada
    Então recebo o erro 401
    E a resposta não indica se o erro foi no e-mail ou na senha

  Cenário: Bloquear recurso protegido sem credencial ✅
    Quando eu chamo "/api/auth/eu" sem token
    Então recebo o erro 401
    # E não 403: sem credencial nenhuma, o problema é autenticação
```

```gherkin
Funcionalidade: Recuperação de senha

  Cenário: Redefinir e entrar com a senha nova ✅
    Dado que "Helena" pediu o link de recuperação
    Quando ela redefine a senha pelo link
    Então entra com a senha nova
    E a senha antiga deixa de funcionar

  Cenário: Pedir para e-mail sem conta responde igual ✅
    Quando alguém pede recuperação para um e-mail sem conta
    Então recebe a mesma resposta de quando a conta existe
    # Responder "não encontrado" permitiria varrer endereços

  Cenário: O link vale uma vez só ✅
    Dado um link já usado
    Quando alguém tenta usá-lo de novo
    Então recebe o erro 422

  Cenário: Pedir de novo cancela o pedido anterior ✅
    Dado dois pedidos seguidos
    Então só o segundo link funciona

  Cenário: Redefinir derruba as sessões abertas ✅
    Dado que "Helena" está logada num aparelho
    Quando ela redefine a senha noutro
    Então a sessão do primeiro aparelho para de funcionar
    # Quem obteve a senha antiga sai na hora, sem esperar o token expirar

  Cenário: A sessão aberta depois da troca continua valendo ✅
    Quando "Helena" entra com a senha nova
    Então a sessão funciona normalmente
    # O contador de versão evita o erro oposto: deslogar quem acabou de trocar
```

```gherkin
Funcionalidade: Isolamento entre consultórios

  Cenário: Não ler paciente de outro consultório ✅
    Dado que "Helena" cadastrou a paciente "Marina Duarte"
    Quando "Bruna" tenta abrir essa paciente
    Então recebe o erro 404
    # 404 e não 403: um 403 confirmaria que o registro existe

  Cenário: Não alterar nem remover registro alheio ✅
    Dado que "Helena" cadastrou a paciente "Marina Duarte"
    Quando "Bruna" tenta editá-la
    Então recebe o erro 404
    Quando "Bruna" tenta inativá-la
    Então recebe o erro 404
    E os dados de "Marina Duarte" permanecem intactos

  Cenário: Listagem não vaza registro alheio ✅
    Dado que "Helena" tem 1 paciente
    E que "Bruna" não tem nenhum
    Quando "Bruna" lista seus pacientes
    Então a lista vem vazia
```

---

# 2. Pacientes ✅

```gherkin
Funcionalidade: Cadastro de pacientes

  Cenário: Calcular a idade a partir do nascimento ✅
    Quando "Helena" cadastra "Marina Duarte" nascida em 15/04/1992
    Então a paciente é criada
    E a idade exibida corresponde aos anos completos até hoje

  Cenário: Recusar nascimento no futuro ✅
    Quando "Helena" cadastra um paciente nascido amanhã
    Então recebe o erro 400
    E o campo apontado é "dataNascimento"

  Cenário: Recusar CPF repetido no mesmo consultório ✅
    Dado que "Helena" tem um paciente com o CPF "123.456.789-00"
    Quando "Helena" cadastra outro paciente com o mesmo CPF
    Então recebe o erro 422

  Esquema do Cenário: Buscar por termo parcial ✅
    Dado que "Helena" tem os pacientes "Joana Prado" e "Ricardo Alves"
    Quando ela busca por "<termo>"
    Então encontra <quantidade> paciente(s)

    Exemplos:
      | termo   | quantidade |
      | joana   | 1          |
      | JOANA   | 1          |
      | alves   | 1          |
      | pereira | 0          |

  Cenário: Inativar preserva o histórico ✅
    Dado que "Helena" tem a paciente "Marina Duarte"
    Quando ela inativa a paciente
    Então a paciente sai da lista de ativos
    Mas continua acessível pelo id
    E pode ser reativada

  Cenário: Barrar o sexto paciente no plano experimental ✅
    Dado que "Helena" está no plano experimental
    E que ela já tem 5 pacientes ativos
    Quando tenta cadastrar o sexto
    Então recebe o erro 422
    E a mensagem nomeia o limite do plano
```

---

```gherkin
Funcionalidade: Importação de pacientes

  Cenário: Importar a planilha ✅
    Dado um arquivo com as colunas nome, email, telefone, nascimento e sexo
    Quando "Helena" o importa
    Então os pacientes entram com os campos reconhecidos preenchidos

  Cenário: Reconhecer os formatos de data da planilha brasileira ✅
    Dado nascimentos escritos como 12/03/1992, 1992-03-12, 5/7/1986 e 12-03-1992
    Quando "Helena" importa o arquivo
    Então os quatro pacientes entram com a data de nascimento

  Cenário: Linha sem nome não derruba o arquivo ✅
    Dado um arquivo de três linhas em que a segunda não tem nome
    Quando "Helena" o importa
    Então dois pacientes entram
    E o aviso aponta a linha 3 como ignorada
    # A numeração conta o cabeçalho: é o número que o usuário vê na planilha

  Cenário: Dado ilegível não descarta o paciente ✅
    Dado uma linha com e-mail "nao tenho" e nascimento "ontem"
    Quando "Helena" a importa
    Então o paciente entra apenas com o nome
    E o aviso diz que a data não foi reconhecida
    # O nome é o que torna o cadastro útil; o resto se corrige na ficha

  Cenário: Não duplicar paciente pelo CPF ✅
    Dado que "Marina Duarte" já está cadastrada com o CPF 12345678901
    Quando "Helena" importa uma planilha com o CPF 123.456.789-01
    Então essa linha é ignorada
    E o aviso menciona o CPF
    # A comparação é por dígito: a planilha traz o CPF pontuado

  Cenário: O limite do plano vale na importação ✅
    Dado o plano experimental, que permite 5 pacientes ativos
    Quando "Helena" importa uma planilha com 7 nomes
    Então 5 pacientes entram
    E o aviso diz em que linha a importação parou
    # Importar não é atalho para contornar o limite
```


# 3. Alimentos e porções ✅

```gherkin
Funcionalidade: Busca na base de alimentos

  Cenário: Buscar sem acento encontra o acentuado ✅
    Quando "Helena" busca por "acucar"
    Então encontra os mesmos alimentos que encontraria buscando "açúcar"
    E a quantidade de resultados é a mesma nas duas buscas

  Cenário: Alimento de tabela de referência não é editável ✅
    Dado o alimento "Arroz, tipo 1, cozido" da TACO
    Quando "Helena" tenta editá-lo
    Então recebe o erro 422
    E a mensagem sugere cadastrar um alimento próprio a partir dele

  Cenário: Alimento próprio não vaza para outro consultório ✅
    Dado que "Helena" cadastrou o alimento "Mistura secreta da nutri"
    Quando "Bruna" busca por "mistura secreta"
    Então não encontra nenhum resultado
```

```gherkin
Funcionalidade: Relevância da busca

  Cenário: Alimento de referência vence industrializado ✅
    Dado que a base tem 597 alimentos da TACO e 21.377 industrializados
    Quando "Helena" busca por "banana"
    Então o primeiro resultado vem da TACO
    # Sem ranqueamento a ordem é alfabética, e o volume decide:
    # o primeiro resultado era "&Joy Frutas Banana + Cacau"

  Esquema do Cenário: A busca desce de fonte em fonte até encontrar ✅
    Quando "Helena" busca por "<termo>"
    Então o primeiro resultado vem de "<fonte>"

    Exemplos:
      | termo       | fonte           |
      | arroz       | TACO            |
      | banana      | TACO            |
      | leite       | TACO            |
      | frango      | TACO            |
      | feijao      | TACO            |
      | feijoada    | IBGE            |
      | carne suina | IBGE            |
      | nescau      | OPEN_FOOD_FACTS |
    # TACO responde pelo alimento básico; IBGE pela preparação que ela não tem;
    # o industrializado aparece quando nenhuma tabela cobre o termo

  Cenário: O termo precisa ser palavra inteira, não prefixo ✅
    Dado que existe o cereal infantil "Arrozina"
    Quando "Helena" busca por "arroz"
    Então "Arrozina" não é o primeiro resultado
    E o primeiro resultado começa com a palavra "arroz" inteira
    # "Arrozina" tem nome mais curto e vencia no desempate por tamanho,
    # embora as cinco letras sejam só o começo de outra palavra

  Cenário: Sem termo, a listagem volta a ser alfabética ✅
    Quando "Helena" lista os alimentos sem informar termo
    Então vêm em ordem alfabética
    # Ranquear por relevância sem termo não faria sentido: ao navegar um grupo
    # inteiro, o profissional espera ordem previsível

  Cenário: Filtro de fonte prevalece sobre o ranqueamento ✅
    Quando "Helena" busca "arroz" filtrando por Open Food Facts
    Então todos os resultados vêm do Open Food Facts
```

```gherkin
Funcionalidade: Composição da base

  Cenário: As três fontes são carregadas no boot ✅
    Então a base tem 597 alimentos da TACO
    E 1.971 do IBGE
    E 21.377 do Open Food Facts

  Cenário: As medidas do IBGE vêm da pesquisa, não de estimativa ✅
    Então existem 11.801 porções ligadas a alimentos do IBGE
    E praticamente nenhum alimento do IBGE fica sem porção
    # Registradas em campo pelo entrevistador da POF, com o utensílio que a
    # família usou — procedência melhor que qualquer estimativa

  Cenário: O IBGE traz nutrientes que a TACO não determina ✅
    Quando "Helena" abre um alimento do IBGE
    Então a composição inclui cobalamina (B12) e folato
    # Nenhuma linha da TACO tem esses nutrientes
```

```gherkin
Funcionalidade: Cálculo de porção

  Cenário: Escalar a composição proporcionalmente ✅
    Dado o alimento "Arroz, tipo 1, cozido" com 128,3 kcal por 100 g
    Quando "Helena" calcula uma porção de 150 g
    Então a energia da porção é 1,5 vez a energia por 100 g

  Cenário: Calcular a partir de uma medida caseira ✅
    Dado o alimento "Granola da casa" com 400 kcal por 100 g
    E que ele tem a porção "colher de sopa" de 15 g
    Quando "Helena" prescreve 3 colheres de sopa
    Então o peso resultante é 45 g
    E a energia é 180 kcal
    E a porção é apresentada como "3 colher de sopa"

  Cenário: Nutriente ausente na fonte continua ausente ✅
    Dado o alimento "Sal, grosso", que não tem energia determinada na TACO
    Quando "Helena" calcula uma porção de 10 g
    Então a energia não aparece na resposta
    E o sódio aparece normalmente
    # Ausente é diferente de zero: zero afirmaria que o sal não tem calorias
    # por análise, quando na verdade o dado não foi determinado

  Cenário: Recusar quantidade não positiva ✅
    Quando "Helena" calcula uma porção com quantidade 0
    Então recebe o erro 422
```

```gherkin
Funcionalidade: Medidas caseiras

  Cenário: Sal é prescrito em pitada ✅
    Dado o alimento "Sal, grosso"
    Quando "Helena" abre o alimento
    Então entre as porções há "pitada", "colher de chá" e "colher de sopa"
    E a porção padrão é "pitada", com 0,4 g
    # Ninguém serve 5 g de sal

  Cenário: Todo alimento da TACO tem porção usual ✅
    Quando se percorre os 597 alimentos da TACO
    Então nenhum deles está sem ao menos uma porção

  Cenário: Cadastrar porção própria sobre alimento de referência ✅
    Dado o alimento "Arroz, tipo 1, cozido" da TACO
    Quando "Helena" cadastra a porção "colher de servir da clínica" com 45 g
    Então a porção é criada e é editável por ela
    E aparece antes das porções que acompanham o sistema
    E "Bruna" não a enxerga

  Cenário: Não remover porção do acervo comum ✅
    Quando "Helena" tenta remover a porção "pitada" do sal
    Então recebe o erro 422
    E a mensagem explica que ela pode criar a própria versão

  Cenário: Não calcular com porção de outro consultório ✅
    Dado que "Helena" criou a porção "porção do consultório A" no abacate
    Quando "Bruna" tenta calcular usando essa porção
    Então recebe o erro 404

  Cenário: Produto industrializado ganha a porção da embalagem ✅
    Dado um produto do Open Food Facts com quantidade declarada "395 g"
    Quando se abre o produto
    Então existe a porção "embalagem (395 g)", marcada como padrão
```

---

```gherkin
Funcionalidade: Busca por código de barras

  Cenário: Encontrar o produto pelo código impresso na embalagem ✅
    Dado o produto "Leite Condensado Semidesnatado ITALAC" no acervo público
    Quando "Helena" busca pelo código 7890000110266
    Então o produto é devolvido com a composição e a procedência

  Cenário: Aceitar o código pontuado ✅
    Quando "Helena" busca por "789-0000.110266"
    Então o resultado é o mesmo da busca sem pontuação
    # O código chega pontuado do leitor ou digitado com separador

  Cenário: Código sem produto devolve lista vazia ✅
    Quando "Helena" busca por um código que não existe na base
    Então recebe uma lista vazia, e não um erro
    # A base cobre o que o Open Food Facts tem do Brasil, não o mercado inteiro

  Cenário: O produto do consultório vem antes do público ✅
    Dado que "Helena" cadastrou um produto próprio com o código 7890000110266
    Quando ela busca por esse código
    Então o produto dela aparece em primeiro lugar

  Cenário: Código de outro consultório não aparece ✅
    Dado um produto próprio de "Bruna" com o código 1111111111111
    Quando "Helena" busca por esse código
    Então recebe uma lista vazia
```


```gherkin
Funcionalidade: Receitas compostas

  Cenário: A composição é por 100 g da preparação pronta ✅
    Dado 100 g de arroz cru, que tem 360 kcal por 100 g
    Quando "Helena" monta uma receita que rende 250 g cozidos
    Então a composição da receita é 144 kcal por 100 g
    # Os 360 kcal continuam existindo, agora diluídos em 250 g.
    # Dividir pela soma dos ingredientes daria 360 — errado por 2,5 vezes

  Cenário: Sem peso pronto informado, a soma é usada e o sistema admite ✅
    Dado dois ingredientes de 100 g somando 400 kcal
    Quando "Helena" salva a receita sem informar o peso pronto
    Então a composição é 200 kcal por 100 g
    E o resultado marca o rendimento como estimado

  Cenário: Ingrediente entra por medida caseira ✅
    Dado que "1 colher de sopa" de óleo pesa 8 g
    Quando "Helena" usa 2 colheres de sopa na receita
    Então o ingrediente entra com 16 g
    E aparece como "2 colheres de sopa"

  Cenário: Nutriente presente em parte dos ingredientes vira piso ✅
    Dado uma farinha com 9 g de fibra por 100 g e um fermento sem fibra declarada
    Quando "Helena" monta a receita com 100 g de cada, rendendo 200 g
    Então a fibra da receita é 4,5 g por 100 g
    E a fibra é listada como nutriente incompleto
    # Somar tratando o ausente como zero daria o mesmo número aparentando exatidão

  Cenário: Nutriente que nenhum ingrediente determina continua ausente ✅
    Dado uma receita cujos ingredientes não têm sódio determinado
    Quando "Helena" abre a composição
    Então o sódio aparece como não informado
    E não é listado como incompleto
    # Ausente em todos não é incompleto: é simplesmente não determinado

  Cenário: A receita nasce com as porções derivadas do rendimento ✅
    Quando "Helena" salva uma receita que rende 800 g em 8 porções
    Então a receita ganha as medidas "porção" de 100 g e "receita inteira" de 800 g

  Cenário: Mudar o rendimento refaz as porções ✅
    Dado a receita que rendia 800 g em 8 porções
    Quando "Helena" a corrige para 600 g em 6 porções
    Então continua existindo uma única medida "porção"

  Cenário: A receita entra no acervo como alimento ✅
    Dado a receita "Panqueca de aveia" salva
    Quando "Helena" busca por "panqueca"
    Então a receita aparece com a procedência "Receita calculada"

  Cenário: Uma receita pode ser ingrediente de outra ✅
    Dado a receita "Refogado" com 400 kcal em 100 g
    Quando "Helena" usa 100 g de refogado numa torta que rende 200 g
    Então o refogado contribui com 400 kcal para a torta

  Cenário: Uma receita não pode ser ingrediente dela mesma ✅
    Quando "Helena" tenta usar a receita "Sopa" como ingrediente da "Sopa"
    Então recebe o erro 422
    # Calcular a composição entraria em recursão

  Cenário: Receita sem ingrediente é recusada ✅
    Quando "Helena" salva uma receita com a lista de ingredientes vazia
    Então recebe o erro 400

  Cenário: Ingrediente de outro consultório não é encontrado ✅
    Dado um alimento próprio de "Bruna"
    Quando "Helena" tenta usá-lo como ingrediente
    Então recebe o erro 404

  Cenário: Um consultório não abre a receita de outro ✅
    Dado a receita de "Helena"
    Quando "Bruna" tenta abri-la
    Então recebe o erro 404

  Cenário: Remover inativa em vez de apagar ✅
    Quando "Helena" remove uma receita
    Então ela some da lista
    # Não apaga: a receita pode estar prescrita num plano ativo
```


# 4. Prescrição ✅

```gherkin
Funcionalidade: Montagem do plano alimentar

  Cenário: Totalizar a refeição a partir das porções ✅
    Dado o alimento "Arroz, tipo 1, cozido" com a porção "colher de sopa cheia" de 25 g
    Quando "Helena" prescreve 4 colheres no almoço
    Então o item registra 100 g
    E a porção é apresentada como "4 colher de sopa cheia"
    E o total da refeição corresponde a 100 g do alimento
    E o total do dia é igual ao total da única refeição

  Cenário: Distribuição energética entre macronutrientes ✅
    Dado um alimento com 10 g de proteína, 20 g de carboidrato e 10 g de gordura por 100 g
    Quando "Helena" prescreve 100 g dele
    Então a energia calculada pelos macronutrientes é 210 kcal
    E a distribuição é 19,0% proteína, 38,1% carboidrato e 42,9% gordura
    # 4 kcal/g para proteína e carboidrato, 9 kcal/g para gordura

  Cenário: Adequação frente à meta ✅
    Dado um plano com meta de 420 kcal
    E que o prescrito totaliza 210 kcal
    Então a adequação energética é 50,0%

  Cenário: Avisar que o total é parcial ✅
    Dado um item de arroz, que tem proteína determinada
    E um item de um suplemento que só tem energia determinada
    Quando "Helena" totaliza o dia
    Então 2 itens entram no cálculo
    E "proteinaG" consta entre os nutrientes incompletos
    E o total é marcado como não confiável
    # O valor somado é um piso, não o total real: apresentá-lo como exato
    # levaria o profissional a concluir que a proteína está adequada

  Cenário: Plano qualitativo não inventa quantidade ✅
    Dado um plano com método qualitativo
    Quando "Helena" inclui "Salada de folhas à vontade"
    E inclui um item com quantidade informada
    Então nenhum item registra peso
    E a porção do primeiro item é exibida como "a vontade"
    E nenhum item entra no cálculo nutricional

  Cenário: Substituição exige o método compatível ✅
    Dado um plano com método "por alimentos"
    Quando "Helena" tenta incluir uma substituição num item
    Então recebe o erro 422
    Mas ao trocar o método para "por equivalentes"
    Então a substituição é aceita
```

```gherkin
Funcionalidade: Entrega do plano ao paciente

  Cenário: Link não serve rascunho ✅
    Dado um plano em rascunho
    Quando alguém abre o link público
    Então recebe o erro 404
    # Responder "existe, mas ainda não" entregaria informação sobre
    # trabalho em andamento

  Cenário: Publicar libera o link ✅
    Dado um plano em rascunho com ao menos um item
    Quando "Helena" publica o plano
    Então o link passa a responder
    E mostra o título, a paciente pelo primeiro nome e o CRN da profissional
    E mostra a porção como "4 colher de sopa cheia"

  Cenário: Não publicar plano vazio ✅
    Dado um plano sem nenhum item
    Quando "Helena" tenta publicá-lo
    Então recebe o erro 422
    # O paciente abriria o link e encontraria uma página vazia

  Cenário: Anotação interna nunca sai no link ✅
    Dado um plano cuja anotação interna diz "Paciente relatou ansiedade noturna"
    E cuja orientação ao paciente diz "Beba dois litros de água por dia"
    Quando o plano é aberto pelo link
    Então o conteúdo não contém "ansiedade noturna"
    E não contém nenhum identificador de conta
    Mas contém "dois litros de água"

  Cenário: Regerar o link invalida o anterior ✅
    Dado um plano publicado cujo link foi entregue
    Quando "Helena" gera um novo link
    Então o link antigo passa a responder 404
    E o novo link responde normalmente

  Cenário: Plano encerrado continua visível, marcado ✅
    Dado um plano publicado
    Quando "Helena" o encerra
    Então ela não consegue mais editá-lo
    E o paciente ainda abre o link
    E o plano aparece como encerrado e fora de vigência
```

```gherkin
Funcionalidade: Reaproveitamento

  Cenário: Modelo não pertence a paciente ✅
    Quando "Helena" salva um plano marcado como modelo
    Então o plano é criado sem paciente vinculado

  Cenário: Aplicar modelo a um paciente ✅
    Dado um plano modelo com uma refeição e um item
    Quando "Helena" o duplica para "Marina Duarte"
    Então nasce um plano em rascunho vinculado à paciente
    E as refeições e itens foram copiados

  Cenário: Corrigir a porção não reescreve plano já prescrito ✅
    Dado que "Helena" tem o alimento "Granola da casa" com "colher de sopa" de 15 g
    E que ela prescreveu 2 colheres num plano, gravando 30 g
    Quando ela corrige a colher para 20 g no cadastro do alimento
    Então o plano já prescrito continua registrando 30 g
    # O paciente recebeu um documento com valores calculados naquele dia
```

---

```gherkin
Funcionalidade: Plano em papel

  Cenário: Gerar o PDF do plano ✅
    Dado um plano publicado para "Marina Duarte"
    Quando "Helena" pede o PDF
    Então recebe um arquivo PDF válido
    E o nome sugerido é "plano-de-reeducacao.pdf", sem acento nem espaço

  Cenário: Rascunho imprime identificado ✅
    Dado um plano ainda em rascunho
    Quando "Helena" pede o PDF
    Então a folha sai com a tarja de rascunho
    # Conferir antes de publicar é parte do trabalho; a folha é que avisa

  Cenário: Plano sem refeição ainda gera folha ✅
    Dado um plano qualitativo sem refeição cadastrada
    Quando "Helena" pede o PDF
    Então recebe um arquivo PDF válido, em vez de erro

  Cenário: O PDF é do profissional, não do link ✅
    Quando alguém pede o PDF sem credencial
    Então recebe o erro 401

  Cenário: Um consultório não imprime o plano de outro ✅
    Quando "Bruna" pede o PDF de um plano de "Helena"
    Então recebe o erro 404
```


# 5. Antropometria 🔶 coberto, exceto o que segue marcado

Cenários que especificam o módulo a construir.

```gherkin
Funcionalidade: Registro de avaliação antropométrica

  Cenário: Registrar avaliação com o mínimo ✅
    Quando "Helena" registra para "Marina Duarte" uma avaliação
      com peso 70 kg e altura 170 cm
    Então a avaliação é criada com a data de hoje
    E o IMC calculado é 24,22

  Cenário: Recusar avaliação futura ✅
    Quando "Helena" registra uma avaliação com data de amanhã
    Então recebe o erro 400
    # Medida que ainda não foi feita não é dado, é suposição

  Cenário: Aceitar avaliação retroativa ✅
    Quando "Helena" registra uma avaliação com data de 30 dias atrás
    Então a avaliação é criada
    # Consultório que digitaliza fichas antigas precisa disso

  Esquema do Cenário: Classificar o IMC pelas faixas da OMS ✅
    Dado um paciente adulto com altura 170 cm
    Quando "Helena" registra o peso <peso> kg
    Então o IMC é <imc>
    E a classificação é "<classificacao>"

    Exemplos:
      | peso  | imc   | classificacao        |
      | 50,0  | 17,30 | Baixo peso           |
      | 60,0  | 20,76 | Eutrofia             |
      | 75,0  | 25,95 | Sobrepeso            |
      | 90,0  | 31,14 | Obesidade grau I     |
      | 105,0 | 36,33 | Obesidade grau II    |
      | 120,0 | 41,52 | Obesidade grau III   |

  Cenário: Não classificar adolescente pela faixa adulta ✅
    Dado o paciente "Pedro", de 15 anos
    Quando "Helena" registra peso 55 kg e altura 165 cm
    Então o IMC é calculado normalmente
    Mas a classificação não é preenchida
    E o sistema informa que abaixo de 20 anos a leitura correta é por percentil
    # Aplicar a faixa adulta a um adolescente produziria conclusão errada
```

```gherkin
Funcionalidade: Composição corporal

  Cenário: Estimar gordura pelo protocolo de Faulkner ✅
    Dado que "Marina Duarte" pesa 70 kg
    E que suas dobras são tríceps 20 mm, subescapular 18 mm,
      suprailíaca 22 mm e abdominal 25 mm
    Quando "Helena" estima a composição pelo protocolo de Faulkner
    Então o percentual de gordura é 18,79%
    E a massa gorda é 13,15 kg
    E a massa magra é 56,85 kg
    E o protocolo usado fica gravado com o resultado
    # Faulkner: %G = soma das 4 dobras x 0,153 + 5,783

  Cenário: Recusar estimativa com dobra faltando ✅
    Dado que "Marina Duarte" tem apenas as dobras tríceps e abdominal
    Quando "Helena" tenta estimar pelo protocolo de Faulkner
    Então recebe o erro 422
    E a mensagem nomeia quais dobras faltam
    # Completar a conta com dobra ausente inventaria composição corporal

  Cenário: Não comparar protocolos diferentes ✅
    Dado que a avaliação de janeiro usou o protocolo de Faulkner
    E que a de março usou o protocolo de Pollock de 3 dobras
    Quando "Helena" vê a evolução do percentual de gordura
    Então a variação entre as duas não é apresentada
    E o sistema explica que os protocolos diferem
    # Protocolos têm erro-padrão próprio; a diferença entre eles seria lida
    # como mudança do paciente

  Cenário: Registrar dobras sem estimar composição ✅
    Quando "Helena" registra apenas as dobras, sem escolher protocolo
    Então as dobras ficam gravadas
    E nenhum percentual de gordura é calculado
```

```gherkin
Funcionalidade: Risco pela circunferência

  Cenário: Calcular a relação cintura-quadril ✅
    Dado que "Marina Duarte" tem cintura 80 cm e quadril 100 cm
    Quando "Helena" registra a avaliação
    Então a relação cintura-quadril é 0,80

  Esquema do Cenário: Classificar o risco por sexo ✅
    Dado um paciente do sexo <sexo>
    Quando a relação cintura-quadril é <rcq>
    Então o risco é classificado como "<risco>"

    Exemplos:
      | sexo      | rcq  | risco    |
      | FEMININO  | 0,75 | Baixo    |
      | FEMININO  | 0,82 | Moderado |
      | FEMININO  | 0,88 | Alto     |
      | MASCULINO | 0,88 | Baixo    |
      | MASCULINO | 0,95 | Moderado |
      | MASCULINO | 1,02 | Alto     |

  Cenário: Não classificar sem sexo informado ✅
    Dado um paciente sem sexo informado
    Quando "Helena" registra cintura e quadril
    Então a relação é calculada
    Mas o risco não é classificado
    # Os pontos de corte são específicos por sexo

  Cenário: Não calcular a relação sem as duas medidas ✅
    Quando "Helena" registra apenas a cintura
    Então a medida fica gravada
    E a relação cintura-quadril não é calculada
```

```gherkin
Funcionalidade: Necessidade energética

  Cenário: Estimar o gasto basal ✅
    Dado que "Marina Duarte" tem 34 anos, é do sexo feminino,
      pesa 70 kg e mede 170 cm
    Quando "Helena" estima o gasto pela equação de Mifflin-St Jeor
    Então o gasto energético basal é 1431,5 kcal
    E a equação usada fica registrada
    # Mifflin-St Jeor (mulheres): 10 x peso + 6,25 x altura - 5 x idade - 161

  Cenário: Aplicar o fator de atividade ✅
    Dado um gasto basal de 1431,5 kcal
    Quando "Helena" informa fator de atividade 1,55
    Então o gasto energético total é 2218,8 kcal

  Cenário: Não estimar sem idade ✅
    Dado um paciente sem data de nascimento
    Quando "Helena" tenta estimar o gasto energético
    Então recebe o erro 422
    E a mensagem explica que a equação depende da idade

  Cenário: Levar a estimativa para o plano 🔶
    Dado um gasto energético total estimado de 2218,8 kcal
    Quando "Helena" cria um plano a partir dessa avaliação
    Então a meta energética do plano vem preenchida com 2218,8
    # Implementado, e verificado a mao. O passo liga uma tela a outra e vive no
    # cliente, que nao tem suite automatizada — ver Dividas conhecidas.
```

```gherkin
Funcionalidade: Evolução do paciente

  Cenário: Comparar com a avaliação anterior ✅
    Dado que "Marina Duarte" pesava 72 kg em janeiro
    E que pesa 70 kg em março
    Quando "Helena" abre a evolução
    Então a avaliação de março mostra variação de -2 kg frente à anterior

  Cenário: Comparar com a primeira avaliação ✅
    Dado avaliações de 75 kg, 72 kg e 70 kg em ordem cronológica
    Quando "Helena" abre a evolução
    Então a última mostra -2 kg frente à anterior
    E -5 kg frente à primeira

  Cenário: Não comparar medida ausente ✅
    Dado que a avaliação de janeiro registrou cintura
    E que a de março não registrou
    Quando "Helena" abre a evolução
    Então nenhuma variação de cintura é apresentada em março
    # Ausência não é redução

  Cenário: Isolar evolução entre consultórios ✅
    Dado que "Helena" registrou avaliações de "Marina Duarte"
    Quando "Bruna" tenta abrir a evolução dessa paciente
    Então recebe o erro 404
```

---

```gherkin
Funcionalidade: Avaliação de criança e adolescente

  Cenário: Classificar pelo escore-z, e não pela faixa adulta ✅
    Dado que "Pedro" tem 8 anos, pesa 24 kg e mede 128 cm
    Quando "Helena" registra a avaliação
    Então o estado nutricional é classificado por IMC-para-idade
    E a curva da OMS usada fica registrada com o resultado
    E a faixa adulta do IMC não é aplicada

  Cenário: Escolher a curva pela idade ✅
    Dado um paciente de 3 anos
    Quando "Helena" registra a avaliação
    Então é usada a curva de 0 a 5 anos
    # Acima de 5 anos vale a referência de 5 a 19 anos, que é outra curva

  Cenário: A faixa de corte do IMC muda aos cinco anos ✅
    Dado o mesmo escore-z de +1,5
    Quando ele é lido aos 48 meses e aos 120 meses
    Então é "risco de sobrepeso" na primeira e "sobrepeso" na segunda
    # Classificar um adolescente pela faixa de criança subestimaria um grau

  Cenário: Baixa estatura é reconhecida ✅
    Dado uma menina de 60 meses com 97 cm
    Quando "Helena" registra a avaliação
    Então o escore-z de estatura fica abaixo de −2 e o quadro é sinalizado

  Cenário: Sem sexo informado não há curva ✅
    Quando "Helena" avalia uma criança sem sexo cadastrado
    Então o sistema não classifica, e diz que as curvas são por sexo

  Cenário: Não classificar sem a idade ✅
    Dado um paciente sem data de nascimento
    Quando "Helena" tenta classificar pelo escore-z
    Então recebe o erro 422
    E a mensagem explica que a curva depende da idade
```

```gherkin
Funcionalidade: Acompanhamento gestacional

  Cenário: Ganho dentro do esperado ✅
    Dado que "Marina Duarte" iniciou a gestação com IMC 22,5
    E está na 20ª semana gestacional
    Quando "Helena" registra ganho acumulado de 6 kg
    Então o ganho é classificado como adequado
    E a faixa recomendada para o IMC pré-gestacional fica registrada

  Cenário: Ganho acima do esperado ✅
    Dado que "Marina Duarte" iniciou a gestação com IMC 22,5
    E está na 20ª semana gestacional
    Quando "Helena" registra ganho acumulado de 13 kg
    Então o ganho é classificado como acima do esperado

  Cenário: A faixa vem do IMC pré-gestacional, e não do atual ✅
    Dado uma gestante que iniciou com IMC 30,1
    Quando "Helena" registra o ganho na 30ª semana
    Então o ganho total recomendado é de 5 a 9 kg, e não de 11,5 a 16

  Cenário: Não classificar sem o peso pré-gestacional ✅
    Dado que o peso pré-gestacional não foi informado
    Quando "Helena" tenta classificar o ganho
    Então o sistema não classifica
    E a mensagem explica que a faixa depende do IMC pré-gestacional
```

---

# 6. Agenda ✅

```gherkin
Funcionalidade: Agendamento de atendimentos

  Cenário: Agendar uma consulta ✅
    Quando "Helena" agenda "Marina Duarte" para 10/09/2026 às 14:00,
      com duração de 60 minutos, do tipo "Primeira consulta"
    Então o atendimento é criado com situação "Agendado"
    E termina às 15:00

  Cenário: Recusar sobreposição de horário ✅
    Dado um atendimento agendado das 14:00 às 15:00
    Quando "Helena" tenta agendar outro das 14:30 às 15:30
    Então recebe o erro 422
    E a mensagem indica o conflito de horário

  Cenário: Atendimentos encostados não são conflito ✅
    Dado um atendimento agendado das 14:00 às 15:00
    Quando "Helena" agenda outro das 15:00 às 16:00
    Então o atendimento é criado
    # O fim de um coincidir com o início de outro é agenda cheia, não erro

  Cenário: Horário liberado por cancelamento ✅
    Dado um atendimento das 14:00 às 15:00 que foi cancelado
    Quando "Helena" agenda outro das 14:00 às 15:00
    Então o atendimento é criado

  Cenário: Agenda de um consultório não conflita com a de outro ✅
    Dado que "Bruna" tem atendimento das 14:00 às 15:00
    Quando "Helena" agenda das 14:00 às 15:00
    Então o atendimento é criado
    # Agendas são independentes por consultório

  Cenário: Listar a agenda de um dia ✅
    Dado três atendimentos em 10/09/2026 e um em 11/09/2026
    Quando "Helena" lista a agenda de 10/09/2026
    Então vê três atendimentos, em ordem de horário
```

```gherkin
Funcionalidade: Desfecho do atendimento

  Esquema do Cenário: Transições válidas ✅
    Dado um atendimento na situação "<de>"
    Quando "Helena" o marca como "<para>"
    Então a operação é <resultado>

    Exemplos:
      | de        | para      | resultado |
      | Agendado  | Confirmado| aceita    |
      | Agendado  | Cancelado | aceita    |
      | Agendado  | Faltou    | aceita    |
      | Confirmado| Realizado | aceita    |
      | Realizado | Agendado  | recusada  |
      | Cancelado | Realizado | recusada  |

  Cenário: Falta fica no histórico do paciente ✅
    Dado um atendimento marcado como falta
    Quando "Helena" abre o histórico de "Marina Duarte"
    Então a falta aparece no histórico
```

```gherkin
Funcionalidade: Assinatura da agenda em calendário externo

  Cenário: O endereço só existe depois de pedido ✅
    Quando "Helena" abre a agenda pela primeira vez
    Então não há endereço de assinatura
    Quando ela gera um
    Então recebe o endereço para assinar no calendário
    # Agenda com nome de paciente não fica exposta por padrão

  Cenário: O calendário recebe os atendimentos ✅
    Dado um atendimento de "Marina Duarte" às 09:00
    Quando o calendário de "Helena" busca o endereço assinado
    Então recebe o arquivo no formato iCalendar
    E o atendimento vem com o horário em UTC, convertido do fuso de Brasília
    # 09:00 em Brasília é 12:00 UTC; sem converter, o evento aparece três horas fora

  Cenário: O mesmo atendimento não vira evento novo a cada busca ✅
    Dado um atendimento já entregue ao calendário
    Quando o calendário busca o endereço outra vez
    Então o atendimento chega com o mesmo identificador
    # Se mudasse, o calendário apagaria e recriaria o evento, e o alerta tocaria de novo

  Cenário: Vírgula no nome do paciente não corta o evento ✅
    Dado o paciente "Marina, Duarte e Silva"
    Quando o calendário busca o endereço
    Então o nome chega inteiro, com a vírgula escapada
    # Vírgula separa valores no formato: sem escapar, o leitor corta no primeiro sobrenome

  Cenário: Gerar outro endereço invalida o anterior ✅
    Dado um endereço já entregue a um calendário
    Quando "Helena" gera outro
    Então o anterior responde 404 e o novo responde a agenda

  Cenário: Desligar a assinatura derruba o feed ✅
    Quando "Helena" desliga a assinatura
    Então o endereço responde 404

  Cenário: O endereço traz só a agenda daquele consultório ✅
    Dado que "Helena" tem "Marina Duarte" na agenda
    Quando o calendário assinado por "Bruna" busca o endereço dela
    Então "Marina Duarte" não aparece
```

---

# 7. Financeiro ✅

```gherkin
Funcionalidade: Lançamentos

  Cenário: Registrar uma receita ✅
    Quando "Helena" lança uma receita de R$ 250,00 em 10/09/2026,
      categoria "Consulta", vinculada a "Marina Duarte"
    Então o lançamento é criado com situação "Pendente"

  Cenário: Recusar valor não positivo ✅
    Quando "Helena" lança um valor de R$ 0,00
    Então recebe o erro 400
    # O que distingue entrada de saída é o tipo, não o sinal do valor

  Cenário: Registrar recebimento ✅
    Dado um lançamento pendente de R$ 250,00
    Quando "Helena" o marca como pago em 12/09/2026
    Então a situação passa a "Pago"
    E a data de recebimento fica registrada

  Cenário: Listar inadimplência ✅
    Dado um lançamento pendente com vencimento em 01/09/2026
    E um lançamento pendente com vencimento em 30/12/2026
    Quando "Helena" lista os vencidos em 15/09/2026
    Então vê apenas o primeiro
```

```gherkin
Funcionalidade: Apuração do período

  Cenário: Apurar resultado ✅
    Dado receitas pagas de R$ 1.000,00 no período
    E receitas pendentes de R$ 300,00 no período
    E despesas pagas de R$ 400,00 no período
    Quando "Helena" apura o período
    Então o total recebido é R$ 1.000,00
    E o total a receber é R$ 300,00
    E as despesas somam R$ 400,00
    E o resultado efetivado é R$ 600,00
    E o resultado previsto é R$ 900,00
    # Efetivado e previsto separados: misturá-los faria o consultório
    # parecer ter dinheiro que ainda não entrou

  Cenário: Isolar financeiro entre consultórios ✅
    Dado que "Bruna" tem receitas no período
    Quando "Helena" apura o mesmo período
    Então os valores de "Bruna" não entram na apuração
```

---

# 8. Questionários e coleta pré-consulta ✅

```gherkin
Funcionalidade: Questionário pré-consulta

  Cenário: Paciente responde sem ter conta ✅
    Dado que "Helena" enviou um questionário para "Marina Duarte"
    Quando "Marina" abre o link recebido e responde
    Então as respostas ficam vinculadas a "Marina Duarte"
    E "Helena" vê as respostas na tela do atendimento
    # Mesmo mecanismo do plano público: quem tem o link, responde

  Cenário: Link respondido não aceita nova resposta ✅
    Dado um questionário já respondido por "Marina Duarte"
    Quando alguém abre o mesmo link outra vez
    Então recebe o erro 404
    E as respostas gravadas permanecem intactas

  Cenário: Recusar envio sem resposta obrigatória ✅
    Dado um questionário com a pergunta "Faz uso de medicamento?" obrigatória
    Quando "Marina" envia sem responder essa pergunta
    Então recebe o erro 422
    E a mensagem aponta a pergunta que falta

  Cenário: Editar o modelo não altera resposta já recebida ✅
    Dado um questionário respondido por "Marina Duarte"
    Quando "Helena" remove uma pergunta do modelo
    Então a resposta de "Marina" continua mostrando a pergunta removida
    # A resposta é o registro de uma consulta; reescrevê-la mudaria o passado
```

```gherkin
Funcionalidade: Questionário com escore

  Cenário: Somar o escore pelas regras do instrumento ✅
    Dado o questionário de rastreamento metabólico respondido por "Marina Duarte"
    Quando "Helena" abre o resultado
    Então o escore é somado pelo sistema
    E a classificação vem da faixa de corte do próprio instrumento
    E o instrumento e a versão ficam registrados com o resultado

  Cenário: Não pontuar questionário incompleto ✅
    Dado um questionário com resposta obrigatória em branco
    Quando "Helena" tenta ver o escore
    Então o escore não é calculado
    E a mensagem diz qual resposta falta
    # Mesma regra das dobras cutâneas: sem o dado exigido, não se estima
```

---

# 9. Exames laboratoriais ✅

```gherkin
Funcionalidade: Catálogo de parâmetros

  Cenário: O sistema traz um catálogo com faixas ✅
    Quando "Helena" abre o catálogo de exames
    Então encontra "Glicemia de jejum" em mg/dL, com faixa de referência

  Cenário: O consultório cadastra parâmetro próprio ✅
    Quando "Helena" cadastra "Selênio sérico" de 70 a 150 µg/L
    Então o parâmetro fica editável e não aparece para "Bruna"
```

```gherkin
Funcionalidade: Classificação contra a referência

  Cenário: Classificar o valor pela faixa ✅
    Dado que "Marina Duarte" tem 34 anos e é do sexo feminino
    Quando "Helena" registra glicemia de 60, 85 e 130 mg/dL
    Então os resultados são classificados como abaixo, normal e acima
    E a referência exibida é "70 a 99"

  Cenário: A faixa depende do sexo ✅
    Quando ferritina de 200 ng/mL é registrada para uma mulher e para um homem
    Então é "acima" para ela (15 a 150) e "normal" para ele (30 a 400)

  Cenário: Sem faixa aplicável, registra sem classificar ✅
    Dado um paciente sem sexo informado
    Quando "Helena" registra ferritina, que só tem faixa por sexo
    Então o valor é gravado sem classificação
    # Sem classificação é diferente de "normal"

  Cenário: Alterar o cadastro não reclassifica o passado ✅
    Dado um exame classificado pela faixa de 10 a 20
    Quando a faixa cadastrada muda
    Então o exame já gravado continua exibindo "10 a 20"
    # A faixa foi copiada para dentro dele na entrada

  Cenário: Unidade diferente não é classificada ✅
    Quando "Helena" registra glicemia de 4,7 mmol/L
    Então o valor é gravado sem classificação
    # 4,7 mmol/L é normal, mas comparar com 70–99 mg/dL diria "abaixo"
```

```gherkin
Funcionalidade: Registro e série

  Cenário: Parâmetro não determinado fica sem valor ✅
    Quando "Helena" registra um exame pedido e ainda não liberado
    Então o resultado fica sem valor e sem classificação, e não como zero

  Cenário: Recusar coleta no futuro ✅
    Quando "Helena" registra uma coleta de amanhã
    Então recebe o erro 400

  Cenário: Aceitar coleta retroativa ✅
    Quando "Helena" registra uma coleta de dois anos atrás
    Então o exame é gravado com aquela data

  Cenário: A série mostra a evolução, com a variação ✅
    Dado glicemias de 120, 105 e 92 mg/dL ao longo de dois anos
    Quando "Helena" abre a série
    Então vê os três pontos do mais antigo para o mais recente
    E a variação do segundo ponto é −15

  Cenário: Série com unidades diferentes é sinalizada ✅
    Dado uma glicemia em mg/dL e outra em mmol/L
    Quando "Helena" abre a série
    Então a série é marcada como de unidades misturadas
    E a variação entre elas não é calculada
```

```gherkin
Funcionalidade: Laudo e solicitação

  Cenário: Anexar e abrir o laudo ✅
    Quando "Helena" anexa o PDF do laudo a um resultado
    Então o exame passa a indicar que tem laudo, e o arquivo pode ser aberto

  Cenário: Exame sem laudo responde 404 ✅
    Quando alguém pede o laudo de um exame que não tem
    Então recebe o erro 404

  Cenário: Registrar o pedido de exames ✅
    Quando "Helena" solicita glicemia e ferritina com jejum de 8 horas
    Então a solicitação fica registrada no histórico do paciente

  Cenário: Solicitação sem exame escolhido é recusada ✅
    Quando "Helena" envia uma solicitação vazia
    Então recebe o erro 400

  Cenário: Um consultório não vê exame de paciente de outro ✅
    Quando "Bruna" tenta listar ou corrigir exames de "Marina Duarte"
    Então recebe o erro 404
```

---

# 10. Orientações nutricionais ✅

```gherkin
Funcionalidade: Biblioteca de orientações

  Cenário: O sistema traz modelos, e eles não são editáveis ✅
    Quando "Helena" abre a biblioteca
    Então vê os modelos que acompanham o sistema
    E tentar editar um deles recebe o erro 422
    # Acervo compartilhado: editar em nome de todos seria decidir por consultório alheio

  Cenário: Duplicar cria a minha versão sem tocar no original ✅
    Quando "Helena" duplica um modelo do sistema
    Então a cópia pertence ao consultório dela e é editável
    E o modelo original continua com o título que tinha

  Cenário: Orientação própria é do consultório ✅
    Dado que "Helena" criou a orientação "Minha conduta"
    Quando "Bruna" lista a biblioteca dela
    Então "Minha conduta" não aparece

  Cenário: A minha orientação vem antes dos modelos ✅
    Dado que "Helena" tem uma orientação própria
    Quando ela abre a biblioteca
    Então a orientação dela aparece antes dos modelos do sistema
```

```gherkin
Funcionalidade: Orientação no plano

  Cenário: Anexar copia o texto para o plano ✅
    Dado a orientação "Como montar o prato" na biblioteca
    Quando "Helena" a anexa ao plano
    Então o plano guarda o título e o corpo, e a origem como procedência

  Cenário: Editar a biblioteca não altera o que já foi anexado ✅
    Dado um plano com a orientação "Beba 2 litros" anexada
    Quando "Helena" corrige o modelo para "Beba 3 litros"
    Então o plano continua dizendo "Beba 2 litros"
    # O paciente recebeu 2 litros; corrigir o modelo depois não reescreve isso

  Cenário: O texto anexado pode ser adaptado ao paciente ✅
    Dado um plano com a orientação anexada
    Quando "Helena" edita o texto dentro do plano
    Então o modelo da biblioteca continua como estava
    # A cópia é o que permite personalizar sem sujar o modelo

  Cenário: Anexar um texto escrito na hora ✅
    Quando "Helena" anexa "Trazer o exame na próxima consulta" sem escolher da biblioteca
    Então a orientação entra no plano sem procedência

  Cenário: Anexo sem texto e sem origem é recusado ✅
    Quando "Helena" anexa uma orientação vazia
    Então recebe o erro 422

  Cenário: As orientações chegam ao paciente pelo link ✅
    Dado um plano publicado com orientação anexada
    Quando "Marina" abre o link
    Então lê a orientação junto do plano

  Cenário: Um consultório não anexa no plano de outro ✅
    Quando "Bruna" tenta anexar uma orientação ao plano de "Helena"
    Então recebe o erro 404

  Cenário: Desanexar tira do plano e mantém a biblioteca ✅
    Quando "Helena" desanexa a orientação
    Então ela some do plano e continua na biblioteca

  Cenário: Remover da biblioteca não apaga o que foi entregue ✅
    Dado um plano com a orientação anexada
    Quando "Helena" remove a orientação da biblioteca
    Então o plano continua exibindo o texto entregue
```

```gherkin
Funcionalidade: Figura na orientação

  Cenário: A figura acompanha o texto até o plano ✅
    Dado a orientação "Como montar o prato" com um desenho anexado
    Quando "Helena" a anexa ao plano
    Então o plano guarda a própria cópia da figura
    # Trocar o desenho no modelo depois não muda o que o paciente já recebeu

  Cenário: A figura chega ao paciente pelo link ✅
    Dado um plano publicado com orientação ilustrada
    Quando "Marina" abre o link
    Então vê a figura junto do texto, sem outra credencial

  Cenário: O link de um plano não abre a figura de outro ✅
    Quando alguém pede a figura de um plano usando o link de outro
    Então recebe o erro 404
    # Como inexistente, e não como proibido: quem tem este link não precisa
    # saber que o outro plano existe

  Cenário: Arquivo que não é imagem é recusado ✅
    Quando "Helena" envia uma planilha como figura da orientação
    Então recebe o erro 422

  Cenário: Orientação sem figura responde 404 no download ✅
    Quando "Helena" pede a figura de uma orientação que não tem nenhuma
    Então recebe o erro 404
```

---

## 11. Rastreabilidade dos cenários

Uma linha por seção deste documento, na mesma ordem. A contagem é dos cenários
escritos aqui: um Esquema do Cenário conta uma vez, embora o teste parametrizado
que o executa rode uma vez por linha de exemplo — é por isso que o número de
testes é bem maior que o de cenários.

| Seção | Cobertos | Especificados | Verificado em |
|---|---|---|---|
| 1. Acesso e isolamento | 13 | — | `AuthenticationFlowTest`, `PatientTest`, `PasswordRecoveryTest` |
| 2. Pacientes | 12 | — | `PatientTest`, `PatientsImportTest` |
| 3. Alimentos e porções | 40 | — | `FoodTest`, `HouseholdMeasureTest`, `FoodsBaseTest`, `RecipeTest` |
| 4. Prescrição | 20 | — | `PrescriptionTest`, `PlanPdfTest` |
| 5. Antropometria | 30 | 1 parcial | `AnthropometryTest`, `GrowthAndPregnancyTest` |
| 6. Agenda | 15 | — | `ScheduleTest`, `ScheduleSubscriptionTest` |
| 7. Financeiro | 6 | — | `FinanceTest` |
| 8. Questionários e coleta pré-consulta | 6 | — | `QuestionnaireTest` |
| 9. Exames laboratoriais | 17 | — | `LabtestTest` |
| 10. Orientações nutricionais | 18 | — | `HandoutTest` |
| **Total** | **177** | **1 parcial** | **284 testes, todos passando** |

O único parcial é o passo que leva o gasto energético à meta do plano (RF68):
está implementado, mas liga uma tela a outra e vive no cliente, que não tem
suíte automatizada. Nenhum cenário deste documento continua apenas especificado.

O número de testes é maior que o de cenários cobertos: parte da suíte verifica
regra que não nasceu de cenário — a concordância da medida caseira
(`PluralMeasureTest`) e a composição da base de alimentos, por exemplo.

---

## 12. Nota sobre a escrita dos cenários

Duas escolhas atravessam este documento e valem ser justificadas.

**Cenários carregam números, não adjetivos.** "Então o IMC é calculado
corretamente" não é verificável — qualquer resultado passaria. "Então o IMC é
24,22" prende o comportamento. Foi assim que o erro de arredondamento na página
do paciente apareceu: 0,23 g de gordura estava sendo exibido como "0 g".

**Cenários negativos são mais frequentes que os positivos.** A maior parte do
risco de um sistema clínico não está em calcular errado, e sim em **afirmar com
segurança o que não se sabe** — total parcial exibido como exato, nutriente não
determinado exibido como zero, composição corporal estimada com dobra faltando.
Por isso há tantos cenários que verificam o sistema se recusando a responder.
