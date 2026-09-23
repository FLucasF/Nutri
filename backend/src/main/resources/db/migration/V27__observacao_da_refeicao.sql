-- A observação da refeição passa a ser texto formatado.
--
-- "Abaixo dos alimentos estará um bloco de texto que eu poderei colocar minhas
-- observações." É o mesmo editor do resto do sistema, e o documento em JSON
-- ocupa algumas vezes o tamanho do texto que carrega — daí 8000 e não 1000.
--
-- O que já está gravado é frase, não JSON. Não há UPDATE aqui de propósito:
-- converter em SQL exigiria montar JSON com concatenação de string, e o
-- resultado seria pior do que a conversão feita na entrada, onde o texto
-- antigo vira um documento de um parágrafo e continua dizendo o que dizia.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

ALTER TABLE meal ALTER COLUMN notes SET DATA TYPE VARCHAR(8000);
