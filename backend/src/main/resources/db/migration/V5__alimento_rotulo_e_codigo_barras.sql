-- Amplia o alimento para comportar produtos industrializados.
--
-- A TACO cobre alimentos in natura e preparados, mas nao traz o que aparece no
-- rotulo brasileiro: acucares, acucares adicionados, gorduras saturadas e trans.
-- Sem esses campos nao da para prescrever com base em rotulo, nem avaliar
-- adequacao a RDC de rotulagem nutricional.
--
-- O codigo de barras identifica o produto de forma estavel entre importacoes e
-- abre caminho para leitura por camera no aplicativo do paciente.

ALTER TABLE alimento ADD COLUMN codigo_barras VARCHAR(20);

ALTER TABLE alimento ADD COLUMN acucares_g                 DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN acucares_adicionados_g     DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN gorduras_saturadas_g       DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN gorduras_trans_g           DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN gorduras_monoinsaturadas_g DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN gorduras_poliinsaturadas_g DECIMAL(10,4);

-- Busca por codigo de barras precisa ser imediata na tela de prescricao.
CREATE INDEX ix_alimento_codigo_barras ON alimento (codigo_barras);

-- Um mesmo produto nao pode entrar duas vezes pela mesma fonte. Fica no escopo
-- da fonte porque o mesmo EAN pode legitimamente existir na base publica e como
-- copia ajustada de um consultorio.
CREATE UNIQUE INDEX uk_alimento_fonte_barras
    ON alimento (fonte, codigo_barras);
