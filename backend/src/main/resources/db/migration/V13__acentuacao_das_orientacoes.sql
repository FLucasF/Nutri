-- Nota: quebra de linha e chr(10) e nao CHAR(10).
--
-- No H2, CHAR(10) e a funcao que devolve o caractere de codigo 10. No
-- PostgreSQL, CHAR(10) e a declaracao de um tipo de dez caracteres, e a
-- concatenacao vira erro de sintaxe. chr(10) e a mesma funcao nos dois.
-- Isto so apareceu quando as migracoes rodaram num PostgreSQL de verdade:
-- o H2 em modo de compatibilidade aceitava a forma que o Postgres recusa.
--
-- Corrige a acentuacao dos modelos de orientacao.
--
-- Os textos entraram na V12 sem acento, por cautela minha com codificacao. Sao
-- textos que o paciente le no plano e no PDF, e "Hidratacao ao longo do dia"
-- numa folha entregue por um profissional de saude e erro visivel.
--
-- Vai numa migracao nova em vez de editar a V12 porque a V12 ja rodou: alterar
-- um arquivo aplicado quebra a soma de verificacao do Flyway e trava o start
-- de qualquer ambiente que ja o tenha executado.
--
-- UPDATE e nao DELETE + INSERT: um plano pode ja ter anexado um destes modelos,
-- e a orientacao do plano guarda a procedencia por chave estrangeira.

UPDATE orientacao SET
    corpo = 'Divida o prato em quatro partes iguais.' || chr(10) ||
            'Duas partes de legumes e verduras, cruas ou cozidas.' || chr(10) ||
            'Uma parte de arroz, macarrão, batata, mandioca ou pão.' || chr(10) ||
            'Uma parte de carne, ovo, peixe ou leguminosas.' || chr(10) || chr(10) ||
            'Sirva-se uma vez só e coma sentado, sem tela por perto.'
WHERE conta_id IS NULL AND titulo = 'Como montar o prato';

UPDATE orientacao SET
    titulo = 'Hidratação ao longo do dia',
    corpo = 'Beba de 30 a 35 ml de água por quilo de peso, distribuídos no dia.' || chr(10) ||
            'Deixe uma garrafa à vista: sede não é um bom aviso, ela chega tarde.' || chr(10) ||
            'Chá sem açúcar e água de coco contam. Refrigerante e suco de caixa, não.'
WHERE conta_id IS NULL AND titulo = 'Hidratacao ao longo do dia';

UPDATE orientacao SET
    titulo = 'Leitura de rótulo',
    corpo = 'Olhe primeiro a lista de ingredientes, e não a tabela nutricional.' || chr(10) ||
            'Os ingredientes vêm em ordem de quantidade: o primeiro é o que mais tem.' || chr(10) ||
            'Açúcar aparece com muitos nomes — xarope de glicose, dextrose, maltodextrina.' || chr(10) ||
            'Quanto mais curta a lista, menos processado o produto.'
WHERE conta_id IS NULL AND titulo = 'Leitura de rotulo';

UPDATE orientacao SET
    titulo = 'Organização das compras',
    corpo = 'Faça a lista antes de sair e não vá ao mercado com fome.' || chr(10) ||
            'Circule pelas bordas do mercado: é onde ficam hortifrúti, carnes e laticínios.' || chr(10) ||
            'Deixe legumes lavados e porcionados assim que chegar em casa.'
WHERE conta_id IS NULL AND titulo = 'Organizacao das compras';

UPDATE orientacao SET
    corpo = 'Escolha o prato antes de sentar, olhando o cardápio com calma.' || chr(10) ||
            'Peça o molho à parte e prefira grelhado a frito.' || chr(10) ||
            'Uma refeição fora do plano não desfaz a semana. Retome na próxima.'
WHERE conta_id IS NULL AND titulo = 'Comer fora de casa';
