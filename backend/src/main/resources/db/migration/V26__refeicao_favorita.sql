-- Refeições favoritas.
--
-- "Favoritar Refeição, que irá salvar a refeição para que eu possa utilizar
-- depois", e do outro lado "um botão em que eu posso acessar refeições que eu
-- salvei".
--
-- Uma refeição favorita é uma refeição sem plano. Não são tabelas novas
-- paralelas a `meal`, `meal_item` e `item_substitution`: seriam três tabelas
-- com as mesmas colunas, três mapeamentos e duas rotinas de cópia que teriam
-- que ser mantidas iguais para sempre. Aqui a cópia que já existe serve para
-- as duas direções.
--
-- O que torna isso seguro é que `meal.plan` não é lido em lugar nenhum fora do
-- próprio agregado — só `MealPlan` o atribui.
--
-- A favorita é uma **cópia**, não uma referência. Apagar o plano de origem não
-- pode levar junto a refeição que ele salvou.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

-- `DROP NOT NULL` é a forma que H2 e PostgreSQL aceitam; `SET NULL` só o H2.
ALTER TABLE meal ALTER COLUMN plan_id DROP NOT NULL;

ALTER TABLE meal ADD COLUMN account_id BIGINT;
ALTER TABLE meal ADD COLUMN favorite_name VARCHAR(150);

ALTER TABLE meal ADD CONSTRAINT fk_meal_account
    FOREIGN KEY (account_id) REFERENCES account (id);

-- A invariante, escrita onde ela não pode ser esquecida: ou a refeição
-- pertence a um plano, ou é uma favorita de um consultório e tem nome.
ALTER TABLE meal ADD CONSTRAINT ck_meal_plan_ou_favorita CHECK (
    plan_id IS NOT NULL
    OR (account_id IS NOT NULL AND favorite_name IS NOT NULL)
);

CREATE INDEX ix_meal_favorita ON meal (account_id, favorite_name);
