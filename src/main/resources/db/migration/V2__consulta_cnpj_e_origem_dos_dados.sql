-- Checkpoint 3, Etapa 4.

-- Cache das consultas públicas de CNPJ (BrasilAPI / Receita Federal).
-- Guarda só dados públicos de empresas; CPF nunca é consultado nem gravado aqui.
-- encontrado = false registra um CNPJ que a fonte respondeu como inexistente (404).
CREATE TABLE consulta_cnpj_cache (
    cnpj varchar(14) NOT NULL,
    encontrado boolean NOT NULL,
    razao_social varchar(255),
    nome_fantasia varchar(255),
    situacao_cadastral varchar(50),
    data_situacao_cadastral date,
    fonte varchar(30) NOT NULL,
    consultado_em timestamp(6) NOT NULL,
    CONSTRAINT consulta_cnpj_cache_pkey PRIMARY KEY (cnpj)
);

-- De onde veio cada dado informado na análise (texto do PDF, código de barras, OCR, IA ou digitado),
-- quando o usuário confirmou os dados e o banco que ele esperava (opcional).
ALTER TABLE boletos ADD COLUMN fonte_linha varchar(20);
ALTER TABLE boletos ADD COLUMN fonte_nome varchar(20);
ALTER TABLE boletos ADD COLUMN fonte_documento varchar(20);
ALTER TABLE boletos ADD COLUMN fonte_valor varchar(20);
ALTER TABLE boletos ADD COLUMN fonte_vencimento varchar(20);
ALTER TABLE boletos ADD COLUMN dados_conferidos_em timestamp(6);
ALTER TABLE boletos ADD COLUMN instituicao_esperada varchar(3);
