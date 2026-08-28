-- Nutrientes trazidos pela tabela do IBGE (POF 2008-2009), ausentes na TACO.
--
-- Os quatro primeiros têm peso clínico direto e nenhuma das bases anteriores
-- os trazia: B12 e ferro orientam avaliação de dieta vegetariana, folato pesa
-- na gestação, e vitamina D é rotineiramente investigada em idosos.

ALTER TABLE alimento ADD COLUMN selenio_mcg      DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN vitamina_b12_mcg DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN folato_mcg       DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN vitamina_d_mcg   DECIMAL(10,4);
ALTER TABLE alimento ADD COLUMN vitamina_e_mg    DECIMAL(10,4);
