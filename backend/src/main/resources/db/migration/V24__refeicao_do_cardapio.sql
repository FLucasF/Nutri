-- A refeição como o cliente a descreve nas páginas 32 a 35.
--
-- Três coisas entram aqui, e cada uma resolve um pedido explícito:
--
--   1. `in_calculation` na refeição. Ele chama de "Calcular refeição": um
--      clique tira a refeição da contabilização do dia, que é como se
--      prescreve refeição substituta — duas opções de almoço não somam duas
--      vezes. O total da própria refeição continua sendo calculado, porque ele
--      precisa saber quanto vale a opção que não está somando.
--
--   2. `kind` no item. Ele descreve a barra que o Word cria com "---" e Enter,
--      para separar o que é para comer junto: "eu quero que o paciente coma o
--      farelo de aveia com o mamão". Um separador é uma posição na ordem dos
--      itens, e não um alimento — daí ser um tipo de item e não uma tabela
--      nova. Ele nunca entra no total: não tem grama para somar.
--
--   3. A foto da refeição, que ele quer subir e ver sair no PDF. Nome e tipo
--      ficam na refeição, o binário numa tabela à parte, como já é feito com a
--      imagem da orientação.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

ALTER TABLE meal ADD COLUMN in_calculation BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE meal ADD COLUMN photo_name VARCHAR(200);
ALTER TABLE meal ADD COLUMN photo_type VARCHAR(100);

CREATE TABLE meal_photo (
    meal_id    BIGINT NOT NULL PRIMARY KEY,
    content    BYTEA     NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by VARCHAR(180),
    updated_by VARCHAR(180),
    CONSTRAINT fk_meal_photo_meal FOREIGN KEY (meal_id) REFERENCES meal (id)
);

-- FOOD | SEPARATOR. O DEFAULT preenche as linhas que já existem.
ALTER TABLE meal_item ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'FOOD';

-- O nome da refeição passa a caber uma frase.
--
-- Ele dá o exemplo: "Café da Manhã – Mamão c/ Farelo de Aveia, Pão c/ Ovos
-- Fritos e Café c/ Açúcar". Cem caracteres cortavam isso no meio.
ALTER TABLE meal ALTER COLUMN name SET DATA TYPE VARCHAR(250);
