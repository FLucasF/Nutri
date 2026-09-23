-- Todo campo de observação passa a ser texto formatado.
--
-- "Todo local que eu consiga digitar um texto, tipo observações, deve ter essa
-- opção que já existe em um que eu não achei, de formatar texto e talz,
-- colocar coluna."
--
-- O editor já existia — na anamnese, na observação da refeição e nas
-- orientações — e é o mesmo que traz negrito, lista, tamanho de fonte e
-- tabela com coluna. O que faltava era ele estar nos outros lugares onde se
-- escreve.
--
-- O documento em JSON ocupa algumas vezes o tamanho do texto que carrega: um
-- parágrafo de 200 caracteres vira uns 500, e uma tabela de preparo passa
-- fácil de mil. Por isso cada coluna sobe, e o modo de preparo sobe mais que
-- as outras — é o que vai receber a tabela de ingredientes.
--
-- Não há UPDATE convertendo o que já está gravado. O texto antigo é frase, não
-- JSON, e convertê-lo em SQL exigiria montar documento por concatenação de
-- string. A conversão acontece na entrada, onde a frase antiga vira um
-- documento de um parágrafo e continua dizendo exatamente o que dizia.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

-- O modo de preparo da receita. É o maior porque é o que o cliente quer com
-- tabela, e porque ele agora viaja junto para o PDF do cardápio.
ALTER TABLE food ALTER COLUMN instructions_mode SET DATA TYPE VARCHAR(20000);

-- As orientações que o paciente lê, e a anotação que só o nutricionista vê.
ALTER TABLE meal_plan ALTER COLUMN handouts SET DATA TYPE VARCHAR(20000);
ALTER TABLE meal_plan ALTER COLUMN internal_notes SET DATA TYPE VARCHAR(20000);

-- A observação de um alimento dentro da refeição — "depois do ovo, não antes".
-- Cabia 500 caracteres, que não dá nem para o documento de uma linha.
ALTER TABLE meal_item ALTER COLUMN notes SET DATA TYPE VARCHAR(8000);

-- As observações clínicas: avaliação, cálculo energético, cadastro do paciente
-- e o bloco de anotações do prontuário.
ALTER TABLE anthropometric_assessment ALTER COLUMN notes SET DATA TYPE VARCHAR(8000);
ALTER TABLE energy_plan ALTER COLUMN notes SET DATA TYPE VARCHAR(8000);
ALTER TABLE patient ALTER COLUMN notes SET DATA TYPE VARCHAR(8000);
ALTER TABLE patient_note ALTER COLUMN body SET DATA TYPE VARCHAR(8000);
