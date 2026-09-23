-- A aba de distribuição do planejamento (páginas 31 e 32).
--
-- O plano passa a guardar o que foi *planejado*, e não só o que foi prescrito.
-- Sem isso não existe a coluna "teórico" que ele compara com a prescrita, nem
-- a diferença, nem a faixa de 95 a 105% que muda a cor do rótulo.
--
-- Sobre o peso: g/kg é sobre o **peso programado**, e não o atual. Foi a
-- resposta dele à pergunta 1. Por isso o peso entra aqui congelado, e não é
-- lido da antropometria na hora de mostrar — o peso atual muda toda consulta,
-- e a distribuição do plano é de uma data.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

-- Distribuição planejada, em porcentagem da energia. Ele usa só porcentagem.
ALTER TABLE meal_plan ADD COLUMN target_protein_pct      DECIMAL(5,2);
ALTER TABLE meal_plan ADD COLUMN target_carbohydrate_pct DECIMAL(5,2);
ALTER TABLE meal_plan ADD COLUMN target_fat_pct          DECIMAL(5,2);

-- O peso que sustenta o g/kg do relatório.
ALTER TABLE meal_plan ADD COLUMN target_weight_kg DECIMAL(6,2);

-- De qual cálculo energético a meta veio, quando veio de um.
-- É o "Importar Dados da Aba de Cálculo" que ele descreve na página 31.
ALTER TABLE meal_plan ADD COLUMN energy_plan_id BIGINT;

ALTER TABLE meal_plan ADD CONSTRAINT fk_meal_plan_energy
    FOREIGN KEY (energy_plan_id) REFERENCES energy_plan (id);
