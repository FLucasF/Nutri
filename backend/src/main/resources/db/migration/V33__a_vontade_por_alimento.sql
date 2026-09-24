-- "À vontade" em qualquer alimento do plano por alimentos.
--
-- "Preciso de 'à vontade' em TODOS OS ALIMENTOS." Até aqui a porção sem
-- quantidade só existia no plano qualitativo; no plano por alimentos toda
-- linha exigia número. Salada, folhas, chá: alimentos de densidade calórica
-- perto de zero que o nutricionista prescreve sem medir.
--
-- A regra que ele fechou: o item à vontade fica fora do somatório do dia. Ele
-- continua no cardápio, no PDF e no link como "à vontade", só não soma.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

ALTER TABLE meal_item ADD COLUMN ad_libitum BOOLEAN NOT NULL DEFAULT FALSE;
