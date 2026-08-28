-- Imagem na orientacao nutricional.
--
-- Uma figura de prato dividido vale mais que o paragrafo que a descreve, e e
-- justamente o tipo de material que o paciente consulta na cozinha.
--
-- O binario mora em tabela propria, e nao em coluna de orientacao: se ficasse
-- junto, toda listagem da biblioteca arrastaria as imagens. E o mesmo desenho
-- do laudo de exame. Nome e tipo ficam na orientacao para que "tem imagem?"
-- nao custe uma consulta.
--
-- Ha duas tabelas de binario, e nao uma, porque a orientacao do plano guarda a
-- propria copia. Se ela apontasse para a imagem da biblioteca, trocar a figura
-- depois mudaria o que dezenas de pacientes ja receberam — a mesma razao pela
-- qual o texto e copiado no anexo. O custo e algumas centenas de KB por plano,
-- e a alternativa e um plano entregue que muda sozinho.

ALTER TABLE orientacao ADD COLUMN imagem_nome VARCHAR(200);
ALTER TABLE orientacao ADD COLUMN imagem_tipo VARCHAR(100);

CREATE TABLE imagem_de_orientacao (
    orientacao_id  BIGINT PRIMARY KEY,
    conteudo       BYTEA     NOT NULL,
    criado_em      TIMESTAMP NOT NULL,
    atualizado_em  TIMESTAMP,
    criado_por     VARCHAR(180),
    atualizado_por VARCHAR(180),
    CONSTRAINT fk_imagem_orientacao FOREIGN KEY (orientacao_id) REFERENCES orientacao (id)
);

ALTER TABLE orientacao_do_plano ADD COLUMN imagem_nome VARCHAR(200);
ALTER TABLE orientacao_do_plano ADD COLUMN imagem_tipo VARCHAR(100);

CREATE TABLE imagem_do_plano (
    orientacao_do_plano_id BIGINT PRIMARY KEY,
    conteudo               BYTEA     NOT NULL,
    criado_em              TIMESTAMP NOT NULL,
    atualizado_em          TIMESTAMP,
    criado_por             VARCHAR(180),
    atualizado_por         VARCHAR(180),
    CONSTRAINT fk_imagem_plano FOREIGN KEY (orientacao_do_plano_id)
        REFERENCES orientacao_do_plano (id)
);
