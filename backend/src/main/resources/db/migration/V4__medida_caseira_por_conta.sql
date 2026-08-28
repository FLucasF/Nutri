-- Medidas caseiras passam a ter dono opcional.
--
-- conta_id NULO  -> porcao do acervo base, visivel para todos os consultorios.
-- conta_id CHEIO -> porcao criada por um consultorio, visivel so para ele.
--
-- Sem isso um nutricionista nao conseguiria registrar "1 colher de sopa" para
-- um alimento da TACO, ja que a base publica nao e editavel. O paciente precisa
-- da porcao usual: ninguem serve 5 g de sal, serve uma pitada.

ALTER TABLE medida_caseira ADD COLUMN conta_id BIGINT;

ALTER TABLE medida_caseira
    ADD CONSTRAINT fk_medida_conta FOREIGN KEY (conta_id) REFERENCES conta (id);

CREATE INDEX ix_medida_conta ON medida_caseira (conta_id);

-- Consulta quente: medidas visiveis de um alimento (as do acervo + as da conta).
CREATE INDEX ix_medida_alimento_conta ON medida_caseira (alimento_id, conta_id);
