-- O pedido de exame ganha grupos e a chance de desligar um exame sem apaga-lo.
--
-- "Digamos que eu apertei Marcadores Glicidicos e aparecem uns 6 fatores, eu
-- tenho que ter a liberdade de retirar quantos eu quiser, e os que forem
-- ficando la e o que irao para o PDF, separados pelos agrupamentos deles. E
-- nao e para excluir diretamente se eu quiser que ele nao va para o PDF, mas
-- que ele fique inativo, para caso eu precise reativar eu nao tenha que
-- refazer a area."
--
-- panel_name guarda de que painel o exame veio (nulo quando foi marcado a
-- mao); active e o interruptor: desligado, o exame fica no pedido e sai do
-- PDF.
--
-- So usa o subconjunto comum a H2 e PostgreSQL (AD-11).

ALTER TABLE ordered_parameter ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ordered_parameter ADD COLUMN panel_name VARCHAR(120);
